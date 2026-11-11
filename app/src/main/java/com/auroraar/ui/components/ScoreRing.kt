package com.auroraar.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.auroraar.R
import com.auroraar.ui.theme.AuroraGreen
import com.auroraar.ui.theme.AuroraTeal
import com.auroraar.ui.theme.TextMuted
import com.auroraar.ui.theme.WarmAmber

fun scoreColor(score: Int): Color = when {
    score >= 85 -> AuroraGreen
    score >= 70 -> AuroraTeal
    score >= 50 -> WarmAmber
    score > 0 -> Color(0xFFE07A5F)
    else -> TextMuted
}

@Composable
fun ScoreRing(
    score: Int,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 8.dp,
    textStyle: TextStyle = MaterialTheme.typography.titleLarge,
    showScore: Boolean = true,
) {
    val animated by animateFloatAsState(
        targetValue = (score / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 850),
        label = "scoreRing",
    )
    val color = scoreColor(score)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Color.White.copy(alpha = 0.10f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (animated > 0.001f) {
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = 270f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        if (showScore) {
            Text(
                text = if (score > 0) score.toString() else "—",
                style = textStyle,
                color = if (score > 0) color else TextMuted,
            )
        }
    }
}

@Composable
fun SkyScoreChip(
    score: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scoreText = if (enabled && score > 0) score.toString() else "—"
    val description = stringResource(R.string.skyscore_a11y, scoreText)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Color(0xB30A0F16))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(start = 5.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        ScoreRing(
            score = if (enabled) score else 0,
            modifier = Modifier.size(34.dp),
            strokeWidth = 3.dp,
            textStyle = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.width(9.dp))
        Column {
            Text(
                text = stringResource(R.string.skyscore),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
            Text(
                text = scoreText,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) scoreColor(score) else TextMuted,
            )
        }
    }
}
