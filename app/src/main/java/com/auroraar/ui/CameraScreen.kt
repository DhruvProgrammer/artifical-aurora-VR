package com.auroraar.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auroraar.BuildConfig
import com.auroraar.R
import com.auroraar.calibration.CalibrationRecorder
import com.auroraar.camera.CameraSession
import com.auroraar.data.SettingsStore
import com.auroraar.render.AuroraGlView
import com.auroraar.skyscore.SkyScoreEngine
import com.auroraar.skyscore.model.Gate
import com.auroraar.skyscore.model.MessageId
import com.auroraar.skyscore.model.Mode
import com.auroraar.skyscore.model.UserSettings
import com.auroraar.ui.components.CalibrationSheet
import com.auroraar.ui.components.DaylightCard
import com.auroraar.ui.components.MessageBubble
import com.auroraar.ui.components.ModePill
import com.auroraar.ui.components.SettingsSheet
import com.auroraar.ui.components.SkyScoreChip
import com.auroraar.ui.components.SkyScoreSheet
import com.auroraar.ui.theme.AuroraGreen
import com.auroraar.ui.theme.AuroraTheme
import com.auroraar.ui.theme.NightBase
import com.auroraar.ui.theme.TextMuted
import com.auroraar.ui.theme.TextPrimary
import com.auroraar.ui.theme.WarmAmber
import kotlinx.coroutines.delay

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
    val store = remember { SettingsStore(context) }

    var settings by remember { mutableStateOf(store.load()) }
    var useLocation by remember { mutableStateOf(store.useLocation) }

    val engine = remember { SkyScoreEngine(settings) }
    val session = remember { CameraSession(context, lifecycleOwner, engine) }
    val recorder = remember { CalibrationRecorder(context) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    val conditions by engine.conditions.collectAsStateWithLifecycle()
    val renderParams by engine.renderParams.collectAsStateWithLifecycle()

    var showSheet by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showCalibration by remember { mutableStateOf(false) }
    var daylightDismissedAt by remember { mutableStateOf(0L) }
    var elevation by remember { mutableStateOf(0f) }

    var visibleMessage by remember { mutableStateOf<MessageId?>(null) }
    var lastShown by remember { mutableStateOf<Pair<MessageId, Long>?>(null) }

    var calibRecording by remember { mutableStateOf(false) }
    var calibSamples by remember { mutableStateOf(0) }
    var calibPhase by remember { mutableStateOf("night") }
    var calibOkta by remember { mutableStateOf(2) }
    var calibEnvironment by remember { mutableStateOf("rural") }

    fun applySettings(update: UserSettings) {
        settings = update
        engine.setSettings(update)
        store.save(update)
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            useLocation = true
            store.useLocation = true
        }
    }

    LaunchedEffect(Unit) { session.recorder = recorder }
    LaunchedEffect(useLocation) { session.useLocation = useLocation }

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
            session.stop()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            elevation = session.cameraElevationDeg
            delay(400)
        }
    }

    LaunchedEffect(conditions.message, conditions.timestampMs / 20_000L) {
        val message = conditions.message
        if (message == null) {
            visibleMessage = null
            return@LaunchedEffect
        }
        val now = System.currentTimeMillis()
        val previous = lastShown
        if (previous != null && previous.first == message && now - previous.second < 59_000L) {
            return@LaunchedEffect
        }
        lastShown = message to now
        visibleMessage = message
        delay(4_200L)
        visibleMessage = null
    }

    LaunchedEffect(showCalibration) {
        while (showCalibration) {
            calibSamples = recorder.sampleCount
            delay(1_000L)
        }
    }

    val shareCalibration: () -> Unit = {
        try {
            val file = recorder.export()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.calibration_share)))
        } catch (t: Throwable) {
            Toast.makeText(context, t.message ?: "Export failed", Toast.LENGTH_LONG).show()
        }
    }

    val showDaylight = settings.mode == Mode.AUTO &&
        conditions.gate == Gate.BLOCKED_DAYLIGHT &&
        elevation > -25f &&
        System.currentTimeMillis() - daylightDismissedAt > 10 * 60 * 1000L &&
        !showSheet && !showSettings && !showCalibration

    val subline = when {
        settings.mode == Mode.CREATIVE -> stringResource(R.string.creative_badge)
        conditions.gate == Gate.NO_SKY -> stringResource(R.string.hint_no_sky)
        conditions.gate == Gate.BLOCKED_DAYLIGHT -> stringResource(R.string.phase_day)
        else -> stringResource(R.string.hint_tilt)
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        AndroidView(
            factory = { AuroraGlView(context) },
            modifier = Modifier.fillMaxSize(),
            update = { it.setParams(renderParams) },
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
                        text = subline,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (settings.mode == Mode.CREATIVE) WarmAmber else TextMuted,
                    )
                }
                if (settings.hudEnabled && settings.mode != Mode.OFF) {
                    SkyScoreChip(
                        score = conditions.skyScore,
                        enabled = conditions.gate == Gate.OPEN,
                        onClick = { showSheet = true },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier
                        .size(40.dp)
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                ModePill(
                    mode = settings.mode,
                    onModeChange = { mode ->
                        applySettings(settings.copy(mode = mode))
                        if (mode == Mode.CREATIVE) daylightDismissedAt = System.currentTimeMillis()
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.hint_tilt),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
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

    if (showSheet) {
        SkyScoreSheet(
            conditions = conditions,
            showSubScores = settings.showSubScores,
            onDismiss = { showSheet = false },
        )
    }

    if (showSettings) {
        SettingsSheet(
            settings = settings,
            useLocation = useLocation,
            versionName = BuildConfig.VERSION_NAME,
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
            onOpenCalibration = {
                showSettings = false
                showCalibration = true
            },
            onDismiss = { showSettings = false },
        )
    }

    if (showCalibration) {
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
                    recorder.phaseLabel = calibPhase
                    recorder.okta = calibOkta
                    recorder.environment = calibEnvironment
                    recorder.start()
                    calibRecording = true
                    calibSamples = 0
                }
            },
            onExport = {
                val file = recorder.export()
                Toast.makeText(
                    context,
                    context.getString(R.string.calibration_saved, file.absolutePath),
                    Toast.LENGTH_LONG,
                ).show()
            },
            onShare = shareCalibration,
            onDismiss = { showCalibration = false },
        )
    }
}
