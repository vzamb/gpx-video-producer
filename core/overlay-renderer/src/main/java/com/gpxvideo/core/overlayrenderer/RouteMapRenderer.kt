package com.gpxvideo.core.overlayrenderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.gpxvideo.core.model.GpxData

/**
 * Renders a mini route map onto a Canvas region.
 * Extracted from StoryTemplateRenderer for shared use in Lottie placeholder regions.
 */
object RouteMapRenderer {

    fun render(
        canvas: Canvas,
        gpxData: GpxData?,
        left: Float, top: Float, right: Float, bottom: Float,
        dp: Float,
        progress: Float,
        style: PlaceholderStyle
    ) {
        val allPoints = gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points } ?: return
        if (allPoints.size < 2) return
        val bounds = gpxData.bounds

        val accentColor = style.accentColor
        val latRange = (bounds.maxLatitude - bounds.minLatitude).coerceAtLeast(0.0001)
        val lonRange = (bounds.maxLongitude - bounds.minLongitude).coerceAtLeast(0.0001)
        val w = right - left
        val h = bottom - top
        val pad = 8f * dp

        // Background with rounded corners
        val bgRect = RectF(left, top, right, bottom)
        val cornerR = style.cornerRadius
        if (style.hasBackground) {
            canvas.drawRoundRect(bgRect, cornerR, cornerR, Paint().apply {
                color = style.backgroundColor; isAntiAlias = true
            })
        } else {
            canvas.drawRoundRect(bgRect, cornerR, cornerR, Paint().apply {
                color = Color.argb(100, 0, 0, 0); isAntiAlias = true
            })
        }
        if (style.borderColor != Color.TRANSPARENT) {
            canvas.drawRoundRect(bgRect, cornerR, cornerR, Paint().apply {
                color = style.borderColor; setStyle(Paint.Style.STROKE)
                strokeWidth = style.borderWidth * dp; isAntiAlias = true
            })
        }

        // Sample points
        val sampled = if (allPoints.size > 200) {
            val step = allPoints.size.toFloat() / 200f
            (0 until 200).map { i -> allPoints[(i * step).toInt().coerceAtMost(allPoints.lastIndex)] }
        } else allPoints

        fun projectX(lon: Double) = left + pad + ((lon - bounds.minLongitude) / lonRange).toFloat() * (w - 2 * pad)
        fun projectY(lat: Double) = bottom - pad - ((lat - bounds.minLatitude) / latRange).toFloat() * (h - 2 * pad)

        // Full route (using design style or dimmed default)
        val fullPath = Path().apply {
            sampled.forEachIndexed { i, pt ->
                val x = projectX(pt.longitude)
                val y = projectY(pt.latitude)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        canvas.drawPath(fullPath, Paint().apply {
            color = style.fullPathColor
            setStyle(Paint.Style.STROKE); strokeWidth = style.fullPathWidth * dp; isAntiAlias = true
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        })

        // Progress portion
        val progressIdx = (progress * (sampled.size - 1)).toInt().coerceIn(0, sampled.lastIndex)
        val progressLineColor = if (style.lineColor != Color.WHITE) style.lineColor else accentColor
        if (progressIdx > 0) {
            val progressPath = Path().apply {
                for (i in 0..progressIdx) {
                    val x = projectX(sampled[i].longitude)
                    val y = projectY(sampled[i].latitude)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            canvas.drawPath(progressPath, Paint().apply {
                color = progressLineColor
                setStyle(Paint.Style.STROKE); strokeWidth = style.lineWidth * dp; isAntiAlias = true
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            })

            // Current position dot
            val pt = sampled[progressIdx]
            val cx = projectX(pt.longitude)
            val cy = projectY(pt.latitude)
            canvas.drawCircle(cx, cy, style.glowRadius * dp, Paint().apply {
                color = style.glowColor; isAntiAlias = true
            })
            canvas.drawCircle(cx, cy, style.dotRadius * 1.25f * dp, Paint().apply {
                color = style.dotColor; isAntiAlias = true
            })
            canvas.drawCircle(cx, cy, style.dotRadius * 0.875f * dp, Paint().apply {
                color = progressLineColor; isAntiAlias = true
            })
        }
    }
}
