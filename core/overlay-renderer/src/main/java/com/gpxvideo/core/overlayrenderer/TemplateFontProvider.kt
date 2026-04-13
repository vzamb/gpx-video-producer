package com.gpxvideo.core.overlayrenderer

import android.content.Context
import android.graphics.Typeface
import com.caverock.androidsvg.SVGExternalFileResolver
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads and caches custom fonts from the app's assets directory.
 *
 * Templates can reference custom font families (e.g. "Racing Display") which map
 * to bundled .ttf/.otf files. Fonts are loaded lazily and cached.
 *
 * Also acts as an [SVGExternalFileResolver] so AndroidSVG can resolve font-family
 * attributes in `<text>` elements to the correct Typeface.
 */
class TemplateFontProvider(private val context: Context) : SVGExternalFileResolver() {

    private val typefaceCache = ConcurrentHashMap<String, Typeface>()
    private val fontPathMap = ConcurrentHashMap<String, String>()

    /**
     * Register a font family → asset path mapping.
     * Call this when loading a template's meta.json.
     *
     * @param family  The font-family name as used in SVG (e.g. "Racing Display")
     * @param assetPath  Path within assets (e.g. "fonts/racing_display.ttf")
     */
    fun registerFont(family: String, assetPath: String) {
        fontPathMap[family.lowercase()] = assetPath
    }

    /**
     * Get a Typeface for the given font family and weight.
     * Returns the custom font if registered, otherwise falls back to system fonts.
     */
    fun getTypeface(fontFamily: String?, bold: Boolean = false): Typeface {
        if (fontFamily.isNullOrBlank()) {
            return if (bold) SYSTEM_BOLD else SYSTEM_NORMAL
        }

        val key = "${fontFamily.lowercase()}:${if (bold) "bold" else "normal"}"
        typefaceCache[key]?.let { return it }

        val loaded = loadTypeface(fontFamily)
        val styled = if (bold && loaded != null) {
            Typeface.create(loaded, Typeface.BOLD)
        } else {
            loaded
        }

        val result = styled ?: if (bold) SYSTEM_BOLD else SYSTEM_NORMAL
        typefaceCache[key] = result
        return result
    }

    private fun loadTypeface(fontFamily: String): Typeface? {
        val assetPath = fontPathMap[fontFamily.lowercase()] ?: return trySystemFont(fontFamily)
        return try {
            Typeface.createFromAsset(context.assets, assetPath)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to load font '$fontFamily' from $assetPath: ${e.message}")
            trySystemFont(fontFamily)
        }
    }

    private fun trySystemFont(fontFamily: String): Typeface? {
        return try {
            val tf = Typeface.create(fontFamily, Typeface.NORMAL)
            if (tf != Typeface.DEFAULT) tf else null
        } catch (_: Exception) {
            null
        }
    }

    /** Clear all cached typefaces and registered fonts. */
    fun clearCache() {
        typefaceCache.clear()
        fontPathMap.clear()
    }

    // ── SVGExternalFileResolver implementation ─────────────────────────

    override fun resolveFont(fontFamily: String?, fontWeight: Int, fontStyle: String?): Typeface {
        val bold = fontWeight >= 700
        return getTypeface(fontFamily, bold)
    }

    companion object {
        private const val TAG = "TemplateFontProvider"
        private val SYSTEM_BOLD = Typeface.create("sans-serif", Typeface.BOLD)
        private val SYSTEM_NORMAL = Typeface.create("sans-serif", Typeface.NORMAL)
    }
}
