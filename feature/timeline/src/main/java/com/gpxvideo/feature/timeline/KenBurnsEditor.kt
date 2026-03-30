package com.gpxvideo.feature.timeline

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun KenBurnsEditor(
    config: KenBurnsConfig,
    onConfigChanged: (KenBurnsConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "Ken Burns Effect",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Preview animation
            KenBurnsPreview(config = config)

            Spacer(modifier = Modifier.height(12.dp))

            // Start scale
            Text(
                text = "Start Scale: %.1fx".format(config.startScale),
                style = MaterialTheme.typography.labelSmall
            )
            Slider(
                value = config.startScale,
                onValueChange = { onConfigChanged(config.copy(startScale = it)) },
                valueRange = 0.5f..3.0f,
                modifier = Modifier.fillMaxWidth()
            )

            // End scale
            Text(
                text = "End Scale: %.1fx".format(config.endScale),
                style = MaterialTheme.typography.labelSmall
            )
            Slider(
                value = config.endScale,
                onValueChange = { onConfigChanged(config.copy(endScale = it)) },
                valueRange = 0.5f..3.0f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Start position
            Text(
                text = "Start Position",
                style = MaterialTheme.typography.labelSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("X: %.0f%%".format(config.startX * 100), style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = config.startX,
                        onValueChange = { onConfigChanged(config.copy(startX = it)) },
                        valueRange = 0f..1f
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Y: %.0f%%".format(config.startY * 100), style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = config.startY,
                        onValueChange = { onConfigChanged(config.copy(startY = it)) },
                        valueRange = 0f..1f
                    )
                }
            }

            // End position
            Text(
                text = "End Position",
                style = MaterialTheme.typography.labelSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("X: %.0f%%".format(config.endX * 100), style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = config.endX,
                        onValueChange = { onConfigChanged(config.copy(endX = it)) },
                        valueRange = 0f..1f
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Y: %.0f%%".format(config.endY * 100), style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = config.endY,
                        onValueChange = { onConfigChanged(config.copy(endY = it)) },
                        valueRange = 0f..1f
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Easing type selector
            Text(
                text = "Easing",
                style = MaterialTheme.typography.labelSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                EasingType.entries.forEach { easing ->
                    FilterChip(
                        selected = config.easingType == easing,
                        onClick = { onConfigChanged(config.copy(easingType = easing)) },
                        label = { Text(easing.toDisplayName(), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

@Composable
private fun KenBurnsPreview(
    config: KenBurnsConfig,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "kenBurns")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "kenBurnsProgress"
    )

    val currentScale = config.startScale + (config.endScale - config.startScale) * progress
    val currentX = config.startX + (config.endX - config.startX) * progress
    val currentY = config.startY + (config.endY - config.startY) * progress

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .aspectRatio(16f / 9f)
                .graphicsLayer {
                    scaleX = currentScale
                    scaleY = currentScale
                    transformOrigin = TransformOrigin(currentX, currentY)
                }
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    RoundedCornerShape(4.dp)
                )
        )

        Text(
            text = "%.1fx".format(currentScale),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
