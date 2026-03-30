package com.gpxvideo.feature.overlays

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.gpxvideo.core.model.DynamicField
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.GpxPoint
import com.gpxvideo.core.model.MapStyle
import com.gpxvideo.core.model.OverlayConfig
import com.gpxvideo.lib.gpxparser.GpxStatistics
import java.time.Duration
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object DynamicOverlayRenderer {

    // ── Dynamic Altitude Profile ──────────────────────────────────────────

    fun renderDynamicAltitudeProfile(
        overlay: OverlayConfig.DynamicAltitudeProfile,
        gpxData: GpxData,
        currentPoint: InterpolatedPoint,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val allPoints = gpxData.tracks.flatMap { it.segments.flatMap { s -> s.points } }

        drawBackground(canvas, width, height, overlay.style.backgroundColor, overlay.style.cornerRadius)
        if (allPoints.size < 2) return bitmap

        val leftPad = width * 0.05f
        val rightPad = width * 0.05f
        val topPad = height * 0.12f
        val bottomPad = height * 0.12f
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

        val progress = currentPoint.progress.coerceIn(0f, 1f)
        val currentDist = currentPoint.elapsedDistance

        // Full profile line in faded color
        val fadedLinePath = Path().apply {
            moveTo(xPos(distances[0]), yPos(elevations[0]))
            for (i in 1 until allPoints.size) {
                lineTo(xPos(distances[i]), yPos(elevations[i]))
            }
        }
        val fadedPaint = Paint().apply {
            color = applyAlpha(colorFromLong(overlay.lineColor), 60)
            strokeWidth = 2f.coerceAtLeast(width * 0.004f)
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(fadedLinePath, fadedPaint)

        // Trail fill up to current position
        val trailFillPath = Path()
        val trailLinePath = Path()
        var trailStarted = false
        for (i in allPoints.indices) {
            val d = distances[i]
            if (d > currentDist) {
                // Interpolate the cutoff point
                if (i > 0 && !trailStarted) {
                    trailFillPath.moveTo(xPos(distances[0]), yPos(elevations[0]))
                    trailLinePath.moveTo(xPos(distances[0]), yPos(elevations[0]))
                    trailStarted = true
                }
                if (i > 0) {
                    val prevD = distances[i - 1]
                    val frac = if (d - prevD > 0) (currentDist - prevD) / (d - prevD) else 0.0
                    val cutX = xPos(currentDist)
                    val cutEle = elevations[i - 1] + (elevations[i] - elevations[i - 1]) * frac
                    val cutY = yPos(cutEle)
                    trailFillPath.lineTo(cutX, cutY)
                    trailLinePath.lineTo(cutX, cutY)
                }
                break
            }
            if (!trailStarted) {
                trailFillPath.moveTo(xPos(d), yPos(elevations[i]))
                trailLinePath.moveTo(xPos(d), yPos(elevations[i]))
                trailStarted = true
            } else {
                trailFillPath.lineTo(xPos(d), yPos(elevations[i]))
                trailLinePath.lineTo(xPos(d), yPos(elevations[i]))
            }
        }

        if (trailStarted) {
            // Close fill path to bottom
            val cutoffX = xPos(currentDist.coerceAtMost(totalDist))
            trailFillPath.lineTo(cutoffX, topPad + drawH)
            trailFillPath.lineTo(xPos(distances[0]), topPad + drawH)
            trailFillPath.close()

            val trailFillPaint = Paint().apply {
                style = Paint.Style.FILL
                isAntiAlias = true
                shader = LinearGradient(
                    0f, topPad, 0f, topPad + drawH,
                    colorFromLong(overlay.trailColor),
                    applyAlpha(colorFromLong(overlay.trailColor), 20),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawPath(trailFillPath, trailFillPaint)

            // Trail line in bright color
            val trailPaint = Paint().apply {
                color = colorFromLong(overlay.lineColor)
                strokeWidth = 2.5f.coerceAtLeast(width * 0.005f)
                style = Paint.Style.STROKE
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawPath(trailLinePath, trailPaint)
        }

        // Marker dot at current position
        val markerX = xPos(currentDist.coerceAtMost(totalDist))
        val markerEle = currentPoint.elevation
        val markerY = yPos(markerEle)
        val markerRadius = width * 0.015f

        // Glow
        val glowPaint = Paint().apply {
            isAntiAlias = true
            shader = RadialGradient(
                markerX, markerY, markerRadius * 3,
                applyAlpha(colorFromLong(overlay.markerColor), 80),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(markerX, markerY, markerRadius * 3, glowPaint)

        val markerPaint = Paint().apply {
            color = colorFromLong(overlay.markerColor)
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawCircle(markerX, markerY, markerRadius, markerPaint)

        // White border on marker
        val borderPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawCircle(markerX, markerY, markerRadius, borderPaint)

        // Current elevation text near marker
        val elevText = "${markerEle.toInt()}m"
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = height * 0.1f
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            setShadowLayer(3f, 1f, 1f, Color.argb(160, 0, 0, 0))
        }
        val textY = if (markerY - markerRadius * 2 > topPad + textPaint.textSize) {
            markerY - markerRadius * 2
        } else {
            markerY + markerRadius * 2 + textPaint.textSize
        }
        canvas.drawText(elevText, markerX, textY, textPaint)

        return bitmap
    }

    // ── Dynamic Map ──────────────────────────────────────────────────────

    fun renderDynamicMap(
        overlay: OverlayConfig.DynamicMap,
        gpxData: GpxData,
        currentPoint: InterpolatedPoint,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val allPoints = gpxData.tracks.flatMap { it.segments.flatMap { s -> s.points } }

        val bgColor = when (overlay.mapStyle) {
            MapStyle.DARK -> Color.argb(255, 20, 20, 20)
            MapStyle.TERRAIN -> Color.argb(255, 30, 40, 30)
            MapStyle.SATELLITE -> Color.argb(255, 15, 25, 15)
            MapStyle.MINIMAL -> overlay.style.backgroundColor?.let { colorFromLong(it) }
                ?: Color.argb(255, 30, 30, 30)
        }
        drawBackgroundColor(canvas, width, height, bgColor, overlay.style.cornerRadius)
        if (allPoints.size < 2) return bitmap

        val padding = width * 0.08f
        val drawW = width - 2 * padding
        val drawH = height - 2 * padding

        val viewBounds = if (overlay.followPosition) {
            computeFollowBounds(allPoints, currentPoint, gpxData)
        } else {
            gpxData.bounds
        }

        val latRange = viewBounds.maxLatitude - viewBounds.minLatitude
        val lonRange = viewBounds.maxLongitude - viewBounds.minLongitude
        if (latRange <= 0 || lonRange <= 0) return bitmap

        val latScale = drawH / latRange
        val lonScale = drawW / lonRange
        val scale = min(latScale, lonScale)
        val offsetX = padding + (drawW - lonRange * scale).toFloat() / 2
        val offsetY = padding + (drawH - latRange * scale).toFloat() / 2

        fun projectX(lon: Double) = (offsetX + (lon - viewBounds.minLongitude) * scale).toFloat()
        fun projectY(lat: Double) = (offsetY + (viewBounds.maxLatitude - lat) * scale).toFloat()

        // Full route in faded color
        val fullRoutePath = Path().apply {
            moveTo(projectX(allPoints[0].longitude), projectY(allPoints[0].latitude))
            for (i in 1 until allPoints.size) {
                lineTo(projectX(allPoints[i].longitude), projectY(allPoints[i].latitude))
            }
        }
        val fadedRoutePaint = Paint().apply {
            color = applyAlpha(colorFromLong(overlay.routeColor), 50)
            strokeWidth = 2f.coerceAtLeast(width * 0.004f)
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(fullRoutePath, fadedRoutePaint)

        // Highlighted trail up to current position
        if (overlay.showTrail) {
            val distances = computeDistances(allPoints)
            val currentDist = currentPoint.elapsedDistance
            val trailPath = Path()
            var started = false

            for (i in allPoints.indices) {
                val d = distances[i]
                if (d > currentDist && i > 0) {
                    val prevD = distances[i - 1]
                    val frac = if (d - prevD > 0) (currentDist - prevD) / (d - prevD) else 0.0
                    val interpLat = allPoints[i - 1].latitude + (allPoints[i].latitude - allPoints[i - 1].latitude) * frac
                    val interpLon = allPoints[i - 1].longitude + (allPoints[i].longitude - allPoints[i - 1].longitude) * frac
                    trailPath.lineTo(projectX(interpLon), projectY(interpLat))
                    break
                }
                if (!started) {
                    trailPath.moveTo(projectX(allPoints[i].longitude), projectY(allPoints[i].latitude))
                    started = true
                } else {
                    trailPath.lineTo(projectX(allPoints[i].longitude), projectY(allPoints[i].latitude))
                }
            }

            val trailPaint = Paint().apply {
                color = colorFromLong(overlay.routeColor)
                strokeWidth = 3f.coerceAtLeast(width * 0.006f)
                style = Paint.Style.STROKE
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(trailPath, trailPaint)
        }

        // Current position dot with glow
        val posX = projectX(currentPoint.longitude)
        val posY = projectY(currentPoint.latitude)
        val dotRadius = width * 0.025f

        val glowPaint = Paint().apply {
            isAntiAlias = true
            shader = RadialGradient(
                posX, posY, dotRadius * 3.5f,
                applyAlpha(colorFromLong(overlay.routeColor), 100),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(posX, posY, dotRadius * 3.5f, glowPaint)

        val dotPaint = Paint().apply {
            color = colorFromLong(overlay.routeColor)
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawCircle(posX, posY, dotRadius, dotPaint)

        val dotBorderPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawCircle(posX, posY, dotRadius, dotBorderPaint)

        // Direction arrow at current position
        if (currentPoint.speed > 0.5) {
            drawDirectionArrow(canvas, posX, posY, currentPoint, dotRadius, overlay.routeColor)
        }

        return bitmap
    }

    // ── Dynamic Stat ─────────────────────────────────────────────────────

    fun renderDynamicStat(
        overlay: OverlayConfig.DynamicStat,
        currentPoint: InterpolatedPoint,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(canvas, width, height, overlay.style.backgroundColor, overlay.style.cornerRadius)

        val field = overlay.field
        val value = formatDynamicValue(field, currentPoint, overlay.format)
        val label = field.displayName
        val unit = field.defaultUnit
        val zoneColor = getZoneColor(field, currentPoint)

        val valuePaint = Paint().apply {
            color = zoneColor ?: colorFromLong(overlay.style.fontColor)
            textSize = (height * 0.42f).coerceAtMost(width * 0.22f)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(2f, 1f, 1f, Color.argb(100, 0, 0, 0))
        }

        val labelPaint = Paint().apply {
            color = Color.argb(170, 255, 255, 255)
            textSize = (height * 0.16f).coerceAtMost(width * 0.08f)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val unitPaint = Paint().apply {
            color = Color.argb(140, 255, 255, 255)
            textSize = (height * 0.14f).coerceAtMost(width * 0.07f)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val cx = width / 2f
        val baseY = height * 0.32f
        canvas.drawText(label, cx, baseY, labelPaint)
        canvas.drawText(value, cx, baseY + valuePaint.textSize * 1.0f, valuePaint)
        if (unit.isNotEmpty()) {
            canvas.drawText(unit, cx, baseY + valuePaint.textSize * 1.0f + unitPaint.textSize * 1.2f, unitPaint)
        }

        return bitmap
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun computeDistances(points: List<GpxPoint>): List<Double> {
        if (points.size < 2) return if (points.isNotEmpty()) listOf(0.0) else emptyList()
        val dists = ArrayList<Double>(points.size)
        dists.add(0.0)
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

    private fun computeFollowBounds(
        allPoints: List<GpxPoint>,
        current: InterpolatedPoint,
        gpxData: GpxData
    ): com.gpxvideo.core.model.GeoBounds {
        val fullLatRange = gpxData.bounds.maxLatitude - gpxData.bounds.minLatitude
        val fullLonRange = gpxData.bounds.maxLongitude - gpxData.bounds.minLongitude
        // Show ~30% of route around current position
        val viewLat = (fullLatRange * 0.3).coerceAtLeast(0.002)
        val viewLon = (fullLonRange * 0.3).coerceAtLeast(0.002)
        return com.gpxvideo.core.model.GeoBounds(
            minLatitude = current.latitude - viewLat / 2,
            maxLatitude = current.latitude + viewLat / 2,
            minLongitude = current.longitude - viewLon / 2,
            maxLongitude = current.longitude + viewLon / 2
        )
    }

    private fun drawDirectionArrow(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        point: InterpolatedPoint,
        dotRadius: Float,
        routeColorLong: Long
    ) {
        // Simple forward arrow offset from dot
        val arrowSize = dotRadius * 1.8f
        val offset = dotRadius * 2.5f

        // Use bearing based on latitude/longitude hint (simplified: point "north-ish")
        val arrowPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val arrowPath = Path().apply {
            moveTo(cx, cy - offset - arrowSize)
            lineTo(cx - arrowSize * 0.4f, cy - offset)
            lineTo(cx + arrowSize * 0.4f, cy - offset)
            close()
        }
        canvas.drawPath(arrowPath, arrowPaint)
    }

    private fun formatDynamicValue(
        field: DynamicField,
        point: InterpolatedPoint,
        format: String
    ): String {
        if (format.isNotBlank()) {
            return try {
                when (field) {
                    DynamicField.CURRENT_SPEED -> format.format(point.speed * 3.6)
                    DynamicField.CURRENT_PACE -> format.format(if (point.speed > 0) (1000.0 / point.speed) / 60.0 else 0.0)
                    DynamicField.CURRENT_ELEVATION -> format.format(point.elevation)
                    DynamicField.CURRENT_HEART_RATE -> format.format(point.heartRate ?: 0)
                    DynamicField.CURRENT_CADENCE -> format.format(point.cadence ?: 0)
                    DynamicField.CURRENT_POWER -> format.format(point.power ?: 0)
                    DynamicField.CURRENT_TEMPERATURE -> format.format(point.temperature ?: 0.0)
                    DynamicField.CURRENT_GRADE -> format.format(point.grade)
                    DynamicField.ELAPSED_DISTANCE -> format.format(point.elapsedDistance / 1000.0)
                    else -> formatDefaultValue(field, point)
                }
            } catch (_: Exception) {
                formatDefaultValue(field, point)
            }
        }
        return formatDefaultValue(field, point)
    }

    private fun formatDefaultValue(field: DynamicField, point: InterpolatedPoint): String {
        return when (field) {
            DynamicField.CURRENT_SPEED -> "%.1f".format(point.speed * 3.6)
            DynamicField.CURRENT_PACE -> {
                val pace = if (point.speed > 0.3) (1000.0 / point.speed) / 60.0 else 0.0
                if (pace > 0 && pace < 60) {
                    val m = pace.toInt()
                    val s = ((pace - m) * 60).toInt()
                    "%d:%02d".format(m, s)
                } else "--:--"
            }
            DynamicField.CURRENT_ELEVATION -> "%.0f".format(point.elevation)
            DynamicField.CURRENT_HEART_RATE -> point.heartRate?.toString() ?: "--"
            DynamicField.CURRENT_CADENCE -> point.cadence?.toString() ?: "--"
            DynamicField.CURRENT_POWER -> point.power?.toString() ?: "--"
            DynamicField.CURRENT_TEMPERATURE -> point.temperature?.let { "%.1f".format(it) } ?: "--"
            DynamicField.CURRENT_GRADE -> "%.1f".format(point.grade)
            DynamicField.ELAPSED_TIME -> formatDuration(point.elapsedTime)
            DynamicField.ELAPSED_DISTANCE -> "%.2f".format(point.elapsedDistance / 1000.0)
            DynamicField.REMAINING_TIME -> "--:--"
            DynamicField.REMAINING_DISTANCE -> "--"
        }
    }

    private fun getZoneColor(field: DynamicField, point: InterpolatedPoint): Int? {
        return when (field) {
            DynamicField.CURRENT_HEART_RATE -> {
                val hr = point.heartRate ?: return null
                when {
                    hr < 120 -> Color.argb(255, 76, 175, 80)    // green
                    hr < 150 -> Color.argb(255, 255, 235, 59)   // yellow
                    hr < 170 -> Color.argb(255, 255, 152, 0)    // orange
                    else -> Color.argb(255, 244, 67, 54)         // red
                }
            }
            DynamicField.CURRENT_POWER -> {
                val pow = point.power ?: return null
                when {
                    pow < 150 -> Color.argb(255, 76, 175, 80)
                    pow < 250 -> Color.argb(255, 255, 235, 59)
                    pow < 350 -> Color.argb(255, 255, 152, 0)
                    else -> Color.argb(255, 244, 67, 54)
                }
            }
            else -> null
        }
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

    private fun drawBackground(canvas: Canvas, w: Int, h: Int, bgColor: Long?, cr: Float) {
        val color = bgColor?.let { colorFromLong(it) } ?: Color.argb(255, 30, 30, 30)
        drawBackgroundColor(canvas, w, h, color, cr)
    }

    private fun drawBackgroundColor(canvas: Canvas, w: Int, h: Int, color: Int, cr: Float) {
        val paint = Paint().apply {
            this.color = color
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), cr, cr, paint)
    }

    private fun colorFromLong(color: Long): Int = color.toInt()

    private fun applyAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
