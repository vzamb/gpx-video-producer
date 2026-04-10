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
        val path = assetPath(template, rKey)
        cache[path]?.let { return it }

        return withContext(Dispatchers.IO) {
            loadFromAsset(path)
        }
    }

    fun loadSync(template: String, width: Int, height: Int): LoadedTemplate? {
        val rKey = ratioKey(width, height)
        val path = assetPath(template, rKey)
        cache[path]?.let { return it }
        return loadFromAsset(path)
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
     * Hide text and placeholder shape layers in JSON so Lottie doesn't render them.
     * Text (stat_*, label_*, title_*) and placeholder (placeholder_*) layers
     * are drawn natively by the renderer, so they must be hidden from Lottie.
     * Only affects type-4 shape layers; type-5 text and type-1 solid layers
     * are already handled correctly by Lottie (text is overridden, solids are transparent).
     */
    private fun hideOverlayLayers(jsonString: String): String {
        return try {
            val root = JSONObject(jsonString)
            val layers = root.optJSONArray("layers") ?: return jsonString
            var modified = false
            for (i in 0 until layers.length()) {
                val layer = layers.getJSONObject(i)
                val ty = layer.optInt("ty", -1)
                if (ty != 4) continue // only modify shape layers
                val name = layer.optString("nm", "")
                if (name.startsWith("stat_") || name.startsWith("label_") ||
                    name.startsWith("title_") || name.startsWith("placeholder_")) {
                    layer.put("hd", true)
                    modified = true
                }
            }
            if (modified) root.toString() else jsonString
        } catch (_: Exception) {
            jsonString
        }
    }

    fun clearCache() {
        cache.clear()
        discoveredTemplates = null
    }
}
