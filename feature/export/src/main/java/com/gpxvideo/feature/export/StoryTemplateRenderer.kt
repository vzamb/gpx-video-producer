package com.gpxvideo.feature.export

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
import com.gpxvideo.lib.gpxparser.GpxStats

/**
 * Renders story template overlays as bitmaps for the export pipeline.
 * Replicates the Compose template previews using Android Canvas API.
 */
object StoryTemplateRenderer {

    fun render(
        template: String,
        width: Int,
        height: Int,
        gpxData: GpxData?,
        gpxStats: GpxStats?
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Transparent background - only draw overlay elements
        canvas.drawColor(Color.TRANSPARENT)

        when (template) {
            "CINEMATIC" -> renderCinematic(canvas, width, height, gpxData, gpxStats)
            "HERO" -> renderHero(canvas, width, height, gpxData, gpxStats)
            "PRO_DASHBOARD" -> renderProDashboard(canvas, width, height, gpxData, gpxStats)
        }

        return bitmap
    }

    private fun renderCinematic(
        canvas: Canvas, w: Int, h: Int,
        gpxData: GpxData?, gpxStats: GpxStats?
    ) {
        val scale = w / 1080f

        // Bottom gradient scrim
        val gradientPaint = Paint().apply {
            shader = LinearGradient(
                0f, h * 0.6f, 0f, h.toFloat(),
                Color.TRANSPARENT, Color.argb(200, 0, 0, 0),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, h * 0.6f, w.toFloat(), h.toFloat(), gradientPaint)

        val margin = 40f * scale
        var y = h - margin

        // Glassmorphic card background
        val cardPaint = Paint().apply {
            color = Color.argb(20, 255, 255, 255)
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = Color.argb(38, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * scale
            isAntiAlias = true
        }

        val condensed = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val labelPaint = Paint().apply {
            color = Color.argb(153, 255, 255, 255)
            textSize = 22f * scale
            typeface = condensed
            isAntiAlias = true
            letterSpacing = 0.1f
        }
        val valuePaint = Paint().apply {
            color = Color.WHITE
            textSize = 48f * scale
            typeface = condensed
            isAntiAlias = true
        }
        val unitPaint = Paint().apply {
            color = Color.argb(128, 255, 255, 255)
            textSize = 24f * scale
            typeface = condensed
            isAntiAlias = true
        }

        val distKm = gpxData?.let { "%.1f".format(it.totalDistance / 1000.0) } ?: "42.5"
        val elevGain = gpxData?.let { "%.0f".format(it.totalElevationGain) } ?: "820"
        val duration = gpxData?.let { formatDuration(it.totalDuration.toMillis()) } ?: "3:42:15"

        // Draw 3 metric cards at bottom-left
        val cardH = 120f * scale
        val cardW = 160f * scale
        val cardSpacing = 12f * scale
        val cards = listOf(
            Triple("DISTANCE", distKm, "km"),
            Triple("ELEVATION", elevGain, "m"),
            Triple("TIME", duration, "")
        )

        y -= cardH
        for ((i, card) in cards.withIndex()) {
            val cx = margin + i * (cardW + cardSpacing)
            val rect = RectF(cx, y, cx + cardW, y + cardH)
            canvas.drawRoundRect(rect, 24f * scale, 24f * scale, cardPaint)
            canvas.drawRoundRect(rect, 24f * scale, 24f * scale, borderPaint)

            canvas.drawText(card.first, cx + 16f * scale, y + 36f * scale, labelPaint)
            canvas.drawText(card.second, cx + 16f * scale, y + 80f * scale, valuePaint)
            if (card.third.isNotEmpty()) {
                canvas.drawText(card.third, cx + 16f * scale + valuePaint.measureText(card.second) + 6f * scale, y + 80f * scale, unitPaint)
            }
        }
    }

    private fun renderHero(
        canvas: Canvas, w: Int, h: Int,
        gpxData: GpxData?, gpxStats: GpxStats?
    ) {
        val scale = w / 1080f

        // Centered distance
        val condensed = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val distKm = gpxData?.let { "%.1f".format(it.totalDistance / 1000.0) } ?: "42.5"
        val elevGain = gpxData?.let { "%.0f".format(it.totalElevationGain) } ?: "820"
        val duration = gpxData?.let { formatDuration(it.totalDuration.toMillis()) } ?: "3:42:15"
        val avgHr = gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points }
            ?.mapNotNull { it.heartRate }?.takeIf { it.isNotEmpty() }?.average()?.let { "%.0f".format(it) }

        val centerY = h * 0.42f

        // "DISTANCE" label
        val labelPaint = Paint().apply {
            color = Color.argb(128, 255, 255, 255)
            textSize = 22f * scale
            typeface = condensed
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.15f
        }
        canvas.drawText("DISTANCE", w / 2f, centerY - 80f * scale, labelPaint)

        // Big number
        val heroPaint = Paint().apply {
            color = Color.WHITE
            textSize = 120f * scale
            typeface = condensed
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(distKm, w / 2f, centerY + 30f * scale, heroPaint)

        // "KM" label
        val kmPaint = Paint().apply {
            color = Color.argb(200, 100, 180, 255) // primary-like color
            textSize = 36f * scale
            typeface = condensed
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.2f
        }
        canvas.drawText("KM", w / 2f, centerY + 70f * scale, kmPaint)

        // Secondary metrics row (glassmorphic cards)
        val cardPaint = Paint().apply {
            color = Color.argb(20, 255, 255, 255)
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = Color.argb(38, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * scale
            isAntiAlias = true
        }
        val metricLabelPaint = Paint().apply {
            color = Color.argb(128, 255, 255, 255)
            textSize = 20f * scale
            typeface = condensed
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val metricValuePaint = Paint().apply {
            color = Color.WHITE
            textSize = 36f * scale
            typeface = condensed
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val metricsY = centerY + 130f * scale
        val cardW = 180f * scale
        val cardH = 110f * scale
        val metrics = mutableListOf(
            Triple("⬆️", elevGain, "m gain"),
            Triple("⏱️", duration, "time")
        )
        if (avgHr != null) metrics.add(Triple("💓", avgHr, "avg bpm"))

        val totalW = metrics.size * cardW + (metrics.size - 1) * 16f * scale
        var cx = (w - totalW) / 2f

        for (m in metrics) {
            val rect = RectF(cx, metricsY, cx + cardW, metricsY + cardH)
            canvas.drawRoundRect(rect, 24f * scale, 24f * scale, cardPaint)
            canvas.drawRoundRect(rect, 24f * scale, 24f * scale, borderPaint)

            val cardCenterX = cx + cardW / 2f
            // Emoji not rendered well in Canvas; just show the value/label
            canvas.drawText(m.second, cardCenterX, metricsY + 50f * scale, metricValuePaint)
            canvas.drawText(m.third, cardCenterX, metricsY + 82f * scale, metricLabelPaint)

            cx += cardW + 16f * scale
        }

        // Elevation chart at bottom
        renderElevationChart(canvas, w, h, gpxData, scale, 0.65f)
    }

    private fun renderProDashboard(
        canvas: Canvas, w: Int, h: Int,
        gpxData: GpxData?, gpxStats: GpxStats?
    ) {
        val scale = w / 1080f
        val panelW = (w * 0.38f).toInt()
        val panelX = w - panelW

        // Semi-transparent dark panel on right
        val panelPaint = Paint().apply {
            color = Color.argb(200, 15, 15, 30)
        }
        canvas.drawRect(panelX.toFloat(), 0f, w.toFloat(), h.toFloat(), panelPaint)

        // Divider line
        val dividerPaint = Paint().apply {
            color = Color.argb(51, 255, 255, 255)
            strokeWidth = 2f
        }
        canvas.drawLine(panelX.toFloat(), 0f, panelX.toFloat(), h.toFloat(), dividerPaint)

        val condensed = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val distKm = gpxData?.let { "%.1f".format(it.totalDistance / 1000.0) } ?: "42.5"
        val elevGain = gpxData?.let { "%.0f".format(it.totalElevationGain) } ?: "820"
        val duration = gpxData?.let { formatDuration(it.totalDuration.toMillis()) } ?: "3:42:15"
        val speed = gpxData?.let {
            val s = it.totalDistance / it.totalDuration.seconds.toDouble()
            "%.1f".format(s * 3.6)
        } ?: "28.5"
        val avgHr = gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points }
            ?.mapNotNull { it.heartRate }?.takeIf { it.isNotEmpty() }?.average()?.let { "%.0f".format(it) }

        val labelPaint = Paint().apply {
            color = Color.argb(128, 255, 255, 255)
            textSize = 20f * scale
            typeface = condensed
            isAntiAlias = true
            letterSpacing = 0.1f
        }
        val valuePaint = Paint().apply {
            color = Color.WHITE
            textSize = 40f * scale
            typeface = condensed
            isAntiAlias = true
        }
        val unitPaint = Paint().apply {
            color = Color.argb(128, 255, 255, 255)
            textSize = 22f * scale
            typeface = condensed
            isAntiAlias = true
        }

        val mx = panelX + 24f * scale
        var my = 80f * scale
        val rowSpacing = 120f * scale

        val metrics = mutableListOf(
            Triple("DISTANCE", distKm, "km"),
            Triple("SPEED", speed, "km/h"),
            Triple("ELEVATION", elevGain, "m"),
            Triple("TIME", duration, "")
        )
        if (avgHr != null) metrics.add(Triple("HEART RATE", avgHr, "bpm"))

        for (m in metrics) {
            canvas.drawText(m.first, mx, my, labelPaint)
            canvas.drawText(m.second, mx, my + 44f * scale, valuePaint)
            if (m.third.isNotEmpty()) {
                canvas.drawText(
                    m.third,
                    mx + valuePaint.measureText(m.second) + 8f * scale,
                    my + 44f * scale,
                    unitPaint
                )
            }
            my += rowSpacing
        }

        // Mini elevation chart at bottom of panel
        val chartLeft = panelX + 16f * scale
        val chartRight = w - 16f * scale
        val chartBottom = h - 30f * scale
        val chartTop = chartBottom - 80f * scale
        renderElevationChartInRect(canvas, gpxData, chartLeft, chartTop, chartRight, chartBottom, scale)
    }

    private fun renderElevationChart(
        canvas: Canvas, w: Int, h: Int,
        gpxData: GpxData?, scale: Float, progress: Float
    ) {
        val margin = 32f * scale
        val chartH = 80f * scale
        val chartBottom = h - margin
        val chartTop = chartBottom - chartH
        renderElevationChartInRect(canvas, gpxData, margin, chartTop, w - margin, chartBottom, scale)
    }

    private fun renderElevationChartInRect(
        canvas: Canvas, gpxData: GpxData?,
        left: Float, top: Float, right: Float, bottom: Float, scale: Float
    ) {
        val elevations = gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points }
            ?.mapNotNull { it.elevation } ?: return
        if (elevations.size < 2) return

        // Downsample to ~80 points
        val step = (elevations.size / 80).coerceAtLeast(1)
        val sampled = elevations.filterIndexed { i, _ -> i % step == 0 }
        val minElev = sampled.min()
        val maxElev = sampled.max()
        val range = (maxElev - minElev).coerceAtLeast(1.0)

        val chartW = right - left
        val chartH = bottom - top

        val path = Path()
        sampled.forEachIndexed { i, elev ->
            val x = left + (i.toFloat() / (sampled.size - 1)) * chartW
            val y = bottom - ((elev - minElev) / range).toFloat() * chartH * 0.85f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // Draw line
        val linePaint = Paint().apply {
            color = Color.argb(200, 100, 180, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * scale
            isAntiAlias = true
        }
        canvas.drawPath(path, linePaint)

        // Fill below
        val fillPath = Path(path)
        fillPath.lineTo(right, bottom)
        fillPath.lineTo(left, bottom)
        fillPath.close()

        val fillPaint = Paint().apply {
            shader = LinearGradient(
                0f, top, 0f, bottom,
                Color.argb(80, 100, 180, 255), Color.argb(10, 100, 180, 255),
                Shader.TileMode.CLAMP
            )
            isAntiAlias = true
        }
        canvas.drawPath(fillPath, fillPaint)
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(minutes, seconds)
    }
}
