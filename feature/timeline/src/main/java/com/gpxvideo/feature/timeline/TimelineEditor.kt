package com.gpxvideo.feature.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.gpxvideo.core.model.TrackType
import com.gpxvideo.core.model.Transition
import java.util.UUID
import kotlin.math.roundToInt

const val BASE_PX_PER_MS = 0.1f
private val TRACK_ROW_HEIGHT = 56.dp
private val RULER_HEIGHT_DP = 28.dp

sealed interface TimelineEditorAction {
    data class ClipSelected(val clipId: UUID?) : TimelineEditorAction
    data class ClipMoved(val clipId: UUID, val newStartMs: Long) : TimelineEditorAction
    data class ClipTrimmed(val clipId: UUID, val newStartMs: Long, val newEndMs: Long) :
        TimelineEditorAction
    data class PlayheadMoved(val positionMs: Long) : TimelineEditorAction
    data class ZoomChanged(val level: Float) : TimelineEditorAction
    data object PlayPauseToggled : TimelineEditorAction
    data object UndoRequested : TimelineEditorAction
    data object RedoRequested : TimelineEditorAction
    data class TrackAdded(val type: TrackType) : TimelineEditorAction
    data class TrackDeleted(val trackId: UUID) : TimelineEditorAction
    data class TrackVisibilityToggled(val trackId: UUID) : TimelineEditorAction
    data class TrackLockToggled(val trackId: UUID) : TimelineEditorAction
    data class ClipDeleted(val clipId: UUID) : TimelineEditorAction
    data class ClipDuplicated(val clipId: UUID) : TimelineEditorAction
    data class ClipSplit(val clipId: UUID) : TimelineEditorAction
    data class SpeedChanged(val clipId: UUID, val speed: Float) : TimelineEditorAction
    data class VolumeChanged(val clipId: UUID, val volume: Float) : TimelineEditorAction
    data class EntryTransitionChanged(val clipId: UUID, val transition: Transition?) :
        TimelineEditorAction
    data class ExitTransitionChanged(val clipId: UUID, val transition: Transition?) :
        TimelineEditorAction
    data class KenBurnsChanged(val clipId: UUID, val config: KenBurnsConfig) :
        TimelineEditorAction
}

@Composable
fun TimelineEditor(
    state: TimelineState,
    onAction: (TimelineEditorAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val pxPerMs = state.zoomLevel * BASE_PX_PER_MS
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()
    val density = LocalDensity.current

    Column(modifier = modifier.fillMaxSize()) {
        // Playback controls
        PlaybackControls(
            currentPositionMs = state.playheadPositionMs,
            totalDurationMs = state.totalDurationMs,
            isPlaying = state.isPlaying,
            canUndo = state.canUndo,
            canRedo = state.canRedo,
            onPlayPause = { onAction(TimelineEditorAction.PlayPauseToggled) },
            onSeek = { onAction(TimelineEditorAction.PlayheadMoved(it)) },
            onUndo = { onAction(TimelineEditorAction.UndoRequested) },
            onRedo = { onAction(TimelineEditorAction.RedoRequested) }
        )

        // Zoom slider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Zoom",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(end = 8.dp)
            )
            Slider(
                value = state.zoomLevel,
                onValueChange = { onAction(TimelineEditorAction.ZoomChanged(it)) },
                valueRange = 0.25f..8.0f,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "%.1fx".format(state.zoomLevel),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Timeline content area: ruler + track headers + track rows
        val timelineContentWidth = with(density) {
            ((state.totalDurationMs + 10000) * pxPerMs).toDp()
        }

        Column(modifier = Modifier.weight(1f)) {
            // Ruler row
            Row(modifier = Modifier.fillMaxWidth()) {
                // Header spacer
                Spacer(
                    modifier = Modifier
                        .width(TRACK_HEADER_WIDTH)
                        .height(RULER_HEIGHT_DP)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                )
                // Ruler
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScrollState)
                ) {
                    TimelineRuler(
                        totalDurationMs = state.totalDurationMs,
                        zoomLevel = state.zoomLevel,
                        pxPerMs = pxPerMs,
                        scrollOffset = 0f,
                        modifier = Modifier.width(timelineContentWidth)
                    )
                }
            }

            // Tracks area
            Box(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Track headers (fixed, scrolls vertically with tracks)
                    Column(
                        modifier = Modifier
                            .width(TRACK_HEADER_WIDTH)
                            .fillMaxHeight()
                            .verticalScroll(verticalScrollState)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        state.tracks.forEach { track ->
                            TrackHeader(
                                track = track,
                                isSelected = track.id == state.selectedTrackId,
                                onToggleVisibility = {
                                    onAction(TimelineEditorAction.TrackVisibilityToggled(track.id))
                                },
                                onToggleLock = {
                                    onAction(TimelineEditorAction.TrackLockToggled(track.id))
                                },
                                onDelete = {
                                    onAction(TimelineEditorAction.TrackDeleted(track.id))
                                }
                            )
                        }
                    }

                    // Timeline track content (scrolls both directions)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .horizontalScroll(horizontalScrollState)
                                .verticalScroll(verticalScrollState)
                        ) {
                            state.tracks.forEach { track ->
                                TimelineTrackRow(
                                    track = track,
                                    zoomLevel = state.zoomLevel,
                                    pxPerMs = pxPerMs,
                                    selectedClipId = state.selectedClipId,
                                    onClipSelected = { clipId ->
                                        onAction(TimelineEditorAction.ClipSelected(clipId))
                                    },
                                    onClipMoved = { clipId, newStart ->
                                        onAction(
                                            TimelineEditorAction.ClipMoved(clipId, newStart)
                                        )
                                    },
                                    onClipTrimmed = { clipId, newStart, newEnd ->
                                        onAction(
                                            TimelineEditorAction.ClipTrimmed(
                                                clipId, newStart, newEnd
                                            )
                                        )
                                    },
                                    modifier = Modifier.width(timelineContentWidth)
                                )
                            }
                        }

                        // Playhead line
                        val playheadOffsetPx =
                            (state.playheadPositionMs * pxPerMs) -
                                    horizontalScrollState.value.toFloat()

                        if (playheadOffsetPx >= 0) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(2.dp)
                                    .offset { IntOffset(playheadOffsetPx.roundToInt(), 0) }
                            ) {
                                drawLine(
                                    color = Color.Red,
                                    start = Offset(size.width / 2, 0f),
                                    end = Offset(size.width / 2, size.height),
                                    strokeWidth = 2f
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add Track button
        AddTrackButton(onTrackTypeSelected = { type ->
            onAction(TimelineEditorAction.TrackAdded(type))
        })

        // Clip properties panel (shown when a clip is selected)
        val selectedClip = state.selectedClipId?.let { clipId ->
            state.tracks.flatMap { it.clips }.find { it.id == clipId }
        }
        val selectedTrackType = state.selectedClipId?.let { clipId ->
            state.tracks.find { track -> track.clips.any { it.id == clipId } }?.type
        }
        val hasAdjacentClips = selectedClip?.let { clip ->
            val track = state.tracks.find { t -> t.clips.any { it.id == clip.id } }
            track != null && track.clips.size > 1
        } ?: false

        if (selectedClip != null) {
            ClipPropertiesPanel(
                clip = selectedClip,
                trackType = selectedTrackType ?: TrackType.VIDEO,
                hasAdjacentClips = hasAdjacentClips,
                entryTransition = null,
                exitTransition = null,
                waveformData = emptyList(),
                onSpeedChange = {
                    onAction(TimelineEditorAction.SpeedChanged(selectedClip.id, it))
                },
                onVolumeChange = {
                    onAction(TimelineEditorAction.VolumeChanged(selectedClip.id, it))
                },
                onEntryTransitionChanged = {
                    onAction(TimelineEditorAction.EntryTransitionChanged(selectedClip.id, it))
                },
                onExitTransitionChanged = {
                    onAction(TimelineEditorAction.ExitTransitionChanged(selectedClip.id, it))
                },
                onKenBurnsConfigChanged = if (selectedTrackType == TrackType.IMAGE) { config ->
                    onAction(TimelineEditorAction.KenBurnsChanged(selectedClip.id, config))
                } else null,
                onDelete = { onAction(TimelineEditorAction.ClipDeleted(selectedClip.id)) },
                onDuplicate = {
                    onAction(TimelineEditorAction.ClipDuplicated(selectedClip.id))
                },
                onSplit = { onAction(TimelineEditorAction.ClipSplit(selectedClip.id)) }
            )
        }
    }
}

@Composable
private fun AddTrackButton(
    onTrackTypeSelected: (TrackType) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        SmallFloatingActionButton(
            onClick = { showMenu = true },
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "Add track"
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            TrackType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.toLabel()) },
                    onClick = {
                        onTrackTypeSelected(type)
                        showMenu = false
                    }
                )
            }
        }
    }
}
