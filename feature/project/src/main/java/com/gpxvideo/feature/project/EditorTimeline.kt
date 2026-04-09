package com.gpxvideo.feature.project

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpxvideo.core.database.entity.MediaItemEntity
import com.gpxvideo.core.model.TrackType
import com.gpxvideo.feature.timeline.BASE_PX_PER_MS
import com.gpxvideo.feature.timeline.TimelineClipState
import com.gpxvideo.feature.timeline.TimelineEditorAction
import com.gpxvideo.feature.timeline.TimelineState
import com.gpxvideo.feature.timeline.TimelineTrackState
import com.gpxvideo.feature.timeline.toColor
import com.gpxvideo.lib.mediautils.ThumbnailGenerator
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

private val VIDEO_TRACK_HEIGHT = 72.dp
private val COMPONENT_TRACK_HEIGHT = 48.dp
private val RULER_HEIGHT = 24.dp
private val TRACK_ICON_WIDTH = 32.dp
private val CLIP_CORNER = 6.dp
private val CLIP_HANDLE_WIDTH = 10.dp
private const val MIN_CLIP_DURATION_MS = 300L

private val VideoClipColor = Color(0xFF2196F3)
private val AudioClipColor = Color(0xFF4CAF50)
private val OverlayClipColor = Color(0xFFFF9800)
private val TextClipColor = Color(0xFFFFC107)
private val EffectClipColor = Color(0xFF9C27B0)

@Composable
fun EditorTimeline(
    timelineState: TimelineState,
    mediaItems: List<MediaItemEntity>,
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
    val mediaItemsById = remember(mediaItems) { mediaItems.associateBy { it.id } }

    val timelineContentWidth = with(density) {
        ((timelineState.totalDurationMs + 10_000) * pxPerMs).toDp()
    }

    val videoTracks = timelineState.tracks.filter {
        it.type == TrackType.VIDEO || it.type == TrackType.IMAGE
    }
    val componentTracks = timelineState.tracks.filter {
        (it.type == TrackType.OVERLAY || it.type == TrackType.TEXT) && it.clips.isNotEmpty()
    }
    val audioTracks = timelineState.tracks.filter {
        it.type == TrackType.AUDIO && it.clips.isNotEmpty()
    }

    Column(modifier = modifier) {
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
                    .pointerInput(pxPerMs, horizontalScrollState.value, timelineState.totalDurationMs) {
                        detectTapGestures { offset ->
                            val tapMs = ((offset.x + horizontalScrollState.value) / pxPerMs).toLong()
                            onSeekTo(tapMs.coerceIn(0L, timelineState.totalDurationMs))
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

        Box(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.verticalScroll(verticalScrollState)) {
                videoTracks.forEach { track ->
                    TrackRow(
                        track = track,
                        mediaItemsById = mediaItemsById,
                        height = VIDEO_TRACK_HEIGHT,
                        pxPerMs = pxPerMs,
                        selectedClipId = timelineState.selectedClipId,
                        horizontalScrollState = horizontalScrollState,
                        timelineContentWidth = timelineContentWidth,
                        totalDurationMs = timelineState.totalDurationMs,
                        onSeekTo = onSeekTo,
                        onAction = onAction,
                        trailingContent = {
                            Box(
                                modifier = Modifier
                                    .size(VIDEO_TRACK_HEIGHT - 8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable(onClick = onAddMediaClick),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = "Add clip",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    )
                }

                if (videoTracks.isEmpty()) {
                    EmptyVideoTrackRow(onAddMediaClick = onAddMediaClick)
                }

                componentTracks.forEachIndexed { index, track ->
                    TrackRow(
                        track = track,
                        mediaItemsById = mediaItemsById,
                        height = COMPONENT_TRACK_HEIGHT,
                        pxPerMs = pxPerMs,
                        selectedClipId = timelineState.selectedClipId,
                        horizontalScrollState = horizontalScrollState,
                        timelineContentWidth = timelineContentWidth,
                        totalDurationMs = timelineState.totalDurationMs,
                        onSeekTo = onSeekTo,
                        onAction = onAction,
                        layerNumber = index + 2
                    )
                }

                audioTracks.forEach { track ->
                    TrackRow(
                        track = track,
                        mediaItemsById = mediaItemsById,
                        height = COMPONENT_TRACK_HEIGHT,
                        pxPerMs = pxPerMs,
                        selectedClipId = timelineState.selectedClipId,
                        horizontalScrollState = horizontalScrollState,
                        timelineContentWidth = timelineContentWidth,
                        totalDurationMs = timelineState.totalDurationMs,
                        onSeekTo = onSeekTo,
                        onAction = onAction,
                        iconOverride = Icons.Outlined.MusicNote
                    )
                }

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
                            imageVector = Icons.Outlined.MusicNote,
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
        zoomLevel >= 4.0f -> 1_000L
        zoomLevel >= 2.0f -> 2_000L
        zoomLevel >= 1.0f -> 5_000L
        zoomLevel >= 0.5f -> 10_000L
        else -> 30_000L
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
    mediaItemsById: Map<UUID, MediaItemEntity>,
    height: androidx.compose.ui.unit.Dp,
    pxPerMs: Float,
    selectedClipId: UUID?,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    timelineContentWidth: androidx.compose.ui.unit.Dp,
    totalDurationMs: Long,
    onSeekTo: (Long) -> Unit,
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
                    .pointerInput(pxPerMs, horizontalScrollState.value, totalDurationMs) {
                        detectTapGestures { offset ->
                            val tapMs = ((offset.x + horizontalScrollState.value) / pxPerMs).toLong()
                            onSeekTo(tapMs.coerceIn(0L, totalDurationMs))
                        }
                    }
            ) {
                track.clips.forEach { clip ->
                    ClipBar(
                        clip = clip,
                        mediaItem = clip.mediaItemId?.let(mediaItemsById::get),
                        trackType = track.type,
                        trackHeight = height,
                        pxPerMs = pxPerMs,
                        isSelected = clip.id == selectedClipId,
                        totalDurationMs = totalDurationMs,
                        onSeekTo = onSeekTo,
                        onAction = onAction
                    )
                }

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
    mediaItem: MediaItemEntity?,
    trackType: TrackType,
    trackHeight: androidx.compose.ui.unit.Dp,
    pxPerMs: Float,
    isSelected: Boolean,
    totalDurationMs: Long,
    onSeekTo: (Long) -> Unit,
    onAction: (TimelineEditorAction) -> Unit
) {
    val density = LocalDensity.current
    val startPx = clip.startTimeMs * pxPerMs
    val widthPx = (clip.endTimeMs - clip.startTimeMs) * pxPerMs

    val clipColor = when (trackType) {
        TrackType.VIDEO -> VideoClipColor
        TrackType.IMAGE -> EffectClipColor
        TrackType.AUDIO -> AudioClipColor
        TrackType.OVERLAY -> OverlayClipColor
        TrackType.TEXT -> TextClipColor
    }

    var moveOffsetPx by remember(clip.id) { mutableFloatStateOf(0f) }
    var trimStartOffsetPx by remember(clip.id) { mutableFloatStateOf(0f) }
    var trimEndOffsetPx by remember(clip.id) { mutableFloatStateOf(0f) }

    val minWidthPx = (MIN_CLIP_DURATION_MS * pxPerMs).coerceAtLeast(24f)
    val displayStartPx = startPx + moveOffsetPx + trimStartOffsetPx
    val displayWidthPx = (widthPx - trimStartOffsetPx + trimEndOffsetPx).coerceAtLeast(minWidthPx)
    val offsetDp = with(density) { displayStartPx.toDp() }
    val widthDp = with(density) { displayWidthPx.toDp() }

    Box(
        modifier = Modifier
            .offset(x = offsetDp)
            .width(widthDp.coerceAtLeast(4.dp))
            .height(trackHeight - 6.dp)
            .clip(RoundedCornerShape(CLIP_CORNER))
            .background(clipColor.copy(alpha = if (isSelected) 0.95f else 0.75f))
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = Color.White,
                        shape = RoundedCornerShape(CLIP_CORNER)
                    )
                } else {
                    Modifier
                }
            )
            .pointerInput(clip.id, widthPx, totalDurationMs) {
                detectTapGestures { offset ->
                    val clipDurationMs = (clip.endTimeMs - clip.startTimeMs).coerceAtLeast(1L)
                    val fraction = if (widthPx <= 0f) 0f else (offset.x / widthPx).coerceIn(0f, 1f)
                    val seekMs = clip.startTimeMs + (clipDurationMs * fraction).toLong()
                    onAction(TimelineEditorAction.ClipSelected(clip.id))
                    onSeekTo(seekMs.coerceIn(0L, totalDurationMs))
                }
            }
            .pointerInput(clip.id, pxPerMs) {
                detectDragGestures(
                    onDragStart = {
                        moveOffsetPx = 0f
                        onAction(TimelineEditorAction.ClipSelected(clip.id))
                    },
                    onDragEnd = {
                        val newStartMs = ((clip.startTimeMs * pxPerMs + moveOffsetPx) / pxPerMs).toLong()
                            .coerceAtLeast(0)
                        onAction(TimelineEditorAction.ClipMoved(clip.id, newStartMs))
                        moveOffsetPx = 0f
                    },
                    onDragCancel = { moveOffsetPx = 0f }
                ) { change, dragAmount ->
                    change.consume()
                    moveOffsetPx += dragAmount.x
                }
            }
    ) {
        when {
            trackType == TrackType.VIDEO || trackType == TrackType.IMAGE -> {
                MediaFilmstrip(
                    clip = clip,
                    mediaItem = mediaItem,
                    trackType = trackType
                )
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = clip.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(CLIP_HANDLE_WIDTH)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = if (isSelected) 0.65f else 0.28f))
                .pointerInput(clip.id, pxPerMs) {
                    detectDragGestures(
                        onDragStart = {
                            trimStartOffsetPx = 0f
                            onAction(TimelineEditorAction.ClipSelected(clip.id))
                        },
                        onDragEnd = {
                            val newStartMs = (clip.startTimeMs + (trimStartOffsetPx / pxPerMs).toLong())
                                .coerceAtLeast(0L)
                                .coerceAtMost(clip.endTimeMs - MIN_CLIP_DURATION_MS)
                            onAction(TimelineEditorAction.ClipTrimmed(clip.id, newStartMs, clip.endTimeMs))
                            trimStartOffsetPx = 0f
                        },
                        onDragCancel = { trimStartOffsetPx = 0f }
                    ) { change, dragAmount ->
                        change.consume()
                        val maxRightTrim = widthPx - minWidthPx
                        trimStartOffsetPx = (trimStartOffsetPx + dragAmount.x)
                            .coerceIn(-clip.startTimeMs * pxPerMs, maxRightTrim)
                    }
                }
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(CLIP_HANDLE_WIDTH)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = if (isSelected) 0.65f else 0.28f))
                .pointerInput(clip.id, pxPerMs, totalDurationMs) {
                    detectDragGestures(
                        onDragStart = {
                            trimEndOffsetPx = 0f
                            onAction(TimelineEditorAction.ClipSelected(clip.id))
                        },
                        onDragEnd = {
                            val newEndMs = (clip.endTimeMs + (trimEndOffsetPx / pxPerMs).toLong())
                                .coerceAtLeast(clip.startTimeMs + MIN_CLIP_DURATION_MS)
                                .coerceAtMost(totalDurationMs.coerceAtLeast(clip.startTimeMs + MIN_CLIP_DURATION_MS))
                            onAction(TimelineEditorAction.ClipTrimmed(clip.id, clip.startTimeMs, newEndMs))
                            trimEndOffsetPx = 0f
                        },
                        onDragCancel = { trimEndOffsetPx = 0f }
                    ) { change, dragAmount ->
                        change.consume()
                        val maxLeftTrim = -(widthPx - minWidthPx)
                        val maxRightTrim = (totalDurationMs - clip.endTimeMs).coerceAtLeast(0L) * pxPerMs
                        trimEndOffsetPx = (trimEndOffsetPx + dragAmount.x)
                            .coerceIn(maxLeftTrim, maxRightTrim)
                    }
                }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = clip.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "%.1fs".format((clip.endTimeMs - clip.startTimeMs) / 1000f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun MediaFilmstrip(
    clip: TimelineClipState,
    mediaItem: MediaItemEntity?,
    trackType: TrackType
) {
    if (mediaItem == null) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val context = LocalContext.current
    val frameCount = (((clip.endTimeMs - clip.startTimeMs) * BASE_PX_PER_MS) / 56f)
        .roundToInt()
        .coerceIn(1, 8)
    val frames by rememberFilmstripFrames(
        context = context,
        mediaItem = mediaItem,
        clip = clip,
        frameCount = frameCount,
        isImage = trackType == TrackType.IMAGE
    )

    if (frames.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
        return
    }

    Row(modifier = Modifier.fillMaxSize()) {
        frames.forEach { bitmap ->
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color.Black.copy(alpha = 0.2f))
                )
            }
        }
    }
}

@Composable
private fun rememberFilmstripFrames(
    context: android.content.Context,
    mediaItem: MediaItemEntity,
    clip: TimelineClipState,
    frameCount: Int,
    isImage: Boolean
) = produceState<List<ImageBitmap?>>(initialValue = emptyList(), context, mediaItem.id, clip.id, frameCount, clip.trimStartMs, clip.trimEndMs) {
    val uri = Uri.fromFile(File(mediaItem.localCopyPath))
    val sourceDurationMs = mediaItem.durationMs
    val visibleDurationMs = when {
        clip.trimEndMs > clip.trimStartMs -> clip.trimEndMs - clip.trimStartMs
        sourceDurationMs != null && sourceDurationMs > 0L -> {
            (clip.endTimeMs - clip.startTimeMs).coerceAtMost(sourceDurationMs)
        }
        else -> clip.endTimeMs - clip.startTimeMs
    }.coerceAtLeast(1L)

    value = if (isImage) {
        val image = ThumbnailGenerator.generateImageThumbnail(context, uri)?.asImageBitmap()
        List(frameCount) { image }
    } else {
        (0 until frameCount).map { index ->
            val fraction = if (frameCount == 1) 0f else index.toFloat() / (frameCount - 1)
            val frameTimeMs = clip.trimStartMs + (visibleDurationMs * fraction).toLong()
            ThumbnailGenerator.generateVideoThumbnail(context, uri, frameTimeMs)?.asImageBitmap()
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
            imageVector = Icons.Outlined.Videocam,
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
    TrackType.VIDEO -> Icons.Outlined.Videocam
    TrackType.IMAGE -> Icons.Outlined.Image
    TrackType.AUDIO -> Icons.Outlined.Audiotrack
    TrackType.OVERLAY -> Icons.Outlined.Layers
    TrackType.TEXT -> Icons.Outlined.TextFields
}

private fun formatRulerTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
