package com.auroraar.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.auroraar.R
import com.auroraar.ui.theme.AuroraGreen
import com.auroraar.ui.theme.NightSurface
import com.auroraar.ui.theme.TextMuted
import com.auroraar.ui.theme.TextPrimary
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationSheet(
    recording: Boolean,
    sampleCount: Int,
    phase: String,
    okta: Int,
    environment: String,
    onPhaseChange: (String) -> Unit,
    onOktaChange: (Int) -> Unit,
    onEnvironmentChange: (String) -> Unit,
    onToggleRecording: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
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
                    text = stringResource(R.string.calibration_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.settings_close), tint = TextMuted)
                }
            }
            Text(
                text = stringResource(R.string.calibration_body),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.calibration_phase).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
            Spacer(Modifier.height(6.dp))
            ChipRow {
                PHASES.forEach { (value, labelRes) ->
                    FilterChip(
                        selected = phase == value,
                        onClick = { onPhaseChange(value) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "${stringResource(R.string.calibration_cloud)}: $okta",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
            )
            Slider(
                value = okta.toFloat(),
                onValueChange = { onOktaChange(it.roundToInt()) },
                valueRange = 0f..8f,
                steps = 7,
            )

            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.calibration_env).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
            Spacer(Modifier.height(6.dp))
            ChipRow {
                listOf("rural" to R.string.calibration_env_rural, "town" to R.string.calibration_env_town, "city" to R.string.calibration_env_city)
                    .forEach { (value, labelRes) ->
                        FilterChip(
                            selected = environment == value,
                            onClick = { onEnvironmentChange(value) },
                            label = { Text(stringResource(labelRes)) },
                        )
                    }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.calibration_samples, sampleCount),
                style = MaterialTheme.typography.bodyMedium,
                color = if (sampleCount > 0) AuroraGreen else TextMuted,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onToggleRecording,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (recording) Color(0xFFE07A5F) else AuroraGreen,
                    contentColor = Color(0xFF04231A),
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(if (recording) R.string.calibration_stop else R.string.calibration_record),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onExport,
                    enabled = sampleCount > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.calibration_export))
                }
                OutlinedButton(
                    onClick = onShare,
                    enabled = sampleCount > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.calibration_share))
                }
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

/** Recorder keys stay ASCII; only the chips are localized. */
private val PHASES = listOf(
    "day" to R.string.phase_day,
    "sunset" to R.string.phase_sunset,
    "twilight" to R.string.phase_twilight,
    "night" to R.string.phase_night,
)
