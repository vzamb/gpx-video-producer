package com.gpxvideo.core.overlayrenderer

import android.graphics.Color
import android.graphics.RectF
import com.airbnb.lottie.LottieComposition
import org.json.JSONObject
import org.json.JSONArray

/**
 * Style info extracted from a Lottie chart/map layer's named sub-elements.
 *
 * In Lottie Studio the designer creates a visual mockup of each chart element
 * (background, line, area fill, dots) inside named groups. This data class
 * holds the style properties read from those design elements.
 */
data class PlaceholderStyle(
    val accentColor: Int = Color.argb(204, 68, 138, 255),
    // "background" group
    val backgroundColor: Int = Color.argb(40, 0, 0, 0),
    val borderColor: Int = Color.argb(30, 255, 255, 255),
    val borderWidth: Float = 1f,
    val cornerRadius: Float = 8f,
    val hasBackground: Boolean = true,
    // "line" / "route" group → visited path stroke
    val lineColor: Int = Color.WHITE,
    val lineWidth: Float = 2.5f,
    // "full_path" / "full_route" group → unvisited portion stroke
    val fullPathColor: Int = Color.argb(60, 255, 255, 255),
    val fullPathWidth: Float = 1.5f,
    // "area" group → gradient fill below elevation line
    val areaFillColor: Int = 0, // 0 = derive from lineColor
    val areaFillOpacity: Int = 80,
    // "dot" group → current position indicator
    val dotRadius: Float = 4f,
    val dotColor: Int = Color.WHITE,
    // "glow" group → outer glow around position dot
    val glowRadius: Float = 7f,
    val glowColor: Int = Color.argb(60, 255, 255, 255)
)

data class PlaceholderInfo(
    val name: String,
    val bounds: RectF,
    val style: PlaceholderStyle
)

data class TextLayerInfo(
    val name: String,
    val x: Float,
    val y: Float,
    val fontSize: Float,
    val color: Int,
    val justify: Int,
    val fontBold: Boolean
)

/**
 * Resolves chart/map layers and text layers from a Lottie composition's raw JSON.
 *
 * Chart/map layers are identified by name:
 * - "elevation_chart" or "placeholder_elevation_chart" → elevation profile
 * - "route_map" or "placeholder_route_map" → mini route map
 *
 * Inside these layers the designer creates named sub-groups to style each
 * visual element of the chart (background, line, area, dot, glow, etc.).
 * The app renders real GPS data using those exact styles.
 */
object PlaceholderResolver {

    const val ELEVATION_CHART = "elevation_chart"
    const val ROUTE_MAP = "route_map"

    private val CHART_LAYER_NAMES = setOf(
        "elevation_chart", "placeholder_elevation_chart"
    )
    private val MAP_LAYER_NAMES = setOf(
        "route_map", "placeholder_route_map"
    )

    private fun isChartOrMapLayer(name: String): Boolean =
        name in CHART_LAYER_NAMES || name in MAP_LAYER_NAMES

    private fun normalizeLayerName(name: String): String = when (name) {
        "placeholder_elevation_chart" -> ELEVATION_CHART
        "placeholder_route_map" -> ROUTE_MAP
        else -> name
    }

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
                val rawName = layer.optString("nm", "")
                if (!isChartOrMapLayer(rawName)) continue

                val name = normalizeLayerName(rawName)
                val ks = layer.optJSONObject("ks") ?: continue

                // Legacy type-1 solid layers (sw/sh)
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
                    result[name] = PlaceholderInfo(name, RectF(left, top,
                        left + layerW * scaleX, top + layerH * scaleY),
                        defaultStyleFor(name, accentColor, dp))
                    continue
                }

                // Shape layer: resolve from named sub-groups or flat shapes
                resolveShapeLayer(layer, ks, scaleX, scaleY, name, accentColor, dp)
                    ?.let { result[name] = it }
            }
        } catch (_: Exception) {}
        return result
    }

    // ── Shape layer resolution ─────────────────────────────────────────

    private fun resolveShapeLayer(
        layer: JSONObject, ks: JSONObject,
        scaleX: Float, scaleY: Float,
        name: String, accentColor: Int, dp: Float
    ): PlaceholderInfo? {
        val posObj = ks.optJSONObject("p") ?: return null
        val posK = posObj.opt("k")
        if (posK !is JSONArray || posK.length() < 2 || posK.opt(0) !is Number) return null
        val posX = posK.optDouble(0, 0.0).toFloat()
        val posY = posK.optDouble(1, 0.0).toFloat()

        val scaleObj = ks.optJSONObject("s")
        val lsX = scaleObj?.let { s ->
            val sk = s.opt("k")
            if (sk is JSONArray && sk.length() >= 2) sk.optDouble(0, 100.0).toFloat() / 100f else 1f
        } ?: 1f
        val lsY = scaleObj?.let { s ->
            val sk = s.opt("k")
            if (sk is JSONArray && sk.length() >= 2) sk.optDouble(1, 100.0).toFloat() / 100f else 1f
        } ?: 1f

        val shapes = layer.optJSONArray("shapes") ?: return null
        val scale = minOf(scaleX, scaleY)

        // Try named sub-groups first (design-driven)
        val named = findNamedGroups(shapes)
        if (named.isNotEmpty()) {
            return resolveFromNamedGroups(named, posX, posY, lsX, lsY, scaleX, scaleY, scale, name, accentColor, dp)
        }

        // Fallback: flat shapes (legacy)
        return resolveFromFlatShapes(shapes, posX, posY, lsX, lsY, scaleX, scaleY, scale, name, accentColor, dp)
    }

    // ── Named sub-groups ───────────────────────────────────────────────

    private val MEANINGFUL_NAMES = setOf(
        "background", "line", "route", "area",
        "full_path", "full_route", "dot", "glow"
    )

    private fun findNamedGroups(shapes: JSONArray): Map<String, JSONObject> {
        val result = mutableMapOf<String, JSONObject>()
        for (i in 0 until shapes.length()) {
            val shape = shapes.getJSONObject(i)
            if (shape.optString("ty") != "gr") continue
            val nm = shape.optString("nm", "").lowercase().trim()
            if (nm in MEANINGFUL_NAMES) result[nm] = shape
        }
        return result
    }

    private fun resolveFromNamedGroups(
        groups: Map<String, JSONObject>,
        posX: Float, posY: Float,
        lsX: Float, lsY: Float,
        scaleX: Float, scaleY: Float, scale: Float,
        name: String, accentColor: Int, dp: Float
    ): PlaceholderInfo? {
        val base = defaultStyleFor(name, accentColor, dp)

        // ── Background → bounds + bg style ──
        val bgGroup = groups["background"]
        val bounds: RectF
        var bgColor = base.backgroundColor
        var hasBackground = base.hasBackground
        var cornerRadius = base.cornerRadius
        var borderColor = base.borderColor
        var borderWidth = base.borderWidth

        if (bgGroup != null) {
            val items = bgGroup.optJSONArray("it")
            val rect = findInGroup(items, "rc") ?: return null
            val sizeK = rect.optJSONObject("s")?.optJSONArray("k") ?: return null
            val w = sizeK.optDouble(0, 0.0).toFloat() * lsX
            val h = sizeK.optDouble(1, 0.0).toFloat() * lsY
            if (w <= 0 || h <= 0) return null
            val left = (posX - w / 2f) * scaleX
            val top = (posY - h / 2f) * scaleY
            bounds = RectF(left, top, left + w * scaleX, top + h * scaleY)
            cornerRadius = (rect.optJSONObject("r")?.optDouble("k", 0.0)?.toFloat() ?: 0f) * scale

            findFillInGroup(items)?.let { fl ->
                val c = colorFrom(fl)
                val o = fl.optJSONObject("o")?.optDouble("k", 100.0)?.toInt() ?: 100
                if (c != null && o > 0) {
                    bgColor = Color.argb((o * 255 / 100).coerceIn(0, 255),
                        Color.red(c), Color.green(c), Color.blue(c))
                    hasBackground = true
                }
            }
            findInGroup(items, "st")?.let { st ->
                borderColor = colorFrom(st) ?: borderColor
                borderWidth = st.optJSONObject("w")?.optDouble("k", 1.0)?.toFloat() ?: borderWidth
            }
        } else {
            // No background group → look for a bare rc for bounds
            val rect = findBareRect(groups, posX, posY, lsX, lsY, scaleX, scaleY)
            bounds = rect ?: return null
        }

        // ── Line / Route → visited stroke ──
        var lineColor = base.lineColor
        var lineWidth = base.lineWidth
        (groups["line"] ?: groups["route"])?.let { g ->
            val items = g.optJSONArray("it")
            findInGroup(items, "st")?.let { st ->
                lineColor = colorFrom(st) ?: lineColor
                st.optJSONObject("w")?.optDouble("k", 0.0)?.toFloat()?.let {
                    if (it > 0) lineWidth = it * scale
                }
            }
            if (findInGroup(items, "st") == null) {
                findFillInGroup(items)?.let { fl -> lineColor = colorFrom(fl) ?: lineColor }
            }
        }

        // ── Full path / Full route → unvisited stroke ──
        var fullPathColor = base.fullPathColor
        var fullPathWidth = base.fullPathWidth
        (groups["full_path"] ?: groups["full_route"])?.let { g ->
            val items = g.optJSONArray("it")
            findInGroup(items, "st")?.let { st ->
                val c = colorFrom(st)
                val o = st.optJSONObject("o")?.optDouble("k", 100.0)?.toInt() ?: 100
                if (c != null) fullPathColor = Color.argb(
                    (o * 255 / 100).coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))
                st.optJSONObject("w")?.optDouble("k", 0.0)?.toFloat()?.let {
                    if (it > 0) fullPathWidth = it * scale
                }
            }
        }

        // ── Area → gradient fill ──
        var areaFillColor = base.areaFillColor
        var areaFillOpacity = base.areaFillOpacity
        groups["area"]?.let { g ->
            val items = g.optJSONArray("it")
            findFillInGroup(items)?.let { fl ->
                areaFillColor = colorFrom(fl) ?: areaFillColor
                areaFillOpacity = fl.optJSONObject("o")?.optDouble("k", 100.0)?.toInt() ?: areaFillOpacity
            }
        }

        // ── Dot → position indicator ──
        var dotRadius = base.dotRadius
        var dotColor = base.dotColor
        groups["dot"]?.let { g ->
            val items = g.optJSONArray("it")
            findInGroup(items, "el")?.let { el ->
                val sz = el.optJSONObject("s")?.optJSONArray("k")
                val ew = sz?.optDouble(0, 0.0)?.toFloat() ?: 0f
                if (ew > 0) dotRadius = (ew / 2f) * scale
            }
            findFillInGroup(items)?.let { fl -> dotColor = colorFrom(fl) ?: dotColor }
        }

        // ── Glow → outer glow ──
        var glowRadius = base.glowRadius
        var glowColor = base.glowColor
        groups["glow"]?.let { g ->
            val items = g.optJSONArray("it")
            findInGroup(items, "el")?.let { el ->
                val sz = el.optJSONObject("s")?.optJSONArray("k")
                val ew = sz?.optDouble(0, 0.0)?.toFloat() ?: 0f
                if (ew > 0) glowRadius = (ew / 2f) * scale
            }
            findFillInGroup(items)?.let { fl ->
                val c = colorFrom(fl)
                val o = fl.optJSONObject("o")?.optDouble("k", 100.0)?.toInt() ?: 100
                if (c != null) glowColor = Color.argb(
                    (o * 255 / 100).coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))
            }
        }

        // Lottie renders the background group — ChartRenderer/RouteMapRenderer
        // should not draw their own background.
        return PlaceholderInfo(name, bounds, base.copy(
            backgroundColor = bgColor, hasBackground = false,
            cornerRadius = cornerRadius, borderColor = borderColor, borderWidth = borderWidth,
            lineColor = lineColor, lineWidth = lineWidth,
            fullPathColor = fullPathColor, fullPathWidth = fullPathWidth,
            areaFillColor = areaFillColor, areaFillOpacity = areaFillOpacity,
            dotRadius = dotRadius, dotColor = dotColor,
            glowRadius = glowRadius, glowColor = glowColor
        ))
    }

    // ── Flat shapes fallback ───────────────────────────────────────────

    private fun resolveFromFlatShapes(
        shapes: JSONArray,
        posX: Float, posY: Float,
        lsX: Float, lsY: Float,
        scaleX: Float, scaleY: Float, scale: Float,
        name: String, accentColor: Int, dp: Float
    ): PlaceholderInfo? {
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
                    val w = sizeK.optDouble(0, 0.0).toFloat() * lsX
                    val h = sizeK.optDouble(1, 0.0).toFloat() * lsY
                    if (w <= 0 || h <= 0) continue
                    val left = (posX - w / 2f) * scaleX
                    val top = (posY - h / 2f) * scaleY
                    bounds = RectF(left, top, left + w * scaleX, top + h * scaleY)
                    cornerRadius = shape.optJSONObject("r")?.optDouble("k", 0.0)?.toFloat() ?: 0f
                }
                "fl", "gf" -> {
                    fillColor = colorFrom(shape)
                    fillOpacity = shape.optJSONObject("o")?.optDouble("k", 100.0)?.toInt() ?: 100
                }
                "st" -> {
                    strokeColor = colorFrom(shape)
                    strokeWidth = shape.optJSONObject("w")?.optDouble("k", 0.0)?.toFloat() ?: 0f
                }
            }
        }
        if (bounds == null) return null

        val base = defaultStyleFor(name, accentColor, dp)
        // Lottie renders the flat shapes (background rect) — don't draw it natively
        return PlaceholderInfo(name, bounds, base.copy(
            backgroundColor = if (fillColor != null && fillOpacity > 0)
                Color.argb((fillOpacity * 255 / 100).coerceIn(0, 255),
                    Color.red(fillColor!!), Color.green(fillColor!!), Color.blue(fillColor!!))
            else Color.TRANSPARENT,
            hasBackground = false,
            cornerRadius = if (cornerRadius > 0) cornerRadius * scale else base.cornerRadius,
            lineColor = strokeColor ?: base.lineColor,
            lineWidth = if (strokeWidth > 0) strokeWidth * scale else base.lineWidth,
            areaFillColor = strokeColor ?: base.areaFillColor
        ))
    }

    private fun findBareRect(
        groups: Map<String, JSONObject>,
        posX: Float, posY: Float,
        lsX: Float, lsY: Float,
        scaleX: Float, scaleY: Float
    ): RectF? = null // named groups should always have a background

    // ── Helpers ────────────────────────────────────────────────────────

    /**
     * Find the first fill-like shape in a group: flat fill ("fl") or gradient fill ("gf").
     * Prefers "fl" when both exist.
     */
    private fun findFillInGroup(items: JSONArray?): JSONObject? {
        return findInGroup(items, "fl") ?: findInGroup(items, "gf")
    }

    private fun findInGroup(items: JSONArray?, type: String): JSONObject? {
        items ?: return null
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            if (item.optString("ty") == type) return item
        }
        return null
    }

    /**
     * Extract a color from a fill shape.
     * Supports flat fills ("fl") with a simple c.k array,
     * and gradient fills ("gf") — reads the first color stop from g.k.k.
     */
    private fun colorFrom(shape: JSONObject): Int? {
        val ty = shape.optString("ty")

        // Flat fill → c.k = [r, g, b]
        if (ty != "gf") {
            val c = shape.optJSONObject("c")?.optJSONArray("k")
            if (c != null && c.length() >= 3) {
                val r = (c.optDouble(0) * 255).toInt().coerceIn(0, 255)
                val g = (c.optDouble(1) * 255).toInt().coerceIn(0, 255)
                val b = (c.optDouble(2) * 255).toInt().coerceIn(0, 255)
                return Color.rgb(r, g, b)
            }
            return null
        }

        // Gradient fill → g.k.k = [stop0, r0, g0, b0, stop1, r1, g1, b1, ...]
        // Extract the first color stop
        val gObj = shape.optJSONObject("g") ?: return null
        val kObj = gObj.optJSONObject("k") ?: return null
        val stops = kObj.optJSONArray("k") ?: return null
        val numColors = gObj.optInt("p", 0)
        // Color data occupies the first numColors*4 entries (stop, r, g, b per stop)
        // After that come opacity stops. Read first color stop (index 1,2,3).
        if (stops.length() >= 4) {
            val r = (stops.optDouble(1) * 255).toInt().coerceIn(0, 255)
            val g = (stops.optDouble(2) * 255).toInt().coerceIn(0, 255)
            val b = (stops.optDouble(3) * 255).toInt().coerceIn(0, 255)
            return Color.rgb(r, g, b)
        }
        return null
    }

    // ── Text layer resolution ──────────────────────────────────────────

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
                    resolveType5TextLayer(layer, name, ks, scaleX, scaleY, scale)
                        ?.let { result[name] = it }
                } else if (ty == 4 && isTextLayerName(name)) {
                    resolveType4TextLayer(layer, name, ks, scaleX, scaleY)
                        ?.let { result[name] = it }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    // Only stat_* and title_* need native text (labels are rendered by Lottie as designed)
    private val TEXT_NAME_PREFIXES = arrayOf("stat_", "title_")
    private fun isTextLayerName(name: String) = TEXT_NAME_PREFIXES.any { name.startsWith(it) }

    private fun resolveType5TextLayer(
        layer: JSONObject, name: String, ks: JSONObject,
        scaleX: Float, scaleY: Float, scale: Float
    ): TextLayerInfo? {
        val posArray = ks.optJSONObject("p")?.optJSONArray("k")
        val posX = (posArray?.optDouble(0, 0.0)?.toFloat() ?: 0f) * scaleX
        val posY = (posArray?.optDouble(1, 0.0)?.toFloat() ?: 0f) * scaleY
        val textDoc = layer.optJSONObject("t")?.optJSONObject("d")
            ?.optJSONArray("k")?.optJSONObject(0)?.optJSONObject("s") ?: return null
        val fontSize = textDoc.optDouble("s", 24.0).toFloat() * scale
        val fontName = textDoc.optString("f", "")
        val justify = textDoc.optInt("j", 0)
        val isBold = fontName.contains("Bold", ignoreCase = true)
        val fcArray = textDoc.optJSONArray("fc")
        val color = if (fcArray != null && fcArray.length() >= 3) {
            Color.rgb((fcArray.optDouble(0, 1.0) * 255).toInt(),
                (fcArray.optDouble(1, 1.0) * 255).toInt(),
                (fcArray.optDouble(2, 1.0) * 255).toInt())
        } else Color.WHITE
        return TextLayerInfo(name, posX, posY, fontSize, color, justify, isBold)
    }

    private fun resolveType4TextLayer(
        layer: JSONObject, name: String, ks: JSONObject,
        scaleX: Float, scaleY: Float
    ): TextLayerInfo? {
        val posObj = ks.optJSONObject("p")
        val offsetX: Float
        val offsetY: Float
        if (posObj != null) {
            val pk = posObj.opt("k")
            if (pk is JSONArray && pk.length() >= 2 && pk.opt(0) is Number) {
                offsetX = pk.optDouble(0, 0.0).toFloat()
                offsetY = pk.optDouble(1, 0.0).toFloat()
            } else { offsetX = 0f; offsetY = 0f }
        } else { offsetX = 0f; offsetY = 0f }

        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE; var maxY = Float.MIN_VALUE
        var vertexCount = 0
        val shapes = layer.optJSONArray("shapes") ?: return null
        for (s in 0 until shapes.length()) {
            collectVerticesFromShape(shapes.getJSONObject(s), offsetX, offsetY) { x, y ->
                if (x < minX) minX = x; if (y < minY) minY = y
                if (x > maxX) maxX = x; if (y > maxY) maxY = y
                vertexCount++
            }
        }
        if (vertexCount == 0) return null

        val fontSize = ((maxY - minY) / 0.72f) * minOf(scaleX, scaleY)
        val color = extractFillColor(shapes) ?: Color.WHITE
        val isBold = name.startsWith("stat_") || name.startsWith("title_")
        val isTitle = name.startsWith("title_")
        return if (isTitle) TextLayerInfo(name, minX * scaleX, maxY * scaleY, fontSize, color, 0, isBold)
        else TextLayerInfo(name, maxX * scaleX, maxY * scaleY, fontSize, color, 2, isBold)
    }

    private fun collectVerticesFromShape(
        shape: JSONObject, offsetX: Float, offsetY: Float,
        consumer: (Float, Float) -> Unit
    ) {
        when (shape.optString("ty", "")) {
            "gr" -> {
                val items = shape.optJSONArray("it") ?: return
                for (j in 0 until items.length())
                    collectVerticesFromShape(items.getJSONObject(j), offsetX, offsetY, consumer)
            }
            "sh" -> {
                val k = shape.optJSONObject("ks")?.opt("k")
                if (k is JSONObject) {
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

    private fun extractFillColor(shapes: JSONArray): Int? {
        for (i in 0 until shapes.length()) {
            val shape = shapes.getJSONObject(i)
            val ty = shape.optString("ty", "")
            if (ty == "fl") return colorFromLegacy(shape)
            if (ty == "gr") {
                val items = shape.optJSONArray("it") ?: continue
                for (j in 0 until items.length()) {
                    val item = items.getJSONObject(j)
                    if (item.optString("ty") == "fl") return colorFromLegacy(item)
                }
            }
        }
        return null
    }

    private fun colorFromLegacy(fill: JSONObject): Int {
        val cArr = fill.optJSONObject("c")?.optJSONArray("k")
        return if (cArr != null && cArr.length() >= 3) Color.rgb(
            (cArr.optDouble(0, 1.0) * 255).toInt().coerceIn(0, 255),
            (cArr.optDouble(1, 1.0) * 255).toInt().coerceIn(0, 255),
            (cArr.optDouble(2, 1.0) * 255).toInt().coerceIn(0, 255))
        else Color.WHITE
    }

    fun resolve(
        composition: LottieComposition, jsonString: String,
        canvasWidth: Int, canvasHeight: Int,
        accentColor: Int = Color.argb(204, 68, 138, 255)
    ): Map<String, PlaceholderInfo> =
        resolveFromJson(jsonString, canvasWidth, canvasHeight, accentColor)

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
}
