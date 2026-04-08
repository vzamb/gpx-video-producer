package com.gpxvideo.core.overlayrenderer

import android.content.Context
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Loaded template: composition + raw JSON for placeholder resolution.
 */
data class LoadedTemplate(
    val composition: LottieComposition,
    val jsonString: String
)

/**
 * Loads and caches Lottie compositions from asset files.
 * Resolves (template, aspectRatio) → asset path.
 *
 * Asset path pattern: templates/{template}_{ratio}.json
 * e.g. "templates/cinematic_9x16.json"
 */
class LottieTemplateLoader(private val context: Context) {

    private val cache = ConcurrentHashMap<String, LoadedTemplate>()

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
            val result = LottieCompositionFactory.fromJsonStringSync(jsonString, path)
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

    fun clearCache() {
        cache.clear()
    }
}
