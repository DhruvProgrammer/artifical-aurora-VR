package com.auroraar.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.auroraar.calibration.CalibrationRecorder
import com.auroraar.capture.CompositeEffect
import com.auroraar.capture.CompositeProcessor
import com.auroraar.capture.DeviceGuards
import com.auroraar.capture.Entitlements
import com.auroraar.capture.PhotoSaver
import com.auroraar.capture.RecordingController
import com.auroraar.skyscore.SkyScoreEngine
import com.auroraar.skyscore.features.AstronomyPrior
import com.auroraar.skyscore.model.AstroPrior
import com.auroraar.skyscore.model.AuroraRenderParams
import com.auroraar.skyscore.model.EngineInput
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Camera binding state so the UI can offer a retry instead of silently failing. */
enum class CameraStatus { IDLE, STARTING, RUNNING, ERROR }

/**
 * Binds CameraX Preview + VideoCapture + ImageAnalysis with one composite effect
 * shared by Preview and VideoCapture. SkyScore still reads the raw camera stream
 * through ImageAnalysis so it never depends on the rendered overlay.
 */
class CameraSession(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val engine: SkyScoreEngine,
    private val entitlements: Entitlements,
) {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val extractor = SkyFeatureExtractor()
    private val exposureReader = ExposureReader()
    private val lightReader = LightSensorReader(context)
    private val poseReader = PoseReader(context)
    private val locationProvider = LocationProvider(context)
    private val deviceGuards = DeviceGuards(context)

    val recording = RecordingController(
        context = context,
        entitlements = entitlements,
        guards = deviceGuards,
        onFinalized = { onRecordingFinalized() },
    )

    @Volatile
    private var renderParams: AuroraRenderParams = AuroraRenderParams.IDLE

    private val processor = CompositeProcessor(
        context = context,
        params = { renderParams },
        watermarkAlpha = { if (recording.state.value.watermarkVisible) WATERMARK_ALPHA else 0f },
        clock = recording,
        onError = { t ->
            Log.e(TAG, "Composite effect failed", t)
            ContextCompat.getMainExecutor(context).execute {
                if (recording.state.value.active) recording.stop()
            }
        },
    )
    private val videoEffect = CompositeEffect(
        CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE,
        processor,
    ) { t -> Log.w(TAG, "Camera effect error", t) }
    private val previewEffect = CompositeEffect(CameraEffect.PREVIEW, processor) { t ->
        Log.w(TAG, "Camera effect error", t)
    }

    private val _status = MutableStateFlow(CameraStatus.IDLE)
    val status: StateFlow<CameraStatus> = _status.asStateFlow()

    private val _recordingAvailable = MutableStateFlow(true)
    val recordingAvailable: StateFlow<Boolean> = _recordingAvailable.asStateFlow()

    private val _auroraInPreview = MutableStateFlow(true)
    val auroraInPreview: StateFlow<Boolean> = _auroraInPreview.asStateFlow()

    @Volatile
    var recorder: CalibrationRecorder? = null

    @Volatile
    var useLocation: Boolean = false
        set(value) {
            field = value
            astroCacheMs = 0L
            locationCacheMs = 0L
        }

    @Volatile
    private var running = false

    @Volatile
    private var closed = false

    private var provider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null
    private var activePreview: Preview? = null
    private var activeVideo: VideoCapture<Recorder>? = null
    private var selector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private var pendingUnbind = false
    private var lastAcceptedMs = 0L

    // Location + solar position are refreshed on a slow cadence, not per frame.
    private var astroCache: AstroPrior? = null

    @Volatile
    private var astroCacheMs = 0L
    private var locationCache: Pair<Double, Double>? = null

    @Volatile
    private var locationCacheMs = 0L

    val cameraElevationDeg: Float
        get() = poseReader.elevationDeg

    fun setRenderParams(params: AuroraRenderParams) {
        renderParams = params
    }

    fun start(previewView: PreviewView) {
        if (closed || running) return
        running = true
        // A start supersedes any deferred unbind from the previous take. Its
        // finalize callback must not tear down the pipeline we bind here.
        pendingUnbind = false
        this.previewView = previewView
        _status.value = CameraStatus.STARTING
        lightReader.start()
        poseReader.start()

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!running) {
                runCatching { future.get().unbindAll() }
                return@addListener
            }
            try {
                val cameraProvider = future.get()
                if (!running) return@addListener
                provider = cameraProvider
                if (!bindWithFallback(previewView)) {
                    throw IllegalStateException("No supported camera pipeline")
                }
                _status.value = CameraStatus.RUNNING
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to bind camera", t)
                runCatching { provider?.unbindAll() }
                provider = null
                running = false
                lightReader.stop()
                poseReader.stop()
                _status.value = CameraStatus.ERROR
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        running = false
        lightReader.stop()
        poseReader.stop()
        if (recording.state.value.active) {
            // Keep the session bound until CameraX has written the MP4 trailer.
            pendingUnbind = true
            recording.stop()
        } else {
            unbindNow()
        }
        if (_status.value != CameraStatus.IDLE) _status.value = CameraStatus.IDLE
    }

    /** Releases the analysis thread. Call once when the screen leaves composition. */
    fun close() {
        closed = true
        running = false
        lightReader.stop()
        poseReader.stop()
        recording.close()
        unbindNow()
        processor.release()
        analysisExecutor.shutdownNow()
        ioScope.cancel()
    }

    /** Rebuilds the camera pipeline after the user switches tier (quality changes). */
    fun onEntitlementsChanged() {
        recording.refreshTier()
        val view = previewView ?: return
        if (!running || recording.state.value.active) return
        bindWithFallback(view)
    }

    /** Rear ⇄ front. Returns false when unavailable or while recording. */
    fun flipCamera(): Boolean {
        if (recording.state.value.active) return false
        val cameraProvider = provider ?: return false
        val next = if (selector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        val exists = runCatching { cameraProvider.hasCamera(next) }.getOrDefault(false)
        if (!exists) return false
        selector = next
        val view = previewView ?: return true
        if (running) bindWithFallback(view)
        return true
    }

    fun onDisplayRotationChanged(rotation: Int) {
        if (!running || recording.state.value.active) return
        activePreview?.targetRotation = rotation
        activeVideo?.targetRotation = rotation
    }

    /**
     * Still capture from the next composited frame. Photos are not allowed
     * during a take; the prompt keeps photo rules separate from video rules.
     */
    fun takePhoto(onSaved: (android.net.Uri) -> Unit, onError: () -> Unit) {
        if (recording.state.value.active) {
            onError()
            return
        }
        val answered = AtomicBoolean(false)
        processor.requestPhoto { bitmap ->
            if (!answered.compareAndSet(false, true)) {
                bitmap.recycle()
                return@requestPhoto
            }
            ioScope.launch {
                val uri = PhotoSaver.save(context, bitmap)
                bitmap.recycle()
                ContextCompat.getMainExecutor(context).execute {
                    if (uri != null) onSaved(uri) else onError()
                }
            }
        }
        ioScope.launch {
            delay(3_000L)
            if (answered.compareAndSet(false, true)) {
                ContextCompat.getMainExecutor(context).execute { onError() }
            }
        }
    }

    private fun bindWithFallback(view: PreviewView): Boolean {
        val cameraProvider = provider ?: return false
        // Temporal noise compares consecutive frames. A new bind may be a
        // different camera, or the first frame after a long unbind, so drop
        // the previous frame instead of differencing against it.
        extractor.resetTemporal()
        if (tryBind(cameraProvider, view, withVideo = true)) return true
        Log.w(TAG, "Preview + Video + effect unsupported; trying preview effect only")
        _recordingAvailable.value = false
        if (tryBind(cameraProvider, view, withVideo = false)) return true
        Log.w(TAG, "Composite effect unsupported; falling back to plain preview")
        _auroraInPreview.value = false
        return bindPlain(cameraProvider, view)
    }

    private fun tryBind(
        cameraProvider: ProcessCameraProvider,
        view: PreviewView,
        withVideo: Boolean,
    ): Boolean {
        val rotation = view.display?.rotation ?: Surface.ROTATION_0
        val preview = Preview.Builder()
            .setTargetRotation(rotation)
            .build()
            .also { it.setSurfaceProvider(view.surfaceProvider) }

        val analysis = analysisUseCase()

        val group = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(analysis)

        var video: VideoCapture<Recorder>? = null
        if (withVideo) {
            val policy = entitlements.policy
            val target = if (policy.allows1080p) Quality.FHD else Quality.HD
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        target,
                        FallbackStrategy.lowerQualityOrHigherThan(target),
                    ),
                )
                .build()
            video = VideoCapture.Builder(recorder)
                .setTargetRotation(rotation)
                .build()
            group.addUseCase(video)
            group.addEffect(videoEffect)
        } else {
            group.addEffect(previewEffect)
        }

        return try {
            runCatching { cameraProvider.unbindAll() }
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, group.build())
            activePreview = preview
            activeVideo = video
            if (video != null) {
                recording.attach(video)
                _recordingAvailable.value = true
            } else {
                recording.detach()
            }
            _auroraInPreview.value = true
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Bind failed (video=$withVideo)", t)
            runCatching { cameraProvider.unbindAll() }
            activePreview = null
            activeVideo = null
            recording.detach()
            false
        }
    }

    private fun bindPlain(
        cameraProvider: ProcessCameraProvider,
        view: PreviewView,
    ): Boolean {
        val rotation = view.display?.rotation ?: Surface.ROTATION_0
        val preview = Preview.Builder()
            .setTargetRotation(rotation)
            .build()
            .also { it.setSurfaceProvider(view.surfaceProvider) }
        return try {
            runCatching { cameraProvider.unbindAll() }
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                analysisUseCase(),
            )
            activePreview = preview
            activeVideo = null
            recording.detach()
            _recordingAvailable.value = false
            _auroraInPreview.value = false
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Plain preview bind failed", t)
            _status.value = CameraStatus.ERROR
            false
        }
    }

    private fun analysisUseCase(): ImageAnalysis {
        @Suppress("DEPRECATION")
        val builder = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
        exposureReader.attach(builder)
        return builder.build().also {
            it.setAnalyzer(analysisExecutor) { proxy -> analyze(proxy) }
        }
    }

    private fun onRecordingFinalized() {
        if (!pendingUnbind) return
        pendingUnbind = false
        // stop() can be followed by start() before CameraX finishes writing the
        // MP4 trailer (ON_STOP → ON_START on a quick app switch). When that
        // happens a new pipeline is already bound; unbinding it here would
        // leave a black preview with a live status of RUNNING.
        if (!running) unbindNow()
    }

    private fun unbindNow() {
        runCatching { provider?.unbindAll() }
        activePreview = null
        activeVideo = null
        recording.detach()
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            if (!running) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastAcceptedMs < ANALYSIS_INTERVAL_MS) return
            lastAcceptedMs = now

            val features = extractor.analyze(proxy, poseReader.elevationDeg)

            val input = EngineInput(
                features = features,
                exposure = exposureReader.latest,
                lux = lightReader.lux,
                astro = astroForFrame(),
                cameraElevationDeg = poseReader.elevationDeg,
                nowMs = now,
            )
            val conditions = engine.onInput(input)
            recorder?.maybeRecord(features, conditions, input.exposure, input.lux, input.cameraElevationDeg)
        } catch (t: Throwable) {
            Log.w(TAG, "Frame analysis failed", t)
        } finally {
            proxy.close()
        }
    }

    private fun astroForFrame(): AstroPrior {
        val wallMs = System.currentTimeMillis()
        val stale = astroCache == null || wallMs < astroCacheMs || wallMs - astroCacheMs > ASTRO_REFRESH_MS
        if (stale) {
            val latLon = if (useLocation) locationForFrame(wallMs) else null
            astroCache = AstronomyPrior.forTime(wallMs, latLon?.first, latLon?.second)
            astroCacheMs = wallMs
        }
        return astroCache ?: AstronomyPrior.forTime(wallMs, null, null)
    }

    private fun locationForFrame(wallMs: Long): Pair<Double, Double>? {
        if (!hasLocationPermission()) return null
        val stale = locationCache == null ||
            wallMs < locationCacheMs ||
            wallMs - locationCacheMs > LOCATION_REFRESH_MS
        if (stale) {
            locationCache = locationProvider.lastKnown()
            locationCacheMs = wallMs
        }
        return locationCache
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        private const val TAG = "CameraSession"
        private const val ANALYSIS_INTERVAL_MS = 200L
        private const val ASTRO_REFRESH_MS = 30_000L
        private const val LOCATION_REFRESH_MS = 10 * 60_000L
        private const val WATERMARK_ALPHA = 0.7f
    }
}
