package com.gpxvideo.core.overlayrenderer

import android.graphics.Color
import android.graphics.RectF
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Resolves text layer positions, chart/map bounds, and chart styles from SVG templates.
 *
 * Uses XML parsing to traverse the SVG DOM and extract:
 * - `<text id="stat_*">` → text layer position, font, colors, stroke
 * - `<g id="elevation_chart">` → chart bounds + style from named children
 * - `<g id="route_map">` → map bounds + style from named children
 *
 * Parses SVG XML to extract text layers, chart bounds, and chart styles.
 */
object SvgPlaceholderResolver {

    fun resolveFromSvg(
        svgString: String,
        canvasWidth: Int,
        canvasHeight: Int,
        accentColor: Int = Color.argb(204, 68, 138, 255)
    ): Map<String, PlaceholderInfo> {
        val result = mutableMapOf<String, PlaceholderInfo>()
        try {
            val viewBox = parseViewBox(svgString)
            val svgW = viewBox?.width ?: 1080f
            val svgH = viewBox?.height ?: 1920f
            val scaleX = canvasWidth / svgW
            val scaleY = canvasHeight / svgH
            val dp = canvasWidth / 360f

            val parser = createParser(svgString)
            var insideChartGroup: String? = null
            var chartGroupDepth = 0
            var chartBounds = RectF()
            var chartStyle = defaultStyleFor(SvgTemplateConventions.ELEVATION_CHART, accentColor, dp)
            val subGroupStyles = mutableMapOf<String, SubGroupStyle>()
            var groupTranslateX = 0f
            var groupTranslateY = 0f

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val tag = parser.name
                    val id = parser.getAttributeValue(null, "id") ?: ""

                    if (tag == "g" && SvgTemplateConventions.isChartOrMapLayer(id)) {
                        insideChartGroup = id
                        chartGroupDepth = 1
                        chartStyle = defaultStyleFor(id, accentColor, dp)
                        chartBounds = RectF()
                        subGroupStyles.clear()
                        // Parse transform="translate(x,y)" on the group
                        val transform = parser.getAttributeValue(null, "transform") ?: ""
                        val (tx, ty) = parseTranslate(transform)
                        groupTranslateX = tx * scaleX
                        groupTranslateY = ty * scaleY
                    } else if (insideChartGroup != null && tag == "g" &&
                        SvgTemplateConventions.isChartSubGroup(id)
                    ) {
                        // Inside a chart, entering a named sub-group (wrapped)
                        val subStyle = parseSubGroupStyle(parser, id, scaleX, scaleY)
                        val normalizedId = SvgTemplateConventions.stripFigmaSuffix(id).lowercase()
                        subGroupStyles[normalizedId] = subStyle
                        if (normalizedId == SvgTemplateConventions.SUB_BACKGROUND) {
                            chartBounds = subStyle.bounds
                        }
                        // parseSubGroupStyle consumes through closing </g>, no depth change
                    } else if (insideChartGroup != null &&
                        SvgTemplateConventions.isChartSubGroup(id) &&
                        tag in arrayOf("rect", "circle", "line", "path", "polyline", "ellipse")
                    ) {
                        // Inside a chart, direct element with sub-group id (not wrapped in <g>)
                        val subStyle = parseDirectSubElement(parser, tag, scaleX, scaleY)
                        val normalizedId = SvgTemplateConventions.stripFigmaSuffix(id).lowercase()
                        subGroupStyles[normalizedId] = subStyle
                        if (normalizedId == SvgTemplateConventions.SUB_BACKGROUND) {
                            chartBounds = subStyle.bounds
                        }
                    } else if (insideChartGroup != null && tag == "g") {
                        // Nested group inside chart that's not a recognized sub-group
                        chartGroupDepth++
                    } else if (insideChartGroup != null && tag == "rect" &&
                        chartBounds.isEmpty
                    ) {
                        // Fallback bounds from a bare rect inside the chart group
                        chartBounds = parseRectBounds(parser, scaleX, scaleY)
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    val tag = parser.name

                    if (tag == "g" && insideChartGroup != null) {
                        chartGroupDepth--
                        if (chartGroupDepth <= 0) {
                            // If no background sub-element, infer bounds from the largest rect
                            if (chartBounds.isEmpty) {
                                chartBounds = inferBoundsFromSubGroups(subGroupStyles)
                            }
                            // Closing the top-level chart/map group — apply group translation
                            chartBounds.offset(groupTranslateX, groupTranslateY)
                            if (chartBounds.width() > 0 && chartBounds.height() > 0) {
                                val style = buildPlaceholderStyle(
                                    chartStyle, subGroupStyles, accentColor, dp, scaleX
                                )
                                result[insideChartGroup!!] = PlaceholderInfo(insideChartGroup!!, chartBounds, style)
                            }
                            insideChartGroup = null
                            groupTranslateX = 0f
                            groupTranslateY = 0f
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to resolve placeholders: ${e.message}")
        }
        return result
    }

    fun resolveTextLayers(
        svgString: String,
        canvasWidth: Int,
        canvasHeight: Int
    ): Map<String, TextLayerInfo> {
        val result = mutableMapOf<String, TextLayerInfo>()
        try {
            val viewBox = parseViewBox(svgString)
            val svgW = viewBox?.width ?: 1080f
            val svgH = viewBox?.height ?: 1920f
            val scaleX = canvasWidth / svgW
            val scaleY = canvasHeight / svgH
            val scale = minOf(scaleX, scaleY)

            val parser = createParser(svgString)
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "text") {
                    val id = parser.getAttributeValue(null, "id") ?: ""
                    if (SvgTemplateConventions.isDynamicTextLayer(id)) {
                        parseTextLayer(parser, id, scaleX, scaleY, scale)?.let {
                            result[id] = it
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to resolve text layers: ${e.message}")
        }
        return result
    }

    // ── Text layer parsing ─────────────────────────────────────────────

    private fun parseTextLayer(
        parser: XmlPullParser,
        id: String,
        scaleX: Float, scaleY: Float, scale: Float
    ): TextLayerInfo? {
        // Read attributes from <text> element
        var x = parser.getAttributeValue(null, "x")?.toFloatOrNull() ?: 0f
        var y = parser.getAttributeValue(null, "y")?.toFloatOrNull() ?: 0f
        val fontSize = parser.getAttributeValue(null, "font-size")?.toFloatOrNull() ?: 24f
        val fontFamily = parser.getAttributeValue(null, "font-family")
        val fontWeight = parser.getAttributeValue(null, "font-weight") ?: ""
        val textAnchor = parser.getAttributeValue(null, "text-anchor") ?: "start"

        val fillAttr = parser.getAttributeValue(null, "fill") ?: "#FFFFFF"
        val strokeAttr = parser.getAttributeValue(null, "stroke")
        val strokeWidthAttr = parser.getAttributeValue(null, "stroke-width")

        val fillColor = parseColor(fillAttr) ?: Color.WHITE
        val strokeColor = if (strokeAttr != null) parseColor(strokeAttr) ?: Color.TRANSPARENT else Color.TRANSPARENT
        val strokeWidth = strokeWidthAttr?.toFloatOrNull()?.times(scale) ?: 0f

        // Scan tspan children for position and placeholder text content.
        // Figma puts x/y on <tspan> instead of <text>.
        val tspanData = findTspanData(parser)
        if (x == 0f && y == 0f && tspanData != null) {
            x = tspanData.x
            y = tspanData.y
        }
        val placeholderText = tspanData?.text

        val isBold = fontWeight.equals("bold", ignoreCase = true) ||
                fontWeight.toIntOrNull()?.let { it >= 700 } == true ||
                id.startsWith(SvgTemplateConventions.STAT_PREFIX) ||
                id == SvgTemplateConventions.TITLE_TEXT

        val justify = when (textAnchor) {
            "middle" -> 1
            "end" -> 2
            else -> 0
        }

        return TextLayerInfo(
            name = id,
            x = x * scaleX,
            y = y * scaleY,
            fontSize = fontSize * scale,
            color = fillColor,
            justify = justify,
            fontBold = isBold,
            fontFamily = fontFamily,
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            placeholderText = placeholderText
        )
    }

    /**
     * Data extracted from a `<tspan>` element inside a `<text>`.
     */
    private data class TspanData(val x: Float, val y: Float, val text: String?)

    /**
     * Scans forward to find `<tspan>` x/y and text content within the current `<text>` element.
     * Figma exports text positions on `<tspan>` rather than the parent `<text>`.
     */
    private fun findTspanData(parser: XmlPullParser): TspanData? {
        var depth = 1
        var foundX: Float? = null
        var foundY: Float? = null
        var foundText: String? = null
        while (depth > 0) {
            val event = parser.next()
            when (event) {
                XmlPullParser.START_TAG -> {
                    depth++
                    if (parser.name == "tspan") {
                        val tx = parser.getAttributeValue(null, "x")?.toFloatOrNull()
                        val ty = parser.getAttributeValue(null, "y")?.toFloatOrNull()
                        if (foundX == null && (tx != null || ty != null)) {
                            foundX = tx ?: 0f
                            foundY = ty ?: 0f
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val t = parser.text?.trim()
                    if (!t.isNullOrEmpty() && foundText == null) {
                        foundText = t
                    }
                }
                XmlPullParser.END_TAG -> depth--
            }
        }
        return if (foundX != null || foundText != null) {
            TspanData(foundX ?: 0f, foundY ?: 0f, foundText)
        } else null
    }

    // ── Chart sub-group parsing ────────────────────────────────────────

    private data class SubGroupStyle(
        val fillColor: Int = Color.TRANSPARENT,
        val fillOpacity: Int = 100,
        val strokeColor: Int = Color.TRANSPARENT,
        val strokeWidth: Float = 0f,
        val bounds: RectF = RectF(),
        val radius: Float = 0f,
        val cornerRadius: Float = 0f
    )

    private fun parseSubGroupStyle(
        parser: XmlPullParser, groupId: String,
        scaleX: Float, scaleY: Float
    ): SubGroupStyle {
        var fill = Color.TRANSPARENT
        var fillOpacity = 100
        var stroke = Color.TRANSPARENT
        var strokeWidth = 0f
        var bounds = RectF()
        var radius = 0f
        var cornerRadius = 0f

        // Read style attributes from the group itself
        val groupFill = parser.getAttributeValue(null, "fill")
        val groupStroke = parser.getAttributeValue(null, "stroke")
        if (groupFill != null) fill = parseColor(groupFill) ?: fill
        if (groupStroke != null) stroke = parseColor(groupStroke) ?: stroke

        // Scan child elements for shapes that define the style
        var depth = 1
        while (depth > 0) {
            val event = parser.next()
            when (event) {
                XmlPullParser.START_TAG -> {
                    depth++
                    when (parser.name) {
                        "rect" -> {
                            bounds = parseRectBounds(parser, scaleX, scaleY)
                            cornerRadius = parser.getAttributeValue(null, "rx")
                                ?.toFloatOrNull()?.times(minOf(scaleX, scaleY)) ?: 0f
                            val rectFill = parser.getAttributeValue(null, "fill")
                            val rectStroke = parser.getAttributeValue(null, "stroke")
                            val rectStrokeW = parser.getAttributeValue(null, "stroke-width")
                            val rectOpacity = parser.getAttributeValue(null, "opacity")
                                ?: parser.getAttributeValue(null, "fill-opacity")
                            if (rectFill != null) fill = parseColor(rectFill) ?: fill
                            if (rectStroke != null) stroke = parseColor(rectStroke) ?: stroke
                            if (rectStrokeW != null) strokeWidth = rectStrokeW.toFloatOrNull()
                                ?.times(minOf(scaleX, scaleY)) ?: strokeWidth
                            if (rectOpacity != null) fillOpacity = ((rectOpacity.toFloatOrNull()
                                ?: 1f) * 100).toInt()
                        }
                        "circle" -> {
                            radius = parser.getAttributeValue(null, "r")
                                ?.toFloatOrNull()?.times(minOf(scaleX, scaleY)) ?: 0f
                            val circleFill = parser.getAttributeValue(null, "fill")
                            if (circleFill != null) fill = parseColor(circleFill) ?: fill
                            val circleOpacity = parser.getAttributeValue(null, "opacity")
                                ?: parser.getAttributeValue(null, "fill-opacity")
                            if (circleOpacity != null) fillOpacity = ((circleOpacity.toFloatOrNull()
                                ?: 1f) * 100).toInt()
                        }
                        "line", "path", "polyline" -> {
                            val pathStroke = parser.getAttributeValue(null, "stroke")
                            val pathStrokeW = parser.getAttributeValue(null, "stroke-width")
                            if (pathStroke != null) stroke = parseColor(pathStroke) ?: stroke
                            if (pathStrokeW != null) strokeWidth = pathStrokeW.toFloatOrNull()
                                ?.times(minOf(scaleX, scaleY)) ?: strokeWidth
                            if (fill == Color.TRANSPARENT) {
                                // For strokes, also capture the stroke color as potential fill
                                fill = stroke
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> depth--
            }
        }

        return SubGroupStyle(fill, fillOpacity, stroke, strokeWidth, bounds, radius, cornerRadius)
    }

    /**
     * Parses style from a direct child element (rect, circle, line, etc.) that has a
     * sub-group ID directly on it, rather than being wrapped in a `<g>` element.
     */
    private fun parseDirectSubElement(
        parser: XmlPullParser, tag: String,
        scaleX: Float, scaleY: Float
    ): SubGroupStyle {
        val scale = minOf(scaleX, scaleY)
        val fillAttr = parser.getAttributeValue(null, "fill")
        val strokeAttr = parser.getAttributeValue(null, "stroke")
        val strokeWAttr = parser.getAttributeValue(null, "stroke-width")
        val opacityAttr = parser.getAttributeValue(null, "opacity")
            ?: parser.getAttributeValue(null, "fill-opacity")

        var fill = if (fillAttr != null) parseColor(fillAttr) ?: Color.TRANSPARENT else Color.TRANSPARENT
        val stroke = if (strokeAttr != null) parseColor(strokeAttr) ?: Color.TRANSPARENT else Color.TRANSPARENT
        val strokeWidth = strokeWAttr?.toFloatOrNull()?.times(scale) ?: 0f
        val fillOpacity = if (opacityAttr != null) ((opacityAttr.toFloatOrNull() ?: 1f) * 100).toInt() else 100

        var bounds = RectF()
        var radius = 0f
        var cornerRadius = 0f

        when (tag) {
            "rect" -> {
                bounds = parseRectBounds(parser, scaleX, scaleY)
                cornerRadius = parser.getAttributeValue(null, "rx")
                    ?.toFloatOrNull()?.times(scale) ?: 0f
            }
            "circle" -> {
                radius = parser.getAttributeValue(null, "r")
                    ?.toFloatOrNull()?.times(scale) ?: 0f
            }
            "line", "path", "polyline" -> {
                if (fill == Color.TRANSPARENT) fill = stroke
            }
        }

        return SubGroupStyle(fill, fillOpacity, stroke, strokeWidth, bounds, radius, cornerRadius)
    }

    private fun buildPlaceholderStyle(
        base: PlaceholderStyle,
        subGroups: Map<String, SubGroupStyle>,
        accentColor: Int,
        dp: Float,
        scale: Float
    ): PlaceholderStyle {
        val bg = subGroups[SvgTemplateConventions.SUB_BACKGROUND]
        val line = subGroups[SvgTemplateConventions.SUB_LINE]
            ?: subGroups[SvgTemplateConventions.SUB_ROUTE]
        val fullPath = subGroups[SvgTemplateConventions.SUB_FULL_PATH]
            ?: subGroups[SvgTemplateConventions.SUB_FULL_ROUTE]
        val area = subGroups[SvgTemplateConventions.SUB_AREA]
        val dot = subGroups[SvgTemplateConventions.SUB_DOT]
        val glow = subGroups[SvgTemplateConventions.SUB_GLOW]

        return base.copy(
            // Background rendered by SVG, not natively
            hasBackground = false,
            backgroundColor = bg?.fillColor?.takeIf { it != Color.TRANSPARENT } ?: base.backgroundColor,
            borderColor = bg?.strokeColor?.takeIf { it != Color.TRANSPARENT } ?: base.borderColor,
            borderWidth = bg?.strokeWidth?.takeIf { it > 0 } ?: base.borderWidth,
            cornerRadius = bg?.cornerRadius?.takeIf { it > 0 } ?: base.cornerRadius,
            lineColor = line?.strokeColor?.takeIf { it != Color.TRANSPARENT }
                ?: line?.fillColor?.takeIf { it != Color.TRANSPARENT }
                ?: base.lineColor,
            lineWidth = line?.strokeWidth?.takeIf { it > 0 } ?: base.lineWidth,
            fullPathColor = fullPath?.strokeColor?.takeIf { it != Color.TRANSPARENT }
                ?: fullPath?.fillColor?.takeIf { it != Color.TRANSPARENT }
                ?: base.fullPathColor,
            fullPathWidth = fullPath?.strokeWidth?.takeIf { it > 0 } ?: base.fullPathWidth,
            areaFillColor = area?.fillColor?.takeIf { it != Color.TRANSPARENT } ?: base.areaFillColor,
            areaFillOpacity = area?.fillOpacity ?: base.areaFillOpacity,
            dotRadius = dot?.radius?.takeIf { it > 0 } ?: base.dotRadius,
            dotColor = dot?.fillColor?.takeIf { it != Color.TRANSPARENT } ?: base.dotColor,
            glowRadius = glow?.radius?.takeIf { it > 0 } ?: base.glowRadius,
            glowColor = glow?.fillColor?.takeIf { it != Color.TRANSPARENT }?.let { c ->
                val alpha = glow.fillOpacity * 255 / 100
                Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c))
            } ?: base.glowColor,
            accentColor = accentColor
        )
    }

    // ── SVG geometry helpers ────────────────────────────────────────────

    /**
     * When no `background` sub-element exists, infer chart/map bounds from the
     * largest rect found among the other sub-groups (area, full_path, line, etc.).
     */
    private fun inferBoundsFromSubGroups(subGroups: Map<String, SubGroupStyle>): RectF {
        return subGroups.values
            .map { it.bounds }
            .filter { !it.isEmpty }
            .maxByOrNull { it.width() * it.height() }
            ?: RectF()
    }

    private data class ViewBox(val x: Float, val y: Float, val width: Float, val height: Float)

    private fun parseViewBox(svgString: String): ViewBox? {
        val parser = createParser(svgString)
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "svg") {
                val vb = parser.getAttributeValue(null, "viewBox")
                if (vb != null) {
                    val parts = vb.trim().split("\\s+".toRegex())
                    if (parts.size == 4) {
                        return ViewBox(
                            parts[0].toFloatOrNull() ?: 0f,
                            parts[1].toFloatOrNull() ?: 0f,
                            parts[2].toFloatOrNull() ?: 1080f,
                            parts[3].toFloatOrNull() ?: 1920f
                        )
                    }
                }
                // Fallback to width/height attributes
                val w = parser.getAttributeValue(null, "width")?.toFloatOrNull()
                val h = parser.getAttributeValue(null, "height")?.toFloatOrNull()
                if (w != null && h != null) return ViewBox(0f, 0f, w, h)
                return null
            }
            eventType = parser.next()
        }
        return null
    }

    private fun parseRectBounds(parser: XmlPullParser, scaleX: Float, scaleY: Float): RectF {
        val x = (parser.getAttributeValue(null, "x")?.toFloatOrNull() ?: 0f) * scaleX
        val y = (parser.getAttributeValue(null, "y")?.toFloatOrNull() ?: 0f) * scaleY
        val w = (parser.getAttributeValue(null, "width")?.toFloatOrNull() ?: 0f) * scaleX
        val h = (parser.getAttributeValue(null, "height")?.toFloatOrNull() ?: 0f) * scaleY
        return RectF(x, y, x + w, y + h)
    }

    // ── Color parsing ──────────────────────────────────────────────────

    private fun parseColor(value: String): Int? {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed == "none" || trimmed == "transparent") return null
        return try {
            Color.parseColor(trimmed)
        } catch (_: Exception) {
            // Try rgb() / rgba() format
            parseRgbColor(trimmed)
        }
    }

    private fun parseRgbColor(value: String): Int? {
        val rgbMatch = Regex("""rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*([.\d]+)\s*)?\)""")
            .matchEntire(value) ?: return null
        val r = rgbMatch.groupValues[1].toIntOrNull() ?: return null
        val g = rgbMatch.groupValues[2].toIntOrNull() ?: return null
        val b = rgbMatch.groupValues[3].toIntOrNull() ?: return null
        val a = rgbMatch.groupValues.getOrNull(4)?.toFloatOrNull() ?: 1f
        return Color.argb((a * 255).toInt(), r, g, b)
    }

    // ── XML parser factory ─────────────────────────────────────────────

    private fun createParser(svgString: String): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(svgString.reader())
        return parser
    }

    private fun defaultStyleFor(name: String, accentColor: Int, dp: Float): PlaceholderStyle =
        when {
            name.contains("elevation_chart") -> PlaceholderStyle(
                accentColor = accentColor,
                backgroundColor = Color.TRANSPARENT,
                borderColor = Color.TRANSPARENT,
                hasBackground = false
            )
            name.contains("route_map") -> PlaceholderStyle(
                accentColor = accentColor,
                backgroundColor = Color.argb(40, 0, 0, 0),
                borderColor = Color.argb(30, 255, 255, 255),
                borderWidth = 1f,
                cornerRadius = 8f * dp
            )
            else -> PlaceholderStyle(accentColor = accentColor)
        }

    /**
     * Parses translate(x,y) or translate(x y) from an SVG transform attribute.
     * Returns (0,0) if no translate found.
     */
    private fun parseTranslate(transform: String): Pair<Float, Float> {
        if (transform.isBlank()) return 0f to 0f
        val match = Regex("""translate\(\s*([-.0-9]+)[,\s]+([-.0-9]+)\s*\)""").find(transform)
            ?: return 0f to 0f
        val tx = match.groupValues[1].toFloatOrNull() ?: 0f
        val ty = match.groupValues[2].toFloatOrNull() ?: 0f
        return tx to ty
    }

    private const val TAG = "SvgPlaceholderResolver"
}
