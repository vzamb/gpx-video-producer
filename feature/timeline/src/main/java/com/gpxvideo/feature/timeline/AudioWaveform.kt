package com.gpxvideo.feature.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

@Composable
fun AudioWaveform(
    waveformData: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF4CAF50),
    backgroundColor: Color = Color.Transparent
) {
    Canvas(modifier = modifier) {
        if (backgroundColor != Color.Transparent) {
            drawRect(color = backgroundColor, size = size)
        }

        val barCount = waveformData.size
        if (barCount == 0) return@Canvas

        val barWidthWithGap = size.width / barCount
        val barWidth = (barWidthWithGap * 0.7f).coerceAtLeast(1f)
        val centerY = size.height / 2f
        val maxBarHeight = size.height * 0.9f

        waveformData.forEachIndexed { index, amplitude ->
            val clampedAmplitude = amplitude.coerceIn(0f, 1f)
            val barHeight = (clampedAmplitude * maxBarHeight).coerceAtLeast(1f)
            val x = index * barWidthWithGap + (barWidthWithGap - barWidth) / 2f
            val topY = centerY - barHeight / 2f

            drawRect(
                color = color,
                topLeft = Offset(x, topY),
                size = Size(barWidth, barHeight)
            )
        }
    }
}
