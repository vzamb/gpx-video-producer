package com.gpxvideo.feature.overlays

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.GpxPoint
import com.gpxvideo.core.model.MapStyle
import com.gpxvideo.core.model.OverlayConfig
import com.gpxvideo.core.model.StatField
import com.gpxvideo.core.model.StatsLayout
import com.gpxvideo.lib.gpxparser.GpxStatistics
import com.gpxvideo.lib.gpxparser.GpxStats
import java.time.Duration
import kotlin.math.abs

object OverlayRenderer {

    fun renderStaticAltitudeProfile(
        overlay: OverlayConfig.StaticAltitudeProfile,
        gpxData: GpxData,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val allPoints = gpxData.tracks.flatMap { it.segments.flatMap { s -> s.points } }

        if (allPoints.size < 2) return bitmap

        val leftPad = if (overlay.showLabels) width * 0.12f else width * 0.05f
        val rightPad = width * 0.05f
        val topPad = height * 0.1f
        val bottomPad = if (overlay.showLabels) height * 0.15f else height * 0.1f
        val drawW = width - leftPad - rightPad
        val drawH = height - topPad - bottomPad

        val distances = computeDistances(allPoints)
        val totalDist = distances.last()
        val elevations = allPoints.map { it.elevation ?: 0.0 }
        val minEle = elevations.min()
        val maxEle = elevations.max()
        val eleRange = (maxEle - minEle).coerceAtLeast(1.0)

        fun xPos(dist: Double) = leftPad + (dist / totalDist * drawW).toFloat()
        fun yPos(ele: Double) = topPad + drawH - ((ele - minEle) / eleRange * drawH).toFloat()

        if (overlay.showGrid) {
            val gridPaint = Paint().apply {
                color = Color.argb(20, 255, 255, 255)
                strokeWidth = 1f
                style = Paint.Style.STROKE
            }
            for (i in 0..4) {
                val y = topPad + drawH * i / 4
                canvas.drawLine(leftPad, y, leftPad + drawW, y, gridPaint)
            }
            for (i in 0..4) {
                val x = leftPad + drawW * i / 4
                canvas.drawLine(x, topPad, x, topPad + drawH, gridPaint)
            }
        }

        // Gradient fill
        val fillPath = Path().apply {
            moveTo(xPos(distances[0]), yPos(elevations[0]))
            for (i in 1 until allPoints.size) {
                lineTo(xPos(distances[i]), yPos(elevations[i]))
            }
            lineTo(xPos(distances.last()), topPad + drawH)
            lineTo(xPos(distances[0]), topPad + drawH)
            close()
        }
        val fillPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
            shader = LinearGradient(
                0f, topPad, 0f, topPad + drawH,
                colorFromLong(overlay.fillColor),
                Color.argb(12, Color.red(colorFromLong(overlay.fillColor)),
                    Color.green(colorFromLong(overlay.fillColor)),
                    Color.blue(colorFromLong(overlay.fillColor))),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(fillPath, fillPaint)

        // Line
        val linePath = Path().apply {
            moveTo(xPos(distances[0]), yPos(elevations[0]))
            for (i in 1 until allPoints.size) {
                lineTo(xPos(distances[i]), yPos(elevations[i]))
            }
        }
        val linePaint = Paint().apply {
            color = colorFromLong(overlay.lineColor)
            strokeWidth = 2f.coerceAtLeast(width * 0.005f)
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            setShadowLayer(3f, 0f, 0f, Color.argb(140, 0, 0, 0))
        }
        canvas.drawPath(linePath, linePaint)

        if (overlay.showLabels) {
            val labelPaint = Paint().apply {
                color = Color.argb(220, 255, 255, 255)
                textSize = height * 0.08f
                isAntiAlias = true
                typeface = Typeface.MONOSPACE
                setShadowLayer(3f, 1f, 1f, Color.argb(200, 0, 0, 0))
            }
            canvas.drawText("${maxEle.toInt()}m", 2f, topPad + labelPaint.textSize * 0.4f, labelPaint)
            canvas.drawText("${minEle.toInt()}m", 2f, topPad + drawH + labelPaint.textSize * 0.4f, labelPaint)

            val distText = if (totalDist >= 1000) "%.1f km".format(totalDist / 1000) else "%.0f m".format(totalDist)
            labelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(distText, leftPad + drawW, topPad + drawH + labelPaint.textSize * 1.2f, labelPaint)
        }

        return bitmap
    }

    fun renderStaticMap(
        overlay: OverlayConfig.StaticMap,
        gpxData: GpxData,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val allPoints = gpxData.tracks.flatMap { it.segments.flatMap { s -> s.points } }

        if (allPoints.size < 2) return bitmap

        val bounds = gpxData.bounds
        val padding = width * 0.08f
        val drawW = width - 2 * padding
        val drawH = height - 2 * padding
        val latRange = bounds.maxLatitude - bounds.minLatitude
        val lonRange = bounds.maxLongitude - bounds.minLongitude

        if (latRange <= 0 || lonRange <= 0) return bitmap

        val latScale = drawH / latRange
        val lonScale = drawW / lonRange
        val scale = minOf(latScale, lonScale)
        val offsetX = padding + (drawW - lonRange * scale).toFloat() / 2
        val offsetY = padding + (drawH - latRange * scale).toFloat() / 2

        fun projectX(lon: Double) = (offsetX + (lon - bounds.minLongitude) * scale).toFloat()
        fun projectY(lat: Double) = (offsetY + (bounds.maxLatitude - lat) * scale).toFloat()

        val routePath = Path().apply {
            moveTo(projectX(allPoints[0].longitude), projectY(allPoints[0].latitude))
            for (i in 1 until allPoints.size) {
                lineTo(projectX(allPoints[i].longitude), projectY(allPoints[i].latitude))
            }
        }
        val routePaint = Paint().apply {
            color = colorFromLong(overlay.routeColor)
            strokeWidth = overlay.routeWidth.coerceAtLeast(1f)
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            setShadowLayer(4f, 0f, 0f, Color.argb(160, 0, 0, 0))
        }
        canvas.drawPath(routePath, routePaint)

        if (overlay.showStartEnd) {
            val markerRadius = width * 0.02f
            val startPaint = Paint().apply { color = Color.argb(255, 76, 175, 80); isAntiAlias = true }
            val endPaint = Paint().apply { color = Color.argb(255, 244, 67, 54); isAntiAlias = true }
            canvas.drawCircle(
                projectX(allPoints.first().longitude),
                projectY(allPoints.first().latitude),
                markerRadius, startPaint
            )
            canvas.drawCircle(
                projectX(allPoints.last().longitude),
                projectY(allPoints.last().latitude),
                markerRadius, endPaint
            )
        }

        return bitmap
    }

    fun renderStaticStats(
        overlay: OverlayConfig.StaticStats,
        stats: GpxStats,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val fields = overlay.fields.ifEmpty {
            listOf(StatField.TOTAL_DISTANCE, StatField.TOTAL_TIME)
        }
        val values = fields.map { it to getStatValue(it, stats) }

        val (cols, rows) = layoutDimensions(overlay.layout, values.size)
        val cellW = width.toFloat() / cols
        val cellH = height.toFloat() / rows

        val shadowRadius = width * 0.008f
        val shadowColor = Color.argb(180, 0, 0, 0)

        val labelPaint = Paint().apply {
            color = Color.argb(200, 255, 255, 255)
            textSize = (cellH * 0.22f).coerceAtMost(width * 0.045f).coerceAtLeast(10f)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            letterSpacing = 0.04f
            setShadowLayer(shadowRadius, 1f, 1f, shadowColor)
        }
        val valuePaint = Paint().apply {
            color = colorFromLong(overlay.style.fontColor)
            textSize = (cellH * 0.38f).coerceAtMost(width * 0.08f).coerceAtLeast(14f)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(shadowRadius * 1.5f, 1.5f, 1.5f, shadowColor)
        }
        val unitPaint = Paint().apply {
            color = Color.argb(160, 255, 255, 255)
            textSize = (cellH * 0.16f).coerceAtMost(width * 0.035f).coerceAtLeast(8f)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(shadowRadius, 1f, 1f, shadowColor)
        }

        values.forEachIndexed { index, (field, value) ->
            val col = index % cols
            val row = index / cols
            if (row >= rows) return@forEachIndexed

            val cx = cellW * col + cellW / 2
            val cy = cellH * row + cellH / 2

            canvas.drawText(field.displayName.uppercase(), cx, cy - cellH * 0.18f, labelPaint)
            canvas.drawText(value, cx, cy + cellH * 0.12f, valuePaint)
            if (field.unit.isNotEmpty()) {
                canvas.drawText(field.unit, cx, cy + cellH * 0.32f, unitPaint)
            }
        }

        return bitmap
    }

    private fun computeDistances(points: List<GpxPoint>): List<Double> {
        if (points.size < 2) return listOf(0.0)
        val dists = mutableListOf(0.0)
        for (i in 1 until points.size) {
            dists.add(
                dists.last() + GpxStatistics.computeDistance(
                    points[i - 1].latitude, points[i - 1].longitude,
                    points[i].latitude, points[i].longitude
                )
            )
        }
        return dists
    }

    private fun drawBackground(canvas: Canvas, width: Int, height: Int, bgColor: Long?, cornerRadius: Float) {
        // Only draw background if explicitly set (non-null). Default is transparent.
        val color = bgColor?.let { colorFromLong(it) } ?: return
        if (Color.alpha(color) == 0) return
        drawBackgroundColor(canvas, width, height, color, cornerRadius)
    }

    private fun drawBackgroundColor(canvas: Canvas, width: Int, height: Int, color: Int, cornerRadius: Float) {
        val paint = Paint().apply {
            this.color = color
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
    }

    private fun colorFromLong(color: Long): Int {
        return color.toInt()
    }

    private fun getStatValue(field: StatField, stats: GpxStats): String = when (field) {
        StatField.TOTAL_DISTANCE -> formatDistance(stats.totalDistance)
        StatField.TOTAL_TIME -> formatDuration(stats.totalDuration)
        StatField.MOVING_TIME -> formatDuration(stats.movingDuration)
        StatField.TOTAL_ELEVATION_GAIN -> "%.0f".format(stats.totalElevationGain)
        StatField.TOTAL_ELEVATION_LOSS -> "%.0f".format(stats.totalElevationLoss)
        StatField.AVG_SPEED -> "%.1f".format(stats.avgSpeed * 3.6)
        StatField.MAX_SPEED -> "%.1f".format(stats.maxSpeed * 3.6)
        StatField.AVG_PACE -> formatPace(stats.avgPace)
        StatField.BEST_PACE -> formatPace(stats.bestPace)
        StatField.AVG_HEART_RATE -> stats.avgHeartRate?.let { "%.0f".format(it) } ?: "--"
        StatField.MAX_HEART_RATE -> stats.maxHeartRate?.toString() ?: "--"
        StatField.AVG_CADENCE -> stats.avgCadence?.let { "%.0f".format(it) } ?: "--"
        StatField.AVG_POWER -> stats.avgPower?.let { "%.0f".format(it) } ?: "--"
        StatField.NORMALIZED_POWER -> "--"
        StatField.CALORIES -> "--"
        StatField.AVG_TEMPERATURE -> stats.avgTemperature?.let { "%.1f".format(it) } ?: "--"
    }

    private fun layoutDimensions(layout: StatsLayout, count: Int): Pair<Int, Int> = when (layout) {
        StatsLayout.SINGLE -> 1 to 1
        StatsLayout.GRID_2X1 -> 2 to 1
        StatsLayout.GRID_2X2 -> 2 to 2
        StatsLayout.GRID_3X2 -> 3 to 2
        StatsLayout.GRID_4X2 -> 4 to 2
        StatsLayout.VERTICAL_LIST -> 1 to count.coerceAtLeast(1)
    }

    private fun formatDistance(meters: Double): String {
        return if (meters >= 1000) "%.2f km".format(meters / 1000)
        else "%.0f m".format(meters)
    }

    private fun formatDuration(duration: Duration): String {
        val totalSeconds = duration.seconds
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "%d:%02d:%02d".format(hours, minutes, seconds)
            else -> "%d:%02d".format(minutes, seconds)
        }
    }

    private fun formatPace(paceMinPerKm: Double): String {
        if (paceMinPerKm <= 0 || paceMinPerKm.isInfinite() || paceMinPerKm.isNaN()) return "--:--"
        val minutes = paceMinPerKm.toInt()
        val seconds = ((paceMinPerKm - minutes) * 60).toInt()
        return "%d:%02d".format(minutes, seconds)
    }
}
