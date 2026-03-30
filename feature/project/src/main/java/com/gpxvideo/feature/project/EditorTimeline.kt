package com.gpxvideo.feature.project

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpxvideo.core.model.TrackType
import com.gpxvideo.feature.timeline.TimelineClipState
import com.gpxvideo.feature.timeline.TimelineEditorAction
import com.gpxvideo.feature.timeline.TimelineState
import com.gpxvideo.feature.timeline.TimelineTrackState
import com.gpxvideo.feature.timeline.BASE_PX_PER_MS
import com.gpxvideo.feature.timeline.toColor
import java.util.UUID
import kotlin.math.roundToInt

private val VIDEO_TRACK_HEIGHT = 64.dp
private val COMPONENT_TRACK_HEIGHT = 48.dp
private val RULER_HEIGHT = 24.dp
private val TRACK_ICON_WIDTH = 32.dp
private val CLIP_CORNER = 6.dp

private val VideoClipColor = Color(0xFF2196F3)
private val AudioClipColor = Color(0xFF4CAF50)
private val OverlayClipColor = Color(0xFFFF9800)
private val TextClipColor = Color(0xFFFFC107)
private val EffectClipColor = Color(0xFF9C27B0)

@Composable
fun EditorTimeline(
    timelineState: TimelineState,
    playerPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    onAction: (TimelineEditorAction) -> Unit,
    onAddMediaClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pxPerMs = timelineState.zoomLevel * BASE_PX_PER_MS
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()
    val density = LocalDensity.current

    val timelineContentWidth = with(density) {
        ((timelineState.totalDurationMs + 10000) * pxPerMs).toDp()
    }

    // Separate tracks by type
    val videoTracks = timelineState.tracks.filter {
        it.type == TrackType.VIDEO || it.type == TrackType.IMAGE
    }
    val componentTracks = timelineState.tracks.filter {
        it.type == TrackType.OVERLAY || it.type == TrackType.TEXT
    }
    val audioTracks = timelineState.tracks.filter { it.type == TrackType.AUDIO }

    Column(modifier = modifier) {
        // Time ruler row
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(
                modifier = Modifier
                    .width(TRACK_ICON_WIDTH)
                    .height(RULER_HEIGHT)
                    .background(MaterialTheme.colorScheme.surfaceDim)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalScrollState)
                    .pointerInput(pxPerMs) {
                        detectTapGestures { offset ->
                            val tapMs = ((offset.x + horizontalScrollState.value) / pxPerMs).toLong()
                            onSeekTo(tapMs.coerceAtLeast(0))
                        }
                    }
            ) {
                CompactRuler(
                    totalDurationMs = timelineState.totalDurationMs,
                    pxPerMs = pxPerMs,
                    modifier = Modifier.width(timelineContentWidth)
                )
            }
        }

        // Scrollable tracks area with playhead overlay
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.verticalScroll(verticalScrollState)
            ) {
                // Video track(s)
                videoTracks.forEach { track ->
                    TrackRow(
                        track = track,
                        height = VIDEO_TRACK_HEIGHT,
                        pxPerMs = pxPerMs,
                        selectedClipId = timelineState.selectedClipId,
                        horizontalScrollState = horizontalScrollState,
                        timelineContentWidth = timelineContentWidth,
                        onAction = onAction,
                        trailingContent = {
                            // + button to add media at end of video track
                            Box(
                                modifier = Modifier
                                    .size(VIDEO_TRACK_HEIGHT - 8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable(onClick = onAddMediaClick),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add clip",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    )
                }

                // Empty video track placeholder if none exist
                if (videoTracks.isEmpty()) {
                    EmptyVideoTrackRow(onAddMediaClick = onAddMediaClick)
                }

                // Component tracks (overlay, text)
                componentTracks.forEachIndexed { index, track ->
                    TrackRow(
                        track = track,
                        height = COMPONENT_TRACK_HEIGHT,
                        pxPerMs = pxPerMs,
                        selectedClipId = timelineState.selectedClipId,
                        horizontalScrollState = horizontalScrollState,
                        timelineContentWidth = timelineContentWidth,
                        onAction = onAction,
                        layerNumber = index + 2
                    )
                }

                // Audio track(s)
                audioTracks.forEach { track ->
                    TrackRow(
                        track = track,
                        height = COMPONENT_TRACK_HEIGHT,
                        pxPerMs = pxPerMs,
                        selectedClipId = timelineState.selectedClipId,
                        horizontalScrollState = horizontalScrollState,
                        timelineContentWidth = timelineContentWidth,
                        onAction = onAction,
                        iconOverride = Icons.Default.MusicNote
                    )
                }

                // "Add music" placeholder if no audio tracks
                if (audioTracks.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(COMPONENT_TRACK_HEIGHT)
                            .background(MaterialTheme.colorScheme.surfaceDim)
                            .clickable { onAction(TimelineEditorAction.TrackAdded(TrackType.AUDIO)) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = AudioClipColor.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "+ Add music",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            // Playhead line - spans full visible height
            val playheadOffsetPx = (playerPositionMs * pxPerMs) -
                horizontalScrollState.value.toFloat() + with(density) { TRACK_ICON_WIDTH.toPx() }

            if (playheadOffsetPx >= 0) {
                Canvas(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .offset { IntOffset(playheadOffsetPx.roundToInt(), 0) }
                ) {
                    drawLine(
                        color = Color.White,
                        start = Offset(size.width / 2, 0f),
                        end = Offset(size.width / 2, size.height),
                        strokeWidth = 2f
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactRuler(
    totalDurationMs: Long,
    pxPerMs: Float,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(color = Color.Gray, fontSize = 8.sp)
    val zoomLevel = pxPerMs / BASE_PX_PER_MS
    val majorTickIntervalMs = when {
        zoomLevel >= 4.0f -> 1000L
        zoomLevel >= 2.0f -> 2000L
        zoomLevel >= 1.0f -> 5000L
        zoomLevel >= 0.5f -> 10000L
        else -> 30000L
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(RULER_HEIGHT)
    ) {
        drawRect(color = Color(0xFF1A1A1A))

        val minorTickInterval = majorTickIntervalMs / 4
        var tickMs = 0L
        val endMs = totalDurationMs + majorTickIntervalMs

        while (tickMs <= endMs) {
            val x = tickMs * pxPerMs
            if (x > size.width + 50f) break

            val isMajor = tickMs % majorTickIntervalMs == 0L
            if (isMajor) {
                drawLine(
                    color = Color.Gray,
                    start = Offset(x, size.height * 0.5f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
                val label = formatRulerTime(tickMs)
                val textResult = textMeasurer.measure(label, textStyle)
                drawText(
                    textLayoutResult = textResult,
                    topLeft = Offset(x - textResult.size.width / 2f, 1f)
                )
            } else {
                drawLine(
                    color = Color.DarkGray,
                    start = Offset(x, size.height * 0.75f),
                    end = Offset(x, size.height),
                    strokeWidth = 0.5f
                )
            }
            tickMs += minorTickInterval
        }
    }
}

@Composable
private fun TrackRow(
    track: TimelineTrackState,
    height: androidx.compose.ui.unit.Dp,
    pxPerMs: Float,
    selectedClipId: UUID?,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    timelineContentWidth: androidx.compose.ui.unit.Dp,
    onAction: (TimelineEditorAction) -> Unit,
    layerNumber: Int? = null,
    iconOverride: ImageVector? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val trackBg = when (track.type) {
        TrackType.VIDEO, TrackType.IMAGE -> MaterialTheme.colorScheme.surfaceDim
        TrackType.AUDIO -> MaterialTheme.colorScheme.surfaceDim.copy(alpha = 0.9f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(trackBg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track icon column
        Box(
            modifier = Modifier
                .width(TRACK_ICON_WIDTH)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceDim),
            contentAlignment = Alignment.Center
        ) {
            if (layerNumber != null) {
                Text(
                    text = "\u2461".plus((layerNumber - 2).toString()),
                    style = MaterialTheme.typography.labelSmall,
                    color = track.type.toColor().copy(alpha = 0.8f)
                )
            } else {
                Icon(
                    imageVector = iconOverride ?: track.type.toTrackIcon(),
                    contentDescription = track.type.name,
                    modifier = Modifier.size(16.dp),
                    tint = track.type.toColor().copy(alpha = 0.8f)
                )
            }
        }

        // Clips area (horizontal scroll shared with ruler)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(horizontalScrollState)
        ) {
            Box(
                modifier = Modifier
                    .width(timelineContentWidth)
                    .fillMaxHeight()
                    .padding(vertical = 2.dp)
            ) {
                track.clips.forEach { clip ->
                    ClipBar(
                        clip = clip,
                        trackType = track.type,
                        trackHeight = height,
                        pxPerMs = pxPerMs,
                        isSelected = clip.id == selectedClipId,
                        onAction = onAction
                    )
                }

                // Trailing content (e.g., + button) after last clip
                if (trailingContent != null) {
                    val lastEndPx = track.clips.maxOfOrNull { it.endTimeMs * pxPerMs } ?: 0f
                    val density = LocalDensity.current
                    val offsetDp = with(density) { (lastEndPx + 8).toDp() }
                    Box(
                        modifier = Modifier
                            .offset(x = offsetDp)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        trailingContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipBar(
    clip: TimelineClipState,
    trackType: TrackType,
    trackHeight: androidx.compose.ui.unit.Dp,
    pxPerMs: Float,
    isSelected: Boolean,
    onAction: (TimelineEditorAction) -> Unit
) {
    val density = LocalDensity.current
    val startPx = clip.startTimeMs * pxPerMs
    val widthPx = (clip.endTimeMs - clip.startTimeMs) * pxPerMs
    val offsetDp = with(density) { startPx.toDp() }
    val widthDp = with(density) { widthPx.toDp() }

    val clipColor = when (trackType) {
        TrackType.VIDEO -> VideoClipColor
        TrackType.IMAGE -> EffectClipColor
        TrackType.AUDIO -> AudioClipColor
        TrackType.OVERLAY -> OverlayClipColor
        TrackType.TEXT -> TextClipColor
    }

    var dragOffsetX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .offset(x = offsetDp + with(density) { dragOffsetX.toDp() })
            .width(widthDp.coerceAtLeast(4.dp))
            .height(trackHeight - 6.dp)
            .clip(RoundedCornerShape(CLIP_CORNER))
            .background(clipColor.copy(alpha = if (isSelected) 1f else 0.7f))
            .then(
                if (isSelected) Modifier.border(
                    width = 1.5.dp,
                    color = Color.White,
                    shape = RoundedCornerShape(CLIP_CORNER)
                ) else Modifier
            )
            .clickable { onAction(TimelineEditorAction.ClipSelected(clip.id)) }
            .pointerInput(clip.id) {
                detectDragGestures(
                    onDragStart = { dragOffsetX = 0f },
                    onDragEnd = {
                        val newStartMs = ((startPx + dragOffsetX) / pxPerMs).toLong()
                            .coerceAtLeast(0)
                        onAction(TimelineEditorAction.ClipMoved(clip.id, newStartMs))
                        dragOffsetX = 0f
                    },
                    onDragCancel = { dragOffsetX = 0f }
                ) { change, dragAmount ->
                    change.consume()
                    dragOffsetX += dragAmount.x
                }
            }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text(
                text = clip.label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val durationSec = (clip.endTimeMs - clip.startTimeMs) / 1000f
            Text(
                text = "%.1fs".format(durationSec),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EmptyVideoTrackRow(
    onAddMediaClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(VIDEO_TRACK_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceDim)
            .clickable(onClick = onAddMediaClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Videocam,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = VideoClipColor.copy(alpha = 0.6f)
        )
        Text(
            text = "+ Add video clips",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

private fun TrackType.toTrackIcon(): ImageVector = when (this) {
    TrackType.VIDEO -> Icons.Default.Videocam
    TrackType.IMAGE -> Icons.Default.Image
    TrackType.AUDIO -> Icons.Default.Audiotrack
    TrackType.OVERLAY -> Icons.Default.Layers
    TrackType.TEXT -> Icons.Default.TextFields
}

private fun formatRulerTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
