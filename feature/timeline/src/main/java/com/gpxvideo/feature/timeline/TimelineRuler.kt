package com.gpxvideo.feature.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val RULER_HEIGHT = 28.dp

@Composable
fun TimelineRuler(
    totalDurationMs: Long,
    zoomLevel: Float,
    pxPerMs: Float,
    scrollOffset: Float,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val textStyle = TextStyle(
        color = Color.Gray,
        fontSize = 9.sp
    )

    val majorTickIntervalMs = computeMajorTickInterval(zoomLevel)
    val minorTicksPerMajor = 4

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(RULER_HEIGHT)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Background
        drawRect(color = Color(0xFF1E1E1E))

        val minorTickInterval = majorTickIntervalMs / minorTicksPerMajor
        val startMs = ((scrollOffset / pxPerMs).toLong() / minorTickInterval) * minorTickInterval
        val endMs = startMs + (canvasWidth / pxPerMs).toLong() + majorTickIntervalMs

        var tickMs = startMs
        while (tickMs <= endMs && tickMs <= totalDurationMs + majorTickIntervalMs) {
            val x = (tickMs * pxPerMs) - scrollOffset

            if (x < -50f || x > canvasWidth + 50f) {
                tickMs += minorTickInterval
                continue
            }

            val isMajor = tickMs % majorTickIntervalMs == 0L

            if (isMajor) {
                // Major tick
                drawLine(
                    color = Color.Gray,
                    start = Offset(x, canvasHeight * 0.5f),
                    end = Offset(x, canvasHeight),
                    strokeWidth = 1.5f
                )
                // Time label
                val label = formatTime(tickMs)
                val textResult = textMeasurer.measure(label, textStyle)
                drawText(
                    textLayoutResult = textResult,
                    topLeft = Offset(
                        x - textResult.size.width / 2f,
                        2f
                    )
                )
            } else {
                // Minor tick
                drawLine(
                    color = Color.DarkGray,
                    start = Offset(x, canvasHeight * 0.75f),
                    end = Offset(x, canvasHeight),
                    strokeWidth = 0.5f
                )
            }

            tickMs += minorTickInterval
        }
    }
}

private fun computeMajorTickInterval(zoomLevel: Float): Long {
    return when {
        zoomLevel >= 4.0f -> 1000L       // 1s
        zoomLevel >= 2.0f -> 2000L       // 2s
        zoomLevel >= 1.0f -> 5000L       // 5s
        zoomLevel >= 0.5f -> 10000L      // 10s
        else -> 30000L                    // 30s
    }
}
