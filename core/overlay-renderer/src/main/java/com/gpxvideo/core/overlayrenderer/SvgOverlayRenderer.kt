package com.gpxvideo.core.overlayrenderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import com.caverock.androidsvg.SVG
import com.gpxvideo.core.model.GpxData

/**
 * Renders overlay frames by compositing:
 *   1. SVG static visuals (scrims, card backgrounds, label paths)
 *   2. Native Canvas text with custom fonts + fill/stroke outlined text
 *   3. Chart/Map data rendered by [ChartRenderer] and [RouteMapRenderer]
 *
 * Renders overlay frames using SVG templates.
 * The same [render] call is used by both the preview (→ Compose Image) and
 * the export pipeline (→ Media3 BitmapOverlay), guaranteeing pixel parity.
 */
class SvgOverlayRenderer(
    private val fontProvider: TemplateFontProvider? = null
) {
    private var reusableBitmap: Bitmap? = null
    private var cachedTextLayers: Map<String, TextLayerInfo>? = null
    private var cachedPlaceholders: Map<String, PlaceholderInfo>? = null
    private var cachedSvgHash: Int = 0

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isSubpixelText = true }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isSubpixelText = true
        style = Paint.Style.STROKE
    }

    /**
     * Render a full overlay frame.
     *
     * @param svg           parsed SVG template (from [SvgTemplateLoader])
     * @param svgString     raw SVG XML string (for placeholder resolution)
     * @param width         output bitmap width in pixels
     * @param height        output bitmap height in pixels
     * @param frameData     current-frame statistics (distance, speed, elevation, etc.)
     * @param gpxData       full GPX data for chart/map rendering
     * @param accentColor   theme accent color
     * @param activityTitle user-supplied activity title
     */
    fun render(
        svg: SVG,
        svgString: String,
        width: Int,
        height: Int,
        frameData: OverlayFrameData,
        gpxData: GpxData?,
        accentColor: Int = Color.argb(204, 68, 138, 255),
        activityTitle: String = "",
        showElevationChart: Boolean = true,
        showRouteMap: Boolean = true
    ): Bitmap {
        val bitmap = getOrCreateBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // 1. Hide dynamic text elements by manipulating SVG before render.
        //    We render an SVG copy where stat_* text content is cleared.
        //    AndroidSVG renders SVG → Canvas (scrims, cards, label paths).
        val cleanedSvg = hideStatTextInSvg(svgString)
        try {
            val staticSvg = SVG.getFromString(cleanedSvg)
            if (fontProvider != null) {
                SVG.registerExternalFileResolver(fontProvider)
            }
            staticSvg.documentWidth = width.toFloat()
            staticSvg.documentHeight = height.toFloat()
            staticSvg.renderToCanvas(canvas)
        } catch (e: Exception) {
            // Fallback: render original SVG as-is
            android.util.Log.w(TAG, "SVG render failed, using original: ${e.message}")
            try {
                svg.documentWidth = width.toFloat()
                svg.documentHeight = height.toFloat()
                svg.renderToCanvas(canvas)
            } catch (_: Exception) { }
        }

        // 2. Resolve text layers and placeholders (cached by svg content hash)
        //    Derive accent from template's own title_text fill color
        val hash = svgString.hashCode() + width * 31 + height
        if (hash != cachedSvgHash) {
            cachedTextLayers = SvgPlaceholderResolver.resolveTextLayers(svgString, width, height)
            val templateAccent = cachedTextLayers
                ?.get(SvgTemplateConventions.TITLE_TEXT)?.color
                ?: accentColor
            cachedPlaceholders = SvgPlaceholderResolver.resolveFromSvg(svgString, width, height, templateAccent)
            cachedSvgHash = hash
        }
        val textLayers = cachedTextLayers ?: emptyMap()

        // 3. Draw dynamic text natively on Canvas (fill + stroke for outlined effect)
        val textValues = buildTextValues(frameData, activityTitle)
        for ((name, info) in textLayers) {
            val text = textValues[name] ?: continue
            if (text.isBlank()) continue
            drawOutlinedText(canvas, text, info, name, width)
        }

        // 4. Draw chart and map overlays (colors come from SVG sub-elements)
        val placeholders = cachedPlaceholders ?: emptyMap()
        val dp = width / 360f

        if (showElevationChart) {
            placeholders[SvgTemplateConventions.ELEVATION_CHART]?.let { info ->
                ChartRenderer.render(
                    canvas, gpxData,
                    info.bounds.left, info.bounds.top, info.bounds.right, info.bounds.bottom,
                    dp, frameData.progress, info.style
                )
            }
        }

        if (showRouteMap) {
            placeholders[SvgTemplateConventions.ROUTE_MAP]?.let { info ->
                RouteMapRenderer.render(
                    canvas, gpxData,
                    info.bounds.left, info.bounds.top, info.bounds.right, info.bounds.bottom,
                    dp, frameData.progress, info.style
                )
            }
        }

        return bitmap
    }

    private fun drawOutlinedText(
        canvas: Canvas,
        text: String,
        info: TextLayerInfo,
        layerName: String,
        canvasWidth: Int
    ) {
        val typeface = fontProvider?.getTypeface(
            info.fontFamily ?: "sans-serif",
            info.fontBold
        ) ?: if (info.fontBold) {
            android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        } else {
            android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        }

        // Detect alignment: Figma exports right-aligned text by computing the
        // left-edge x for the placeholder text. We re-measure the placeholder with
        // the actual font to find the intended right edge, then right-align from there.
        var align = when (info.justify) {
            1 -> Paint.Align.CENTER
            2 -> Paint.Align.RIGHT
            else -> Paint.Align.LEFT
        }
        var drawX = info.x

        if (align == Paint.Align.LEFT && info.placeholderText != null) {
            textPaint.typeface = typeface
            textPaint.textSize = info.fontSize
            val placeholderWidth = textPaint.measureText(info.placeholderText)
            val rightEdge = info.x + placeholderWidth
            // If right edge lands in the rightmost 15% of canvas → right-aligned
            if (rightEdge > canvasWidth * 0.85f) {
                align = Paint.Align.RIGHT
                drawX = rightEdge
            }
        }

        // Auto-scale long text (titles) to fit within the available width
        var fontSize = info.fontSize
        if (layerName == "title_text") {
            textPaint.typeface = typeface
            textPaint.textSize = fontSize
            val measuredWidth = textPaint.measureText(text)
            val margin = canvasWidth * 0.04f
            val availableWidth = when (align) {
                Paint.Align.RIGHT -> drawX - margin
                Paint.Align.CENTER -> (canvasWidth - 2 * margin)
                else -> canvasWidth - drawX - margin
            }
            if (measuredWidth > availableWidth && availableWidth > 0) {
                val scale = (availableWidth / measuredWidth).coerceAtLeast(0.5f)
                fontSize *= scale
            }
        }

        // Stroke pass (dark outline) — only if stroke attributes are set
        if (info.strokeWidth > 0 && info.strokeColor != Color.TRANSPARENT) {
            strokePaint.apply {
                this.typeface = typeface
                textSize = fontSize
                textAlign = align
                color = info.strokeColor
                strokeWidth = info.strokeWidth
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawText(text, drawX, info.y, strokePaint)
        }

        // Fill pass
        textPaint.apply {
            this.typeface = typeface
            textSize = fontSize
            textAlign = align
            color = info.color
        }
        canvas.drawText(text, drawX, info.y, textPaint)
    }

    /**
     * Hides dynamic elements from the SVG before AndroidSVG renders it:
     * - stat_* and title_text: text content cleared (re-drawn natively with custom fonts)
     * - elevation_chart and route_map groups: hidden via display:none (rendered by ChartRenderer/RouteMapRenderer)
     */
    private fun hideStatTextInSvg(svgString: String): String {
        // 1. Clear text content from stat_*/title_text elements
        //    Handles both direct text content and Figma's <tspan> wrapper
        var result = svgString.replace(
            Regex("""(<text\s[^>]*id\s*=\s*"(?:stat_[^"]*|title_text)"[^>]*>)(.+?)(</text>)""")
        ) { match ->
            "${match.groupValues[1]}${match.groupValues[3]}"
        }

        // 2. Hide chart/map groups — add display:none so AndroidSVG skips them
        result = result.replace(
            Regex("""(<g\s[^>]*id\s*=\s*"(?:elevation_chart|route_map)")""")
        ) { match ->
            """${match.groupValues[1]} display="none""""
        }

        return result
    }

    private fun buildTextValues(frameData: OverlayFrameData, activityTitle: String): Map<String, String> {
        return mapOf(
            "stat_distance" to frameData.distanceKm,
            "stat_distance_unit" to "km",
            "stat_elevation" to frameData.elevationStr,
            "stat_elevation_unit" to "m",
            "stat_pace" to frameData.pace,
            "stat_pace_unit" to "min/km",
            "stat_hr" to frameData.heartRateStr,
            "stat_hr_unit" to "bpm",
            "stat_time" to frameData.elapsedTimeStr,
            "stat_grade" to frameData.gradeStr,
            "stat_grade_unit" to "%",
            "stat_speed" to frameData.speedKmh,
            "stat_speed_unit" to "km/h",
            "title_text" to activityTitle
        )
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
        reusableBitmap?.recycle()
        reusableBitmap = null
        cachedTextLayers = null
        cachedPlaceholders = null
    }

    companion object {
        private const val TAG = "SvgOverlayRenderer"
    }
}
