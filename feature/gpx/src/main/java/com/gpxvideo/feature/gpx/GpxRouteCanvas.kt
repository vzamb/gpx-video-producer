package com.gpxvideo.feature.gpx

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.gpxvideo.core.model.GeoBounds
import com.gpxvideo.core.model.GpxPoint

@Composable
fun GpxRouteCanvas(
    points: List<GpxPoint>,
    bounds: GeoBounds,
    modifier: Modifier = Modifier,
    routeColor: Color = Color(0xFF448AFF),
    routeWidth: Float = 3f,
    showStartEnd: Boolean = true,
    backgroundColor: Color = Color(0xFF0D1117)
) {
    Spacer(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .drawWithCache {
                val padding = 24f
                val drawWidth = size.width - 2 * padding
                val drawHeight = size.height - 2 * padding

                val latRange = bounds.maxLatitude - bounds.minLatitude
                val lonRange = bounds.maxLongitude - bounds.minLongitude

                val hasValidData = points.size >= 2 && latRange > 0 && lonRange > 0

                val path = if (hasValidData) {
                    val latScale = drawHeight / latRange
                    val lonScale = drawWidth / lonRange
                    val scale = minOf(latScale, lonScale)
                    val offsetX = padding + (drawWidth - lonRange * scale) / 2
                    val offsetY = padding + (drawHeight - latRange * scale) / 2

                    fun projectX(lon: Double) =
                        (offsetX + (lon - bounds.minLongitude) * scale).toFloat()
                    fun projectY(lat: Double) =
                        (offsetY + (bounds.maxLatitude - lat) * scale).toFloat()

                    Path().apply {
                        moveTo(projectX(points[0].longitude), projectY(points[0].latitude))
                        for (i in 1 until points.size) {
                            lineTo(projectX(points[i].longitude), projectY(points[i].latitude))
                        }
                    }
                } else null

                val startCenter = if (hasValidData) {
                    val latScale = drawHeight / latRange
                    val lonScale = drawWidth / lonRange
                    val scale = minOf(latScale, lonScale)
                    val offsetX = padding + (drawWidth - lonRange * scale) / 2
                    val offsetY = padding + (drawHeight - latRange * scale) / 2
                    Offset(
                        (offsetX + (points.first().longitude - bounds.minLongitude) * scale).toFloat(),
                        (offsetY + (bounds.maxLatitude - points.first().latitude) * scale).toFloat()
                    )
                } else null

                val endCenter = if (hasValidData) {
                    val latScale = drawHeight / latRange
                    val lonScale = drawWidth / lonRange
                    val scale = minOf(latScale, lonScale)
                    val offsetX = padding + (drawWidth - lonRange * scale) / 2
                    val offsetY = padding + (drawHeight - latRange * scale) / 2
                    Offset(
                        (offsetX + (points.last().longitude - bounds.minLongitude) * scale).toFloat(),
                        (offsetY + (bounds.maxLatitude - points.last().latitude) * scale).toFloat()
                    )
                } else null

                onDrawBehind {
                    drawRect(backgroundColor)

                    if (path != null) {
                        drawPath(
                            path = path,
                            color = routeColor,
                            style = Stroke(
                                width = routeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    if (showStartEnd && startCenter != null && endCenter != null) {
                        drawCircle(
                            color = Color(0xFF66BB6A),
                            radius = 8f,
                            center = startCenter
                        )
                        drawCircle(
                            color = Color(0xFFEF5350),
                            radius = 8f,
                            center = endCenter
                        )
                    }
                }
            }
    )
}
