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
 * Supports both static (summary) and dynamic (per-frame) rendering to match
 * the Compose preview overlays in StyleTelemetryScreen.
 */
object StoryTemplateRenderer {

    data class FrameData(
        val distance: Double = 0.0,
        val elevation: Double = 0.0,
        val speed: Double = 0.0,
        val heartRate: Int? = null,
        val progress: Float = 0f
    )

    fun render(
        template: String,
        width: Int,
        height: Int,
        gpxData: GpxData?,
        gpxStats: GpxStats?,
        frameData: FrameData? = null
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val isPortrait = height > width
        val isLandscape = width.toFloat() / height > 1.4f

        when (template) {
            "CINEMATIC" -> renderCinematic(canvas, width, height, gpxData, gpxStats, frameData, isPortrait, isLandscape)
            "HERO" -> renderHero(canvas, width, height, gpxData, gpxStats, frameData, isPortrait, isLandscape)
            "PRO_DASHBOARD" -> renderProDashboard(canvas, width, height, gpxData, gpxStats, frameData, isPortrait, isLandscape)
        }

        return bitmap
    }

    private fun renderCinematic(
        canvas: Canvas, w: Int, h: Int,
        gpxData: GpxData?, gpxStats: GpxStats?,
        frameData: FrameData?,
        isPortrait: Boolean, isLandscape: Boolean
    ) {
        val scale = w / 1080f
        val isLive = frameData != null

        // Bottom gradient scrim
        val scrimH = if (isPortrait) h * 0.35f else h * 0.4f
        val gradientPaint = Paint().apply {
            shader = LinearGradient(
                0f, h - scrimH, 0f, h.toFloat(),
                Color.TRANSPARENT, Color.argb(180, 0, 0, 0),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, h - scrimH, w.toFloat(), h.toFloat(), gradientPaint)

        val condensed = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val accent = Color.argb(200, 100, 180, 255)

        val distKm = if (isLive) "%.1f".format(frameData!!.distance / 1000.0)
        else gpxData?.let { "%.1f".format(it.totalDistance / 1000.0) } ?: "—"

        val elev = if (isLive) "%.0f".format(frameData!!.elevation)
        else gpxData?.let { "%.0f".format(it.totalElevationGain) } ?: "—"

        val pace = if (isLive) formatPace(frameData!!.speed)
        else gpxData?.let {
            val s = if (it.totalDuration.seconds > 0) it.totalDistance / it.totalDuration.seconds.toDouble() else 0.0
            formatPace(s)
        } ?: "—"

        val progress = frameData?.progress ?: 1f
        val margin = if (isLandscape) 32f * scale else 20f * scale
        val bottom = h - margin

        // Glass cards
        val cardPaint = glassCardPaint()
        val borderPaint = glassCardBorder(scale)

        val labelPaint = labelPaint(scale, condensed, accent)
        val valuePaint = valuePaint(scale * (if (isLandscape) 1f else 0.8f), condensed)
        val unitPaint = unitPaint(scale, condensed)

        val cardH = (if (isLandscape) 110f else 85f) * scale
        val bigCardW = (if (isLandscape) 160f else 120f) * scale
        val smallCardW = (if (isLandscape) 100f else 75f) * scale
        val spacing = 8f * scale

        // Big card: DISTANCE
        val bigY = bottom - cardH - (cardH + spacing) * 0f - 60f * scale // leave room for elev chart
        drawGlassCard(canvas, margin, bigY, bigCardW, cardH, cardPaint, borderPaint, scale)
        canvas.drawText("DISTANCE", margin + 12f * scale, bigY + 28f * scale, labelPaint)
        canvas.drawText(distKm, margin + 12f * scale, bigY + 64f * scale, valuePaint)
        canvas.drawText("km", margin + 12f * scale + valuePaint.measureText(distKm) + 4f * scale, bigY + 64f * scale, unitPaint)

        // Small cards: ELEV + PACE
        val smallY = bigY + cardH + spacing
        drawGlassCard(canvas, margin, smallY, smallCardW, cardH * 0.8f, cardPaint, borderPaint, scale)
        canvas.drawText("ELEV", margin + 10f * scale, smallY + 24f * scale, labelPaint)
        canvas.drawText("$elev m", margin + 10f * scale, smallY + 52f * scale,
            valuePaint.also { it.textSize = 28f * scale })

        drawGlassCard(canvas, margin + smallCardW + spacing, smallY, smallCardW, cardH * 0.8f, cardPaint, borderPaint, scale)
        canvas.drawText("PACE", margin + smallCardW + spacing + 10f * scale, smallY + 24f * scale, labelPaint)
        canvas.drawText(pace, margin + smallCardW + spacing + 10f * scale, smallY + 52f * scale,
            valuePaint.also { it.textSize = 28f * scale })

        // Mini elevation chart at bottom
        val chartH = 50f * scale
        val chartBottom = bottom
        val chartTop = chartBottom - chartH
        renderElevationChartInRect(canvas, gpxData, margin, chartTop, w - margin, chartBottom, scale, progress, accent)
    }

    private fun renderHero(
        canvas: Canvas, w: Int, h: Int,
        gpxData: GpxData?, gpxStats: GpxStats?,
        frameData: FrameData?,
        isPortrait: Boolean, isLandscape: Boolean
    ) {
        val scale = w / 1080f
        val isLive = frameData != null
        val condensed = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val accent = Color.argb(200, 100, 180, 255)

        val distKm = if (isLive) "%.1f".format(frameData!!.distance / 1000.0)
        else gpxData?.let { "%.1f".format(it.totalDistance / 1000.0) } ?: "—"

        val elevVal = if (isLive) "%.0f".format(frameData!!.elevation)
        else gpxData?.let { "%.0f".format(it.totalElevationGain) } ?: "—"

        val duration = gpxData?.let { formatDuration(it.totalDuration.toMillis()) } ?: "—"

        val hrVal = if (isLive) frameData!!.heartRate?.toString() ?: "—"
        else gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points }
            ?.mapNotNull { it.heartRate }?.takeIf { it.isNotEmpty() }?.let { "%.0f".format(it.average()) } ?: "—"

        val progress = frameData?.progress ?: 1f
        val centerY = h * 0.42f

        // "DISTANCE" label
        val centerLabelPaint = Paint().apply {
            color = Color.argb(128, 255, 255, 255)
            textSize = 18f * scale
            typeface = condensed
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.15f
        }
        canvas.drawText("DISTANCE", w / 2f, centerY - 70f * scale, centerLabelPaint)

        // Big number
        val heroFontSize = when {
            isLandscape -> 100f * scale
            isPortrait -> 80f * scale
            else -> 90f * scale
        }
        val heroPaint = Paint().apply {
            color = Color.WHITE
            textSize = heroFontSize
            typeface = condensed
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(distKm, w / 2f, centerY + 20f * scale, heroPaint)

        // "KM" label
        val kmPaint = Paint().apply {
            color = accent
            textSize = 28f * scale
            typeface = condensed
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.2f
        }
        canvas.drawText("KM", w / 2f, centerY + 52f * scale, kmPaint)

        // Secondary metric cards
        val cardPaint = glassCardPaint()
        val borderPaint = glassCardBorder(scale)

        val cardW = (if (isLandscape) 160f else 120f) * scale
        val cardH = 85f * scale
        val cardSpacing = 12f * scale

        val metrics = mutableListOf(
            Triple(elevVal, if (isLive) "m alt" else "m gain", "⬆"),
            Triple(duration, "time", "⏱"),
            Triple(hrVal, "avg bpm", "❤")
        )

        val totalW = metrics.size * cardW + (metrics.size - 1) * cardSpacing
        var cx = (w - totalW) / 2f
        val metricsY = centerY + 90f * scale

        val metricValuePaint = Paint().apply {
            color = Color.WHITE
            textSize = 30f * scale
            typeface = condensed
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val metricLabelPaint = Paint().apply {
            color = Color.argb(128, 255, 255, 255)
            textSize = 16f * scale
            typeface = condensed
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        for (m in metrics) {
            drawGlassCard(canvas, cx, metricsY, cardW, cardH, cardPaint, borderPaint, scale)
            val cardCenterX = cx + cardW / 2f
            canvas.drawText(m.first, cardCenterX, metricsY + 40f * scale, metricValuePaint)
            canvas.drawText(m.second, cardCenterX, metricsY + 65f * scale, metricLabelPaint)
            cx += cardW + cardSpacing
        }

        // Elevation chart at bottom
        val margin = 24f * scale
        val chartH = 55f * scale
        val chartBottom = h - margin
        val chartTop = chartBottom - chartH
        renderElevationChartInRect(canvas, gpxData, margin, chartTop, w - margin, chartBottom, scale, progress, accent)
    }

    private fun renderProDashboard(
        canvas: Canvas, w: Int, h: Int,
        gpxData: GpxData?, gpxStats: GpxStats?,
        frameData: FrameData?,
        isPortrait: Boolean, isLandscape: Boolean
    ) {
        val scale = w / 1080f
        val isLive = frameData != null
        val condensed = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val accent = Color.argb(200, 100, 180, 255)

        val distKm = if (isLive) "%.1f".format(frameData!!.distance / 1000.0)
        else gpxData?.let { "%.1f".format(it.totalDistance / 1000.0) } ?: "—"

        val elevGain = if (isLive) "%.0f".format(frameData!!.elevation)
        else gpxData?.let { "%.0f".format(it.totalElevationGain) } ?: "—"

        val speed = if (isLive) "%.1f".format(frameData!!.speed * 3.6)
        else gpxData?.let {
            val s = it.totalDistance / it.totalDuration.seconds.toDouble()
            "%.1f".format(s * 3.6)
        } ?: "—"

        val hrVal = if (isLive) frameData!!.heartRate?.toString() ?: "—"
        else gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points }
            ?.mapNotNull { it.heartRate }?.takeIf { it.isNotEmpty() }?.let { "%.0f".format(it.average()) } ?: "—"

        val duration = gpxData?.let { formatDuration(it.totalDuration.toMillis()) } ?: "—"
        val progress = frameData?.progress ?: 1f

        val metrics = listOf(
            Triple("DISTANCE", "$distKm km", accent),
            Triple("ELEVATION", "$elevGain m", Color.argb(200, 102, 187, 106)),
            Triple("SPEED", "$speed km/h", Color.argb(200, 255, 171, 64)),
            Triple("HEART RATE", "$hrVal bpm", Color.argb(200, 239, 83, 80)),
            Triple("TIME", duration, Color.argb(200, 38, 166, 154))
        )

        if (isPortrait) {
            // Dashboard at bottom
            val panelH = (h * 0.38f).toInt()
            val panelY = h - panelH

            val panelPaint = Paint().apply { color = Color.argb(140, 0, 0, 0) }
            canvas.drawRect(0f, panelY.toFloat(), w.toFloat(), h.toFloat(), panelPaint)

            // 2-column grid
            val mx = 16f * scale
            val cellW = (w - mx * 3) / 2
            val cellH = (panelH - 24f * scale) / 3 // 3 rows

            val labelPaint = Paint().apply {
                color = Color.argb(128, 255, 255, 255)
                textSize = 16f * scale
                typeface = condensed
                isAntiAlias = true
                letterSpacing = 0.1f
            }

            for ((i, m) in metrics.withIndex()) {
                val col = i % 2
                val row = i / 2
                val cx = mx + col * (cellW + mx)
                val cy = panelY + 16f * scale + row * cellH

                // Accent color indicator
                val indicatorPaint = Paint().apply { color = m.third; isAntiAlias = true }
                canvas.drawRoundRect(RectF(cx, cy + 4f * scale, cx + 4f * scale, cy + 36f * scale), 2f, 2f, indicatorPaint)

                labelPaint.color = Color.argb(128, 255, 255, 255)
                canvas.drawText(m.first, cx + 12f * scale, cy + 18f * scale, labelPaint)

                val vp = Paint().apply {
                    color = Color.WHITE
                    textSize = 32f * scale
                    typeface = condensed
                    isAntiAlias = true
                }
                canvas.drawText(m.second, cx + 12f * scale, cy + 52f * scale, vp)
            }

            // Elevation chart at very bottom
            val chartH = 40f * scale
            val chartBottom = h - 8f * scale
            val chartTop = chartBottom - chartH
            renderElevationChartInRect(canvas, gpxData, mx, chartTop, w - mx, chartBottom, scale, progress, accent)

        } else {
            // Landscape: panel on right side
            val panelW = (w * 0.38f).toInt()
            val panelX = w - panelW

            val panelPaint = Paint().apply { color = Color.argb(180, 15, 15, 30) }
            canvas.drawRect(panelX.toFloat(), 0f, w.toFloat(), h.toFloat(), panelPaint)

            val dividerPaint = Paint().apply {
                color = Color.argb(51, 255, 255, 255)
                strokeWidth = 2f
            }
            canvas.drawLine(panelX.toFloat(), 0f, panelX.toFloat(), h.toFloat(), dividerPaint)

            val mx = panelX + 20f * scale
            var my = 50f * scale
            val rowSpacing = (h - 100f * scale - 80f * scale) / metrics.size // leave room for chart

            val labelPaint = labelPaint(scale, condensed, accent)
            val valuePaint = valuePaint(scale, condensed)
            val unitPaint = unitPaint(scale, condensed)

            for (m in metrics) {
                // Accent indicator
                val indicatorPaint = Paint().apply { color = m.third; isAntiAlias = true }
                canvas.drawRoundRect(RectF(mx - 8f * scale, my - 2f * scale, mx - 4f * scale, my + 40f * scale), 2f, 2f, indicatorPaint)

                labelPaint.color = Color.argb(128, 255, 255, 255)
                canvas.drawText(m.first, mx, my + 14f * scale, labelPaint)

                valuePaint.textSize = 36f * scale
                canvas.drawText(m.second, mx, my + 50f * scale, valuePaint)

                my += rowSpacing
            }

            // Mini elevation chart at bottom of panel
            val chartLeft = panelX + 12f * scale
            val chartRight = w - 12f * scale
            val chartBottom = h - 16f * scale
            val chartTop = chartBottom - 60f * scale
            renderElevationChartInRect(canvas, gpxData, chartLeft, chartTop, chartRight, chartBottom, scale, progress, accent)
        }
    }

    // ── Shared helpers ──

    private fun drawGlassCard(
        canvas: Canvas, x: Float, y: Float, w: Float, h: Float,
        cardPaint: Paint, borderPaint: Paint, scale: Float
    ) {
        val r = 16f * scale
        val rect = RectF(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, r, r, cardPaint)
        canvas.drawRoundRect(rect, r, r, borderPaint)
    }

    private fun glassCardPaint() = Paint().apply {
        color = Color.argb(25, 255, 255, 255)
        isAntiAlias = true
    }

    private fun glassCardBorder(scale: Float) = Paint().apply {
        color = Color.argb(40, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * scale
        isAntiAlias = true
    }

    private fun labelPaint(scale: Float, tf: Typeface, accent: Int) = Paint().apply {
        color = Color.argb(android.graphics.Color.alpha(accent), android.graphics.Color.red(accent), android.graphics.Color.green(accent), android.graphics.Color.blue(accent))
        this.color = Color.argb(153, 255, 255, 255)
        textSize = 18f * scale
        typeface = tf
        isAntiAlias = true
        letterSpacing = 0.1f
    }

    private fun valuePaint(scale: Float, tf: Typeface) = Paint().apply {
        color = Color.WHITE
        textSize = 40f * scale
        typeface = tf
        isAntiAlias = true
    }

    private fun unitPaint(scale: Float, tf: Typeface) = Paint().apply {
        color = Color.argb(128, 255, 255, 255)
        textSize = 20f * scale
        typeface = tf
        isAntiAlias = true
    }

    private fun renderElevationChartInRect(
        canvas: Canvas, gpxData: GpxData?,
        left: Float, top: Float, right: Float, bottom: Float,
        scale: Float, progress: Float, accentColor: Int
    ) {
        val elevations = gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points }
            ?.mapNotNull { it.elevation } ?: return
        if (elevations.size < 2) return

        val step = (elevations.size / 80).coerceAtLeast(1)
        val sampled = elevations.filterIndexed { i, _ -> i % step == 0 }
        val minElev = sampled.min()
        val maxElev = sampled.max()
        val range = (maxElev - minElev).coerceAtLeast(1.0)

        val chartW = right - left
        val chartH = bottom - top

        val path = Path()
        val filledPath = Path()
        val progressIndex = (progress * (sampled.size - 1)).toInt().coerceIn(0, sampled.size - 1)

        sampled.forEachIndexed { i, elev ->
            val x = left + (i.toFloat() / (sampled.size - 1)) * chartW
            val y = bottom - ((elev - minElev) / range).toFloat() * chartH * 0.85f
            if (i == 0) {
                path.moveTo(x, y)
                filledPath.moveTo(x, y)
            } else {
                path.lineTo(x, y)
                filledPath.lineTo(x, y)
            }
        }

        // Unvisited part: faint line
        val faintLinePaint = Paint().apply {
            color = Color.argb(60, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f * scale
            isAntiAlias = true
        }
        canvas.drawPath(path, faintLinePaint)

        // Visited part: filled with gradient up to progress
        if (progress > 0.01f) {
            val visitedPath = Path()
            val visitedFill = Path()
            sampled.take(progressIndex + 1).forEachIndexed { i, elev ->
                val x = left + (i.toFloat() / (sampled.size - 1)) * chartW
                val y = bottom - ((elev - minElev) / range).toFloat() * chartH * 0.85f
                if (i == 0) {
                    visitedPath.moveTo(x, y)
                    visitedFill.moveTo(x, y)
                } else {
                    visitedPath.lineTo(x, y)
                    visitedFill.lineTo(x, y)
                }
            }

            // Bright accent line for visited portion
            val visitedLinePaint = Paint().apply {
                color = accentColor
                style = Paint.Style.STROKE
                strokeWidth = 2.5f * scale
                isAntiAlias = true
            }
            canvas.drawPath(visitedPath, visitedLinePaint)

            // Fill below visited portion
            val lastX = left + (progressIndex.toFloat() / (sampled.size - 1)) * chartW
            visitedFill.lineTo(lastX, bottom)
            visitedFill.lineTo(left, bottom)
            visitedFill.close()

            val fillPaint = Paint().apply {
                shader = LinearGradient(
                    0f, top, 0f, bottom,
                    Color.argb(80, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                    Color.argb(10, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                    Shader.TileMode.CLAMP
                )
                isAntiAlias = true
            }
            canvas.drawPath(visitedFill, fillPaint)

            // Progress indicator dot
            if (progressIndex < sampled.size) {
                val dotElev = sampled[progressIndex]
                val dotX = left + (progressIndex.toFloat() / (sampled.size - 1)) * chartW
                val dotY = bottom - ((dotElev - minElev) / range).toFloat() * chartH * 0.85f
                val dotPaint = Paint().apply { color = accentColor; isAntiAlias = true }
                canvas.drawCircle(dotX, dotY, 4f * scale, dotPaint)
                val glowPaint = Paint().apply { color = Color.argb(60, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)); isAntiAlias = true }
                canvas.drawCircle(dotX, dotY, 8f * scale, glowPaint)
            }
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(minutes, seconds)
    }

    private fun formatPace(speedMs: Double): String {
        if (speedMs <= 0.1) return "—"
        val kmh = speedMs * 3.6
        return "%.1f".format(kmh)
    }
}
