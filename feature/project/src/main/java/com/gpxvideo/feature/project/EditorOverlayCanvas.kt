package com.gpxvideo.feature.project

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.OverlayConfig
import com.gpxvideo.feature.overlays.DynamicOverlayRenderer
import com.gpxvideo.feature.overlays.GpxTimeSyncEngine
import com.gpxvideo.feature.overlays.OverlayRenderer
import com.gpxvideo.feature.timeline.TimelineClipState
import com.gpxvideo.feature.timeline.TimelineState
import com.gpxvideo.lib.gpxparser.GpxStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun EditorOverlayCanvas(
    timelineState: TimelineState,
    overlays: List<OverlayConfig>,
    gpxData: GpxData?,
    gpxStats: GpxStats?,
    syncEngine: GpxTimeSyncEngine?,
    currentPositionMs: Long,
    onSelectClip: (java.util.UUID?) -> Unit,
    onUpdateOverlay: (OverlayConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleClipIds = remember(timelineState.tracks, currentPositionMs) {
        timelineState.tracks
            .filter { it.isVisible }
            .flatMap { it.clips }
            .filter { currentPositionMs in it.startTimeMs..it.endTimeMs }
            .associateBy { it.id }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val canvasWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val canvasHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val density = LocalDensity.current

        overlays.forEach { overlay ->
            val clip = visibleClipIds[overlay.timelineClipId] ?: return@forEach
            val widthPx = (overlay.size.width * canvasWidthPx).coerceAtLeast(56f)
            val heightPx = (overlay.size.height * canvasHeightPx).coerceAtLeast(36f)
            val offsetXPx = (overlay.position.x * canvasWidthPx)
                .coerceIn(0f, (canvasWidthPx - widthPx).coerceAtLeast(0f))
            val offsetYPx = (overlay.position.y * canvasHeightPx)
                .coerceIn(0f, (canvasHeightPx - heightPx).coerceAtLeast(0f))
            val selected = timelineState.selectedClipId == overlay.timelineClipId

            // Keep a live reference so gesture closures always see the latest overlay state
            val currentOverlay by rememberUpdatedState(overlay)

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetXPx.roundToInt(), offsetYPx.roundToInt()) }
                    .size(
                        width = with(density) { widthPx.toDp() },
                        height = with(density) { heightPx.toDp() }
                    )
                    .alpha(overlay.style.opacity.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (selected) {
                            Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp))
                        } else {
                            Modifier
                        }
                    )
                    .pointerInput(overlay.id) {
                        detectTapGestures(
                            onTap = { onSelectClip(overlay.timelineClipId) }
                        )
                    }
                    .pointerInput(overlay.id) {
                        detectDragGestures(
                            onDragStart = {
                                onSelectClip(currentOverlay.timelineClipId)
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            // Always read the LATEST position so consecutive drags are correct
                            val live = currentOverlay
                            val newX = (live.position.x + dragAmount.x / canvasWidthPx)
                                .coerceIn(0f, (1f - live.size.width).coerceAtLeast(0f))
                            val newY = (live.position.y + dragAmount.y / canvasHeightPx)
                                .coerceIn(0f, (1f - live.size.height).coerceAtLeast(0f))
                            onUpdateOverlay(live.moveTo(newX, newY))
                        }
                    }
            ) {
                OverlayPreviewContent(
                    overlay = overlay,
                    clip = clip,
                    gpxData = gpxData,
                    gpxStats = gpxStats,
                    syncEngine = syncEngine,
                    currentPositionMs = currentPositionMs,
                    modifier = Modifier.fillMaxSize()
                )

                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 8.dp, y = 8.dp)
                            .size(22.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .pointerInput(overlay.id) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val live = currentOverlay
                                    val newWidth = (live.size.width + dragAmount.x / canvasWidthPx)
                                        .coerceIn(0.12f, (1f - live.position.x).coerceAtLeast(0.12f))
                                    val newHeight = (live.size.height + dragAmount.y / canvasHeightPx)
                                        .coerceIn(0.06f, (1f - live.position.y).coerceAtLeast(0.06f))
                                    onUpdateOverlay(live.resizeTo(newWidth, newHeight))
                                }
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayPreviewContent(
    overlay: OverlayConfig,
    clip: TimelineClipState,
    gpxData: GpxData?,
    gpxStats: GpxStats?,
    syncEngine: GpxTimeSyncEngine?,
    currentPositionMs: Long,
    modifier: Modifier = Modifier
) {
    val bitmap by rememberOverlayBitmap(
        overlay = overlay,
        gpxData = gpxData,
        gpxStats = gpxStats,
        syncEngine = syncEngine,
        currentPositionMs = currentPositionMs
    )

    when (overlay) {
        is OverlayConfig.TextLabel -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = overlay.text,
                    color = Color(overlay.style.fontColor),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        else -> {
            val imageBitmap = bitmap
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = overlay.name,
                    modifier = modifier,
                    contentScale = ContentScale.FillBounds
                )
            }
        }
    }
}

@Composable
private fun rememberOverlayBitmap(
    overlay: OverlayConfig,
    gpxData: GpxData?,
    gpxStats: GpxStats?,
    syncEngine: GpxTimeSyncEngine?,
    currentPositionMs: Long
) : State<ImageBitmap?> = produceState<ImageBitmap?>(
    initialValue = null,
    overlay,
    gpxData,
    gpxStats,
    if (overlay.isDynamic()) syncEngine else null,
    // Throttle dynamic overlays: only re-render every 500ms of playback
    if (overlay.isDynamic()) (currentPositionMs / 500L) else Long.MIN_VALUE
) {
    value = withContext(Dispatchers.Default) {
        renderOverlayBitmap(overlay, gpxData, gpxStats, syncEngine, currentPositionMs)?.asImageBitmap()
    }
}

private fun renderOverlayBitmap(
    overlay: OverlayConfig,
    gpxData: GpxData?,
    gpxStats: GpxStats?,
    syncEngine: GpxTimeSyncEngine?,
    currentPositionMs: Long
): Bitmap? {
    val width = (overlay.size.width * 1280).roundToInt().coerceAtLeast(160)
    val height = (overlay.size.height * 720).roundToInt().coerceAtLeast(90)
    return when (overlay) {
        is OverlayConfig.StaticAltitudeProfile ->
            gpxData?.let { OverlayRenderer.renderStaticAltitudeProfile(overlay, it, width, height) }

        is OverlayConfig.StaticMap ->
            gpxData?.let { OverlayRenderer.renderStaticMap(overlay, it, width, height) }

        is OverlayConfig.StaticStats ->
            gpxStats?.let { OverlayRenderer.renderStaticStats(overlay, it, width, height) }

        is OverlayConfig.DynamicAltitudeProfile -> gpxData?.let { data ->
            val point = syncEngine?.getPointAtVideoTime(currentPositionMs)
                ?: GpxTimeSyncEngine(data, overlay.syncMode).getPointAtVideoTime(currentPositionMs)
            DynamicOverlayRenderer.renderDynamicAltitudeProfile(overlay, data, point, width, height)
        }

        is OverlayConfig.DynamicMap -> gpxData?.let { data ->
            val point = syncEngine?.getPointAtVideoTime(currentPositionMs)
                ?: GpxTimeSyncEngine(data, overlay.syncMode).getPointAtVideoTime(currentPositionMs)
            DynamicOverlayRenderer.renderDynamicMap(overlay, data, point, width, height)
        }

        is OverlayConfig.DynamicStat -> gpxData?.let { data ->
            val point = syncEngine?.getPointAtVideoTime(currentPositionMs)
                ?: GpxTimeSyncEngine(data, overlay.syncMode).getPointAtVideoTime(currentPositionMs)
            DynamicOverlayRenderer.renderDynamicStat(overlay, point, width, height)
        }

        is OverlayConfig.TextLabel -> null
    }
}

private fun OverlayConfig.moveTo(x: Float, y: Float): OverlayConfig = when (this) {
    is OverlayConfig.StaticAltitudeProfile -> copy(position = position.copy(x = x, y = y))
    is OverlayConfig.StaticMap -> copy(position = position.copy(x = x, y = y))
    is OverlayConfig.StaticStats -> copy(position = position.copy(x = x, y = y))
    is OverlayConfig.DynamicAltitudeProfile -> copy(position = position.copy(x = x, y = y))
    is OverlayConfig.DynamicMap -> copy(position = position.copy(x = x, y = y))
    is OverlayConfig.DynamicStat -> copy(position = position.copy(x = x, y = y))
    is OverlayConfig.TextLabel -> copy(position = position.copy(x = x, y = y))
}

private fun OverlayConfig.resizeTo(width: Float, height: Float): OverlayConfig = when (this) {
    is OverlayConfig.StaticAltitudeProfile -> copy(size = size.copy(width = width, height = height))
    is OverlayConfig.StaticMap -> copy(size = size.copy(width = width, height = height))
    is OverlayConfig.StaticStats -> copy(size = size.copy(width = width, height = height))
    is OverlayConfig.DynamicAltitudeProfile -> copy(size = size.copy(width = width, height = height))
    is OverlayConfig.DynamicMap -> copy(size = size.copy(width = width, height = height))
    is OverlayConfig.DynamicStat -> copy(size = size.copy(width = width, height = height))
    is OverlayConfig.TextLabel -> copy(size = size.copy(width = width, height = height))
}

private fun OverlayConfig.isDynamic(): Boolean = when (this) {
    is OverlayConfig.DynamicAltitudeProfile,
    is OverlayConfig.DynamicMap,
    is OverlayConfig.DynamicStat -> true
    else -> false
}
