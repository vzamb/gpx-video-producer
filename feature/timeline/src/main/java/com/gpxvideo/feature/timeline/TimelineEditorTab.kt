package com.gpxvideo.feature.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.UUID

@Composable
fun TimelineEditorTab(
    projectId: UUID,
    modifier: Modifier = Modifier
) {
    val viewModel = hiltViewModel<TimelineViewModel, TimelineViewModel.Factory>(
        key = projectId.toString(),
        creationCallback = { factory -> factory.create(projectId.toString()) }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    TimelineEditor(
        state = state,
        onAction = { action ->
            when (action) {
                is TimelineEditorAction.ClipSelected -> viewModel.selectClip(action.clipId)
                is TimelineEditorAction.ClipMoved -> viewModel.moveClip(
                    action.clipId, action.newStartMs
                )
                is TimelineEditorAction.ClipTrimmed -> viewModel.trimClip(
                    action.clipId, action.newStartMs, action.newEndMs
                )
                is TimelineEditorAction.PlayheadMoved -> viewModel.setPlayheadPosition(
                    action.positionMs
                )
                is TimelineEditorAction.ZoomChanged -> viewModel.setZoomLevel(action.level)
                TimelineEditorAction.PlayPauseToggled -> viewModel.togglePlayback()
                TimelineEditorAction.UndoRequested -> viewModel.undo()
                TimelineEditorAction.RedoRequested -> viewModel.redo()
                is TimelineEditorAction.TrackAdded -> viewModel.addTrack(action.type)
                is TimelineEditorAction.TrackDeleted -> viewModel.deleteTrack(action.trackId)
                is TimelineEditorAction.TrackVisibilityToggled -> viewModel.toggleTrackVisibility(
                    action.trackId
                )
                is TimelineEditorAction.TrackLockToggled -> viewModel.toggleTrackLock(
                    action.trackId
                )
                is TimelineEditorAction.ClipDeleted -> viewModel.deleteClip(action.clipId)
                is TimelineEditorAction.ClipDuplicated -> viewModel.duplicateClip(action.clipId)
                is TimelineEditorAction.ClipSplit -> viewModel.splitClipAtPlayhead(action.clipId)
                is TimelineEditorAction.SpeedChanged -> { /* TODO: adjust speed */ }
                is TimelineEditorAction.VolumeChanged -> { /* TODO: adjust volume */ }
                is TimelineEditorAction.EntryTransitionChanged -> { /* TODO: adjust entry transition */ }
                is TimelineEditorAction.ExitTransitionChanged -> { /* TODO: adjust exit transition */ }
                is TimelineEditorAction.KenBurnsChanged -> { /* TODO: adjust ken burns config */ }
            }
        },
        modifier = modifier
    )
}
