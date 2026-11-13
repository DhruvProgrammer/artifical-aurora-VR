package com.auroraar.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.auroraar.BuildConfig
import com.auroraar.R
import com.auroraar.calibration.CalibrationRecorder
import com.auroraar.camera.CameraSession
import com.auroraar.camera.CameraStatus
import com.auroraar.capture.Entitlements
import com.auroraar.capture.GuardRefusal
import com.auroraar.capture.GuardWarning
import com.auroraar.capture.PendingRecordingCleanup
import com.auroraar.capture.RecordingError
import com.auroraar.capture.RecordingPhase
import com.auroraar.capture.caps.DeviceRecordingCaps
import com.auroraar.data.SettingsStore
import com.auroraar.premium.FeatureGate
import com.auroraar.premium.styles.AuroraStyle
import com.auroraar.skyscore.SkyScoreEngine
import com.auroraar.skyscore.model.Gate
import com.auroraar.skyscore.model.MessageId
import com.auroraar.skyscore.model.Mode
import com.auroraar.skyscore.model.UserSettings
import com.auroraar.ui.components.AuroraOrb
import com.auroraar.ui.components.CalibrationSheet
import com.auroraar.ui.components.CustomizeSheet
import com.auroraar.ui.components.DaylightCard
import com.auroraar.ui.components.GallerySheet
import com.auroraar.ui.components.MessageBubble
import com.auroraar.ui.components.PauseResumeButton
import com.auroraar.ui.components.PhotoButton
import com.auroraar.ui.components.PremiumInfoSheet
import com.auroraar.ui.components.RecordButton
import com.auroraar.ui.components.RecordingPreviewDialog
import com.auroraar.ui.components.RecordingTimerPill
import com.auroraar.ui.components.SettingsSheet
import com.auroraar.ui.components.SkyScoreChip
import com.auroraar.ui.components.SkyScoreSheet
import com.auroraar.ui.components.VerticalIntensityRail
import com.auroraar.ui.theme.AuroraGreen
import com.auroraar.ui.theme.NightBase
import com.auroraar.ui.theme.TextMuted
import com.auroraar.ui.theme.TextPrimary
import com.auroraar.ui.theme.WarmAmber
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AuroraRoot() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) denied = true
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }
    // The user may grant the permission from system settings and come back.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = hasCameraPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (granted) {
        CameraScreen()
    } else {
        PermissionScreen(
            showSettings = denied,
            onRequest = { launcher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            },
        )
    }
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun hasMicPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

@Composable
fun PermissionScreen(
    showSettings: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBase)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.brand),
                style = MaterialTheme.typography.labelSmall,
                color = AuroraGreen,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.permission_body),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(26.dp))
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuroraGreen,
                    contentColor = Color(0xFF04231A),
                ),
            ) {
                Text(stringResource(R.string.permission_button))
            }
            if (showSettings) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.permission_settings), color = AuroraGreen)
                }
            }
        }
    }
}

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val store = remember { SettingsStore(context) }

    var settings by remember { mutableStateOf(store.load()) }
    var useLocation by remember { mutableStateOf(store.useLocation) }
    var recordAudio by remember { mutableStateOf(store.recordAudio) }
    var countdownEnabled by remember { mutableStateOf(store.countdown) }
    var holdToRecord by remember { mutableStateOf(store.holdToRecord) }
    var holdStopPending by remember { mutableStateOf(false) }
    var photoBusy by remember { mutableStateOf(false) }

    val entitlements = remember { Entitlements(context) }
    val isPremium by entitlements.isPremium.collectAsStateWithLifecycle()
    val caps = remember { DeviceRecordingCaps.probe() }
    val engine = remember {
        SkyScoreEngine(FeatureGate.effectiveSettings(settings, entitlements.isPremium.value))
    }
    val session = remember { CameraSession(context, lifecycleOwner, engine, entitlements) }
    val recorder = remember { CalibrationRecorder(context) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    val conditions by engine.conditions.collectAsStateWithLifecycle()
    val renderParams by engine.renderParams.collectAsStateWithLifecycle()
    val cameraStatus by session.status.collectAsStateWithLifecycle()
    val recordingAvailable by session.recordingAvailable.collectAsStateWithLifecycle()
    val auroraInPreview by session.auroraInPreview.collectAsStateWithLifecycle()
    val recording by session.recording.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var overlay by remember { mutableStateOf<CameraOverlay>(CameraOverlay.None) }
    var previewJob by remember { mutableStateOf<Job?>(null) }
    var pendingPremium by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var daylightDismissedAt by remember { mutableLongStateOf(0L) }
    var elevation by remember { mutableFloatStateOf(0f) }

    var visibleMessage by remember { mutableStateOf<MessageId?>(null) }
    var lastShown by remember { mutableStateOf<Pair<MessageId, Long>?>(null) }

    var calibRecording by remember { mutableStateOf(false) }
    var calibSamples by remember { mutableIntStateOf(0) }
    var calibPhase by remember { mutableStateOf("night") }
    var calibOkta by remember { mutableIntStateOf(2) }
    var calibEnvironment by remember { mutableStateOf("rural") }

    fun applySettings(update: UserSettings) {
        settings = update
        engine.setSettings(FeatureGate.effectiveSettings(update, isPremium))
        store.save(update)
    }

    fun startRecordingNow(withAudio: Boolean) {
        if (recording.active) return
        if (session.recording.start(withAudio)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } else {
            Toast.makeText(context, R.string.record_error_start, Toast.LENGTH_LONG).show()
        }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(context, R.string.record_mic_denied, Toast.LENGTH_SHORT).show()
        }
        startRecordingNow(withAudio = granted && recordAudio)
    }

    fun requestStart() {
        if (recording.active) return
        if (recordAudio && !hasMicPermission(context)) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startRecordingNow(withAudio = recordAudio && hasMicPermission(context))
        }
    }

    fun startHoldRecording() {
        if (countdown > 0) {
            countdown = 0
            return
        }
        if (recording.active || recording.phase == RecordingPhase.PREPARING) return
        if (recording.phase == RecordingPhase.SAVED || recording.phase == RecordingPhase.ERROR) {
            session.recording.reset()
        }
        // A gesture cannot wait for a runtime-permission dialog. Hold-to-record
        // starts silent if the mic was never granted; audio can be enabled later.
        startRecordingNow(withAudio = recordAudio && hasMicPermission(context))
    }

    fun endHoldRecording() {
        if (countdown > 0) {
            countdown = 0
            return
        }
        when (pendingHoldStop(recording.phase)) {
            PendingHoldStop.STOP -> session.recording.stop()
            PendingHoldStop.WAIT -> holdStopPending = true
            PendingHoldStop.DISCARD -> Unit
        }
    }

    fun capturePhoto() {
        if (photoBusy) return
        if (recording.active) {
            Toast.makeText(context, R.string.photo_blocked_recording, Toast.LENGTH_SHORT).show()
            return
        }
        photoBusy = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        session.takePhoto(
            onSaved = {
                photoBusy = false
                Toast.makeText(context, R.string.photo_saved, Toast.LENGTH_LONG).show()
            },
            onError = {
                photoBusy = false
                Toast.makeText(context, R.string.photo_failed, Toast.LENGTH_LONG).show()
            },
        )
    }

    fun closePreview(reset: Boolean = true) {
        if (overlay is CameraOverlay.Preview) overlay = CameraOverlay.None
        if (reset) session.recording.reset()
    }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1_000L)
            if (countdown == 1) {
                countdown = 0
                requestStart()
            } else {
                countdown -= 1
            }
        }
    }

    LaunchedEffect(recording.phase, recording.uri) {
        val uri = recording.uri
        if (recording.phase == RecordingPhase.SAVED && uri != null) {
            overlay = CameraOverlay.Preview(uri)
        }
    }

    // The locked-style demo ends after 3 s. If a sheet is open, or a take is
    // running, show the paywall on the next clean transition instead of
    // replacing the user's surface or covering the camera.
    LaunchedEffect(overlay, pendingPremium, recording.active, countdown, isPremium) {
        if (!pendingPremium) return@LaunchedEffect
        if (isPremium) {
            pendingPremium = false
            return@LaunchedEffect
        }
        if (overlay != CameraOverlay.None || recording.active || countdown > 0) {
            return@LaunchedEffect
        }
        pendingPremium = false
        overlay = CameraOverlay.Premium
    }

    LaunchedEffect(recording.error) {
        val message = when (recording.error) {
            RecordingError.START_FAILED, null -> return@LaunchedEffect
            RecordingError.STORAGE -> R.string.record_error_storage
            RecordingError.ENCODER_FAILED -> R.string.record_error_encoder
            RecordingError.SOURCE_INACTIVE -> R.string.record_error_source
            RecordingError.UNKNOWN -> R.string.record_error_unknown
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(recording.warnings) {
        val warning = recording.warnings.firstOrNull() ?: return@LaunchedEffect
        val message = when (warning) {
            GuardWarning.LOW_STORAGE -> R.string.record_warn_storage
            GuardWarning.LOW_BATTERY -> R.string.record_warn_battery
            GuardWarning.THERMAL -> R.string.record_warn_thermal
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(recording.stoppedForSafety) {
        val reason = recording.stoppedForSafety ?: return@LaunchedEffect
        val message = when (reason) {
            GuardRefusal.STORAGE -> R.string.record_stopped_storage
            GuardRefusal.BATTERY -> R.string.record_stopped_battery
            GuardRefusal.THERMAL -> R.string.record_stopped_thermal
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            useLocation = true
            store.useLocation = true
        }
    }

    LaunchedEffect(Unit) { session.recorder = recorder }
    LaunchedEffect(useLocation) { session.useLocation = useLocation }
    LaunchedEffect(renderParams) { session.setRenderParams(renderParams) }
    LaunchedEffect(isPremium) {
        engine.setSettings(FeatureGate.effectiveSettings(settings, isPremium))
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { PendingRecordingCleanup.clean(context) }
    }
    LaunchedEffect(recording.phase, holdStopPending) {
        if (!holdStopPending) return@LaunchedEffect
        when (pendingHoldStop(recording.phase)) {
            PendingHoldStop.WAIT -> Unit
            PendingHoldStop.STOP -> {
                holdStopPending = false
                session.recording.stop()
            }
            PendingHoldStop.DISCARD -> holdStopPending = false
        }
    }
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    LaunchedEffect(configuration.orientation) {
        session.onDisplayRotationChanged(
            previewView.display?.rotation ?: Surface.ROTATION_0,
        )
    }

    DisposableEffect(recorder) {
        onDispose { recorder.stop() }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> session.start(previewView)
                Lifecycle.Event.ON_STOP -> session.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            session.start(previewView)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            session.close()
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                elevation = session.cameraElevationDeg
                delay(500)
            }
        }
    }

    // Key only on the message. Keying on a timestamp bucket restarted this
    // effect every 20 s; if that happened during the 4.2 s visible window, the
    // early return cancelled the hide and left the bubble on screen forever.
    LaunchedEffect(conditions.message) {
        val message = conditions.message
        if (message == null) {
            visibleMessage = null
            return@LaunchedEffect
        }
        while (true) {
            val now = System.currentTimeMillis()
            val previous = lastShown
            if (previous != null && previous.first == message && now - previous.second < 59_000L) {
                // Same message inside its cooldown: wait the remainder. A
                // message change cancels this effect and starts fresh.
                delay(59_000L - (now - previous.second))
            }
            lastShown = message to System.currentTimeMillis()
            visibleMessage = message
            delay(4_200L)
            visibleMessage = null
            // Re-show a still-relevant message about once a minute.
            delay(54_800L)
        }
    }

    LaunchedEffect(overlay) {
        while (overlay == CameraOverlay.Calibration) {
            calibSamples = recorder.sampleCount
            delay(1_000L)
        }
    }

    fun exportAsync(onDone: (File) -> Unit, onError: () -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { recorder.export() } }
            result.fold(
                onSuccess = { file -> runCatching { onDone(file) } },
                onFailure = { onError() },
            )
        }
    }

    val shareCalibration: () -> Unit = {
        exportAsync(
            onDone = { file ->
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.calibration_share)))
                } catch (t: Throwable) {
                    Toast.makeText(context, R.string.export_failed, Toast.LENGTH_LONG).show()
                }
            },
            onError = {
                Toast.makeText(context, R.string.export_failed, Toast.LENGTH_LONG).show()
            },
        )
    }

    val showDaylight = settings.mode == Mode.AUTO &&
        conditions.gate == Gate.BLOCKED_DAYLIGHT &&
        cameraStatus != CameraStatus.ERROR &&
        elevation > -25f &&
        System.currentTimeMillis() - daylightDismissedAt > 10 * 60 * 1000L &&
        overlay == CameraOverlay.None &&
        !recording.active && countdown == 0

    val contextualHint = when {
        settings.mode == Mode.CREATIVE -> R.string.creative_badge
        cameraStatus == CameraStatus.ERROR -> R.string.camera_error_title
        conditions.timestampMs == 0L -> R.string.hint_loading
        conditions.gate == Gate.CAMERA_BLOCKED -> R.string.msg_camera_covered
        conditions.gate == Gate.NO_SKY -> R.string.hint_no_sky
        conditions.gate == Gate.BLOCKED_DAYLIGHT -> R.string.phase_day
        else -> R.string.hint_tilt
    }

    val chromeAlpha by animateFloatAsState(
        targetValue = if (recording.active) 0.25f else 1f,
        label = "chromeAlpha",
    )

    val limitTickSecond = if (recording.phase == RecordingPhase.RECORDING) {
        recording.limitMs?.let { limit ->
            val remaining = limit - recording.elapsedMs
            if (remaining in 1L..3_000L) ((remaining + 999L) / 1000L).toInt() else null
        }
    } else {
        null
    }
    LaunchedEffect(limitTickSecond) {
        if (limitTickSecond != null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun toggleRecord() {
        if (countdown > 0) {
            countdown = 0
            return
        }
        when (recording.phase) {
            RecordingPhase.RECORDING,
            RecordingPhase.PAUSED,
            RecordingPhase.PREPARING,
            RecordingPhase.FINALIZING,
            -> session.recording.stop()

            else -> {
                if (recording.phase == RecordingPhase.SAVED || recording.phase == RecordingPhase.ERROR) {
                    session.recording.reset()
                }
                if (countdownEnabled) countdown = 3 else requestStart()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.brand),
                        style = MaterialTheme.typography.labelSmall,
                        color = AuroraGreen,
                    )
                    Text(
                        text = stringResource(R.string.brand_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (settings.mode == Mode.CREATIVE) WarmAmber else TextMuted,
                    )
                }
                if (recording.active) {
                    RecordingTimerPill(
                        phase = recording.phase,
                        elapsedMs = recording.elapsedMs,
                        limitMs = recording.limitMs,
                        hasAudio = recording.hasAudio,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                if (settings.hudEnabled && settings.mode != Mode.OFF && !recording.active) {
                    Box(Modifier.alpha(if (recording.active) 0.55f else 1f)) {
                        SkyScoreChip(
                            score = conditions.skyScore,
                            enabled = conditions.gate == Gate.OPEN,
                            // One surface at a time: a sheet opened mid-take
                            // would be replaced by the preview when it saves.
                            onClick = {
                                if (!recording.active) overlay = CameraOverlay.SkyScore
                            },
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(
                    onClick = { overlay = CameraOverlay.Settings },
                    enabled = !recording.active,
                    modifier = Modifier
                        .size(48.dp)
                        .alpha(chromeAlpha)
                        .background(Color(0x660A0F16), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings),
                        tint = TextPrimary,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(visible = visibleMessage != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MessageBubble(text = visibleMessage?.let { messageText(it) } ?: "")
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (!auroraInPreview) {
                Text(
                    text = stringResource(R.string.aurora_inactive),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = WarmAmber,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuickAction(
                    text = stringResource(R.string.customize_open),
                    enabled = !recording.active,
                    onClick = { overlay = CameraOverlay.Customize },
                )
                Spacer(Modifier.width(8.dp))
                QuickAction(
                    text = stringResource(R.string.gallery_open),
                    enabled = !recording.active,
                    onClick = { overlay = CameraOverlay.Gallery },
                )
                if (!isPremium) {
                    Spacer(Modifier.width(8.dp))
                    QuickAction(
                        text = stringResource(R.string.style_tier_premium),
                        enabled = !recording.active,
                        accent = WarmAmber,
                        onClick = { overlay = CameraOverlay.Premium },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    AuroraOrb(
                        mode = settings.mode,
                        enabled = true,
                        onToggle = {
                            val next = if (settings.mode == Mode.OFF) Mode.AUTO else Mode.OFF
                            applySettings(settings.copy(mode = next))
                        },
                        onModeSelected = { mode ->
                            applySettings(settings.copy(mode = mode))
                            if (mode == Mode.CREATIVE) {
                                daylightDismissedAt = System.currentTimeMillis()
                            }
                        },
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (recording.phase == RecordingPhase.RECORDING ||
                        recording.phase == RecordingPhase.PAUSED
                    ) {
                        PauseResumeButton(
                            paused = recording.phase == RecordingPhase.PAUSED,
                            enabled = true,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (recording.phase == RecordingPhase.PAUSED) {
                                    session.recording.resume()
                                } else {
                                    session.recording.pause()
                                }
                            },
                        )
                    }
                    RecordButton(
                        phase = recording.phase,
                        progress = recording.limitMs?.let { limit ->
                            if (limit <= 0) null
                            else (recording.elapsedMs.toFloat() / limit).coerceIn(0f, 1f)
                        },
                        enabled = recordingAvailable && cameraStatus == CameraStatus.RUNNING &&
                            recording.phase != RecordingPhase.FINALIZING,
                        onClick = { toggleRecord() },
                        holdToRecord = holdToRecord,
                        onHoldStart = { startHoldRecording() },
                        onHoldEnd = { endHoldRecording() },
                    )
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier.alpha(chromeAlpha),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PhotoButton(
                            busy = photoBusy,
                            enabled = recordingAvailable && cameraStatus == CameraStatus.RUNNING &&
                                !recording.active,
                            onClick = { capturePhoto() },
                        )
                        FlipButton(
                            enabled = !recording.active,
                            alpha = 1f,
                            onClick = {
                                if (!session.flipCamera()) {
                                    Toast.makeText(context, R.string.camera_error_body, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (!recordingAvailable) {
                    stringResource(R.string.recording_unavailable)
                } else {
                    stringResource(contextualHint)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                style = MaterialTheme.typography.bodySmall,
                color = if (!recordingAvailable) WarmAmber else TextMuted.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }

        // Right-edge quick intensity rail. Always available, including while recording.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
        ) {
            VerticalIntensityRail(
                value = settings.intensity,
                onValueChange = { applySettings(settings.copy(intensity = it)) },
            )
        }

        if (!recordingAvailable) {
            Text(
                text = stringResource(R.string.recording_unavailable),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .systemBarsPadding()
                    .padding(top = 76.dp),
                style = MaterialTheme.typography.labelSmall,
                color = WarmAmber,
            )
        }

        if (countdown > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xB305060B))
                    .clickable { countdown = 0 },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = countdown.toString(),
                        fontSize = 96.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.record_countdown_cancel),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
            }
        }

        if (cameraStatus == CameraStatus.ERROR) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xF20A0F16)),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.camera_error_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.camera_error_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { session.start(previewView) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AuroraGreen,
                                contentColor = Color(0xFF04231A),
                            ),
                        ) {
                            Text(stringResource(R.string.camera_retry))
                        }
                    }
                }
            }
        }

        if (showDaylight) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                DaylightCard(
                    onTryCreative = {
                        applySettings(settings.copy(mode = Mode.CREATIVE))
                        daylightDismissedAt = System.currentTimeMillis()
                    },
                    onDismiss = { daylightDismissedAt = System.currentTimeMillis() },
                )
            }
        }
    }

    if (overlay == CameraOverlay.SkyScore) {
        SkyScoreSheet(
            conditions = conditions,
            showSubScores = settings.showSubScores,
            onDismiss = { overlay = CameraOverlay.None },
        )
    }

    if (overlay == CameraOverlay.Settings) {
        SettingsSheet(
            settings = settings,
            useLocation = useLocation,
            versionName = BuildConfig.VERSION_NAME,
            recordAudio = recordAudio,
            countdown = countdownEnabled,
            holdToRecord = holdToRecord,
            caps = caps,
            isPremium = isPremium,
            onSettingsChange = { applySettings(it) },
            onUseLocationChange = { enabled ->
                if (enabled) {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        useLocation = true
                        store.useLocation = true
                    } else {
                        locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                } else {
                    useLocation = false
                    store.useLocation = false
                }
            },
            onRecordAudioChange = {
                recordAudio = it
                store.recordAudio = it
            },
            onCountdownChange = {
                countdownEnabled = it
                store.countdown = it
            },
            onHoldToRecordChange = {
                holdToRecord = it
                store.holdToRecord = it
            },
            onPremiumChange = {
                entitlements.setPremium(it)
                session.onEntitlementsChanged()
            },
            onOpenCalibration = { overlay = CameraOverlay.Calibration },
            onDismiss = { overlay = CameraOverlay.None },
        )
    }

    if (overlay == CameraOverlay.Customize) {
        CustomizeSheet(
            settings = settings,
            isPremium = isPremium,
            onSettingsChange = { applySettings(it) },
            onLockedStyle = { style ->
                previewJob?.cancel()
                overlay = CameraOverlay.None
                val restore = settings
                // Render the locked style for 3 s with the gate bypassed, then
                // restore persisted Free settings and open the paywall.
                engine.setSettings(
                    FeatureGate.effectiveSettings(restore.copy(styleId = style.id), isPremium = true),
                )
                Toast.makeText(context, R.string.customize_locked_body, Toast.LENGTH_LONG).show()
                previewJob = scope.launch {
                    delay(3_000L)
                    engine.setSettings(FeatureGate.effectiveSettings(restore, isPremium))
                    if (!isPremium) pendingPremium = true
                }
            },
            onLockedControl = { overlay = CameraOverlay.Premium },
            onDismiss = { overlay = CameraOverlay.None },
        )
    }

    if (overlay == CameraOverlay.Gallery) {
        GallerySheet(onDismiss = { overlay = CameraOverlay.None })
    }

    if (overlay == CameraOverlay.Premium) {
        PremiumInfoSheet(
            isPremium = isPremium,
            debugBuild = BuildConfig.DEBUG,
            onPremiumChange = {
                entitlements.setPremium(it)
                session.onEntitlementsChanged()
            },
            onDismiss = { overlay = CameraOverlay.None },
        )
    }

    val preview = overlay as? CameraOverlay.Preview
    if (preview != null) {
        RecordingPreviewDialog(
            uri = preview.uri,
            tier = recording.tier,
            autoStopped = recording.autoStopped,
            onShare = { shareRecording(context, preview.uri) },
            onDelete = {
                if (!deleteRecording(context, preview.uri)) {
                    Toast.makeText(context, R.string.record_delete_failed, Toast.LENGTH_LONG).show()
                }
                closePreview()
            },
            onRecordAgain = {
                closePreview()
                requestStart()
            },
            onSeePremium = { overlay = CameraOverlay.Premium },
            onDismiss = { closePreview() },
        )
    }

    if (overlay == CameraOverlay.Calibration) {
        CalibrationSheet(
            recording = calibRecording,
            sampleCount = calibSamples,
            phase = calibPhase,
            okta = calibOkta,
            environment = calibEnvironment,
            onPhaseChange = {
                calibPhase = it
                recorder.phaseLabel = it
            },
            onOktaChange = {
                calibOkta = it
                recorder.okta = it
            },
            onEnvironmentChange = {
                calibEnvironment = it
                recorder.environment = it
            },
            onToggleRecording = {
                if (calibRecording) {
                    recorder.stop()
                    calibRecording = false
                } else {
                    recorder.setLabels(calibPhase, calibOkta, calibEnvironment)
                    recorder.start()
                    calibRecording = true
                    calibSamples = 0
                }
            },
            onExport = {
                exportAsync(
                    onDone = { file ->
                        Toast.makeText(
                            context,
                            context.getString(R.string.calibration_saved, file.name),
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                    onError = {
                        Toast.makeText(context, R.string.export_failed, Toast.LENGTH_LONG).show()
                    },
                )
            },
            onShare = shareCalibration,
            onDismiss = {
                recorder.stop()
                calibRecording = false
                overlay = CameraOverlay.None
            },
        )
    }
}

@Composable
private fun QuickAction(
    text: String,
    enabled: Boolean = true,
    accent: Color = TextPrimary,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = accent.copy(alpha = if (enabled) 1f else 0.4f),
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color(0x990A0F16))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(percent = 50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun FlipButton(
    enabled: Boolean,
    alpha: Float,
    onClick: () -> Unit,
) {
    val description = stringResource(R.string.camera_flip)
    Box(
        modifier = Modifier
            .size(48.dp)
            .alpha(alpha)
            .background(Color(0xCC0A0F16), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "⇄",
            fontSize = 22.sp,
            color = TextPrimary,
        )
    }
}

private fun shareRecording(context: Context, uri: Uri) {
    try {
        val shareUri = if (uri.scheme == "file") {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(uri.path ?: error("missing path")),
            )
        } else {
            uri
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.record_share_caption))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.record_share)))
    } catch (t: Throwable) {
        Toast.makeText(context, R.string.record_share_failed, Toast.LENGTH_LONG).show()
    }
}

private fun deleteRecording(context: Context, uri: Uri): Boolean = runCatching {
    if (uri.scheme == "file") {
        File(uri.path ?: return@runCatching false).delete()
    } else {
        context.contentResolver.delete(uri, null, null) > 0
    }
}.getOrDefault(false)
