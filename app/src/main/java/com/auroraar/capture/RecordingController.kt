package com.auroraar.capture

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Single-owner recording state machine. CameraX's [Recorder] does encoding,
 * muxing, audio and MediaStore finalisation; this class owns policy, the Free
 * limit, pause bookkeeping and the lifecycle of the [Recording].
 *
 * All public mutators must be called on the main thread (CameraX requires it);
 * [recordedElapsedMs] is safe from the GL thread.
 */
class RecordingController(
    private val context: Context,
    private val entitlements: Entitlements,
    private val guards: DeviceGuards,
    private val onFinalized: () -> Unit,
) : RecordingClock {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(
        RecordingUiState(tier = entitlements.policy.tier),
    )
    val state: StateFlow<RecordingUiState> = _state.asStateFlow()

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var ticker: Job? = null
    private var limitJob: Job? = null
    private var safetyJob: Job? = null
    private val audioManager: AudioManager? = context.getSystemService(AudioManager::class.java)
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    @Volatile
    private var segmentStartMs = 0L

    @Volatile
    private var accumulatedMs = 0L

    @Volatile
    private var closed = false

    fun attach(capture: VideoCapture<Recorder>) {
        videoCapture = capture
    }

    fun detach() {
        videoCapture = null
    }

    fun refreshTier() {
        _state.update { it.copy(tier = entitlements.policy.tier) }
    }

    fun start(withAudio: Boolean): Boolean {
        if (closed || _state.value.active) return false
        val policy = entitlements.policy
        val guard = guards.check()
        if (!guard.canRecord) {
            _state.value = RecordingUiState(
                phase = RecordingPhase.ERROR,
                error = guard.refusal.toError(),
                tier = policy.tier,
                limitMs = policy.maxRecordMs,
            )
            return false
        }

        val capture = videoCapture
        if (capture == null) {
            _state.value = RecordingUiState(
                phase = RecordingPhase.ERROR,
                error = RecordingError.START_FAILED,
                tier = policy.tier,
            )
            return false
        }

        val audioRequested = withAudio && hasAudioPermission()
        val prepared = try {
            val pending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                capture.output.prepareRecording(context, mediaStoreOptions())
            } else {
                capture.output.prepareRecording(context, fileOptions())
            }
            if (audioRequested) enableAudio(pending) else pending
        } catch (t: Throwable) {
            Log.e(TAG, "prepareRecording failed", t)
            _state.value = RecordingUiState(
                phase = RecordingPhase.ERROR,
                error = RecordingError.START_FAILED,
                tier = policy.tier,
            )
            return false
        }

        accumulatedMs = 0L
        segmentStartMs = 0L
        _state.value = RecordingUiState(
            phase = RecordingPhase.PREPARING,
            limitMs = policy.maxRecordMs,
            tier = policy.tier,
            hasAudio = audioRequested,
            warnings = guard.warnings,
        )

        recording = try {
            prepared.start(ContextCompat.getMainExecutor(context)) { event -> onEvent(event) }
        } catch (t: Throwable) {
            Log.e(TAG, "Recording start failed", t)
            _state.value = RecordingUiState(
                phase = RecordingPhase.ERROR,
                error = RecordingError.START_FAILED,
                tier = policy.tier,
            )
            return false
        }
        startTicker()
        return true
    }

    fun pause() {
        if (_state.value.phase != RecordingPhase.RECORDING) return
        runCatching { recording?.pause() }
            .onFailure { Log.w(TAG, "pause failed", it) }
    }

    fun resume() {
        if (_state.value.phase != RecordingPhase.PAUSED) return
        requestAudioFocus()
        runCatching { recording?.resume() }
            .onFailure { Log.w(TAG, "resume failed", it) }
    }

    /** Stops and finalises; the UI shows the preview when [RecordingPhase.SAVED] arrives. */
    fun stop() = stopInternal(autoStopped = false)

    fun reset() {
        if (_state.value.active) return
        _state.value = RecordingUiState(tier = entitlements.policy.tier)
    }

    override fun recordedElapsedMs(): Long? = when (_state.value.phase) {
        RecordingPhase.RECORDING ->
            accumulatedMs + (SystemClock.elapsedRealtime() - segmentStartMs)
        RecordingPhase.PAUSED, RecordingPhase.FINALIZING -> accumulatedMs
        else -> null
    }

    fun close() {
        closed = true
        ticker?.cancel()
        limitJob?.cancel()
        safetyJob?.cancel()
        abandonAudioFocus()
        runCatching { recording?.close() }
        recording = null
        scope.cancel()
    }

    private fun stopInternal(autoStopped: Boolean, safety: GuardRefusal? = null) {
        val phase = _state.value.phase
        if (phase != RecordingPhase.RECORDING &&
            phase != RecordingPhase.PAUSED &&
            phase != RecordingPhase.PREPARING
        ) {
            return
        }
        if (phase == RecordingPhase.RECORDING) {
            accumulatedMs = recordedElapsedMs() ?: accumulatedMs
        }
        limitJob?.cancel()
        limitJob = null
        safetyJob?.cancel()
        safetyJob = null
        _state.update {
            it.copy(
                phase = RecordingPhase.FINALIZING,
                elapsedMs = accumulatedMs,
                autoStopped = it.autoStopped || autoStopped,
                stoppedForSafety = safety ?: it.stoppedForSafety,
            )
        }
        runCatching { recording?.stop() }
            .onFailure { Log.w(TAG, "stop failed", it) }
    }

    private fun onEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Start -> {
                segmentStartMs = SystemClock.elapsedRealtime()
                accumulatedMs = 0L
                _state.update {
                    it.copy(phase = RecordingPhase.RECORDING, elapsedMs = 0L)
                }
                requestAudioFocus()
                scheduleLimit()
                startSafetyMonitor()
            }

            is VideoRecordEvent.Pause -> {
                accumulatedMs = recordedElapsedMs() ?: accumulatedMs
                limitJob?.cancel()
                limitJob = null
                _state.update {
                    it.copy(phase = RecordingPhase.PAUSED, elapsedMs = accumulatedMs)
                }
            }

            is VideoRecordEvent.Resume -> {
                segmentStartMs = SystemClock.elapsedRealtime()
                _state.update { it.copy(phase = RecordingPhase.RECORDING) }
                scheduleLimit()
            }

            is VideoRecordEvent.Finalize -> finalize(event)
            else -> Unit
        }
    }

    private fun finalize(event: VideoRecordEvent.Finalize) {
        ticker?.cancel()
        ticker = null
        limitJob?.cancel()
        limitJob = null
        safetyJob?.cancel()
        safetyJob = null
        abandonAudioFocus()
        recording = null

        if (event.hasError()) {
            Log.e(TAG, "Recording finalised with error ${event.error}: ${event.cause?.message}")
            _state.update {
                it.copy(
                    phase = RecordingPhase.ERROR,
                    error = mapError(event.error),
                    autoStopped = false,
                )
            }
        } else {
            val uri = event.outputResults?.outputUri
            if (uri == null || uri == Uri.EMPTY) {
                _state.update {
                    it.copy(phase = RecordingPhase.ERROR, error = RecordingError.UNKNOWN)
                }
            } else {
                scanLegacyFile(uri)
                _state.update { it.copy(phase = RecordingPhase.SAVED, uri = uri) }
            }
        }
        onFinalized()
    }

    private fun scheduleLimit() {
        limitJob?.cancel()
        val limit = _state.value.limitMs ?: return
        val elapsed = recordedElapsedMs() ?: return
        val remaining = (limit - elapsed).coerceAtLeast(0L)
        limitJob = scope.launch {
            delay(remaining)
            if (_state.value.phase == RecordingPhase.RECORDING) {
                stopInternal(autoStopped = true)
            }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                val elapsed = recordedElapsedMs() ?: accumulatedMs
                _state.update { it.copy(elapsedMs = elapsed) }
                val limit = _state.value.limitMs
                if (limit != null &&
                    _state.value.phase == RecordingPhase.RECORDING &&
                    elapsed >= limit
                ) {
                    stopInternal(autoStopped = true)
                }
            }
        }
    }

    /**
     * Mid-recording safety net from §7.3. CameraX cannot change resolution inside
     * an open file, so storage/battery/thermal hard limits are handled with a
     * clean stop: the muxer finalises, the clip stays playable, and the UI
     * explains why it ended.
     */
    private fun startSafetyMonitor() {
        safetyJob?.cancel()
        safetyJob = scope.launch {
            while (isActive) {
                delay(SAFETY_CHECK_MS)
                val phase = _state.value.phase
                if (phase != RecordingPhase.RECORDING && phase != RecordingPhase.PAUSED) continue
                val check = guards.check()
                if (check.warnings != _state.value.warnings) {
                    _state.update { it.copy(warnings = check.warnings) }
                }
                val refusal = check.refusal
                if (refusal != null) {
                    Log.w(TAG, "Safety stop: $refusal")
                    stopInternal(autoStopped = false, safety = refusal)
                    return@launch
                }
            }
        }
    }

    /**
     * Audio focus makes calls and other playback interrupt the take instead of
     * recording over them. Loss pauses; the user can resume or stop and save.
     */
    private fun requestAudioFocus() {
        if (!_state.value.hasAudio || hasAudioFocus) return
        val manager = audioManager ?: return
        val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(
                { change ->
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        -> {
                            hasAudioFocus = false
                            if (_state.value.phase == RecordingPhase.RECORDING) {
                                Log.i(TAG, "Audio focus lost; pausing the take")
                                pause()
                            }
                        }
                    }
                },
                Handler(Looper.getMainLooper()),
            )
            .build()
        audioFocusRequest = request
        hasAudioFocus =
            manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (!hasAudioFocus) return
        audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
        hasAudioFocus = false
    }

    private fun mediaStoreOptions(): MediaStoreOutputOptions {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName())
            put(MediaStore.Video.Media.MIME_TYPE, MIME)
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/" + FOLDER,
            )
            put(MediaStore.Video.Media.TITLE, DISCLOSURE)
            put(MediaStore.Video.Media.DESCRIPTION, DISCLOSURE)
        }
        return MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        ).setContentValues(values).build()
    }

    private fun fileOptions(): FileOutputOptions {
        val dir = guards.recordingsDir().apply { mkdirs() }
        return FileOutputOptions.Builder(File(dir, fileName())).build()
    }

    private fun fileName(): String = "AuroraAR_" + STAMP.format(Date()) + ".mp4"

    /** API 26–28 files live in app-specific storage; index them so galleries can see them. */
    private fun scanLegacyFile(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return
        val path = uri.path ?: return
        runCatching {
            MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(MIME), null)
        }
    }

    /**
     * The [hasAudioPermission] guard above is the runtime check. Lint only sees
     * the permission annotation on this call, so suppress it here where the
     * invariant is explicit and keep the start() call site readable.
     */
    @SuppressLint("MissingPermission")
    private fun enableAudio(pending: PendingRecording): PendingRecording =
        pending.withAudioEnabled()

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun GuardRefusal?.toError(): RecordingError = when (this) {
        GuardRefusal.STORAGE -> RecordingError.STORAGE
        GuardRefusal.BATTERY, GuardRefusal.THERMAL -> RecordingError.START_FAILED
        null -> RecordingError.UNKNOWN
    }

    private fun mapError(code: Int): RecordingError = when (code) {
        VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> RecordingError.STORAGE
        VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE -> RecordingError.SOURCE_INACTIVE
        VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED -> RecordingError.ENCODER_FAILED
        else -> RecordingError.UNKNOWN
    }

    private companion object {
        const val TAG = "RecordingController"
        const val TICK_MS = 100L
        const val SAFETY_CHECK_MS = 2_000L
        const val MIME = "video/mp4"
        const val FOLDER = "AuroraAR"
        const val DISCLOSURE = "Simulated aurora – made with Aurora AR"
        val STAMP = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
    }
}
