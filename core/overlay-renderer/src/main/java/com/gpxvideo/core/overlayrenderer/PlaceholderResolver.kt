package com.gpxvideo.core.overlayrenderer

import android.graphics.Color
import android.graphics.RectF
import com.airbnb.lottie.LottieComposition
import org.json.JSONObject

/**
 * Style info extracted from a Lottie placeholder layer.
 */
data class PlaceholderStyle(
    val accentColor: Int = Color.argb(204, 68, 138, 255),
    val backgroundColor: Int = Color.argb(40, 0, 0, 0),
    val borderColor: Int = Color.argb(30, 255, 255, 255),
    val borderWidth: Float = 1f,
    val cornerRadius: Float = 8f,
    val hasBackground: Boolean = true
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

                val layerW = layer.optDouble("sw", 0.0).toFloat()
                val layerH = layer.optDouble("sh", 0.0).toFloat()
                if (layerW <= 0 || layerH <= 0) continue

                val ks = layer.optJSONObject("ks") ?: continue
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

                val style = styleForPlaceholder(name, accentColor, dp)

                result[name] = PlaceholderInfo(
                    name = name,
                    bounds = RectF(left, top, right, bottom),
                    style = style
                )
            }
        } catch (_: Exception) {
        }

        return result
    }

    /**
     * Parse text layers from Lottie JSON for native Canvas rendering.
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
                if (ty != 5) continue // text layers only

                val name = layer.optString("nm", "")
                if (name.isEmpty()) continue

                // Position from transform
                val ks = layer.optJSONObject("ks") ?: continue
                val posArray = ks.optJSONObject("p")?.optJSONArray("k")
                val posX = (posArray?.optDouble(0, 0.0)?.toFloat() ?: 0f) * scaleX
                val posY = (posArray?.optDouble(1, 0.0)?.toFloat() ?: 0f) * scaleY

                // Text document properties
                val textDoc = layer.optJSONObject("t")
                    ?.optJSONObject("d")
                    ?.optJSONArray("k")
                    ?.optJSONObject(0)
                    ?.optJSONObject("s") ?: continue

                val fontSize = textDoc.optDouble("s", 24.0).toFloat() * scale
                val fontName = textDoc.optString("f", "")
                val justify = textDoc.optInt("j", 0)
                val isBold = fontName.contains("Bold", ignoreCase = true)

                // Color from fc array [r, g, b] in 0..1
                val fcArray = textDoc.optJSONArray("fc")
                val color = if (fcArray != null && fcArray.length() >= 3) {
                    val r = (fcArray.optDouble(0, 1.0) * 255).toInt()
                    val g = (fcArray.optDouble(1, 1.0) * 255).toInt()
                    val b = (fcArray.optDouble(2, 1.0) * 255).toInt()
                    Color.rgb(r, g, b)
                } else {
                    Color.WHITE
                }

                result[name] = TextLayerInfo(
                    name = name,
                    x = posX,
                    y = posY,
                    fontSize = fontSize,
                    color = color,
                    justify = justify,
                    fontBold = isBold
                )
            }
        } catch (_: Exception) {
        }

        return result
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
