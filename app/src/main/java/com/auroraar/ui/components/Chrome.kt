package com.auroraar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.auroraar.R
import com.auroraar.skyscore.model.Mode
import com.auroraar.ui.theme.AuroraGreen
import com.auroraar.ui.theme.NightSurface
import com.auroraar.ui.theme.TextMuted
import com.auroraar.ui.theme.TextPrimary
import com.auroraar.ui.theme.WarmAmber

@Composable
fun DaylightCard(
    onTryCreative: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = NightSurface.copy(alpha = 0.97f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.size(72.dp)) {
                val r = size.minDimension
                drawCircle(WarmAmber.copy(alpha = 0.18f), radius = r * 0.42f)
                drawCircle(WarmAmber, radius = r * 0.20f)
                repeat(8) { i ->
                    rotate(degrees = i * 45f, pivot = center) {
                        drawLine(
                            color = WarmAmber.copy(alpha = 0.75f),
                            start = Offset(center.x, center.y - r * 0.30f),
                            end = Offset(center.x, center.y - r * 0.44f),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.daylight_card_title),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.daylight_card_body),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.daylight_card_dismiss), color = TextMuted)
                }
                Button(
                    onClick = onTryCreative,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuroraGreen,
                        contentColor = Color(0xFF04231A),
                    ),
                ) {
                    Text(stringResource(R.string.daylight_card_creative))
                }
            }
        }
    }
}

@Composable
fun ModePill(
    mode: Mode,
    onModeChange: (Mode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color(0xCC0A0F16))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(percent = 50))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mode.entries.forEach { item ->
            val selected = item == mode
            Text(
                text = stringResource(
                    when (item) {
                        Mode.AUTO -> R.string.mode_auto
                        Mode.CREATIVE -> R.string.mode_creative
                        Mode.OFF -> R.string.mode_off
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) AuroraGreen else TextMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (selected) AuroraGreen.copy(alpha = 0.14f) else Color.Transparent)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onModeChange(item) },
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
fun MessageBubble(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xD90A0F16))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
    }
}
