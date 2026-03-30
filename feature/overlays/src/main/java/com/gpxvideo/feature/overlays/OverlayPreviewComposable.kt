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
import com.gpxvideo.lib.gpxparser.GpxStats

@Composable
fun OverlayPreviewLayer(
    overlays: List<OverlayConfig>,
    gpxData: GpxData?,
    stats: GpxStats?,
    containerWidth: Int,
    containerHeight: Int,
    currentPositionMs: Long,
    syncEngine: GpxTimeSyncEngine?,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // Quantize position to 100ms to avoid excessive re-rendering
    val quantizedPosition = (currentPositionMs / 100L) * 100L

    // Compute interpolated point once for all dynamic overlays
    val currentPoint = remember(syncEngine, quantizedPosition) {
        syncEngine?.getPointAtVideoTime(quantizedPosition)
    }

    Box(modifier = modifier) {
        overlays.forEach { overlay ->
            val pixelW = (overlay.size.width * containerWidth).toInt().coerceAtLeast(1)
            val pixelH = (overlay.size.height * containerHeight).toInt().coerceAtLeast(1)
            val offsetX = (overlay.position.x * containerWidth).toInt()
            val offsetY = (overlay.position.y * containerHeight).toInt()

            val cacheKey = overlayKey(overlay, quantizedPosition)
            val bitmap = remember(cacheKey, pixelW, pixelH) {
                renderOverlayBitmap(overlay, gpxData, stats, currentPoint, pixelW, pixelH)
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
    currentPoint: InterpolatedPoint?,
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
        is OverlayConfig.DynamicAltitudeProfile -> {
            if (gpxData != null && currentPoint != null) {
                DynamicOverlayRenderer.renderDynamicAltitudeProfile(
                    overlay, gpxData, currentPoint, width, height
                )
            } else null
        }
        is OverlayConfig.DynamicMap -> {
            if (gpxData != null && currentPoint != null) {
                DynamicOverlayRenderer.renderDynamicMap(
                    overlay, gpxData, currentPoint, width, height
                )
            } else null
        }
        is OverlayConfig.DynamicStat -> {
            if (currentPoint != null) {
                DynamicOverlayRenderer.renderDynamicStat(overlay, currentPoint, width, height)
            } else null
        }
    }
}

private fun overlayKey(overlay: OverlayConfig, positionMs: Long): Any = when (overlay) {
    is OverlayConfig.StaticAltitudeProfile -> Triple(overlay.id, overlay.lineColor, overlay.copy())
    is OverlayConfig.StaticMap -> Triple(overlay.id, overlay.routeColor, overlay.copy())
    is OverlayConfig.StaticStats -> Triple(overlay.id, overlay.fields, overlay.copy())
    is OverlayConfig.DynamicAltitudeProfile -> Triple(overlay.id, overlay.copy(), positionMs)
    is OverlayConfig.DynamicMap -> Triple(overlay.id, overlay.copy(), positionMs)
    is OverlayConfig.DynamicStat -> Triple(overlay.id, overlay.copy(), positionMs)
}
