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
 * Faithfully replicates the Compose overlay composables from StyleTelemetryScreen
 * using Android Canvas API, so the export matches the preview exactly.
 *
 * All dimensions use dp = outputWidth / 360f (matching a 360dp baseline phone screen).
 */
object StoryTemplateRenderer {

    data class FrameData(
        val distance: Double = 0.0,
        val elevation: Double = 0.0,
        val speed: Double = 0.0,
        val heartRate: Int? = null,
        val progress: Float = 0f
    )

    private const val GLASS_FILL_ALPHA = 20     // Color.White.copy(alpha = 0.08f)
    private const val GLASS_BORDER_ALPHA = 38   // Color.White.copy(alpha = 0.15f)

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
        val dp = width / 360f

        when (template) {
            "CINEMATIC" -> renderCinematic(canvas, width, height, dp, gpxData, gpxStats, frameData, isPortrait, isLandscape)
            "HERO" -> renderHero(canvas, width, height, dp, gpxData, gpxStats, frameData, isPortrait, isLandscape)
            "PRO_DASHBOARD" -> renderProDashboard(canvas, width, height, dp, gpxData, gpxStats, frameData, isPortrait, isLandscape)
        }

        return bitmap
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRO DASHBOARD — Matches ProDashboardOverlay composable exactly
    // ═══════════════════════════════════════════════════════════════════

    private fun renderProDashboard(
        canvas: Canvas, w: Int, h: Int, dp: Float,
        gpxData: GpxData?, gpxStats: GpxStats?,
        frameData: FrameData?,
        isPortrait: Boolean, isLandscape: Boolean
    ) {
        val isLive = frameData != null
        val condensedBold = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val condensedNormal = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        val accent = Color.rgb(68, 138, 255)

        val distStr = if (isLive) "%.1f km".format(frameData!!.distance / 1000.0)
        else gpxData?.let { "%.1f km".format(it.totalDistance / 1000.0) } ?: "—"

        val elevStr = if (isLive) "%.0f m".format(frameData!!.elevation)
        else gpxData?.let { "%.0f m".format(it.totalElevationGain) } ?: "—"

        val speedStr = if (isLive) "%.1f km/h".format(frameData!!.speed * 3.6)
        else gpxData?.let {
            val s = if (it.totalDuration.seconds > 0) it.totalDistance / it.totalDuration.seconds.toDouble() else 0.0
            "%.1f km/h".format(s * 3.6)
        } ?: "—"

        val hrStr = if (isLive) frameData!!.heartRate?.let { "$it bpm" } ?: "—"
        else gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points }
            ?.mapNotNull { it.heartRate }?.takeIf { it.isNotEmpty() }?.let { "%.0f bpm".format(it.average()) } ?: "—"

        val timeStr = gpxData?.let { formatDuration(it.totalDuration.toMillis()) } ?: "—"
        val progress = frameData?.progress ?: 1f

        val metrics = listOf(
            Triple("DISTANCE", distStr, accent),
            Triple("ELEVATION", elevStr, Color.rgb(102, 187, 106)),
            Triple("SPEED", speedStr, Color.rgb(255, 171, 64)),
            Triple("HEART RATE", hrStr, Color.rgb(239, 83, 80)),
            Triple("TIME", timeStr, Color.rgb(38, 166, 154))
        )

        if (isPortrait) {
            // Portrait: video top (60%), dashboard bottom (40%)
            val panelTop = h * 0.6f
            val panelPad = 10f * dp

            val panelPaint = Paint().apply { color = Color.argb(128, 0, 0, 0) }
            canvas.drawRect(0f, panelTop, w.toFloat(), h.toFloat(), panelPaint)

            val cLeft = panelPad
            val cRight = w - panelPad
            val cTop = panelTop + panelPad
            val cBottom = h.toFloat() - panelPad
            val cWidth = cRight - cLeft
            val cHeight = cBottom - cTop

            val cardSpacing = 6f * dp
            val halfWidth = (cWidth - cardSpacing) / 2f

            val labelSize = 9f * dp
            val valueSize = 13f * dp
            val cardPad = 10f * dp
            val cardH = cardPad * 2 + labelSize + 4f * dp + valueSize
            val chartH = 45f * dp

            // SpaceEvenly with 4 items: equal gaps
            val totalItemH = 3 * cardH + chartH
            val gap = ((cHeight - totalItemH) / 5f).coerceAtLeast(4f * dp)

            var y = cTop + gap

            // Row 1: DISTANCE | ELEVATION
            drawDashMetric(canvas, cLeft, y, cLeft + halfWidth, y + cardH, dp,
                metrics[0].first, metrics[0].second, metrics[0].third, condensedBold, condensedNormal)
            drawDashMetric(canvas, cLeft + halfWidth + cardSpacing, y, cRight, y + cardH, dp,
                metrics[1].first, metrics[1].second, metrics[1].third, condensedBold, condensedNormal)
            y += cardH + gap

            // Row 2: SPEED | HEART RATE
            drawDashMetric(canvas, cLeft, y, cLeft + halfWidth, y + cardH, dp,
                metrics[2].first, metrics[2].second, metrics[2].third, condensedBold, condensedNormal)
            drawDashMetric(canvas, cLeft + halfWidth + cardSpacing, y, cRight, y + cardH, dp,
                metrics[3].first, metrics[3].second, metrics[3].third, condensedBold, condensedNormal)
            y += cardH + gap

            // Row 3: TIME (full width)
            drawDashMetric(canvas, cLeft, y, cRight, y + cardH, dp,
                metrics[4].first, metrics[4].second, metrics[4].third, condensedBold, condensedNormal)
            y += cardH + gap

            // Elevation chart
            renderElevationChart(canvas, gpxData, cLeft, y, cRight, y + chartH, dp, progress, accent)

        } else {
            // Landscape: video left (60%), dashboard right (40%)
            val panelX = w * 0.6f
            val panelPad = 8f * dp

            val panelPaint = Paint().apply { color = Color.argb(128, 0, 0, 0) }
            canvas.drawRect(panelX, 0f, w.toFloat(), h.toFloat(), panelPaint)

            val cLeft = panelX + panelPad
            val cRight = w - panelPad
            val cTop = panelPad
            val cBottom = h - panelPad
            val cHeight = cBottom - cTop

            val labelSize = 9f * dp
            val valueSize = 13f * dp
            val cardPad = 10f * dp
            val cardH = cardPad * 2 + labelSize + 4f * dp + valueSize

            val totalItemH = 5 * cardH
            val gap = ((cHeight - totalItemH) / 6f).coerceAtLeast(2f * dp)

            var y = cTop + gap
            for (m in metrics) {
                drawDashMetric(canvas, cLeft, y, cRight, y + cardH, dp,
                    m.first, m.second, m.third, condensedBold, condensedNormal)
                y += cardH + gap
            }

            // Elevation chart in video area bottom
            val chartMargin = 12f * dp
            val chartH = 40f * dp
            renderElevationChart(canvas, gpxData, chartMargin, h - chartMargin - chartH,
                panelX - chartMargin, h - chartMargin, dp, progress, accent)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CINEMATIC — Matches CinematicOverlay composable
    // ═══════════════════════════════════════════════════════════════════

    private fun renderCinematic(
        canvas: Canvas, w: Int, h: Int, dp: Float,
        gpxData: GpxData?, gpxStats: GpxStats?,
        frameData: FrameData?,
        isPortrait: Boolean, isLandscape: Boolean
    ) {
        val isLive = frameData != null
        val condensedBold = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val condensedNormal = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        val accent = Color.rgb(68, 138, 255)

        val dist = if (isLive) "%.1f".format(frameData!!.distance / 1000.0)
        else gpxData?.let { "%.1f".format(it.totalDistance / 1000.0) } ?: "—"

        val elev = if (isLive) "%.0f m".format(frameData!!.elevation)
        else gpxData?.let { "%.0f m".format(it.totalElevationGain) } ?: "— m"

        val pace = if (isLive) formatPace(frameData!!.speed)
        else gpxData?.let {
            val s = if (it.totalDuration.seconds > 0) it.totalDistance / it.totalDuration.seconds.toDouble() else 0.0
            formatPace(s)
        } ?: "—"

        val progress = frameData?.progress ?: 1f

        // Bottom gradient scrim
        val scrimH = (if (isPortrait) 280f else 180f) * dp
        val gradientPaint = Paint().apply {
            shader = LinearGradient(
                0f, h - scrimH, 0f, h.toFloat(),
                Color.TRANSPARENT, Color.argb(179, 0, 0, 0),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, h - scrimH, w.toFloat(), h.toFloat(), gradientPaint)

        val pad = (if (isLandscape) 20f else 14f) * dp
        val cardSpacing = 6f * dp
        val cardPad = 10f * dp
        val cornerR = 12f * dp
        val labelSize = 9f * dp
        val smallValueSize = 13f * dp
        val largeValueSize = 28f * dp

        // Compute positions from bottom up
        val chartH = 45f * dp
        val chartBottom = h.toFloat() - pad
        val chartTop = chartBottom - chartH

        val smallCardW = (if (isLandscape) 80f else 60f) * dp
        val smallCardH = cardPad * 2 + labelSize + 4f * dp + smallValueSize
        val bigCardW = (if (isLandscape) 160f else 130f) * dp
        val bigCardH = cardPad * 2 + labelSize + 4f * dp + largeValueSize + 4f * dp + labelSize

        val smallRowTop = chartTop - 8f * dp - smallCardH
        val bigCardTop = smallRowTop - cardSpacing - bigCardH

        // Big DISTANCE card
        drawGlassCard(canvas, pad, bigCardTop, pad + bigCardW, bigCardTop + bigCardH, cornerR)
        drawLabel(canvas, "DISTANCE", pad + cardPad, bigCardTop + cardPad + labelSize, labelSize, dp, condensedBold, withAlpha(accent, 179))
        drawValue(canvas, dist, pad + cardPad, bigCardTop + cardPad + labelSize + 4f * dp + largeValueSize, largeValueSize, condensedBold, Color.WHITE)
        drawLabel(canvas, "km", pad + cardPad, bigCardTop + cardPad + labelSize + 4f * dp + largeValueSize + 4f * dp + labelSize, labelSize, dp, condensedBold, Color.argb(128, 255, 255, 255))

        // Small ELEV card
        drawGlassCard(canvas, pad, smallRowTop, pad + smallCardW, smallRowTop + smallCardH, cornerR)
        drawLabel(canvas, "ELEV", pad + cardPad, smallRowTop + cardPad + labelSize, labelSize, dp, condensedBold, withAlpha(accent, 179))
        drawValue(canvas, elev, pad + cardPad, smallRowTop + cardPad + labelSize + 4f * dp + smallValueSize, smallValueSize, condensedNormal, Color.WHITE)

        // Small PACE card
        val paceX = pad + smallCardW + cardSpacing
        drawGlassCard(canvas, paceX, smallRowTop, paceX + smallCardW, smallRowTop + smallCardH, cornerR)
        drawLabel(canvas, "PACE", paceX + cardPad, smallRowTop + cardPad + labelSize, labelSize, dp, condensedBold, withAlpha(accent, 179))
        drawValue(canvas, pace, paceX + cardPad, smallRowTop + cardPad + labelSize + 4f * dp + smallValueSize, smallValueSize, condensedNormal, Color.WHITE)

        // Elevation chart
        renderElevationChart(canvas, gpxData, pad, chartTop, w - pad, chartBottom, dp, progress, accent)
    }

    // ═══════════════════════════════════════════════════════════════════
    // HERO — Matches HeroOverlay composable
    // ═══════════════════════════════════════════════════════════════════

    private fun renderHero(
        canvas: Canvas, w: Int, h: Int, dp: Float,
        gpxData: GpxData?, gpxStats: GpxStats?,
        frameData: FrameData?,
        isPortrait: Boolean, isLandscape: Boolean
    ) {
        val isLive = frameData != null
        val condensedBold = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val condensedNormal = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        val accent = Color.rgb(68, 138, 255)

        val dist = if (isLive) "%.1f".format(frameData!!.distance / 1000.0)
        else gpxData?.let { "%.1f".format(it.totalDistance / 1000.0) } ?: "—"

        val elevVal = if (isLive) "%.0f".format(frameData!!.elevation)
        else gpxData?.let { "%.0f".format(it.totalElevationGain) } ?: "—"

        val timeVal = gpxData?.let { formatDuration(it.totalDuration.toMillis()) } ?: "—"

        val hrVal = if (isLive) frameData!!.heartRate?.toString() ?: "—"
        else gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points }
            ?.mapNotNull { it.heartRate }?.takeIf { it.isNotEmpty() }?.let { "%.0f".format(it.average()) } ?: "—"

        val progress = frameData?.progress ?: 1f

        val heroFontSize = when {
            isLandscape -> 72f * dp
            isPortrait -> 56f * dp
            else -> 64f * dp
        }
        val centerY = h * 0.42f

        // "DISTANCE" label
        val distLabel = Paint().apply {
            color = Color.argb(128, 255, 255, 255)
            textSize = 10f * dp
            typeface = condensedBold
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.3f
        }
        canvas.drawText("DISTANCE", w / 2f, centerY - heroFontSize * 0.55f, distLabel)

        // Big hero number
        val heroPaint = Paint().apply {
            color = Color.WHITE
            textSize = heroFontSize
            typeface = condensedBold
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(dist, w / 2f, centerY + heroFontSize * 0.2f, heroPaint)

        // "KM" label
        val kmPaint = Paint().apply {
            color = accent
            textSize = 16f * dp
            typeface = condensedBold
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.3f
        }
        canvas.drawText("KM", w / 2f, centerY + heroFontSize * 0.2f + 18f * dp, kmPaint)

        // Secondary metric cards
        val cardSpacing = (if (isLandscape) 16f else 10f) * dp
        val cardPad = 10f * dp
        val cornerR = 12f * dp
        val labelSize = 9f * dp
        val mediumValueSize = 18f * dp
        val iconSize = 14f * dp

        data class HM(val icon: String, val value: String, val label: String)
        val hm = listOf(
            HM("⬆", elevVal, if (isLive) "m alt" else "m gain"),
            HM("⏱", timeVal, "time"),
            HM("❤", hrVal, "avg bpm")
        )

        val cardH = cardPad * 2 + iconSize + 4f * dp + mediumValueSize + 4f * dp + labelSize
        val cardW = (w - 2 * cardSpacing - 32f * dp) / 3f
        val metricsY = centerY + heroFontSize * 0.2f + 36f * dp
        var cx = (w - (3 * cardW + 2 * cardSpacing)) / 2f

        val centerLPaint = Paint().apply {
            color = Color.argb(128, 255, 255, 255); textSize = labelSize
            typeface = condensedBold; isAntiAlias = true; textAlign = Paint.Align.CENTER
            letterSpacing = 0.15f
        }
        val centerVPaint = Paint().apply {
            color = Color.WHITE; textSize = mediumValueSize
            typeface = condensedBold; isAntiAlias = true; textAlign = Paint.Align.CENTER
        }
        val iconPaint = Paint().apply {
            textSize = iconSize; isAntiAlias = true; textAlign = Paint.Align.CENTER
        }

        for (m in hm) {
            drawGlassCard(canvas, cx, metricsY, cx + cardW, metricsY + cardH, cornerR)
            val cxCenter = cx + cardW / 2f
            var ty = metricsY + cardPad + iconSize
            canvas.drawText(m.icon, cxCenter, ty, iconPaint)
            ty += 4f * dp + mediumValueSize
            canvas.drawText(m.value, cxCenter, ty, centerVPaint)
            ty += 4f * dp + labelSize
            canvas.drawText(m.label, cxCenter, ty, centerLPaint)
            cx += cardW + cardSpacing
        }

        // Elevation chart at bottom
        val chartPad = 16f * dp
        val chartH = 45f * dp
        renderElevationChart(canvas, gpxData, chartPad, h - chartPad - chartH, w - chartPad, h - chartPad, dp, progress, accent)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SHARED DRAWING HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private fun drawDashMetric(
        canvas: Canvas,
        left: Float, top: Float, right: Float, bottom: Float,
        dp: Float, label: String, value: String, accentColor: Int,
        boldTf: Typeface, normalTf: Typeface
    ) {
        val cornerR = 12f * dp
        val cardPad = 10f * dp
        val labelSize = 9f * dp
        val valueSize = 13f * dp

        drawGlassCard(canvas, left, top, right, bottom, cornerR)
        drawLabel(canvas, label, left + cardPad, top + cardPad + labelSize, labelSize, dp, boldTf, withAlpha(accentColor, 204))
        drawValue(canvas, value, left + cardPad, top + cardPad + labelSize + 4f * dp + valueSize, valueSize, normalTf, Color.WHITE)
    }

    private fun drawGlassCard(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, cr: Float) {
        val rect = RectF(l, t, r, b)
        val fill = Paint().apply { color = Color.argb(GLASS_FILL_ALPHA, 255, 255, 255); isAntiAlias = true }
        canvas.drawRoundRect(rect, cr, cr, fill)
        val border = Paint().apply {
            color = Color.argb(GLASS_BORDER_ALPHA, 255, 255, 255)
            style = Paint.Style.STROKE; strokeWidth = 1.5f; isAntiAlias = true
        }
        canvas.drawRoundRect(rect, cr, cr, border)
    }

    private fun drawLabel(canvas: Canvas, text: String, x: Float, y: Float, size: Float, dp: Float, tf: Typeface, color: Int) {
        canvas.drawText(text, x, y, Paint().apply {
            this.color = color; textSize = size; typeface = tf; isAntiAlias = true; letterSpacing = 0.22f
        })
    }

    private fun drawValue(canvas: Canvas, text: String, x: Float, y: Float, size: Float, tf: Typeface, color: Int) {
        canvas.drawText(text, x, y, Paint().apply {
            this.color = color; textSize = size; typeface = tf; isAntiAlias = true
        })
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    // ═══════════════════════════════════════════════════════════════════
    // ELEVATION CHART — Matches MiniElevChart composable
    // ═══════════════════════════════════════════════════════════════════

    private fun renderElevationChart(
        canvas: Canvas, gpxData: GpxData?,
        left: Float, top: Float, right: Float, bottom: Float,
        dp: Float, progress: Float, accentColor: Int
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
        val progressIndex = (progress * (sampled.size - 1)).toInt().coerceIn(0, sampled.size - 1)

        // Full path (faint)
        val fullPath = Path()
        sampled.forEachIndexed { i, elev ->
            val x = left + (i.toFloat() / (sampled.size - 1)) * chartW
            val y = bottom - ((elev - minElev) / range).toFloat() * chartH * 0.85f
            if (i == 0) fullPath.moveTo(x, y) else fullPath.lineTo(x, y)
        }
        canvas.drawPath(fullPath, Paint().apply {
            color = Color.argb(60, 255, 255, 255); style = Paint.Style.STROKE
            strokeWidth = 1.5f; isAntiAlias = true
        })

        // Visited portion
        if (progress > 0.01f && progressIndex > 0) {
            val vPath = Path()
            val fPath = Path()
            sampled.take(progressIndex + 1).forEachIndexed { i, elev ->
                val x = left + (i.toFloat() / (sampled.size - 1)) * chartW
                val y = bottom - ((elev - minElev) / range).toFloat() * chartH * 0.85f
                if (i == 0) { vPath.moveTo(x, y); fPath.moveTo(x, y) }
                else { vPath.lineTo(x, y); fPath.lineTo(x, y) }
            }
            canvas.drawPath(vPath, Paint().apply {
                color = accentColor; style = Paint.Style.STROKE; strokeWidth = 2.5f; isAntiAlias = true
            })

            val lastX = left + (progressIndex.toFloat() / (sampled.size - 1)) * chartW
            fPath.lineTo(lastX, bottom); fPath.lineTo(left, bottom); fPath.close()
            canvas.drawPath(fPath, Paint().apply {
                shader = LinearGradient(0f, top, 0f, bottom,
                    withAlpha(accentColor, 80), withAlpha(accentColor, 10), Shader.TileMode.CLAMP)
                isAntiAlias = true
            })

            // Progress dot
            val dotElev = sampled[progressIndex]
            val dotY = bottom - ((dotElev - minElev) / range).toFloat() * chartH * 0.85f
            canvas.drawCircle(lastX, dotY, 4f * dp, Paint().apply { color = accentColor; isAntiAlias = true })
            canvas.drawCircle(lastX, dotY, 7f * dp, Paint().apply { color = withAlpha(accentColor, 60); isAntiAlias = true })
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
        val kmh = speedMs * 3.6
        if (kmh <= 0.1) return "—"
        val paceMin = (60.0 / kmh).toInt()
        val paceSec = ((60.0 / kmh - paceMin) * 60).toInt()
        return "%d:%02d".format(paceMin, paceSec)
    }
}
