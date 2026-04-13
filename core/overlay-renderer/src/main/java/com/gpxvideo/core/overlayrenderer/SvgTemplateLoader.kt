package com.gpxvideo.core.overlayrenderer

import android.content.Context
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Loaded SVG template: parsed SVG + metadata.
 */
data class LoadedSvgTemplate(
    val svg: SVG,
    val rawSvgString: String,
    val meta: SvgTemplateMeta
)

data class SvgTemplateMeta(
    val displayName: String,
    val description: String = "",
    val sportType: String? = null,
    val fonts: Map<String, String> = emptyMap()
)

/**
 * Metadata about a discovered SVG template.
 */
data class SvgTemplateInfo(
    val id: String,
    val displayName: String,
    val description: String
)

/**
 * Loads and caches SVG template compositions from asset files.
 *
 * Templates are organised in subdirectories under assets/templates/:
 *   assets/templates/{templateId}/
 *     {templateId}_{ratio}.svg
 *     meta.json
 *
 * For backward compatibility, also supports flat layout:
 *   assets/templates/{templateId}_{ratio}.svg
 *
 * The loader discovers available templates, loads the correct aspect ratio
 * variant, registers custom fonts, and caches everything.
 */
class SvgTemplateLoader(
    private val context: Context,
    private val fontProvider: TemplateFontProvider
) {
    private val cache = ConcurrentHashMap<String, LoadedSvgTemplate>()
    private var discoveredTemplates: List<SvgTemplateInfo>? = null

    private val RATIOS = setOf("16x9", "9x16", "4x5", "1x1")

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
     * Discovers all available SVG templates by scanning assets/templates/.
     * Supports both subdirectory layout (preferred) and flat file layout.
     */
    fun discoverTemplates(): List<SvgTemplateInfo> {
        discoveredTemplates?.let { return it }

        val templateIds = mutableSetOf<String>()

        // Check for subdirectories first
        val entries = try {
            context.assets.list("templates")?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        for (entry in entries) {
            // Check if it's a directory with SVG files
            val subFiles = try {
                context.assets.list("templates/$entry")?.toList() ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }

            if (subFiles.any { it.endsWith(".svg") }) {
                templateIds.add(entry)
                continue
            }

            // Flat file layout: strip ratio suffix from filename
            if (entry.endsWith(".svg")) {
                val baseName = entry.removeSuffix(".svg")
                for (ratio in RATIOS) {
                    if (baseName.endsWith("_$ratio")) {
                        templateIds.add(baseName.removeSuffix("_$ratio"))
                        break
                    }
                }
            }
        }

        val templates = templateIds.sorted().map { id ->
            val meta = readMeta(id)
            SvgTemplateInfo(
                id = id,
                displayName = meta?.displayName ?: id.replace("_", " ")
                    .replaceFirstChar { c -> c.uppercase() },
                description = meta?.description ?: ""
            )
        }

        discoveredTemplates = templates
        return templates
    }

    suspend fun load(templateId: String, width: Int, height: Int): LoadedSvgTemplate? {
        val rKey = ratioKey(width, height)
        val cacheKey = "${templateId}_${rKey}"
        cache[cacheKey]?.let { return it }

        return withContext(Dispatchers.IO) {
            loadWithFallback(templateId, rKey, cacheKey)
        }
    }

    fun loadSync(templateId: String, width: Int, height: Int): LoadedSvgTemplate? {
        val rKey = ratioKey(width, height)
        val cacheKey = "${templateId}_${rKey}"
        cache[cacheKey]?.let { return it }
        return loadWithFallback(templateId, rKey, cacheKey)
    }

    private fun loadWithFallback(
        templateId: String, rKey: String, cacheKey: String
    ): LoadedSvgTemplate? {
        // Try exact match, then lowercase (DB may store "CINEMATIC", dirs are "cinematic")
        val idsToTry = if (templateId == templateId.lowercase()) {
            listOf(templateId)
        } else {
            listOf(templateId, templateId.lowercase())
        }

        for (id in idsToTry) {
            loadFromAsset(id, rKey)?.let {
                cache[cacheKey] = it
                return it
            }
        }
        // Fallback: try other ratios with viewBox scaling
        for (id in idsToTry) {
            for (fallbackRatio in RATIOS) {
                if (fallbackRatio == rKey) continue
                val loaded = loadFromAsset(id, fallbackRatio) ?: continue
                val rescaled = rescaleSvg(loaded, rKey)
                cache[cacheKey] = rescaled
                return rescaled
            }
        }
        return null
    }

    private fun loadFromAsset(templateId: String, ratioKey: String): LoadedSvgTemplate? {
        val paths = listOf(
            "templates/$templateId/${templateId}_${ratioKey}.svg",
            "templates/${templateId}_${ratioKey}.svg"
        )

        for (path in paths) {
            try {
                val svgString = context.assets.open(path).bufferedReader().use { it.readText() }
                val svg = SVG.getFromString(svgString)
                val meta = readMeta(templateId) ?: SvgTemplateMeta(
                    displayName = templateId.replace("_", " ").replaceFirstChar { it.uppercase() }
                )

                // Register custom fonts from meta (asset paths are relative to template dir)
                meta.fonts.forEach { (family, relativePath) ->
                    fontProvider.registerFont(family, "templates/$templateId/$relativePath")
                }

                return LoadedSvgTemplate(svg, svgString, meta)
            } catch (e: Exception) {
                android.util.Log.d(TAG, "Not found: $path")
            }
        }
        return null
    }

    /**
     * Rescale an SVG to a different aspect ratio by adjusting the viewBox.
     * The SVG content stays the same but is fitted into the new viewport.
     */
    private fun rescaleSvg(original: LoadedSvgTemplate, targetRatio: String): LoadedSvgTemplate {
        val (tgtW, tgtH) = when (targetRatio) {
            "16x9" -> 1920f to 1080f
            "9x16" -> 1080f to 1920f
            "4x5" -> 1080f to 1350f
            "1x1" -> 1080f to 1080f
            else -> return original
        }

        return try {
            val svg = SVG.getFromString(original.rawSvgString)
            svg.documentWidth = tgtW
            svg.documentHeight = tgtH
            LoadedSvgTemplate(svg, original.rawSvgString, original.meta)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to rescale SVG: ${e.message}")
            original
        }
    }

    private fun readMeta(templateId: String): SvgTemplateMeta? {
        val paths = listOf(
            "templates/$templateId/meta.json",
            "templates/${templateId}_meta.json"
        )

        for (path in paths) {
            try {
                val json = context.assets.open(path).bufferedReader().use { it.readText() }
                return parseMeta(json)
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private fun parseMeta(jsonString: String): SvgTemplateMeta {
        val root = JSONObject(jsonString)
        val fontsObj = root.optJSONObject("fonts")
        val fonts = mutableMapOf<String, String>()
        if (fontsObj != null) {
            for (key in fontsObj.keys()) {
                fonts[key] = fontsObj.getString(key)
            }
        }
        return SvgTemplateMeta(
            displayName = root.optString("displayName", ""),
            description = root.optString("description", ""),
            sportType = root.optString("sportType", null),
            fonts = fonts
        )
    }

    fun clearCache() {
        cache.clear()
        discoveredTemplates = null
    }

    companion object {
        private const val TAG = "SvgTemplateLoader"
    }
}
