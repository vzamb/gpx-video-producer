package com.gpxvideo.core.overlayrenderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.gpxvideo.core.model.GpxData

/**
 * Renders an elevation profile chart onto a Canvas region.
 * Extracted from StoryTemplateRenderer for shared use in Lottie placeholder regions.
 */
object ChartRenderer {

    /**
     * Draw an elevation chart in the given bounds.
     *
     * @param style  visual hints (accent color, background, border, corner radius) —
     *               typically extracted from the Lottie placeholder layer
     */
    fun render(
        canvas: Canvas,
        gpxData: GpxData?,
        left: Float, top: Float, right: Float, bottom: Float,
        dp: Float,
        progress: Float,
        style: PlaceholderStyle
    ) {
        val elevations = gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points }
            ?.mapNotNull { it.elevation } ?: return
        if (elevations.size < 2) return

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

        val step = (elevations.size / 80).coerceAtLeast(1)
        val sampled = elevations.filterIndexed { i, _ -> i % step == 0 }
        val minElev = sampled.min()
        val maxElev = sampled.max()
        val range = (maxElev - minElev).coerceAtLeast(1.0)
        val chartW = right - left
        val chartH = bottom - top
        val progressIndex = (progress * (sampled.size - 1)).toInt().coerceIn(0, sampled.size - 1)

        // Full path (using design style or faint default)
        val fullPath = Path()
        sampled.forEachIndexed { i, elev ->
            val x = left + (i.toFloat() / (sampled.size - 1)) * chartW
            val y = bottom - ((elev - minElev) / range).toFloat() * chartH * 0.85f
            if (i == 0) fullPath.moveTo(x, y) else fullPath.lineTo(x, y)
        }
        canvas.drawPath(fullPath, Paint().apply {
            color = style.fullPathColor; this.style = Paint.Style.STROKE
            strokeWidth = style.fullPathWidth * dp; isAntiAlias = true
        })

        // Visited portion
        if (progress > 0.01f && progressIndex > 0) {
            val visitedLineColor = if (style.lineColor != Color.WHITE) style.lineColor else accentColor
            val vPath = Path()
            val fPath = Path()
            sampled.take(progressIndex + 1).forEachIndexed { i, elev ->
                val x = left + (i.toFloat() / (sampled.size - 1)) * chartW
                val y = bottom - ((elev - minElev) / range).toFloat() * chartH * 0.85f
                if (i == 0) { vPath.moveTo(x, y); fPath.moveTo(x, y) }
                else { vPath.lineTo(x, y); fPath.lineTo(x, y) }
            }
            canvas.drawPath(vPath, Paint().apply {
                color = visitedLineColor; this.style = Paint.Style.STROKE
                strokeWidth = style.lineWidth * dp; isAntiAlias = true
            })

            val lastX = left + (progressIndex.toFloat() / (sampled.size - 1)) * chartW
            fPath.lineTo(lastX, bottom); fPath.lineTo(left, bottom); fPath.close()
            val areaColor = if (style.areaFillColor != 0) style.areaFillColor else visitedLineColor
            canvas.drawPath(fPath, Paint().apply {
                shader = LinearGradient(
                    0f, top, 0f, bottom,
                    withAlpha(areaColor, style.areaFillOpacity),
                    withAlpha(areaColor, 10),
                    Shader.TileMode.CLAMP
                )
                isAntiAlias = true
            })

            // Progress dot
            val dotElev = sampled[progressIndex]
            val dotY = bottom - ((dotElev - minElev) / range).toFloat() * chartH * 0.85f
            canvas.drawCircle(lastX, dotY, style.dotRadius * dp, Paint().apply { color = visitedLineColor; isAntiAlias = true })
            canvas.drawCircle(lastX, dotY, style.glowRadius * dp, Paint().apply { color = withAlpha(visitedLineColor, 60); isAntiAlias = true })
        }
    }

    internal fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
