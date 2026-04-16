package com.gpxvideo.core.overlayrenderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import com.caverock.androidsvg.SVG
import com.gpxvideo.core.model.ChartType
import com.gpxvideo.core.model.MetricType
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
     * @param metricConfig  ordered list of metrics to fill template slots (packed, no gaps)
     * @param chartType     chart type to display (null = hidden)
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
        chartType: ChartType? = ChartType.ELEVATION,
        showRouteMap: Boolean = true,
        metricConfig: List<MetricType> = MetricType.fallbackMetrics
    ): Bitmap {
        val bitmap = getOrCreateBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // 1. Hide dynamic text elements by manipulating SVG before render.
        //    We render an SVG copy where stat_* text content is cleared.
        //    AndroidSVG renders SVG → Canvas (scrims, cards, label paths).
        val cleanedSvg = hideStatTextInSvg(svgString, metricConfig.size)
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
        val textValues = buildTextValues(frameData, activityTitle, metricConfig)
        val unitSuffixes = buildUnitSuffixes(metricConfig)
        for ((name, info) in textLayers) {
            val text = textValues[name] ?: continue
            if (text.isBlank()) continue
            val unitSuffix = unitSuffixes[name]
            drawOutlinedText(canvas, text, info, name, width, unitSuffix)
        }

        // 4. Draw chart and map overlays (colors come from SVG sub-elements)
        val placeholders = cachedPlaceholders ?: emptyMap()
        val dp = width / 360f

        if (chartType != null) {
            placeholders[SvgTemplateConventions.ELEVATION_CHART]?.let { info ->
                ChartRenderer.render(
                    canvas, gpxData, chartType,
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
        canvasWidth: Int,
        unitSuffix: String? = null
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

        // Unit suffix — smaller, semi-transparent, drawn right after the label text
        if (!unitSuffix.isNullOrBlank()) {
            val mainWidth = textPaint.measureText(text)
            val suffixX = when (align) {
                Paint.Align.LEFT -> drawX + mainWidth
                Paint.Align.CENTER -> drawX + mainWidth / 2f
                Paint.Align.RIGHT -> drawX  // right-aligned: label ends at drawX
                else -> drawX + mainWidth
            }
            val unitFontSize = fontSize * UNIT_SUFFIX_SCALE
            val separatorUnit = " · $unitSuffix"

            // Stroke pass for unit suffix
            if (info.strokeWidth > 0 && info.strokeColor != Color.TRANSPARENT) {
                strokePaint.apply {
                    textSize = unitFontSize
                    textAlign = Paint.Align.LEFT
                    alpha = (Color.alpha(info.strokeColor) * UNIT_SUFFIX_OPACITY).toInt()
                }
                canvas.drawText(separatorUnit, suffixX, info.y, strokePaint)
            }

            // Fill pass for unit suffix
            textPaint.apply {
                textSize = unitFontSize
                textAlign = Paint.Align.LEFT
                alpha = (255 * UNIT_SUFFIX_OPACITY).toInt()
            }
            canvas.drawText(separatorUnit, suffixX, info.y, textPaint)

            // Restore full alpha
            textPaint.alpha = 255
        }
    }

    /**
     * Hides dynamic elements from the SVG before AndroidSVG renders it:
     * - metric_N_* and title_text: text content cleared (re-drawn natively with custom fonts)
     * - elevation_chart and route_map groups: always hidden (rendered natively by ChartRenderer/RouteMapRenderer)
     * - card_N elements for empty metric slots: hidden via display:none
     *
     * @param activeSlotCount how many metric slots are filled — cards beyond this are hidden
     */
    private fun hideStatTextInSvg(
        svgString: String,
        activeSlotCount: Int
    ): String {
        // 1. Clear text content from metric_N_*/title_text elements
        //    Handles both direct text content and Figma's <tspan> wrapper
        var result = svgString.replace(
            Regex("""(<text\s[^>]*id\s*=\s*"(?:metric_\d+_(?:value|label|unit)|title_text)"[^>]*>)(.+?)(</text>)""")
        ) { match ->
            "${match.groupValues[1]}${match.groupValues[3]}"
        }

        // 2. Hide chart/map groups — always hidden, rendered natively by ChartRenderer/RouteMapRenderer
        result = result.replace(
            Regex("""(<g\s[^>]*id\s*=\s*"(?:elevation_chart|route_map)")""")
        ) { match ->
            """${match.groupValues[1]} display="none""""
        }

        // 3. Hide card_N elements for empty slots (N > activeSlotCount)
        result = result.replace(
            Regex("""(<rect\s[^>]*id\s*=\s*"card_(\d+)")""")
        ) { match ->
            val slotNum = match.groupValues[2].toIntOrNull() ?: 0
            if (slotNum > activeSlotCount) {
                """${match.groupValues[1]} display="none""""
            } else {
                match.value
            }
        }

        // 4. Hide metric text for empty slots too (N > activeSlotCount)
        result = result.replace(
            Regex("""(<text\s[^>]*id\s*=\s*"metric_(\d+)_(?:value|label|unit)")""")
        ) { match ->
            val slotNum = match.groupValues[2].toIntOrNull() ?: 0
            if (slotNum > activeSlotCount) {
                """${match.groupValues[1]} display="none""""
            } else {
                match.value
            }
        }

        return result
    }

    /**
     * Build the text value map for all metric slots + title.
     *
     * Maps generic slot IDs (metric_1_value, metric_1_label, ...)
     * to actual values based on the resolved [metricConfig].
     */
    private fun buildTextValues(
        frameData: OverlayFrameData,
        activityTitle: String,
        metricConfig: List<MetricType>
    ): Map<String, String> {
        val map = mutableMapOf<String, String>()
        map["title_text"] = activityTitle

        for ((index, metric) in metricConfig.withIndex()) {
            val slot = index + 1
            map["metric_${slot}_value"] = extractValue(metric, frameData)
            map["metric_${slot}_label"] = metric.labelText
        }

        return map
    }

    /**
     * Build a map of unit suffixes keyed by label layer ID.
     * Only label layers get a unit suffix (e.g. metric_1_label → "BPM").
     */
    private fun buildUnitSuffixes(metricConfig: List<MetricType>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for ((index, metric) in metricConfig.withIndex()) {
            if (metric.unitText.isNotBlank()) {
                map["metric_${index + 1}_label"] = metric.unitText.uppercase()
            }
        }
        return map
    }

    /** Extract the formatted value for a given metric type from frame data. */
    private fun extractValue(metric: MetricType, data: OverlayFrameData): String = when (metric) {
        MetricType.DISTANCE -> data.distanceKm
        MetricType.ELEVATION -> data.elevationStr
        MetricType.PACE -> data.pace
        MetricType.HR -> data.heartRateStr
        MetricType.TIME -> data.elapsedTimeStr
        MetricType.SPEED -> data.speedKmh
        MetricType.GRADE -> data.gradeStr
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
        /** Unit suffix font size as a fraction of the label font size. */
        private const val UNIT_SUFFIX_SCALE = 0.65f
        /** Unit suffix opacity (0.0–1.0). */
        private const val UNIT_SUFFIX_OPACITY = 0.55f
    }
}
