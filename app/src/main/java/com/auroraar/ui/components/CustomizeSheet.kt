package com.auroraar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroraar.R
import com.auroraar.premium.FeatureGate
import com.auroraar.premium.styles.AuroraStyle
import com.auroraar.premium.styles.AuroraStyleRegistry
import com.auroraar.premium.styles.StyleTier
import com.auroraar.skyscore.model.CinematicLook
import com.auroraar.skyscore.model.IntensityAutomation
import com.auroraar.skyscore.model.UserSettings
import com.auroraar.ui.theme.AuroraGreen
import com.auroraar.ui.theme.NightSurface
import com.auroraar.ui.theme.TextMuted
import com.auroraar.ui.theme.TextPrimary
import com.auroraar.ui.theme.WarmAmber
import kotlin.math.roundToInt

/**
 * Premium style picker and advanced controls. Free users see every style, can
 * preview a locked one for 3 s on the viewfinder, then get the paywall sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeSheet(
    settings: UserSettings,
    isPremium: Boolean,
    onSettingsChange: (UserSettings) -> Unit,
    onLockedStyle: (AuroraStyle) -> Unit,
    onLockedControl: () -> Unit,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.customize_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (isPremium) {
                    Text(
                        text = stringResource(R.string.style_tier_premium),
                        style = MaterialTheme.typography.labelSmall,
                        color = WarmAmber,
                    )
                }
            }

            SectionLabel(stringResource(R.string.customize_styles))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AuroraStyleRegistry.ALL.forEach { style ->
                    StyleCard(
                        style = style,
                        selected = settings.styleId == style.id,
                        locked = !FeatureGate.canUseStyle(style, isPremium),
                        onClick = {
                            if (FeatureGate.canUseStyle(style, isPremium)) {
                                onSettingsChange(settings.copy(styleId = style.id))
                            } else {
                                onLockedStyle(style)
                            }
                        },
                    )
                }
            }
            if (!isPremium) {
                Text(
                    text = stringResource(R.string.customize_premium_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            SectionLabel(stringResource(R.string.customize_look))
            if (isPremium) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LookChip(
                        label = stringResource(R.string.customize_look_natural),
                        selected = settings.cinematicLook == CinematicLook.NATURAL,
                        onClick = { onSettingsChange(settings.copy(cinematicLook = CinematicLook.NATURAL)) },
                    )
                    LookChip(
                        label = stringResource(R.string.customize_look_cinematic),
                        selected = settings.cinematicLook == CinematicLook.CINEMATIC,
                        onClick = { onSettingsChange(settings.copy(cinematicLook = CinematicLook.CINEMATIC)) },
                    )
                    LookChip(
                        label = stringResource(R.string.customize_look_dreamy),
                        selected = settings.cinematicLook == CinematicLook.DREAMY,
                        onClick = { onSettingsChange(settings.copy(cinematicLook = CinematicLook.DREAMY)) },
                    )
                }
            } else {
                LockedRow(onClick = onLockedControl)
            }

            SectionLabel(stringResource(R.string.customize_advanced))
            if (isPremium) {
                LookSlider(
                    label = stringResource(R.string.customize_speed),
                    value = settings.advancedSpeed,
                    range = 0.4f..2f,
                    onValueChange = { onSettingsChange(settings.copy(advancedSpeed = it)) },
                )
                LookSlider(
                    label = stringResource(R.string.customize_coverage),
                    value = settings.coverage,
                    onValueChange = { onSettingsChange(settings.copy(coverage = it)) },
                )
                LookSlider(
                    label = stringResource(R.string.customize_wave_amp),
                    value = settings.waveAmp,
                    onValueChange = { onSettingsChange(settings.copy(waveAmp = it)) },
                )
                LookSlider(
                    label = stringResource(R.string.customize_wave_freq),
                    value = settings.waveFreq,
                    onValueChange = { onSettingsChange(settings.copy(waveFreq = it)) },
                )
                LookSlider(
                    label = stringResource(R.string.customize_ray_sharpness),
                    value = settings.raySharpness,
                    range = 0.3f..1.5f,
                    onValueChange = { onSettingsChange(settings.copy(raySharpness = it)) },
                )
                LookSlider(
                    label = stringResource(R.string.customize_burst),
                    value = settings.burst,
                    onValueChange = { onSettingsChange(settings.copy(burst = it)) },
                )
                LookSlider(
                    label = stringResource(R.string.customize_layers),
                    value = settings.layers.toFloat(),
                    range = 1f..4f,
                    steps = 2,
                    valueLabel = { it.roundToInt().toString() },
                    onValueChange = { onSettingsChange(settings.copy(layers = it.roundToInt().coerceIn(1, 4))) },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.customize_direction),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    LookChip(
                        label = stringResource(R.string.customize_direction_forward),
                        selected = settings.direction >= 0f,
                        onClick = { onSettingsChange(settings.copy(direction = 1f)) },
                    )
                    Spacer(Modifier.width(6.dp))
                    LookChip(
                        label = stringResource(R.string.customize_direction_reverse),
                        selected = settings.direction < 0f,
                        onClick = { onSettingsChange(settings.copy(direction = -1f)) },
                    )
                }
            } else {
                LockedRow(onClick = onLockedControl)
            }

            SectionLabel(stringResource(R.string.customize_automation))
            if (isPremium) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AutomationChip(
                        label = stringResource(R.string.customize_automation_none),
                        selected = settings.automation == IntensityAutomation.NONE,
                        onClick = { onSettingsChange(settings.copy(automation = IntensityAutomation.NONE)) },
                    )
                    AutomationChip(
                        label = stringResource(R.string.customize_automation_fade),
                        selected = settings.automation == IntensityAutomation.FADE_IN,
                        onClick = { onSettingsChange(settings.copy(automation = IntensityAutomation.FADE_IN)) },
                    )
                    AutomationChip(
                        label = stringResource(R.string.customize_automation_swell),
                        selected = settings.automation == IntensityAutomation.SLOW_SWELL,
                        onClick = { onSettingsChange(settings.copy(automation = IntensityAutomation.SLOW_SWELL)) },
                    )
                    AutomationChip(
                        label = stringResource(R.string.customize_automation_pulse),
                        selected = settings.automation == IntensityAutomation.PULSE,
                        onClick = { onSettingsChange(settings.copy(automation = IntensityAutomation.PULSE)) },
                    )
                    AutomationChip(
                        label = stringResource(R.string.customize_automation_substorm),
                        selected = settings.automation == IntensityAutomation.SUBSTORM,
                        onClick = { onSettingsChange(settings.copy(automation = IntensityAutomation.SUBSTORM)) },
                    )
                }
                if (settings.automation == IntensityAutomation.SUBSTORM) {
                    LookSlider(
                        label = stringResource(R.string.customize_automation),
                        value = settings.automationParam,
                        valueLabel = { "${(2 + 18 * it).roundToInt()}s" },
                        onValueChange = { onSettingsChange(settings.copy(automationParam = it)) },
                    )
                }
            } else {
                LockedRow(onClick = onLockedControl)
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onSettingsChange(
                            settings.copy(
                                styleId = AuroraStyleRegistry.DEFAULT.id,
                                cinematicLook = CinematicLook.NATURAL,
                                layers = 2,
                                coverage = 0.75f,
                                waveAmp = 0.5f,
                                waveFreq = 0.5f,
                                advancedSpeed = 1f,
                                raySharpness = 1f,
                                burst = 0f,
                                direction = 1f,
                                automation = IntensityAutomation.NONE,
                                automationParam = 0.5f,
                            ),
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuroraGreen.copy(alpha = 0.16f),
                        contentColor = AuroraGreen,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.customize_reset))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.record_close), color = TextMuted)
                }
            }
        }
    }
}

@Composable
private fun StyleCard(
    style: AuroraStyle,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) AuroraGreen.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.04f))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) AuroraGreen else Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(style.highColor).copy(alpha = 0.55f),
                            Color(style.midColor).copy(alpha = 0.75f),
                            Color(style.lowColor).copy(alpha = 0.9f),
                        ),
                    ),
                ),
        ) {
            Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(size.width * 0.30f, size.height * 0.35f),
                        radius = size.minDimension * 0.7f,
                    ),
                    radius = size.minDimension * 0.7f,
                    center = Offset(size.width * 0.30f, size.height * 0.35f),
                )
                repeat(3) { i ->
                    drawLine(
                        color = Color.White.copy(alpha = 0.18f - i * 0.04f),
                        start = Offset(size.width * (0.18f + i * 0.16f), size.height),
                        end = Offset(size.width * (0.30f + i * 0.16f), size.height * 0.18f),
                        strokeWidth = 2f,
                    )
                }
            }
            if (locked) {
                Text(
                    text = stringResource(R.string.style_tier_premium),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF04231A),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .background(WarmAmber, RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(style.nameRes),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) AuroraGreen else TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(
                if (style.tier == StyleTier.FREE) R.string.style_tier_free else R.string.style_tier_premium,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
    )
    Spacer(Modifier.height(4.dp))
    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun LookChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
    )
}

@Composable
private fun AutomationChip(label: String, selected: Boolean, onClick: () -> Unit) = LookChip(label, selected, onClick)

@Composable
private fun LockedRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WarmAmber.copy(alpha = 0.08f))
            .border(1.dp, WarmAmber.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.style_tier_premium),
            style = MaterialTheme.typography.labelSmall,
            color = WarmAmber,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.customize_locked_title),
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LookSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    valueLabel: (Float) -> String = { "${(it * 100f).roundToInt()}%" },
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel(value),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
        )
    }
}
