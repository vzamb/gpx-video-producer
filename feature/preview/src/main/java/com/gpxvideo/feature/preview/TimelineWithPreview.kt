package com.gpxvideo.feature.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpxvideo.feature.timeline.TimelineEditor
import com.gpxvideo.feature.timeline.TimelineEditorAction
import com.gpxvideo.feature.timeline.TimelineViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.UUID

@Composable
fun TimelineWithPreview(
    projectId: UUID,
    modifier: Modifier = Modifier
) {
    val previewVm = hiltViewModel<PreviewViewModel, PreviewViewModel.Factory>(
        key = "preview-$projectId",
        creationCallback = { factory -> factory.create(projectId.toString()) }
    )
    val timelineVm = hiltViewModel<TimelineViewModel, TimelineViewModel.Factory>(
        key = "timeline-$projectId",
        creationCallback = { factory -> factory.create(projectId.toString()) }
    )

    val timelineState by timelineVm.state.collectAsStateWithLifecycle()
    val playerPosition by previewVm.currentPositionMs.collectAsStateWithLifecycle()
    val playerIsPlaying by previewVm.isPlaying.collectAsStateWithLifecycle()

    // Sync player position → timeline playhead while playing
    LaunchedEffect(Unit) {
        snapshotFlow { playerPosition to playerIsPlaying }
            .distinctUntilChanged()
            .collect { (position, playing) ->
                if (playing) {
                    timelineVm.setPlayheadPosition(position)
                }
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        VideoPreview(
            previewEngine = previewVm.previewEngine,
            modifier = Modifier.weight(0.4f)
        )

        TimelineEditor(
            state = timelineState.copy(
                isPlaying = playerIsPlaying,
                playheadPositionMs = if (playerIsPlaying) playerPosition else timelineState.playheadPositionMs
            ),
            onAction = { action ->
                when (action) {
                    is TimelineEditorAction.PlayheadMoved -> {
                        timelineVm.setPlayheadPosition(action.positionMs)
                        previewVm.seekTo(action.positionMs)
                    }
                    TimelineEditorAction.PlayPauseToggled -> {
                        previewVm.togglePlayback()
                    }
                    is TimelineEditorAction.ClipSelected -> timelineVm.selectClip(action.clipId)
                    is TimelineEditorAction.ClipMoved ->
                        timelineVm.moveClip(action.clipId, action.newStartMs)
                    is TimelineEditorAction.ClipTrimmed ->
                        timelineVm.trimClip(action.clipId, action.newStartMs, action.newEndMs)
                    is TimelineEditorAction.ZoomChanged -> timelineVm.setZoomLevel(action.level)
                    TimelineEditorAction.UndoRequested -> timelineVm.undo()
                    TimelineEditorAction.RedoRequested -> timelineVm.redo()
                    is TimelineEditorAction.TrackAdded -> timelineVm.addTrack(action.type)
                    is TimelineEditorAction.TrackDeleted -> timelineVm.deleteTrack(action.trackId)
                    is TimelineEditorAction.TrackVisibilityToggled ->
                        timelineVm.toggleTrackVisibility(action.trackId)
                    is TimelineEditorAction.TrackLockToggled ->
                        timelineVm.toggleTrackLock(action.trackId)
                    is TimelineEditorAction.ClipDeleted -> timelineVm.deleteClip(action.clipId)
                    is TimelineEditorAction.ClipDuplicated ->
                        timelineVm.duplicateClip(action.clipId)
                    is TimelineEditorAction.ClipSplit ->
                        timelineVm.splitClipAtPlayhead(action.clipId)
                    is TimelineEditorAction.SpeedChanged ->
                        timelineVm.setClipSpeed(action.clipId, action.speed)
                    is TimelineEditorAction.VolumeChanged ->
                        timelineVm.setClipVolume(action.clipId, action.volume)
                    is TimelineEditorAction.EntryTransitionChanged ->
                        timelineVm.setClipEntryTransition(
                            action.clipId,
                            action.transition?.type?.name,
                            action.transition?.durationMs
                        )
                    is TimelineEditorAction.ExitTransitionChanged ->
                        timelineVm.setClipExitTransition(
                            action.clipId,
                            action.transition?.type?.name,
                            action.transition?.durationMs
                        )
                    is TimelineEditorAction.KenBurnsChanged ->
                        timelineVm.setClipKenBurns(
                            action.clipId,
                            action.config.startX, action.config.startY, action.config.startScale,
                            action.config.endX, action.config.endY, action.config.endScale
                        )
                }
            },
            modifier = Modifier.weight(0.6f)
        )
    }
}
