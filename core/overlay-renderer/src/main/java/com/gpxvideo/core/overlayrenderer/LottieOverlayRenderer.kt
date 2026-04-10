package com.gpxvideo.core.overlayrenderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.gpxvideo.core.model.GpxData

/**
 * Unified overlay renderer using Lottie compositions for shapes/visuals
 * and native Canvas text rendering for reliable text display.
 *
 * Used by both the preview (renders to ImageBitmap for Compose) and
 * the export pipeline (renders to Bitmap for Media3 Transformer BitmapOverlay).
 */
class LottieOverlayRenderer {

    private var drawable: LottieDrawable? = null
    private var currentCompositionId: String? = null
    private var reusableBitmap: Bitmap? = null
    private var cachedTextLayers: Map<String, TextLayerInfo>? = null
    private var cachedTextJsonHash: Int = 0

    private val boldTypeface: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    private val normalTypeface: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isSubpixelText = true
    }

    fun render(
        composition: LottieComposition,
        jsonString: String,
        width: Int,
        height: Int,
        frameData: OverlayFrameData,
        gpxData: GpxData?,
        accentColor: Int = Color.argb(204, 68, 138, 255),
        activityTitle: String = ""
    ): Bitmap {
        val drw = getOrCreateDrawable(composition)
        drw.setBounds(0, 0, width, height)
        drw.progress = 0f

        val bitmap = getOrCreateBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

        // 1. Draw Lottie shapes (scrims, glass cards, backgrounds)
        drw.draw(canvas)

        // 2. Resolve text layers from JSON (cached)
        val textLayers = getTextLayers(jsonString, width, height)

        // 3. Build text values map
        val textValues = buildTextValues(frameData, activityTitle)

        // 4. Draw text natively on Canvas
        for ((name, info) in textLayers) {
            val text = textValues[name] ?: continue
            if (text.isBlank()) continue

            textPaint.typeface = if (info.fontBold) boldTypeface else normalTypeface
            textPaint.textSize = info.fontSize
            // Apply accent color to title text
            textPaint.color = if (name == "title_text") accentColor else info.color
            textPaint.textAlign = when (info.justify) {
                1 -> Paint.Align.CENTER
                2 -> Paint.Align.RIGHT
                else -> Paint.Align.LEFT
            }

            canvas.drawText(text, info.x, info.y, textPaint)
        }

        // 5. Resolve chart/map layers and draw
        val placeholders = PlaceholderResolver.resolveFromJson(jsonString, width, height, accentColor)
        val dp = width / 360f

        placeholders[PlaceholderResolver.ELEVATION_CHART]?.let { info ->
            ChartRenderer.render(
                canvas, gpxData,
                info.bounds.left, info.bounds.top, info.bounds.right, info.bounds.bottom,
                dp, frameData.progress, info.style.copy(accentColor = accentColor)
            )
        }

        placeholders[PlaceholderResolver.ROUTE_MAP]?.let { info ->
            RouteMapRenderer.render(
                canvas, gpxData,
                info.bounds.left, info.bounds.top, info.bounds.right, info.bounds.bottom,
                dp, frameData.progress, info.style.copy(accentColor = accentColor)
            )
        }

        return bitmap
    }

    private fun buildTextValues(frameData: OverlayFrameData, activityTitle: String): Map<String, String> {
        return mapOf(
            "stat_distance" to frameData.distanceKm,
            "stat_distance_unit" to "km",
            "stat_elevation" to frameData.elevationStr,
            "stat_elevation_unit" to "m",
            "stat_pace" to frameData.pace,
            "stat_pace_unit" to "/km",
            "stat_hr" to frameData.heartRateStr,
            "stat_hr_unit" to "bpm",
            "stat_time" to frameData.elapsedTimeStr,
            "stat_grade" to frameData.gradeStr,
            "stat_speed" to "%.1f".format(frameData.speed * 3.6),
            "stat_speed_unit" to "km/h",
            "title_text" to activityTitle,
            "label_distance" to "DISTANCE",
            "label_elevation" to "ELEVATION",
            "label_pace" to "PACE",
            "label_hr" to "HEART RATE",
            "label_time" to "TIME",
            "label_grade" to "GRADE",
            "label_speed" to "SPEED"
        )
    }

    private fun getTextLayers(jsonString: String, width: Int, height: Int): Map<String, TextLayerInfo> {
        val hash = System.identityHashCode(jsonString) + width * 31 + height
        if (hash == cachedTextJsonHash && cachedTextLayers != null) {
            return cachedTextLayers!!
        }
        val layers = PlaceholderResolver.resolveTextLayers(jsonString, width, height)
        cachedTextLayers = layers
        cachedTextJsonHash = hash
        return layers
    }

    private fun getOrCreateDrawable(composition: LottieComposition): LottieDrawable {
        val compId = composition.hashCode().toString()
        if (currentCompositionId == compId && drawable != null) {
            return drawable!!
        }

        val drw = LottieDrawable()
        drw.composition = composition
        drawable = drw
        currentCompositionId = compId
        return drw
    }

    private fun getOrCreateBitmap(width: Int, height: Int): Bitmap {
        val existing = reusableBitmap
        if (existing != null && existing.width == width && existing.height == height && !existing.isRecycled) {
            return existing
        }
        existing?.recycle()
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        reusableBitmap = bmp
        return bmp
    }

    fun release() {
        drawable = null
        currentCompositionId = null
        reusableBitmap?.recycle()
        reusableBitmap = null
        cachedTextLayers = null
    }
}
