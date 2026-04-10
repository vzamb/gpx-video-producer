package com.gpxvideo.core.overlayrenderer

import android.graphics.Color
import android.graphics.RectF
import com.airbnb.lottie.LottieComposition
import org.json.JSONObject

/**
 * Style info extracted from a Lottie placeholder layer.
 * All visual properties for charts and maps come from the template design.
 */
data class PlaceholderStyle(
    val accentColor: Int = Color.argb(204, 68, 138, 255),
    val backgroundColor: Int = Color.argb(40, 0, 0, 0),
    val borderColor: Int = Color.argb(30, 255, 255, 255),
    val borderWidth: Float = 1f,
    val cornerRadius: Float = 8f,
    val hasBackground: Boolean = true,
    // Chart line styling (extracted from design or defaults)
    val lineColor: Int = Color.WHITE,
    val lineWidth: Float = 2.5f,
    val fullPathColor: Int = Color.argb(60, 255, 255, 255),
    val fullPathWidth: Float = 1.5f,
    val areaFillColor: Int = 0, // 0 = derive from lineColor
    val areaFillOpacity: Int = 80,
    // Progress dot
    val dotRadius: Float = 4f,
    val glowRadius: Float = 7f
)

/**
 * Info about a resolved placeholder layer in a Lottie composition.
 */
data class PlaceholderInfo(
    val name: String,
    val bounds: RectF,
    val style: PlaceholderStyle
)

/**
 * Info about a text layer parsed from Lottie JSON, used for native Canvas text rendering.
 */
data class TextLayerInfo(
    val name: String,
    val x: Float,
    val y: Float,
    val fontSize: Float,
    val color: Int,
    val justify: Int, // 0=left, 1=center, 2=right
    val fontBold: Boolean
)

/**
 * Resolves placeholder layers and text layers from a Lottie composition's raw JSON.
 */
object PlaceholderResolver {

    const val ELEVATION_CHART = "placeholder_elevation_chart"
    const val ROUTE_MAP = "placeholder_route_map"

    fun resolveFromJson(
        jsonString: String,
        canvasWidth: Int,
        canvasHeight: Int,
        accentColor: Int = Color.argb(204, 68, 138, 255)
    ): Map<String, PlaceholderInfo> {
        val result = mutableMapOf<String, PlaceholderInfo>()

        try {
            val root = JSONObject(jsonString)
            val compW = root.optDouble("w", 1080.0).toFloat()
            val compH = root.optDouble("h", 1920.0).toFloat()
            val scaleX = canvasWidth / compW
            val scaleY = canvasHeight / compH
            val dp = canvasWidth / 360f

            val layers = root.optJSONArray("layers") ?: return result
            for (i in 0 until layers.length()) {
                val layer = layers.getJSONObject(i)
                val name = layer.optString("nm", "")
                if (!name.startsWith("placeholder_")) continue

                val ks = layer.optJSONObject("ks") ?: continue

                // Try type-1 solid layer first (sw/sh fields)
                val layerW = layer.optDouble("sw", 0.0).toFloat()
                val layerH = layer.optDouble("sh", 0.0).toFloat()
                if (layerW > 0 && layerH > 0) {
                    val posArray = ks.optJSONObject("p")?.optJSONArray("k")
                    val anchorArray = ks.optJSONObject("a")?.optJSONArray("k")
                    val posX = posArray?.optDouble(0, 0.0)?.toFloat() ?: 0f
                    val posY = posArray?.optDouble(1, 0.0)?.toFloat() ?: 0f
                    val anchorX = anchorArray?.optDouble(0, 0.0)?.toFloat() ?: 0f
                    val anchorY = anchorArray?.optDouble(1, 0.0)?.toFloat() ?: 0f
                    val left = (posX - anchorX) * scaleX
                    val top = (posY - anchorY) * scaleY
                    val right = left + layerW * scaleX
                    val bottom = top + layerH * scaleY
                    result[name] = PlaceholderInfo(name, RectF(left, top, right, bottom), styleForPlaceholder(name, accentColor, dp))
                    continue
                }

                // Fallback: type-4 shape layer with rectangle shape (from Lottie Studio etc.)
                val shapeResult = resolveShapeLayerWithStyle(layer, ks, scaleX, scaleY)
                if (shapeResult != null) {
                    val (bounds, extractedStyle) = shapeResult
                    val baseStyle = styleForPlaceholder(name, accentColor, dp)
                    // Merge: extracted style overrides defaults where present
                    result[name] = PlaceholderInfo(name, bounds, mergeStyles(baseStyle, extractedStyle))
                }
            }
        } catch (_: Exception) {
        }

        return result
    }

    /**
     * Extract bounds AND visual style from a type-4 shape layer.
     * Reads fill colors, stroke colors/widths, corner radius from the Lottie shapes.
     * Returns (bounds, partial style) or null if no rectangle found.
     */
    private fun resolveShapeLayerWithStyle(
        layer: org.json.JSONObject,
        ks: org.json.JSONObject,
        scaleX: Float,
        scaleY: Float
    ): Pair<RectF, PlaceholderStyle>? {
        val posObj = ks.optJSONObject("p") ?: return null
        val posX: Float
        val posY: Float
        val posK = posObj.opt("k")
        if (posK is org.json.JSONArray && posK.length() >= 2 && posK.opt(0) is Number) {
            posX = posK.optDouble(0, 0.0).toFloat()
            posY = posK.optDouble(1, 0.0).toFloat()
        } else return null

        val shapes = layer.optJSONArray("shapes") ?: return null

        var bounds: RectF? = null
        var cornerRadius = 0f
        var fillColor: Int? = null
        var fillOpacity = 100
        var strokeColor: Int? = null
        var strokeWidth = 0f

        for (s in 0 until shapes.length()) {
            val shape = shapes.getJSONObject(s)
            when (shape.optString("ty")) {
                "rc" -> {
                    val sizeK = shape.optJSONObject("s")?.optJSONArray("k") ?: continue
                    val w = sizeK.optDouble(0, 0.0).toFloat()
                    val h = sizeK.optDouble(1, 0.0).toFloat()
                    if (w <= 0 || h <= 0) continue
                    val left = (posX - w / 2f) * scaleX
                    val top = (posY - h / 2f) * scaleY
                    bounds = RectF(left, top, left + w * scaleX, top + h * scaleY)
                    cornerRadius = shape.optJSONObject("r")?.optDouble("k", 0.0)?.toFloat() ?: 0f
                }
                "fl" -> {
                    val c = shape.optJSONObject("c")?.optJSONArray("k")
                    if (c != null && c.length() >= 3) {
                        val r = (c.optDouble(0) * 255).toInt().coerceIn(0, 255)
                        val g = (c.optDouble(1) * 255).toInt().coerceIn(0, 255)
                        val b = (c.optDouble(2) * 255).toInt().coerceIn(0, 255)
                        fillColor = Color.rgb(r, g, b)
                    }
                    fillOpacity = shape.optJSONObject("o")?.optDouble("k", 100.0)?.toInt() ?: 100
                }
                "st" -> {
                    val c = shape.optJSONObject("c")?.optJSONArray("k")
                    if (c != null && c.length() >= 3) {
                        val r = (c.optDouble(0) * 255).toInt().coerceIn(0, 255)
                        val g = (c.optDouble(1) * 255).toInt().coerceIn(0, 255)
                        val b = (c.optDouble(2) * 255).toInt().coerceIn(0, 255)
                        strokeColor = Color.rgb(r, g, b)
                    }
                    strokeWidth = shape.optJSONObject("w")?.optDouble("k", 0.0)?.toFloat() ?: 0f
                }
            }
        }

        if (bounds == null) return null

        val scale = minOf(scaleX, scaleY)
        val style = PlaceholderStyle(
            backgroundColor = if (fillColor != null && fillOpacity > 0)
                Color.argb((fillOpacity * 255 / 100).coerceIn(0, 255),
                    Color.red(fillColor!!), Color.green(fillColor!!), Color.blue(fillColor!!))
            else Color.TRANSPARENT,
            hasBackground = fillColor != null && fillOpacity > 0,
            cornerRadius = cornerRadius * scale,
            lineColor = strokeColor ?: 0,
            lineWidth = if (strokeWidth > 0) strokeWidth * scale else 0f,
            areaFillColor = strokeColor ?: 0
        )
        return bounds to style
    }

    /**
     * Merge an extracted style into a base style. Extracted values override defaults
     * only when they are explicitly set (non-zero colors, positive widths).
     */
    private fun mergeStyles(base: PlaceholderStyle, extracted: PlaceholderStyle): PlaceholderStyle {
        return base.copy(
            backgroundColor = if (extracted.hasBackground) extracted.backgroundColor else base.backgroundColor,
            hasBackground = if (extracted.hasBackground) true else base.hasBackground,
            cornerRadius = if (extracted.cornerRadius > 0) extracted.cornerRadius else base.cornerRadius,
            lineColor = if (extracted.lineColor != 0) extracted.lineColor else base.lineColor,
            lineWidth = if (extracted.lineWidth > 0) extracted.lineWidth else base.lineWidth,
            areaFillColor = if (extracted.areaFillColor != 0) extracted.areaFillColor else base.areaFillColor
        )
    }

    /**
     * Parse text layers from Lottie JSON for native Canvas rendering.
     * Supports both:
     * - Type 5 (proper Lottie text layers) — from converter script or hand-authored JSON
     * - Type 4 (shape layers with vectorized text) — from Lottie Studio, Figma exports, etc.
     *   Recognized by name prefix: stat_*, label_*, title_*
     *
     * Returns map of layer name → TextLayerInfo with scaled positions/sizes.
     */
    fun resolveTextLayers(
        jsonString: String,
        canvasWidth: Int,
        canvasHeight: Int
    ): Map<String, TextLayerInfo> {
        val result = mutableMapOf<String, TextLayerInfo>()

        try {
            val root = JSONObject(jsonString)
            val compW = root.optDouble("w", 1080.0).toFloat()
            val compH = root.optDouble("h", 1920.0).toFloat()
            val scaleX = canvasWidth / compW
            val scaleY = canvasHeight / compH
            val scale = minOf(scaleX, scaleY)

            val layers = root.optJSONArray("layers") ?: return result
            for (i in 0 until layers.length()) {
                val layer = layers.getJSONObject(i)
                val ty = layer.optInt("ty", -1)
                val name = layer.optString("nm", "")
                if (name.isEmpty()) continue

                val ks = layer.optJSONObject("ks") ?: continue

                if (ty == 5) {
                    // Standard Lottie text layer
                    resolveType5TextLayer(layer, name, ks, scaleX, scaleY, scale)?.let {
                        result[name] = it
                    }
                } else if (ty == 4 && isTextLayerName(name)) {
                    // Shape layer with vectorized text — compute from bounding box
                    resolveType4TextLayer(layer, name, ks, scaleX, scaleY)?.let {
                        result[name] = it
                    }
                }
            }
        } catch (_: Exception) {
        }

        return result
    }

    private val TEXT_NAME_PREFIXES = arrayOf("stat_", "label_", "title_")

    private fun isTextLayerName(name: String): Boolean {
        return TEXT_NAME_PREFIXES.any { name.startsWith(it) }
    }

    private fun resolveType5TextLayer(
        layer: org.json.JSONObject,
        name: String,
        ks: org.json.JSONObject,
        scaleX: Float,
        scaleY: Float,
        scale: Float
    ): TextLayerInfo? {
        val posArray = ks.optJSONObject("p")?.optJSONArray("k")
        val posX = (posArray?.optDouble(0, 0.0)?.toFloat() ?: 0f) * scaleX
        val posY = (posArray?.optDouble(1, 0.0)?.toFloat() ?: 0f) * scaleY

        val textDoc = layer.optJSONObject("t")
            ?.optJSONObject("d")
            ?.optJSONArray("k")
            ?.optJSONObject(0)
            ?.optJSONObject("s") ?: return null

        val fontSize = textDoc.optDouble("s", 24.0).toFloat() * scale
        val fontName = textDoc.optString("f", "")
        val justify = textDoc.optInt("j", 0)
        val isBold = fontName.contains("Bold", ignoreCase = true)

        val fcArray = textDoc.optJSONArray("fc")
        val color = if (fcArray != null && fcArray.length() >= 3) {
            val r = (fcArray.optDouble(0, 1.0) * 255).toInt()
            val g = (fcArray.optDouble(1, 1.0) * 255).toInt()
            val b = (fcArray.optDouble(2, 1.0) * 255).toInt()
            Color.rgb(r, g, b)
        } else {
            Color.WHITE
        }

        return TextLayerInfo(name, posX, posY, fontSize, color, justify, isBold)
    }

    /**
     * Resolve text info from a type-4 shape layer with vectorized text.
     * Computes bounding box from all path vertices to determine position and font size.
     * The layer position offset (ks.p) is added to each vertex coordinate.
     */
    private fun resolveType4TextLayer(
        layer: org.json.JSONObject,
        name: String,
        ks: org.json.JSONObject,
        scaleX: Float,
        scaleY: Float
    ): TextLayerInfo? {
        // Get layer position offset
        val posObj = ks.optJSONObject("p")
        val offsetX: Float
        val offsetY: Float
        if (posObj != null) {
            val pk = posObj.opt("k")
            if (pk is org.json.JSONArray && pk.length() >= 2 && pk.opt(0) is Number) {
                offsetX = pk.optDouble(0, 0.0).toFloat()
                offsetY = pk.optDouble(1, 0.0).toFloat()
            } else {
                offsetX = 0f
                offsetY = 0f
            }
        } else {
            offsetX = 0f
            offsetY = 0f
        }

        // Collect all vertices from shape paths
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        var vertexCount = 0

        val shapes = layer.optJSONArray("shapes") ?: return null
        for (s in 0 until shapes.length()) {
            val shape = shapes.getJSONObject(s)
            collectVerticesFromShape(shape, offsetX, offsetY) { x, y ->
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
                vertexCount++
            }
        }

        if (vertexCount == 0) return null

        val bboxH = maxY - minY
        // Font size ~ bboxHeight / 0.72 (cap height ratio)
        val fontSize = (bboxH / 0.72f) * minOf(scaleX, scaleY)

        // Extract fill color from shapes
        val color = extractFillColor(shapes) ?: Color.WHITE

        // Determine defaults based on name
        val isBold = name.startsWith("stat_") || name.startsWith("title_")
        val isLabel = name.startsWith("label_")
        val isTitle = name.startsWith("title_")

        // Position and alignment: right-align for right-side stats, left for title
        val justify: Int
        val drawX: Float
        val drawY: Float

        if (isTitle) {
            // Left-aligned, draw at left edge of bbox
            justify = 0
            drawX = minX * scaleX
            drawY = maxY * scaleY
        } else {
            // Right-aligned for stats/labels (typical overlay layout: right-side cards)
            justify = 2
            drawX = maxX * scaleX
            drawY = maxY * scaleY
        }

        return TextLayerInfo(name, drawX, drawY, fontSize, color, justify, isBold)
    }

    /**
     * Recursively collect all path vertices from a shape or group.
     */
    private fun collectVerticesFromShape(
        shape: org.json.JSONObject,
        offsetX: Float,
        offsetY: Float,
        consumer: (Float, Float) -> Unit
    ) {
        val ty = shape.optString("ty", "")
        when (ty) {
            "gr" -> {
                val items = shape.optJSONArray("it") ?: return
                for (j in 0 until items.length()) {
                    collectVerticesFromShape(items.getJSONObject(j), offsetX, offsetY, consumer)
                }
            }
            "sh" -> {
                val ksObj = shape.optJSONObject("ks") ?: return
                val k = ksObj.opt("k")
                if (k is org.json.JSONObject) {
                    val verts = k.optJSONArray("v") ?: return
                    for (v in 0 until verts.length()) {
                        val pt = verts.optJSONArray(v) ?: continue
                        consumer(pt.optDouble(0, 0.0).toFloat() + offsetX,
                                 pt.optDouble(1, 0.0).toFloat() + offsetY)
                    }
                }
            }
        }
    }

    /**
     * Extract fill color from a shapes array (searches groups and top-level fills).
     */
    private fun extractFillColor(shapes: org.json.JSONArray): Int? {
        for (i in 0 until shapes.length()) {
            val shape = shapes.getJSONObject(i)
            val ty = shape.optString("ty", "")
            if (ty == "fl") {
                return parseFillColor(shape)
            } else if (ty == "gr") {
                val items = shape.optJSONArray("it") ?: continue
                for (j in 0 until items.length()) {
                    val item = items.getJSONObject(j)
                    if (item.optString("ty") == "fl") {
                        return parseFillColor(item)
                    }
                }
            }
        }
        return null
    }

    private fun parseFillColor(fill: org.json.JSONObject): Int {
        val cObj = fill.optJSONObject("c")
        val cArr = cObj?.optJSONArray("k")
        return if (cArr != null && cArr.length() >= 3) {
            val r = (cArr.optDouble(0, 1.0) * 255).toInt().coerceIn(0, 255)
            val g = (cArr.optDouble(1, 1.0) * 255).toInt().coerceIn(0, 255)
            val b = (cArr.optDouble(2, 1.0) * 255).toInt().coerceIn(0, 255)
            Color.rgb(r, g, b)
        } else {
            Color.WHITE
        }
    }

    fun resolve(
        composition: LottieComposition,
        jsonString: String,
        canvasWidth: Int,
        canvasHeight: Int,
        accentColor: Int = Color.argb(204, 68, 138, 255)
    ): Map<String, PlaceholderInfo> {
        return resolveFromJson(jsonString, canvasWidth, canvasHeight, accentColor)
    }

    private fun styleForPlaceholder(name: String, accentColor: Int, dp: Float): PlaceholderStyle {
        return when {
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
    }
}
