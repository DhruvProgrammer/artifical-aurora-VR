package com.auroraar.ui.components

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.auroraar.R
import com.auroraar.capture.RecordingPhase
import com.auroraar.capture.RecordingTier
import com.auroraar.capture.formatDuration
import com.auroraar.skyscore.model.Mode
import com.auroraar.ui.theme.AuroraGreen
import com.auroraar.ui.theme.AuroraTeal
import com.auroraar.ui.theme.AuroraViolet
import com.auroraar.ui.theme.NightSurface
import com.auroraar.ui.theme.TextMuted
import com.auroraar.ui.theme.TextPrimary
import com.auroraar.ui.theme.WarmAmber

private val RecordRed = Color(0xFFFF4D5E)

/** Large record/stop control with the Free progress ring. */
@Composable
fun RecordButton(
    phase: RecordingPhase,
    progress: Float?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    holdToRecord: Boolean = false,
    onHoldStart: () -> Unit = {},
    onHoldEnd: () -> Unit = {},
) {
    val description = stringResource(
        when (phase) {
            RecordingPhase.RECORDING, RecordingPhase.PAUSED, RecordingPhase.FINALIZING ->
                R.string.record_stop
            else -> R.string.record_start
        },
    )
    val ringColor = if (progress != null && progress >= 0.7f) WarmAmber else Color.White
    val tap = onClick
    val interaction = if (holdToRecord) {
        Modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                onHoldStart()
                waitForUpOrCancellation()
                onHoldEnd()
            }
        }
    } else {
        Modifier.clickable(enabled = enabled, onClick = onClick)
    }
    Canvas(
        modifier = modifier
            .size(88.dp)
            .clip(CircleShape)
            .then(interaction)
            .semantics {
                contentDescription = description
                role = androidx.compose.ui.semantics.Role.Button
                if (holdToRecord) {
                    onClick(label = description) {
                        tap()
                        true
                    }
                }
            },
    ) {
        val stroke = 4.dp.toPx()
        val radius = size.minDimension / 2f - stroke
        drawCircle(
            color = Color.White.copy(alpha = 0.20f),
            radius = radius,
            style = Stroke(stroke),
        )
        if (progress != null) {
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
            )
        }
        val center = Offset(size.width / 2f, size.height / 2f)
        when (phase) {
            RecordingPhase.RECORDING, RecordingPhase.FINALIZING -> {
                val side = size.minDimension * 0.36f
                drawRoundRect(
                    color = RecordRed,
                    topLeft = Offset(center.x - side / 2f, center.y - side / 2f),
                    size = Size(side, side),
                    cornerRadius = CornerRadius(side * 0.22f),
                )
            }

            RecordingPhase.PAUSED -> {
                val barW = size.minDimension * 0.10f
                val barH = size.minDimension * 0.30f
                drawRoundRect(
                    color = RecordRed,
                    topLeft = Offset(center.x - barW * 1.9f, center.y - barH / 2f),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(barW / 2f),
                )
                drawRoundRect(
                    color = RecordRed,
                    topLeft = Offset(center.x + barW * 0.9f, center.y - barH / 2f),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(barW / 2f),
                )
            }

            else -> {
                drawCircle(
                    color = RecordRed.copy(alpha = if (phase == RecordingPhase.PREPARING) 0.5f else 1f),
                    radius = size.minDimension * 0.27f,
                    center = center,
                )
            }
        }
    }
}

@Composable
fun PauseResumeButton(
    paused: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(if (paused) R.string.record_resume else R.string.record_pause)
    Canvas(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0xCC0A0F16))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        val c = Offset(size.width / 2f, size.height / 2f)
        if (paused) {
            val path = Path().apply {
                moveTo(c.x - size.width * 0.10f, c.y - size.height * 0.16f)
                lineTo(c.x + size.width * 0.18f, c.y)
                lineTo(c.x - size.width * 0.10f, c.y + size.height * 0.16f)
                close()
            }
            drawPath(path, Color.White)
        } else {
            val barW = size.width * 0.10f
            val barH = size.height * 0.32f
            drawRoundRect(
                Color.White,
                topLeft = Offset(c.x - barW * 1.8f, c.y - barH / 2f),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f),
            )
            drawRoundRect(
                Color.White,
                topLeft = Offset(c.x + barW * 0.8f, c.y - barH / 2f),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}

@Composable
fun PhotoButton(
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(if (busy) R.string.photo_busy else R.string.photo_take)
    Canvas(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0xCC0A0F16))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .clickable(enabled = enabled && !busy, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        val c = Offset(size.width / 2f, size.height / 2f)
        if (busy) {
            drawArc(
                color = AuroraGreen,
                startAngle = -90f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
            )
            return@Canvas
        }
        val bodyW = size.width * 0.52f
        val bodyH = size.height * 0.38f
        drawRoundRect(
            color = TextPrimary,
            topLeft = Offset(c.x - bodyW / 2f, c.y - bodyH / 2f + size.height * 0.03f),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(size.width * 0.06f),
            style = Stroke(1.8.dp.toPx()),
        )
        drawCircle(
            color = TextPrimary,
            radius = size.width * 0.105f,
            center = Offset(c.x, c.y + size.height * 0.03f),
            style = Stroke(1.8.dp.toPx()),
        )
        drawRoundRect(
            color = TextPrimary,
            topLeft = Offset(c.x - bodyW * 0.18f, c.y - bodyH / 2f - size.height * 0.04f),
            size = Size(bodyW * 0.36f, size.height * 0.06f),
            cornerRadius = CornerRadius(size.width * 0.02f),
            style = Stroke(1.8.dp.toPx()),
        )
    }
}

@Composable
fun RecordingTimerPill(
    phase: RecordingPhase,
    elapsedMs: Long,
    limitMs: Long?,
    hasAudio: Boolean,
    modifier: Modifier = Modifier,
) {
    val pulse by rememberInfiniteTransition(label = "recPulse").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recDot",
    )
    val paused = phase == RecordingPhase.PAUSED
    val preparing = phase == RecordingPhase.PREPARING
    val remainingMs = limitMs?.let { (it - elapsedMs).coerceAtLeast(0L) }
    val remainingSeconds = remainingMs?.let { ((it + 999L) / 1000L).toInt() }
    val tickSeconds = remainingSeconds?.takeIf {
        phase == RecordingPhase.RECORDING && it in 1..3
    }
    val danger = remainingMs != null && remainingMs <= 3_000L
    val dotColor = when {
        paused || preparing -> WarmAmber
        danger -> WarmAmber
        else -> RecordRed
    }
    val clock = buildString {
        append(formatDuration(elapsedMs))
        if (limitMs != null) {
            append(" / ")
            append(formatDuration(limitMs))
        }
    }
    val description = when {
        paused -> stringResource(R.string.record_paused)
        preparing -> stringResource(R.string.record_preparing)
        limitMs != null -> stringResource(
            R.string.record_timer_a11y,
            formatDuration(elapsedMs),
            formatDuration(limitMs),
        )
        else -> stringResource(R.string.record_timer_unlimited_a11y, formatDuration(elapsedMs))
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color(0xCC0A0F16))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(percent = 50))
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(8.dp)) {
            drawCircle(dotColor.copy(alpha = if (paused || preparing) 1f else pulse))
        }
        Spacer(Modifier.width(7.dp))
        Text(
            text = when {
                preparing -> stringResource(R.string.record_preparing)
                paused -> stringResource(R.string.record_paused)
                tickSeconds != null ->
                    stringResource(R.string.record_limit_tick, tickSeconds)
                else -> clock
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (danger) WarmAmber else TextPrimary,
        )
        if (!hasAudio) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.record_silent),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
        }
    }
}

/** Large glass orb: tap toggles Auto ⇄ Off, long press opens all three modes. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuroraOrb(
    mode: Mode,
    enabled: Boolean,
    onToggle: () -> Unit,
    onModeSelected: (Mode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val active = mode != Mode.OFF
    val orbHint = stringResource(R.string.aurora_orb_hint)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Canvas(
                modifier = Modifier
                    .size(76.dp)
                    .combinedClickable(
                        enabled = enabled,
                        onClick = onToggle,
                        onLongClick = { menu = true },
                    )
                    .semantics { contentDescription = orbHint },
            ) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val colors = if (active) {
                    listOf(AuroraGreen.copy(alpha = 0.55f), AuroraViolet.copy(alpha = 0.10f), Color.Transparent)
                } else {
                    listOf(Color.White.copy(alpha = 0.12f), Color.Transparent)
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = colors,
                        center = c,
                        radius = size.minDimension / 2f,
                    ),
                    radius = size.minDimension / 2f,
                    center = c,
                )
                drawCircle(
                    color = Color(0x990A0F16),
                    radius = size.minDimension * 0.34f,
                    center = c,
                )
                drawCircle(
                    color = if (active) AuroraGreen.copy(alpha = 0.85f) else TextMuted.copy(alpha = 0.4f),
                    radius = size.minDimension * 0.34f,
                    center = c,
                    style = Stroke(1.5.dp.toPx()),
                )
                if (active) {
                    val ribbon = Path().apply {
                        moveTo(c.x - size.width * 0.16f, c.y + size.height * 0.10f)
                        cubicTo(
                            c.x - size.width * 0.02f,
                            c.y - size.height * 0.18f,
                            c.x + size.width * 0.06f,
                            c.y + size.height * 0.12f,
                            c.x + size.width * 0.17f,
                            c.y - size.height * 0.10f,
                        )
                    }
                    drawPath(
                        ribbon,
                        Brush.linearGradient(listOf(AuroraGreen, AuroraTeal, AuroraViolet)),
                        style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Mode.entries.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(
                                    when (item) {
                                        Mode.AUTO -> R.string.mode_auto
                                        Mode.CREATIVE -> R.string.mode_creative
                                        Mode.OFF -> R.string.mode_off
                                    },
                                ),
                                color = if (item == mode) AuroraGreen else TextPrimary,
                            )
                        },
                        onClick = {
                            menu = false
                            onModeSelected(item)
                        },
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.aurora_orb),
            style = MaterialTheme.typography.labelSmall,
            color = if (active) AuroraGreen else TextMuted,
        )
    }
}

/** Right-edge vertical intensity control. Drag or tap anywhere on the rail. */
@Composable
fun VerticalIntensityRail(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.settings_intensity)
    Box(
        modifier = modifier
            .width(40.dp)
            .height(168.dp)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
            }
            .pointerInput(Unit) {
                fun update(y: Float) {
                    val fraction = 1f - (y / size.height.toFloat())
                    onValueChange(fraction.coerceIn(0f, 1f))
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    update(down.position.y)
                    drag(down.id) { change ->
                        update(change.position.y)
                        change.consume()
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            val trackW = 4.dp.toPx()
            val x = size.width / 2f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.16f),
                topLeft = Offset(x - trackW / 2f, 0f),
                size = Size(trackW, size.height),
                cornerRadius = CornerRadius(trackW / 2f),
            )
            val fillH = size.height * value.coerceIn(0f, 1f)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(AuroraViolet, AuroraGreen),
                    startY = size.height - fillH,
                    endY = size.height,
                ),
                topLeft = Offset(x - trackW / 2f, size.height - fillH),
                size = Size(trackW, fillH),
                cornerRadius = CornerRadius(trackW / 2f),
            )
            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = Offset(x, size.height - fillH),
            )
            drawCircle(
                color = AuroraGreen.copy(alpha = 0.35f),
                radius = 12.dp.toPx(),
                center = Offset(x, size.height - fillH),
            )
        }
    }
}

@Composable
fun RecordingPreviewDialog(
    uri: Uri,
    tier: RecordingTier,
    autoStopped: Boolean,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRecordAgain: () -> Unit,
    onSeePremium: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val context = LocalContext.current
        val videoView = remember(uri) {
            VideoView(context).apply {
                setVideoURI(uri)
                setOnPreparedListener { player ->
                    player.isLooping = true
                    start()
                }
            }
        }
        DisposableEffect(videoView) {
            onDispose { videoView.stopPlayback() }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF505060B))
                .systemBarsPadding()
                .padding(20.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(R.string.record_saved_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                Text(
                    text = stringResource(R.string.record_saved_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroraGreen,
                )
                Spacer(Modifier.height(12.dp))
                AndroidView(
                    factory = { videoView },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black),
                )
                if (tier == RecordingTier.FREE) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.record_watermark_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
                if (autoStopped) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(WarmAmber.copy(alpha = 0.12f))
                            .padding(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.record_upsell),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                        )
                        TextButton(onClick = onSeePremium) {
                            Text(stringResource(R.string.record_see_premium), color = WarmAmber)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.record_ai_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onShare,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AuroraGreen,
                            contentColor = Color(0xFF04231A),
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.record_share))
                    }
                    Button(
                        onClick = onRecordAgain,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AuroraGreen.copy(alpha = 0.16f),
                            contentColor = AuroraGreen,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.record_again))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.record_delete), color = Color(0xFFFF8A80))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.record_close), color = TextMuted)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumInfoSheet(
    isPremium: Boolean,
    debugBuild: Boolean,
    onPremiumChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NightSurface,
        contentColor = TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.premium_title),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.premium_body),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
            Spacer(Modifier.height(14.dp))
            listOf(
                R.string.premium_benefit_quality,
                R.string.premium_benefit_length,
                R.string.premium_benefit_watermark,
            ).forEach { benefit ->
                Text(
                    text = "· " + stringResource(benefit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            if (debugBuild) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_premium_toggle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = isPremium, onCheckedChange = onPremiumChange)
                }
                Text(
                    text = stringResource(R.string.premium_note_debug),
                    style = MaterialTheme.typography.bodySmall,
                    color = WarmAmber,
                )
            } else {
                Text(
                    text = stringResource(R.string.premium_note_release),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuroraGreen,
                    contentColor = Color(0xFF04231A),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.premium_close))
            }
        }
    }
}
