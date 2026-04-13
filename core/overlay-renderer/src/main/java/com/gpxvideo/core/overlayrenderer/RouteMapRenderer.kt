package com.gpxvideo.core.overlayrenderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.gpxvideo.core.model.GpxData

/**
 * Renders a mini route map onto a Canvas region.
 *
 * Improvements over the original:
 *  - Catmull-Rom spline smoothing for natural-looking routes
 *  - Direction arrow chevrons along the visited path
 *  - Radial gradient glow on the current position dot
 *  - Start/end markers with distinct styling
 *  - Aspect-ratio-preserving projection
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

        // Background
        val bgRect = RectF(left, top, right, bottom)
        val cornerR = style.cornerRadius
        if (style.hasBackground) {
            canvas.drawRoundRect(bgRect, cornerR, cornerR, Paint().apply {
                color = style.backgroundColor; isAntiAlias = true
            })
            if (style.borderColor != Color.TRANSPARENT) {
                canvas.drawRoundRect(bgRect, cornerR, cornerR, Paint().apply {
                    color = style.borderColor; setStyle(Paint.Style.STROKE)
                    strokeWidth = style.borderWidth * dp; isAntiAlias = true
                })
            }
        }

        // Sample points
        val sampled = if (allPoints.size > 200) {
            val step = allPoints.size.toFloat() / 200f
            (0 until 200).map { i -> allPoints[(i * step).toInt().coerceAtMost(allPoints.lastIndex)] }
        } else allPoints

        // Aspect-ratio-preserving projection
        val drawW = w - 2 * pad
        val drawH = h - 2 * pad
        val latCos = Math.cos(Math.toRadians((bounds.maxLatitude + bounds.minLatitude) / 2.0))
        val adjLonRange = lonRange * latCos
        val scaleX = drawW / adjLonRange
        val scaleY = drawH / latRange
        val scale = minOf(scaleX, scaleY)
        val offsetX = left + pad + (drawW - adjLonRange * scale).toFloat() / 2f
        val offsetY = top + pad + (drawH - latRange * scale).toFloat() / 2f

        fun projectX(lon: Double) = (offsetX + (lon - bounds.minLongitude) * latCos * scale).toFloat()
        fun projectY(lat: Double) = (offsetY + (bounds.maxLatitude - lat) * scale).toFloat()

        val xs = FloatArray(sampled.size) { projectX(sampled[it].longitude) }
        val ys = FloatArray(sampled.size) { projectY(sampled[it].latitude) }

        // Full route with Catmull-Rom smoothing
        val fullPath = buildCatmullRomPath(xs, ys, 0, sampled.size)
        canvas.drawPath(fullPath, Paint().apply {
            color = style.fullPathColor
            setStyle(Paint.Style.STROKE); strokeWidth = style.fullPathWidth * dp; isAntiAlias = true
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        })

        // Start marker
        val startPaint = Paint().apply { color = Color.argb(200, 76, 175, 80); isAntiAlias = true }
        canvas.drawCircle(xs[0], ys[0], 3f * dp, startPaint)
        canvas.drawCircle(xs[0], ys[0], 3f * dp, Paint().apply {
            color = Color.WHITE; isAntiAlias = true; setStyle(Paint.Style.STROKE); strokeWidth = 1f * dp
        })

        // Progress portion
        val progressIdx = (progress * (sampled.size - 1)).toInt().coerceIn(0, sampled.lastIndex)
        val progressLineColor = if (style.lineColor != Color.WHITE) style.lineColor else accentColor
        if (progressIdx > 0) {
            val progressPath = buildCatmullRomPath(xs, ys, 0, progressIdx + 1)

            // Shadow under visited line
            canvas.drawPath(progressPath, Paint().apply {
                color = Color.TRANSPARENT; setStyle(Paint.Style.STROKE)
                strokeWidth = (style.lineWidth + 1f) * dp; isAntiAlias = true
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                setShadowLayer(2f * dp, 0f, 1f * dp, Color.argb(60, 0, 0, 0))
            })

            // Visited line
            canvas.drawPath(progressPath, Paint().apply {
                color = progressLineColor
                setStyle(Paint.Style.STROKE); strokeWidth = style.lineWidth * dp; isAntiAlias = true
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            })

            // Direction arrows along visited path
            drawDirectionChevrons(canvas, progressPath, progressLineColor, dp)

            // Current position dot with radial glow
            val cx = xs[progressIdx]
            val cy = ys[progressIdx]
            val glowR = style.glowRadius * dp
            val dotR = style.dotRadius * dp

            canvas.drawCircle(cx, cy, glowR, Paint().apply {
                isAntiAlias = true
                shader = RadialGradient(
                    cx, cy, glowR,
                    intArrayOf(ChartRenderer.withAlpha(progressLineColor, 120), Color.TRANSPARENT),
                    floatArrayOf(0.3f, 1f),
                    Shader.TileMode.CLAMP
                )
            })
            canvas.drawCircle(cx, cy, dotR * 1.25f, Paint().apply { color = Color.WHITE; isAntiAlias = true })
            canvas.drawCircle(cx, cy, dotR, Paint().apply { color = progressLineColor; isAntiAlias = true })
        }

        // End marker (only if near the end)
        if (progress > 0.95f || progressIdx >= sampled.size - 2) {
            val endPaint = Paint().apply { color = Color.argb(200, 244, 67, 54); isAntiAlias = true }
            canvas.drawCircle(xs.last(), ys.last(), 3f * dp, endPaint)
            canvas.drawCircle(xs.last(), ys.last(), 3f * dp, Paint().apply {
                color = Color.WHITE; isAntiAlias = true; setStyle(Paint.Style.STROKE); strokeWidth = 1f * dp
            })
        }
    }

    /**
     * Build a smooth path from data points using Catmull-Rom spline → cubic Bezier conversion.
     */
    private fun buildCatmullRomPath(
        xs: FloatArray, ys: FloatArray,
        startIdx: Int, endIdx: Int,
        tension: Float = 0.5f
    ): Path {
        val n = endIdx - startIdx
        if (n < 2) return Path().apply { if (n == 1) moveTo(xs[startIdx], ys[startIdx]) }

        val path = Path()
        path.moveTo(xs[startIdx], ys[startIdx])

        if (n == 2) {
            path.lineTo(xs[startIdx + 1], ys[startIdx + 1])
            return path
        }

        for (i in startIdx until endIdx - 1) {
            val p0x = if (i > startIdx) xs[i - 1] else xs[i]
            val p0y = if (i > startIdx) ys[i - 1] else ys[i]
            val p1x = xs[i]; val p1y = ys[i]
            val p2x = xs[i + 1]; val p2y = ys[i + 1]
            val p3x = if (i + 2 < endIdx) xs[i + 2] else xs[i + 1]
            val p3y = if (i + 2 < endIdx) ys[i + 2] else ys[i + 1]

            val cp1x = p1x + (p2x - p0x) * tension / 3f
            val cp1y = p1y + (p2y - p0y) * tension / 3f
            val cp2x = p2x - (p3x - p1x) * tension / 3f
            val cp2y = p2y - (p3y - p1y) * tension / 3f

            path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2x, p2y)
        }

        return path
    }

    /**
     * Draw small direction chevrons along a path at regular intervals.
     */
    private fun drawDirectionChevrons(
        canvas: Canvas, path: Path, color: Int, dp: Float
    ) {
        val measure = android.graphics.PathMeasure(path, false)
        val length = measure.length
        if (length < 30 * dp) return

        val chevronSize = 3f * dp
        val interval = (length / 6f).coerceIn(20f * dp, 60f * dp)
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        val chevronPaint = Paint().apply {
            this.color = Color.WHITE
            isAntiAlias = true
            setStyle(Paint.Style.STROKE)
            strokeWidth = 1.2f * dp
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        var dist = interval
        while (dist < length - interval * 0.5f) {
            if (measure.getPosTan(dist, pos, tan)) {
                val angle = Math.atan2(tan[1].toDouble(), tan[0].toDouble()).toFloat()
                canvas.save()
                canvas.translate(pos[0], pos[1])
                canvas.rotate(Math.toDegrees(angle.toDouble()).toFloat())
                val chevron = Path().apply {
                    moveTo(-chevronSize, -chevronSize * 0.6f)
                    lineTo(0f, 0f)
                    lineTo(-chevronSize, chevronSize * 0.6f)
                }
                canvas.drawPath(chevron, chevronPaint)
                canvas.restore()
            }
            dist += interval
        }
    }
}
