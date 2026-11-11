package com.auroraar.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.auroraar.BuildConfig
import com.auroraar.R
import com.auroraar.capture.caps.DeviceRecordingCaps
import com.auroraar.skyscore.model.UserSettings
import com.auroraar.ui.theme.AuroraGreen
import com.auroraar.ui.theme.NightSurface
import com.auroraar.ui.theme.TextMuted
import com.auroraar.ui.theme.TextPrimary
import com.auroraar.ui.theme.WarmAmber
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: UserSettings,
    useLocation: Boolean,
    versionName: String,
    recordAudio: Boolean,
    countdown: Boolean,
    holdToRecord: Boolean,
    caps: DeviceRecordingCaps,
    isPremium: Boolean,
    onSettingsChange: (UserSettings) -> Unit,
    onUseLocationChange: (Boolean) -> Unit,
    onRecordAudioChange: (Boolean) -> Unit,
    onCountdownChange: (Boolean) -> Unit,
    onHoldToRecordChange: (Boolean) -> Unit,
    onPremiumChange: (Boolean) -> Unit,
    onOpenCalibration: () -> Unit,
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
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.settings_close), tint = TextMuted)
                }
            }

            SectionHeader(stringResource(R.string.settings_display))
            SwitchRow(
                title = stringResource(R.string.settings_skyscore_hud),
                checked = settings.hudEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(hudEnabled = it)) },
            )
            SwitchRow(
                title = stringResource(R.string.settings_sub_scores),
                checked = settings.showSubScores,
                onCheckedChange = { onSettingsChange(settings.copy(showSubScores = it)) },
            )

            SectionHeader(stringResource(R.string.settings_recording))
            SwitchRow(
                title = stringResource(R.string.settings_record_audio),
                checked = recordAudio,
                onCheckedChange = onRecordAudioChange,
            )
            SwitchRow(
                title = stringResource(R.string.settings_countdown),
                checked = countdown,
                onCheckedChange = onCountdownChange,
            )
            SwitchRow(
                title = stringResource(R.string.settings_hold_to_record),
                checked = holdToRecord,
                onCheckedChange = onHoldToRecordChange,
            )
            InfoRow(
                label = stringResource(R.string.settings_watermark),
                value = stringResource(
                    if (isPremium) R.string.settings_watermark_off else R.string.settings_watermark_on,
                ),
            )
            InfoRow(
                label = stringResource(R.string.settings_quality),
                value = stringResource(
                    if (isPremium) R.string.settings_quality_premium else R.string.settings_quality_free,
                ),
            )
            Text(
                text = stringResource(R.string.settings_quality_note),
                style = MaterialTheme.typography.bodySmall,
                color = WarmAmber,
                modifier = Modifier.padding(start = 2.dp, end = 8.dp, bottom = 8.dp),
            )
            InfoRow(
                label = stringResource(R.string.settings_plan),
                value = stringResource(
                    if (isPremium) R.string.settings_plan_premium else R.string.settings_plan_free,
                ),
            )
            if (BuildConfig.DEBUG) {
                SwitchRow(
                    title = stringResource(R.string.settings_premium_toggle),
                    checked = isPremium,
                    onCheckedChange = onPremiumChange,
                )
            } else {
                Text(
                    text = stringResource(R.string.settings_premium_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(start = 2.dp, end = 8.dp, bottom = 8.dp),
                )
            }

            SectionHeader(stringResource(R.string.settings_caps))
            Text(
                text = if (caps.encoders.isEmpty()) {
                    stringResource(R.string.settings_caps_unavailable)
                } else {
                    caps.summary()
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_caps_body),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted.copy(alpha = 0.8f),
            )

            SectionHeader(stringResource(R.string.settings_conditions))
            SwitchRow(
                title = stringResource(R.string.settings_respect_clouds),
                checked = settings.respectClouds,
                onCheckedChange = { onSettingsChange(settings.copy(respectClouds = it)) },
            )
            SwitchRow(
                title = stringResource(R.string.settings_location),
                checked = useLocation,
                onCheckedChange = onUseLocationChange,
            )
            Text(
                text = stringResource(R.string.settings_location_rationale),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(start = 2.dp, end = 8.dp, bottom = 8.dp),
            )

            SectionHeader(stringResource(R.string.settings_look))
            SliderRow(
                label = stringResource(R.string.settings_intensity),
                value = settings.intensity,
                onValueChange = { onSettingsChange(settings.copy(intensity = it)) },
            )
            SliderRow(
                label = stringResource(R.string.settings_saturation),
                value = settings.saturation,
                range = 0f..1.6f,
                onValueChange = { onSettingsChange(settings.copy(saturation = it)) },
            )
            SliderRow(
                label = stringResource(R.string.settings_max_intensity),
                value = settings.maxIntensity,
                onValueChange = { onSettingsChange(settings.copy(maxIntensity = it)) },
            )
            SwitchRow(
                title = stringResource(R.string.settings_lock_intensity),
                checked = settings.lockIntensity,
                onCheckedChange = { onSettingsChange(settings.copy(lockIntensity = it)) },
            )

            SectionHeader(stringResource(R.string.settings_calibration))
            Button(
                onClick = onOpenCalibration,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuroraGreen.copy(alpha = 0.16f),
                    contentColor = AuroraGreen,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_calibration_open))
            }

            SectionHeader(stringResource(R.string.settings_about))
            Text(
                text = stringResource(R.string.settings_about_body),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${stringResource(R.string.settings_version)} $versionName",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_no_internet),
                style = MaterialTheme.typography.bodySmall,
                color = AuroraGreen.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
    )
    Spacer(Modifier.height(6.dp))
    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(width = 44.dp, height = 26.dp),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(value * 100f).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}
