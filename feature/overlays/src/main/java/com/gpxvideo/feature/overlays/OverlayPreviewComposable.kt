package com.gpxvideo.feature.overlays

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.OverlayConfig
import com.gpxvideo.lib.gpxparser.GpxStatistics
import com.gpxvideo.lib.gpxparser.GpxStats

@Composable
fun OverlayPreviewLayer(
    overlays: List<OverlayConfig>,
    gpxData: GpxData?,
    stats: GpxStats?,
    containerWidth: Int,
    containerHeight: Int,
    currentPositionMs: Long,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    Box(modifier = modifier) {
        overlays.forEach { overlay ->
            val pixelW = (overlay.size.width * containerWidth).toInt().coerceAtLeast(1)
            val pixelH = (overlay.size.height * containerHeight).toInt().coerceAtLeast(1)
            val offsetX = (overlay.position.x * containerWidth).toInt()
            val offsetY = (overlay.position.y * containerHeight).toInt()

            val cacheKey = overlayKey(overlay)
            val bitmap = remember(cacheKey, pixelW, pixelH) {
                renderOverlayBitmap(overlay, gpxData, stats, pixelW, pixelH)
            }

            if (bitmap != null) {
                val widthDp = with(density) { pixelW.toDp() }
                val heightDp = with(density) { pixelH.toDp() }

                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = overlay.name,
                    contentScale = ContentScale.FillBounds,
                    alpha = overlay.style.opacity,
                    modifier = Modifier
                        .offset { IntOffset(offsetX, offsetY) }
                        .size(width = widthDp, height = heightDp)
                )
            }
        }
    }
}

private fun renderOverlayBitmap(
    overlay: OverlayConfig,
    gpxData: GpxData?,
    stats: GpxStats?,
    width: Int,
    height: Int
): Bitmap? {
    return when (overlay) {
        is OverlayConfig.StaticAltitudeProfile -> gpxData?.let {
            OverlayRenderer.renderStaticAltitudeProfile(overlay, it, width, height)
        }
        is OverlayConfig.StaticMap -> gpxData?.let {
            OverlayRenderer.renderStaticMap(overlay, it, width, height)
        }
        is OverlayConfig.StaticStats -> stats?.let {
            OverlayRenderer.renderStaticStats(overlay, it, width, height)
        }
        // Dynamic overlays return null for now — to be implemented in a future phase
        is OverlayConfig.DynamicAltitudeProfile -> null
        is OverlayConfig.DynamicMap -> null
        is OverlayConfig.DynamicStat -> null
    }
}

private fun overlayKey(overlay: OverlayConfig): Any = when (overlay) {
    is OverlayConfig.StaticAltitudeProfile -> Triple(overlay.id, overlay.lineColor, overlay.copy())
    is OverlayConfig.StaticMap -> Triple(overlay.id, overlay.routeColor, overlay.copy())
    is OverlayConfig.StaticStats -> Triple(overlay.id, overlay.fields, overlay.copy())
    is OverlayConfig.DynamicAltitudeProfile -> overlay.id
    is OverlayConfig.DynamicMap -> overlay.id
    is OverlayConfig.DynamicStat -> overlay.id
}
