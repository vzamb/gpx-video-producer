package com.gpxvideo.feature.gpx

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpxvideo.core.model.GpxPoint
import com.gpxvideo.lib.gpxparser.GpxStatistics

@Composable
fun AltitudeProfileCanvas(
    points: List<GpxPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF26A69A),
    fillColor: Color = Color(0x8026A69A),
    showGrid: Boolean = true,
    showLabels: Boolean = true,
    backgroundColor: Color = Color(0xFF0D1117)
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember {
        TextStyle(color = Color(0xFFAAAAAA), fontSize = 10.sp)
    }

    val distances = remember(points) {
        if (points.size < 2) return@remember emptyList()
        val dists = mutableListOf(0.0)
        for (i in 1 until points.size) {
            dists.add(
                dists.last() + GpxStatistics.computeDistance(
                    points[i - 1].latitude, points[i - 1].longitude,
                    points[i].latitude, points[i].longitude
                )
            )
        }
        dists
    }

    Spacer(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .drawWithCache {
                if (points.size < 2 || distances.isEmpty()) {
                    return@drawWithCache onDrawBehind { drawRect(backgroundColor) }
                }

                val leftPadding = if (showLabels) 52f else 24f
                val rightPadding = 24f
                val topPadding = 24f
                val bottomPadding = if (showLabels) 36f else 24f
                val drawWidth = size.width - leftPadding - rightPadding
                val drawHeight = size.height - topPadding - bottomPadding

                val totalDist = distances.last()
                val elevations = points.map { it.elevation ?: 0.0 }
                val minEle = elevations.min()
                val maxEle = elevations.max()
                val eleRange = (maxEle - minEle).coerceAtLeast(1.0)

                fun xPos(dist: Double) = leftPadding + (dist / totalDist * drawWidth).toFloat()
                fun yPos(ele: Double) =
                    topPadding + drawHeight - ((ele - minEle) / eleRange * drawHeight).toFloat()

                val linePath = Path().apply {
                    moveTo(xPos(distances[0]), yPos(elevations[0]))
                    for (i in 1 until points.size) {
                        lineTo(xPos(distances[i]), yPos(elevations[i]))
                    }
                }

                val fillPath = Path().apply {
                    moveTo(xPos(distances[0]), yPos(elevations[0]))
                    for (i in 1 until points.size) {
                        lineTo(xPos(distances[i]), yPos(elevations[i]))
                    }
                    lineTo(xPos(distances.last()), topPadding + drawHeight)
                    lineTo(xPos(distances[0]), topPadding + drawHeight)
                    close()
                }

                val fillBrush = Brush.verticalGradient(
                    colors = listOf(fillColor, fillColor.copy(alpha = 0.05f)),
                    startY = topPadding,
                    endY = topPadding + drawHeight
                )

                val minLabel = if (showLabels) {
                    textMeasurer.measure("${minEle.toInt()}m", labelStyle)
                } else null
                val maxLabel = if (showLabels) {
                    textMeasurer.measure("${maxEle.toInt()}m", labelStyle)
                } else null
                val distLabel = if (showLabels) {
                    val distKm = totalDist / 1000.0
                    val text = if (distKm >= 1) "%.1f km".format(distKm) else "%.0f m".format(totalDist)
                    textMeasurer.measure(text, labelStyle)
                } else null

                onDrawBehind {
                    drawRect(backgroundColor)

                    if (showGrid) {
                        val gridColor = Color.White.copy(alpha = 0.08f)
                        for (i in 0..4) {
                            val y = topPadding + drawHeight * i / 4
                            drawLine(
                                gridColor,
                                Offset(leftPadding, y),
                                Offset(leftPadding + drawWidth, y)
                            )
                        }
                        for (i in 0..4) {
                            val x = leftPadding + drawWidth * i / 4
                            drawLine(
                                gridColor,
                                Offset(x, topPadding),
                                Offset(x, topPadding + drawHeight)
                            )
                        }
                    }

                    drawPath(fillPath, brush = fillBrush)
                    drawPath(
                        linePath, lineColor,
                        style = Stroke(width = 2f, cap = StrokeCap.Round)
                    )

                    if (showLabels) {
                        maxLabel?.let {
                            drawText(
                                it,
                                topLeft = Offset(2f, topPadding - it.size.height / 2f)
                            )
                        }
                        minLabel?.let {
                            drawText(
                                it,
                                topLeft = Offset(
                                    2f,
                                    topPadding + drawHeight - it.size.height / 2f
                                )
                            )
                        }
                        distLabel?.let {
                            drawText(
                                it,
                                topLeft = Offset(
                                    leftPadding + drawWidth - it.size.width,
                                    topPadding + drawHeight + 4f
                                )
                            )
                        }
                    }
                }
            }
    )
}
