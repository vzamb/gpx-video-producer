package com.gpxvideo.core.overlayrenderer

import android.content.Context
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Loaded template: composition + raw JSON for placeholder resolution.
 */
data class LoadedTemplate(
    val composition: LottieComposition,
    val jsonString: String
)

/**
 * Metadata about a discovered template.
 * The [id] is the template base name (e.g. "cinematic", "hero", "pro_dashboard")
 * derived from the asset file naming convention: {id}_{ratio}.json
 */
data class TemplateInfo(
    val id: String,
    val displayName: String,
    val description: String
)

/**
 * Loads and caches Lottie compositions from asset files.
 * Dynamically discovers available templates by scanning the assets/templates/ directory.
 *
 * Asset path pattern: templates/{template}_{ratio}.json
 * e.g. "templates/cinematic_9x16.json"
 */
class LottieTemplateLoader(private val context: Context) {

    private val cache = ConcurrentHashMap<String, LoadedTemplate>()
    private var discoveredTemplates: List<TemplateInfo>? = null

    private val RATIOS = setOf("16x9", "9x16", "4x5", "1x1")

    private fun assetPath(template: String, ratioKey: String): String {
        val templateName = template.lowercase()
        return "templates/${templateName}_${ratioKey}.json"
    }

    fun ratioKey(width: Int, height: Int): String {
        val ratio = width.toFloat() / height
        return when {
            ratio > 1.4f -> "16x9"
            ratio < 0.65f -> "9x16"
            ratio in 0.75f..0.85f -> "4x5"
            else -> "1x1"
        }
    }

    /**
     * Discovers all available templates by scanning the assets/templates/ directory.
     * Groups files by template base name and reads metadata from the JSON.
     * Results are cached after first call.
     */
    fun discoverTemplates(): List<TemplateInfo> {
        discoveredTemplates?.let { return it }

        val files = try {
            context.assets.list("templates")?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        // Group files by template id: strip ratio suffix to get base name
        val templateIds = mutableSetOf<String>()
        for (file in files) {
            if (!file.endsWith(".json")) continue
            val baseName = file.removeSuffix(".json")
            // Try each known ratio suffix
            for (ratio in RATIOS) {
                if (baseName.endsWith("_$ratio")) {
                    templateIds.add(baseName.removeSuffix("_$ratio"))
                    break
                }
            }
        }

        val templates = templateIds.sorted().map { id ->
            // Read metadata from any available ratio variant
            val meta = readTemplateMeta(id)
            TemplateInfo(
                id = id,
                displayName = meta?.first ?: id.replace("_", " ")
                    .replaceFirstChar { c -> c.uppercase() },
                description = meta?.second ?: ""
            )
        }

        discoveredTemplates = templates
        return templates
    }

    private fun readTemplateMeta(templateId: String): Pair<String, String>? {
        // Try to read metadata from any ratio variant
        for (ratio in RATIOS) {
            val path = "templates/${templateId}_${ratio}.json"
            try {
                val json = context.assets.open(path).bufferedReader().use { it.readText() }
                val root = JSONObject(json)
                val meta = root.optJSONObject("templateMeta") ?: continue
                return meta.optString("displayName", templateId) to
                        meta.optString("description", "")
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    suspend fun load(template: String, width: Int, height: Int): LoadedTemplate? {
        val rKey = ratioKey(width, height)
        val cacheKey = "${template}_${rKey}"
        cache[cacheKey]?.let { return it }

        return withContext(Dispatchers.IO) {
            loadWithFallback(template, rKey, cacheKey)
        }
    }

    fun loadSync(template: String, width: Int, height: Int): LoadedTemplate? {
        val rKey = ratioKey(width, height)
        val cacheKey = "${template}_${rKey}"
        cache[cacheKey]?.let { return it }
        return loadWithFallback(template, rKey, cacheKey)
    }

    /**
     * Try the exact ratio file first; if missing, fall back to any available ratio.
     * When falling back, the composition canvas is rescaled to match the requested ratio.
     */
    private fun loadWithFallback(template: String, rKey: String, cacheKey: String): LoadedTemplate? {
        // Try exact match first
        loadFromAsset(assetPath(template, rKey))?.let {
            cache[cacheKey] = it
            return it
        }
        // Fallback: try other ratios and rescale
        for (fallbackRatio in RATIOS) {
            if (fallbackRatio == rKey) continue
            val fallbackPath = assetPath(template, fallbackRatio)
            val loaded = loadFromAsset(fallbackPath) ?: continue
            // Rescale the JSON canvas dimensions to target ratio
            val rescaled = rescaleTemplate(loaded.jsonString, rKey)
            val result = LottieCompositionFactory.fromJsonStringSync(
                hideOverlayLayers(rescaled), "$cacheKey(fallback)"
            )
            result.value?.let { comp ->
                val template = LoadedTemplate(comp, rescaled)
                cache[cacheKey] = template
                return template
            }
        }
        return null
    }

    /**
     * Rescale a template's JSON to fit a different aspect ratio.
     * Scales all layer positions and shape coordinates proportionally.
     */
    private fun rescaleTemplate(jsonString: String, targetRatio: String): String {
        return try {
            val root = JSONObject(jsonString)
            val srcW = root.optDouble("w", 1080.0)
            val srcH = root.optDouble("h", 1920.0)
            val (tgtW, tgtH) = when (targetRatio) {
                "16x9" -> 1920.0 to 1080.0
                "9x16" -> 1080.0 to 1920.0
                "4x5" -> 1080.0 to 1350.0
                "1x1" -> 1080.0 to 1080.0
                else -> return jsonString
            }
            val sx = tgtW / srcW
            val sy = tgtH / srcH
            root.put("w", tgtW.toInt())
            root.put("h", tgtH.toInt())
            val layers = root.optJSONArray("layers") ?: return root.toString()
            for (i in 0 until layers.length()) {
                rescaleLayer(layers.getJSONObject(i), sx, sy)
            }
            root.toString()
        } catch (_: Exception) {
            jsonString
        }
    }

    private fun rescaleLayer(layer: JSONObject, sx: Double, sy: Double) {
        // Scale solid dimensions
        if (layer.has("sw")) layer.put("sw", layer.optDouble("sw") * sx)
        if (layer.has("sh")) layer.put("sh", layer.optDouble("sh") * sy)
        // Scale transform position
        val ks = layer.optJSONObject("ks") ?: return
        rescalePosition(ks.optJSONObject("p"), sx, sy)
        rescalePosition(ks.optJSONObject("a"), sx, sy)
        // Scale shapes
        val shapes = layer.optJSONArray("shapes") ?: return
        for (s in 0 until shapes.length()) {
            rescaleShape(shapes.getJSONObject(s), sx, sy)
        }
    }

    private fun rescalePosition(posObj: JSONObject?, sx: Double, sy: Double) {
        posObj ?: return
        val k = posObj.opt("k")
        if (k is org.json.JSONArray && k.length() >= 2 && k.opt(0) is Number) {
            k.put(0, k.optDouble(0) * sx)
            k.put(1, k.optDouble(1) * sy)
        }
    }

    private fun rescaleShape(shape: JSONObject, sx: Double, sy: Double) {
        when (shape.optString("ty")) {
            "rc", "el" -> {
                rescalePosition(shape.optJSONObject("s"), sx, sy)
                rescalePosition(shape.optJSONObject("p"), sx, sy)
            }
            "sh" -> {
                val ksObj = shape.optJSONObject("ks") ?: return
                val k = ksObj.opt("k")
                if (k is JSONObject) {
                    for (arr in listOf("v", "i", "o")) {
                        val pts = k.optJSONArray(arr) ?: continue
                        for (p in 0 until pts.length()) {
                            val pt = pts.optJSONArray(p) ?: continue
                            if (pt.length() >= 2) {
                                pt.put(0, pt.optDouble(0) * sx)
                                pt.put(1, pt.optDouble(1) * sy)
                            }
                        }
                    }
                }
            }
            "gr" -> {
                val items = shape.optJSONArray("it") ?: return
                for (j in 0 until items.length()) {
                    rescaleShape(items.getJSONObject(j), sx, sy)
                }
            }
            "tr" -> {
                rescalePosition(shape.optJSONObject("p"), sx, sy)
                rescalePosition(shape.optJSONObject("a"), sx, sy)
            }
        }
    }

    private fun loadFromAsset(path: String): LoadedTemplate? {
        return try {
            val jsonString = context.assets.open(path).bufferedReader().use { it.readText() }
            // Create a modified JSON that hides text/placeholder shape layers
            // so Lottie only renders visual card/scrim shapes.
            // The original JSON is kept for text & placeholder position resolution.
            val renderJson = hideOverlayLayers(jsonString)
            val result = LottieCompositionFactory.fromJsonStringSync(renderJson, path)
            if (result.value == null) {
                android.util.Log.e("LottieLoader", "Failed to parse $path: ${result.exception?.message}")
            }
            result.value?.let { comp ->
                LoadedTemplate(comp, jsonString).also { cache[path] = it }
            }
        } catch (e: Exception) {
            android.util.Log.e("LottieLoader", "Error loading $path: ${e.message}", e)
            null
        }
    }

    /**
     * Prepare template JSON for rendering:
     * 1. Strip slide-in animations from group transforms (freeze at final position)
     * 2. Hide stat_* and title_* layers (replaced with native dynamic text)
     * 3. Keep label_* and card_* layers visible (Lottie renders them as designed)
     * 4. For chart/map layers: hide data mockup sub-groups but keep background visible
     */
    private fun hideOverlayLayers(jsonString: String): String {
        return try {
            val root = JSONObject(jsonString)
            val layers = root.optJSONArray("layers") ?: return jsonString
            var modified = false

            for (i in 0 until layers.length()) {
                val layer = layers.getJSONObject(i)
                val ty = layer.optInt("ty", -1)
                if (ty != 4) continue
                val name = layer.optString("nm", "")
                val shapes = layer.optJSONArray("shapes")

                // Strip all group transform animations to freeze at final position
                if (shapes != null && stripGroupAnimations(shapes)) {
                    modified = true
                }

                // Hide stat_* and title_* layers (native dynamic text replaces them)
                if (name.startsWith("stat_") || name.startsWith("title_")) {
                    layer.put("hd", true)
                    modified = true
                    continue
                }

                // Chart/map layers: hide data mockup groups, keep background for Lottie
                if (name == "elevation_chart" || name.startsWith("placeholder_elevation_chart") ||
                    name == "route_map" || name.startsWith("placeholder_route_map")) {
                    if (shapes != null && hideDataSubGroups(shapes)) {
                        modified = true
                    }
                }
            }
            if (modified) root.toString() else jsonString
        } catch (e: Exception) {
            android.util.Log.e("LottieLoader", "hideOverlayLayers failed", e)
            jsonString
        }
    }

    /**
     * Strip slide-in animations from group transforms.
     * Templates often have staggered intro animations that move elements off-screen.
     * We freeze every animated group transform at its final position ([0,0]).
     */
    private fun stripGroupAnimations(shapes: org.json.JSONArray): Boolean {
        var modified = false
        for (i in 0 until shapes.length()) {
            val shape = shapes.getJSONObject(i)
            if (shape.optString("ty") == "gr") {
                val items = shape.optJSONArray("it") ?: continue
                for (j in 0 until items.length()) {
                    val item = items.getJSONObject(j)
                    if (item.optString("ty") == "tr") {
                        if (flattenAnimatedTransform(item)) modified = true
                    }
                }
                // Recurse into nested groups
                if (stripGroupAnimations(items)) modified = true
            }
        }
        return modified
    }

    /**
     * Strip ALL animated properties in a group transform (position, scale, opacity)
     * so the element is frozen at its designed final state regardless of progress.
     */
    private fun flattenAnimatedTransform(transform: JSONObject): Boolean {
        var modified = false
        // Position: animated [offset] → [0,0]
        modified = flattenAnimatedProp(transform, "p", org.json.JSONArray("[0,0]")) || modified
        // Scale: animated [0,0] → [100,100]
        modified = flattenAnimatedProp(transform, "s", org.json.JSONArray("[100,100]")) || modified
        // Opacity: animated [0] → [100]
        modified = flattenAnimatedProp(transform, "o", org.json.JSONArray("[100]")) || modified
        return modified
    }

    private fun flattenAnimatedProp(
        transform: JSONObject, prop: String, fallback: org.json.JSONArray
    ): Boolean {
        val obj = transform.optJSONObject(prop) ?: return false
        val k = obj.opt("k")
        if (k is org.json.JSONArray && k.length() > 0 && k.opt(0) is JSONObject) {
            val lastKf = k.getJSONObject(k.length() - 1)
            val finalVal = lastKf.optJSONArray("s") ?: fallback
            obj.put("a", 0)
            // For scalar props (opacity), Lottie expects a number not an array
            if (prop == "o" && finalVal.length() == 1) {
                obj.put("k", finalVal.getDouble(0))
            } else {
                obj.put("k", finalVal)
            }
            return true
        }
        return false
    }

    /**
     * In chart/map layers, hide the data mockup sub-groups (line, area, dot, etc.)
     * but keep the "background" group visible so Lottie renders the styled background.
     * Also hides loose fills that aren't part of the background group.
     */
    private fun hideDataSubGroups(shapes: org.json.JSONArray): Boolean {
        val dataGroupNames = setOf("line", "route", "area", "full_path", "full_route", "dot", "glow")
        var modified = false
        for (i in 0 until shapes.length()) {
            val shape = shapes.getJSONObject(i)
            val ty = shape.optString("ty")
            if (ty == "gr") {
                val nm = shape.optString("nm", "").lowercase().trim()
                if (nm in dataGroupNames) {
                    shape.put("hd", true)
                    modified = true
                }
            } else if (ty == "fl" || ty == "gf") {
                // Hide loose fills not part of a named group
                shape.put("hd", true)
                modified = true
            }
        }
        return modified
    }

    fun clearCache() {
        cache.clear()
        discoveredTemplates = null
    }
}
