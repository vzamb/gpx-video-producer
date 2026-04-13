package com.gpxvideo.core.overlayrenderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.gpxvideo.core.model.GpxData

/**
 * Renders an elevation profile chart onto a Canvas region.
 *
 * Uses monotone cubic Hermite spline interpolation for smooth, natural-looking
 * curves that never overshoot the data. Supports multi-stop area gradients,
 * line shadow, and radial-gradient dot/glow rendering.
 */
object ChartRenderer {

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

        // Compute (x, y) data points
        val xs = FloatArray(sampled.size) { i -> left + (i.toFloat() / (sampled.size - 1)) * chartW }
        val ys = FloatArray(sampled.size) { i -> bottom - ((sampled[i] - minElev) / range).toFloat() * chartH * 0.85f }

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
