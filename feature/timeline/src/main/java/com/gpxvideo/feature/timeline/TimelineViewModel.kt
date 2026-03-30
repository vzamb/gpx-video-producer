package com.gpxvideo.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpxvideo.core.database.dao.TimelineClipDao
import com.gpxvideo.core.database.dao.TimelineTrackDao
import com.gpxvideo.core.database.entity.TimelineClipEntity
import com.gpxvideo.core.database.entity.TimelineTrackEntity
import com.gpxvideo.core.model.TrackType
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

@HiltViewModel(assistedFactory = TimelineViewModel.Factory::class)
class TimelineViewModel @AssistedInject constructor(
    @Assisted private val projectIdStr: String,
    private val trackDao: TimelineTrackDao,
    private val clipDao: TimelineClipDao
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(projectId: String): TimelineViewModel
    }

    private val projectId: UUID = UUID.fromString(projectIdStr)

    private val _state = MutableStateFlow(TimelineState())
    val state: StateFlow<TimelineState> = _state.asStateFlow()

    private val undoManager = UndoManager()

    init {
        loadTimeline()
    }

    private fun loadTimeline() {
        viewModelScope.launch {
            trackDao.getByProjectId(projectId).collect { trackEntities ->
                val trackStates = trackEntities.map { entity ->
                    val trackType = TrackType.valueOf(entity.type)
                    val clips = clipDao.getByTrackId(entity.id).first()
                    TimelineTrackState(
                        id = entity.id,
                        type = trackType,
                        label = trackType.toLabel(),
                        order = entity.order,
                        isLocked = entity.isLocked,
                        isVisible = entity.isVisible,
                        clips = clips.map { it.toClipState(trackType) }
                    )
                }
                val maxEnd = trackStates.flatMap { it.clips }.maxOfOrNull { it.endTimeMs } ?: 0L
                _state.value = _state.value.copy(
                    tracks = trackStates,
                    totalDurationMs = maxEnd,
                    canUndo = undoManager.canUndo,
                    canRedo = undoManager.canRedo
                )
            }
        }
    }

    fun addClipToTrack(trackId: UUID, mediaItemId: UUID?, startTimeMs: Long, durationMs: Long) {
        val track = _state.value.tracks.find { it.id == trackId } ?: return
        if (track.isLocked) return

        val clip = TimelineClipState(
            id = UUID.randomUUID(),
            trackId = trackId,
            mediaItemId = mediaItemId,
            label = "Clip",
            startTimeMs = snapToGrid(startTimeMs),
            endTimeMs = snapToGrid(startTimeMs + durationMs),
            color = track.type.toColor()
        )
        executeAction(TimelineAction.AddClip(clip, trackId))
    }

    fun moveClip(clipId: UUID, newStartTimeMs: Long) {
        val (clip, track) = findClipAndTrack(clipId) ?: return
        if (track.isLocked) return

        val snappedStart = snapToGrid(newStartTimeMs)
        val duration = clip.endTimeMs - clip.startTimeMs
        val action = TimelineAction.MoveClip(
            clipId = clipId,
            trackId = track.id,
            oldStartMs = clip.startTimeMs,
            oldEndMs = clip.endTimeMs,
            newStartMs = snappedStart.coerceAtLeast(0),
            newEndMs = (snappedStart + duration).coerceAtLeast(duration)
        )
        executeAction(action)
    }

    fun trimClip(clipId: UUID, newStartMs: Long, newEndMs: Long) {
        val (clip, track) = findClipAndTrack(clipId) ?: return
        if (track.isLocked) return
        if (newEndMs <= newStartMs) return

        val action = TimelineAction.TrimClip(
            clipId = clipId,
            oldStart = clip.startTimeMs,
            oldEnd = clip.endTimeMs,
            oldTrimStart = clip.trimStartMs,
            oldTrimEnd = clip.trimEndMs,
            newStart = snapToGrid(newStartMs).coerceAtLeast(0),
            newEnd = snapToGrid(newEndMs),
            newTrimStart = clip.trimStartMs + (snapToGrid(newStartMs) - clip.startTimeMs)
                .coerceAtLeast(0),
            newTrimEnd = clip.trimEndMs + (clip.endTimeMs - snapToGrid(newEndMs))
                .coerceAtLeast(0)
        )
        executeAction(action)
    }

    fun splitClipAtPlayhead(clipId: UUID) {
        val (clip, track) = findClipAndTrack(clipId) ?: return
        if (track.isLocked) return

        val playhead = _state.value.playheadPositionMs
        if (playhead <= clip.startTimeMs || playhead >= clip.endTimeMs) return

        val firstClip = clip.copy(
            id = UUID.randomUUID(),
            endTimeMs = playhead,
            trimEndMs = clip.trimEndMs + (clip.endTimeMs - playhead)
        )
        val secondClip = clip.copy(
            id = UUID.randomUUID(),
            startTimeMs = playhead,
            trimStartMs = clip.trimStartMs + (playhead - clip.startTimeMs)
        )
        executeAction(
            TimelineAction.SplitClip(
                originalClip = clip,
                firstClip = firstClip,
                secondClip = secondClip,
                trackId = track.id
            )
        )
    }

    fun deleteClip(clipId: UUID) {
        val (clip, track) = findClipAndTrack(clipId) ?: return
        if (track.isLocked) return
        executeAction(TimelineAction.RemoveClip(clip, track.id))
    }

    fun duplicateClip(clipId: UUID) {
        val (clip, track) = findClipAndTrack(clipId) ?: return
        if (track.isLocked) return

        val duplicate = clip.copy(
            id = UUID.randomUUID(),
            startTimeMs = clip.endTimeMs,
            endTimeMs = clip.endTimeMs + (clip.endTimeMs - clip.startTimeMs)
        )
        executeAction(TimelineAction.AddClip(duplicate, track.id))
    }

    fun addTrack(type: TrackType) {
        val maxOrder = _state.value.tracks.maxOfOrNull { it.order } ?: -1
        val track = TimelineTrackState(
            id = UUID.randomUUID(),
            type = type,
            label = type.toLabel(),
            order = maxOrder + 1,
            clips = emptyList()
        )
        executeAction(TimelineAction.AddTrack(track))
    }

    fun deleteTrack(trackId: UUID) {
        val track = _state.value.tracks.find { it.id == trackId } ?: return
        executeAction(TimelineAction.RemoveTrack(track))
    }

    fun reorderTrack(trackId: UUID, newOrder: Int) {
        val track = _state.value.tracks.find { it.id == trackId } ?: return
        val previousOrders = _state.value.tracks.associate { it.id to it.order }
        executeAction(
            TimelineAction.ReorderTrack(
                trackId = trackId,
                oldOrder = track.order,
                newOrder = newOrder,
                previousOrders = previousOrders
            )
        )
    }

    fun toggleTrackVisibility(trackId: UUID) {
        executeAction(TimelineAction.ToggleTrackVisibility(trackId))
    }

    fun toggleTrackLock(trackId: UUID) {
        executeAction(TimelineAction.ToggleTrackLock(trackId))
    }

    fun setPlayheadPosition(ms: Long) {
        _state.value = _state.value.copy(
            playheadPositionMs = ms.coerceIn(0, _state.value.totalDurationMs.coerceAtLeast(1))
        )
    }

    fun setZoomLevel(level: Float) {
        _state.value = _state.value.copy(
            zoomLevel = level.coerceIn(0.25f, 8.0f)
        )
    }

    fun togglePlayback() {
        _state.value = _state.value.copy(isPlaying = !_state.value.isPlaying)
    }

    fun selectClip(clipId: UUID?) {
        _state.value = _state.value.copy(selectedClipId = clipId)
    }

    fun selectTrack(trackId: UUID?) {
        _state.value = _state.value.copy(selectedTrackId = trackId)
    }

    fun undo() {
        undoManager.undo(_state.value)?.let { newState ->
            _state.value = newState.copy(
                canUndo = undoManager.canUndo,
                canRedo = undoManager.canRedo
            )
            persistState()
        }
    }

    fun redo() {
        undoManager.redo(_state.value)?.let { newState ->
            _state.value = newState.copy(
                canUndo = undoManager.canUndo,
                canRedo = undoManager.canRedo
            )
            persistState()
        }
    }

    private fun executeAction(action: TimelineAction) {
        val newState = undoManager.execute(action, _state.value)
        _state.value = newState.copy(
            canUndo = undoManager.canUndo,
            canRedo = undoManager.canRedo
        )
        persistState()
    }

    private fun persistState() {
        viewModelScope.launch {
            val currentState = _state.value
            for (track in currentState.tracks) {
                trackDao.insert(
                    TimelineTrackEntity(
                        id = track.id,
                        projectId = projectId,
                        type = track.type.name,
                        order = track.order,
                        isLocked = track.isLocked,
                        isVisible = track.isVisible
                    )
                )
                for (clip in track.clips) {
                    clipDao.insert(clip.toEntity())
                }
            }
        }
    }

    private fun findClipAndTrack(clipId: UUID): Pair<TimelineClipState, TimelineTrackState>? {
        for (track in _state.value.tracks) {
            val clip = track.clips.find { it.id == clipId }
            if (clip != null) return clip to track
        }
        return null
    }

    companion object {
        private const val SNAP_GRID_MS = 100L

        fun snapToGrid(ms: Long): Long {
            return ((ms + SNAP_GRID_MS / 2) / SNAP_GRID_MS) * SNAP_GRID_MS
        }
    }
}

private fun TimelineClipEntity.toClipState(trackType: TrackType): TimelineClipState {
    return TimelineClipState(
        id = id,
        trackId = trackId,
        mediaItemId = mediaItemId,
        label = "Clip",
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        color = trackType.toColor()
    )
}

private fun TimelineClipState.toEntity(): TimelineClipEntity {
    return TimelineClipEntity(
        id = id,
        trackId = trackId,
        mediaItemId = mediaItemId,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs
    )
}
