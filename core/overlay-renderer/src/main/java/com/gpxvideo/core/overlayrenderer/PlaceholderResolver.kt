package com.gpxvideo.core.overlayrenderer

import android.graphics.Color
import android.graphics.RectF

/**
 * Style info extracted from chart/map layer's named sub-elements.
 *
 * The designer creates a visual mockup of each chart element
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
    val fontBold: Boolean,
    val fontFamily: String? = null,
    val strokeColor: Int = Color.TRANSPARENT,
    val strokeWidth: Float = 0f,
    /** Original placeholder text from the SVG (used to detect right-alignment from Figma exports). */
    val placeholderText: String? = null
)

