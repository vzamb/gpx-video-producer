package com.gpxvideo.core.overlayrenderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.gpxvideo.core.model.ChartType
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.GpxPoint
import java.time.Duration

/**
 * Renders a data chart (elevation, pace, heart rate, or power) onto a Canvas region.
 *
 * Uses monotone cubic Hermite spline interpolation for smooth, natural-looking
 * curves that never overshoot the data. Supports multi-stop area gradients,
 * line shadow, and radial-gradient dot/glow rendering.
 */
object ChartRenderer {

    fun render(
        canvas: Canvas,
        gpxData: GpxData?,
        chartType: ChartType,
        left: Float, top: Float, right: Float, bottom: Float,
        dp: Float,
        progress: Float,
        style: PlaceholderStyle
    ) {
        val points = gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points }
            ?: return
        if (points.size < 2) return

        val dataSeries = extractSeries(points, chartType)
        if (dataSeries.size < 2) return

        val accentColor = style.accentColor

        // Draw styled background if present
        if (style.hasBackground) {
            val bgPaint = Paint().apply {
                color = style.backgroundColor; isAntiAlias = true
            }
            canvas.drawRoundRect(left, top, right, bottom, style.cornerRadius, style.cornerRadius, bgPaint)
            if (style.borderColor != Color.TRANSPARENT) {
                val borderPaint = Paint().apply {
                    color = style.borderColor; setStyle(Paint.Style.STROKE)
                    strokeWidth = style.borderWidth * dp; isAntiAlias = true
                }
                canvas.drawRoundRect(left, top, right, bottom, style.cornerRadius, style.cornerRadius, borderPaint)
            }
        }

        val step = (dataSeries.size / 80).coerceAtLeast(1)
        val sampled = dataSeries.filterIndexed { i, _ -> i % step == 0 }
        val minVal = sampled.min()
        val maxVal = sampled.max()
        val range = (maxVal - minVal).coerceAtLeast(0.01)
        val chartW = right - left
        val chartH = bottom - top
        val chartDrawH = chartH * 0.85f
        val progressIndex = (progress * (sampled.size - 1)).toInt().coerceIn(0, sampled.size - 1)

        // Draw Y-axis grid lines and labels
        drawGrid(canvas, left, top, right, bottom, chartDrawH, minVal, maxVal, range, dp, accentColor, chartType)

        // Compute (x, y) data points
        val xs = FloatArray(sampled.size) { i -> left + (i.toFloat() / (sampled.size - 1)) * chartW }
        val ys = FloatArray(sampled.size) { i -> bottom - ((sampled[i] - minVal) / range).toFloat() * chartDrawH }

        // Full path with cubic spline
        val fullPath = buildSplinePath(xs, ys, 0, sampled.size)
        canvas.drawPath(fullPath, Paint().apply {
            color = style.fullPathColor; this.style = Paint.Style.STROKE
            strokeWidth = style.fullPathWidth * dp; isAntiAlias = true
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        })

        // Visited portion
        if (progress > 0.01f && progressIndex > 0) {
            val visitedLineColor = if (style.lineColor != Color.WHITE) style.lineColor else accentColor

            val visitedPath = buildSplinePath(xs, ys, 0, progressIndex + 1)

            // Line shadow
            canvas.drawPath(visitedPath, Paint().apply {
                color = Color.TRANSPARENT; this.style = Paint.Style.STROKE
                strokeWidth = (style.lineWidth + 1.5f) * dp; isAntiAlias = true
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                setShadowLayer(3f * dp, 0f, 1.5f * dp, Color.argb(80, 0, 0, 0))
            })

            // Visited line
            canvas.drawPath(visitedPath, Paint().apply {
                color = visitedLineColor; this.style = Paint.Style.STROKE
                strokeWidth = style.lineWidth * dp; isAntiAlias = true
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            })

            // Area fill with multi-stop gradient
            val fillPath = Path(visitedPath)
            val lastX = xs[progressIndex]
            fillPath.lineTo(lastX, bottom)
            fillPath.lineTo(xs[0], bottom)
            fillPath.close()

            val areaColor = if (style.areaFillColor != 0) style.areaFillColor else visitedLineColor
            canvas.drawPath(fillPath, Paint().apply {
                shader = LinearGradient(
                    0f, top, 0f, bottom,
                    intArrayOf(
                        withAlpha(areaColor, style.areaFillOpacity),
                        withAlpha(areaColor, (style.areaFillOpacity * 0.4f).toInt()),
                        withAlpha(areaColor, 5)
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
                isAntiAlias = true
            })

            // Progress dot with radial glow
            val dotY = ys[progressIndex]
            val dotFill = if (style.dotColor != Color.WHITE) style.dotColor else visitedLineColor
            val glowR = style.glowRadius * dp
            val dotR = style.dotRadius * dp

            // Outer glow (radial gradient)
            canvas.drawCircle(lastX, dotY, glowR, Paint().apply {
                isAntiAlias = true
                shader = RadialGradient(
                    lastX, dotY, glowR,
                    intArrayOf(withAlpha(dotFill, 100), withAlpha(dotFill, 30), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            })

            // Inner dot
            canvas.drawCircle(lastX, dotY, dotR, Paint().apply {
                isAntiAlias = true; color = dotFill
            })

            // White ring
            canvas.drawCircle(lastX, dotY, dotR, Paint().apply {
                isAntiAlias = true; color = Color.WHITE
                this.style = Paint.Style.STROKE; strokeWidth = 1.5f * dp
            })
        }
    }

    /**
     * Extract the data series for the given chart type from the GPX points.
     * Returns empty list if the data is not available.
     */
    private fun extractSeries(points: List<GpxPoint>, chartType: ChartType): List<Double> {
        return when (chartType) {
            ChartType.ELEVATION -> points.mapNotNull { it.elevation }
            ChartType.HEART_RATE -> points.mapNotNull { it.heartRate?.toDouble() }
            ChartType.POWER -> points.mapNotNull { it.power?.toDouble() }
            ChartType.PACE -> extractPaceSeries(points)
        }
    }

    /**
     * Compute pace (min/km) from consecutive points.
     * Uses distance/time deltas, smoothed with a rolling window.
     */
    private fun extractPaceSeries(points: List<GpxPoint>): List<Double> {
        if (points.size < 2) return emptyList()
        val paces = mutableListOf<Double>()
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val timeSec = if (prev.time != null && curr.time != null)
                Duration.between(prev.time, curr.time).seconds.toDouble() else 0.0
            val distM = haversineDistance(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
            val paceMinPerKm = if (distM > 1.0 && timeSec > 0) (timeSec / 60.0) / (distM / 1000.0) else 0.0
            // Clamp to reasonable range (1 to 20 min/km)
            paces.add(paceMinPerKm.coerceIn(1.0, 20.0))
        }
        // Smooth with simple rolling average (window = 5)
        val windowSize = 5
        return paces.mapIndexed { i, _ ->
            val start = (i - windowSize / 2).coerceAtLeast(0)
            val end = (i + windowSize / 2).coerceAtMost(paces.size - 1)
            paces.subList(start, end + 1).average()
        }
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    /**
     * Draws horizontal grid lines and value labels along the Y-axis.
     * Adapts label formatting based on chart type.
     */
    private fun drawGrid(
        canvas: Canvas,
        left: Float, top: Float, right: Float, bottom: Float,
        chartDrawH: Float,
        minVal: Double, maxVal: Double, range: Double,
        dp: Float,
        accentColor: Int,
        chartType: ChartType
    ) {
        val gridLineCount = if (range > 200 || chartType != ChartType.ELEVATION) 3 else 2
        val gridPaint = Paint().apply {
            color = Color.argb(30, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 0.5f * dp
            isAntiAlias = true
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f * dp, 4f * dp), 0f)
        }
        val labelPaint = Paint().apply {
            color = Color.argb(100, 255, 255, 255)
            textSize = 8f * dp
            isAntiAlias = true
            typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)
        }

        for (i in 0..gridLineCount) {
            val fraction = i.toFloat() / gridLineCount
            val value = minVal + fraction * range
            val y = bottom - fraction * chartDrawH

            canvas.drawLine(left, y, right, y, gridPaint)

            val label = formatGridLabel(value, chartType)
            val labelWidth = labelPaint.measureText(label)
            canvas.drawText(label, right - labelWidth - 4f * dp, y - 2f * dp, labelPaint)
        }
    }

    /** Format a grid label value based on the chart type. */
    private fun formatGridLabel(value: Double, chartType: ChartType): String = when (chartType) {
        ChartType.ELEVATION -> "${value.toInt()}m"
        ChartType.HEART_RATE -> "${value.toInt()}bpm"
        ChartType.POWER -> "${value.toInt()}W"
        ChartType.PACE -> {
            val totalMin = value
            val min = totalMin.toInt()
            val sec = ((totalMin - min) * 60).toInt()
            "$min:%02d".format(sec)
        }
    }

    /**
     * Build a smooth path through the given points using monotone cubic Hermite
     * spline interpolation. This guarantees no overshoot (monotonicity preserved)
     * and produces natural-looking curves.
     */
    private fun buildSplinePath(
        xs: FloatArray, ys: FloatArray,
        startIdx: Int, endIdx: Int
    ): Path {
        val n = endIdx - startIdx
        if (n < 2) return Path().apply { if (n == 1) moveTo(xs[startIdx], ys[startIdx]) }

        val path = Path()
        path.moveTo(xs[startIdx], ys[startIdx])

        if (n == 2) {
            path.lineTo(xs[startIdx + 1], ys[startIdx + 1])
            return path
        }

        // Compute tangents using monotone cubic Hermite method (Fritsch–Carlson)
        val tangents = FloatArray(n)
        val deltas = FloatArray(n - 1)
        val dxs = FloatArray(n - 1)

        for (i in 0 until n - 1) {
            dxs[i] = xs[startIdx + i + 1] - xs[startIdx + i]
            deltas[i] = if (dxs[i] != 0f) (ys[startIdx + i + 1] - ys[startIdx + i]) / dxs[i] else 0f
        }

        tangents[0] = deltas[0]
        tangents[n - 1] = deltas[n - 2]

        for (i in 1 until n - 1) {
            if (deltas[i - 1] * deltas[i] <= 0) {
                tangents[i] = 0f
            } else {
                tangents[i] = (deltas[i - 1] + deltas[i]) / 2f
            }
        }

        // Fritsch–Carlson monotonicity fix
        for (i in 0 until n - 1) {
            if (deltas[i] == 0f) {
                tangents[i] = 0f
                tangents[i + 1] = 0f
            } else {
                val alpha = tangents[i] / deltas[i]
                val beta = tangents[i + 1] / deltas[i]
                val sq = alpha * alpha + beta * beta
                if (sq > 9f) {
                    val tau = 3f / kotlin.math.sqrt(sq)
                    tangents[i] = tau * alpha * deltas[i]
                    tangents[i + 1] = tau * beta * deltas[i]
                }
            }
        }

        // Build cubic segments
        for (i in 0 until n - 1) {
            val dx = dxs[i] / 3f
            path.cubicTo(
                xs[startIdx + i] + dx, ys[startIdx + i] + tangents[i] * dx,
                xs[startIdx + i + 1] - dx, ys[startIdx + i + 1] - tangents[i + 1] * dx,
                xs[startIdx + i + 1], ys[startIdx + i + 1]
            )
        }

        return path
    }

    internal fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color), Color.green(color), Color.blue(color)
        )
    }
}
