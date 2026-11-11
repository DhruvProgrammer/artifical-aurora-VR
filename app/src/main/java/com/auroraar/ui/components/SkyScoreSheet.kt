package com.auroraar.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.auroraar.R
import com.auroraar.skyscore.model.Gate
import com.auroraar.skyscore.model.SkyConditions
import com.auroraar.skyscore.policy.SkyScoreFormula
import com.auroraar.ui.cloudSkyText
import com.auroraar.ui.cloudStateText
import com.auroraar.ui.darknessStateText
import com.auroraar.ui.lightingStateText
import com.auroraar.ui.phaseText
import com.auroraar.ui.ratingText
import com.auroraar.ui.theme.NightSurface
import com.auroraar.ui.theme.TextMuted
import com.auroraar.ui.theme.TextPrimary
import com.auroraar.ui.visibilityStateText
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkyScoreSheet(
    conditions: SkyConditions,
    showSubScores: Boolean,
    onDismiss: () -> Unit,
) {
    val available = conditions.gate == Gate.OPEN
    var infoOpen by remember { mutableStateOf(false) }
    val score = if (available) conditions.skyScore else 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NightSurface,
        contentColor = TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted.copy(alpha = 0.5f)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.skyscore_simulated_note),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            ScoreRing(
                score = score,
                modifier = Modifier.size(168.dp),
                strokeWidth = 12.dp,
                textStyle = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = ratingText(SkyScoreFormula.band(score)),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.summary_format,
                    phaseText(conditions.phase),
                    cloudSkyText(conditions.cloudClass),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )

            if (showSubScores) {
                Spacer(Modifier.height(18.dp))
                SubScoreRow(
                    label = stringResource(R.string.sub_darkness),
                    state = darknessStateText(conditions.subScores.brightness),
                    value = conditions.subScores.brightness,
                    available = available,
                )
                SubScoreRow(
                    label = stringResource(R.string.sub_cloud),
                    state = cloudStateText(conditions.cloudCover),
                    value = conditions.subScores.cloud,
                    available = available,
                )
                SubScoreRow(
                    label = stringResource(R.string.sub_visibility),
                    state = visibilityStateText(conditions.skyVisibility),
                    value = conditions.subScores.visibility,
                    available = available,
                )
                SubScoreRow(
                    label = stringResource(R.string.sub_lighting),
                    state = lightingStateText(conditions.lightPollution),
                    value = conditions.subScores.lighting,
                    available = available,
                )
            }

            Spacer(Modifier.height(10.dp))
            val why = when {
                !available -> when (conditions.gate) {
                    Gate.CAMERA_BLOCKED -> R.string.why_camera
                    Gate.NO_SKY -> R.string.why_visibility
                    else -> R.string.why_brightness
                }
                conditions.subScores.cloud <= conditions.subScores.brightness &&
                    conditions.subScores.cloud <= conditions.subScores.visibility &&
                    conditions.subScores.cloud <= conditions.subScores.lighting -> R.string.why_cloud
                conditions.subScores.brightness <= conditions.subScores.visibility &&
                    conditions.subScores.brightness <= conditions.subScores.lighting -> R.string.why_brightness
                conditions.subScores.visibility <= conditions.subScores.lighting -> R.string.why_visibility
                conditions.subScores.lighting < 0.90f -> R.string.why_lighting
                else -> R.string.why_none
            }
            Text(
                text = stringResource(why),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )

            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { infoOpen = !infoOpen }) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.skyscore_info_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
            AnimatedVisibility(visible = infoOpen) {
                Text(
                    text = stringResource(R.string.skyscore_info_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            Spacer(Modifier.height(18.dp))
            Surface(
                color = Color.White.copy(alpha = 0.04f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "92",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextMuted,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.skyscore_illustrative),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScoreRow(
    label: String,
    state: String,
    value: Float,
    available: Boolean,
) {
    val color = scoreColor((value * 100f).roundToInt())
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (available) state else stringResource(R.string.row_rating_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = if (available) color else TextMuted,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { if (available) value.coerceIn(0f, 1f) else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(percent = 50)),
            color = if (available) color else TextMuted,
            trackColor = Color.White.copy(alpha = 0.08f),
            strokeCap = StrokeCap.Round,
        )
    }
}
