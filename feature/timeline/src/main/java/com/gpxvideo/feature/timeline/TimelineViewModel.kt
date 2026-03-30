package com.gpxvideo.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpxvideo.core.database.dao.MediaItemDao
import com.gpxvideo.core.database.dao.TimelineClipDao
import com.gpxvideo.core.database.dao.TimelineTrackDao
import com.gpxvideo.core.database.entity.MediaItemEntity
import com.gpxvideo.core.database.entity.TimelineClipEntity
import com.gpxvideo.core.database.entity.TimelineTrackEntity
import com.gpxvideo.core.model.TrackType
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

@HiltViewModel(assistedFactory = TimelineViewModel.Factory::class)
class TimelineViewModel @AssistedInject constructor(
    @Assisted private val projectIdStr: String,
    private val trackDao: TimelineTrackDao,
    private val clipDao: TimelineClipDao,
    private val mediaItemDao: MediaItemDao
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(projectId: String): TimelineViewModel
    }

    private val projectId: UUID = UUID.fromString(projectIdStr)

    private val _state = MutableStateFlow(TimelineState())
    val state: StateFlow<TimelineState> = _state.asStateFlow()

    // Emits whenever timeline content changes so preview can reload
    private val _timelineChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val timelineChanged: SharedFlow<Unit> = _timelineChanged.asSharedFlow()

    private val undoManager = UndoManager()

    init {
        loadTimeline()
        watchMediaItems()
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

    /**
     * Watches media items and creates timeline clips for new imports.
     * - If no tracks exist: creates VIDEO+AUDIO tracks and clips for all media.
     * - If tracks exist: appends clips for any media not yet in the timeline.
     */
    private fun watchMediaItems() {
        viewModelScope.launch {
            mediaItemDao.getByProjectId(projectId).collect { mediaItems ->
                if (mediaItems.isEmpty()) return@collect

                val existingTracks = trackDao.getByProjectId(projectId).first()
                if (existingTracks.isEmpty()) {
                    initializeTimeline(mediaItems)
                } else {
                    addNewMediaToTimeline(mediaItems, existingTracks)
                }
            }
        }
    }

    private suspend fun initializeTimeline(mediaItems: List<MediaItemEntity>) {
        val videoTrackId = UUID.randomUUID()
        val audioTrackId = UUID.randomUUID()
        trackDao.insert(
            TimelineTrackEntity(
                id = videoTrackId,
                projectId = projectId,
                type = TrackType.VIDEO.name,
                order = 0
            )
        )
        trackDao.insert(
            TimelineTrackEntity(
                id = audioTrackId,
                projectId = projectId,
                type = TrackType.AUDIO.name,
                order = 1
            )
        )

        var currentTimeMs = 0L
        for (media in mediaItems) {
            val durationMs = getMediaDuration(media)
            clipDao.insert(
                TimelineClipEntity(
                    id = UUID.randomUUID(),
                    trackId = videoTrackId,
                    mediaItemId = media.id,
                    startTimeMs = currentTimeMs,
                    endTimeMs = currentTimeMs + durationMs
                )
            )
            currentTimeMs += durationMs
        }
        _timelineChanged.tryEmit(Unit)
    }

    private suspend fun addNewMediaToTimeline(
        mediaItems: List<MediaItemEntity>,
        existingTracks: List<TimelineTrackEntity>
    ) {
        val videoTrack = existingTracks.find { it.type == TrackType.VIDEO.name } ?: return

        val existingClips = clipDao.getByTrackId(videoTrack.id).first()
        val existingMediaIds = existingClips.mapNotNull { it.mediaItemId }.toSet()
        val newMedia = mediaItems.filter { it.id !in existingMediaIds }
        if (newMedia.isEmpty()) return

        var currentEnd = existingClips.maxOfOrNull { it.endTimeMs } ?: 0L
        for (media in newMedia) {
            val durationMs = getMediaDuration(media)
            clipDao.insert(
                TimelineClipEntity(
                    id = UUID.randomUUID(),
                    trackId = videoTrack.id,
                    mediaItemId = media.id,
                    startTimeMs = currentEnd,
                    endTimeMs = currentEnd + durationMs
                )
            )
            currentEnd += durationMs
        }
        _timelineChanged.tryEmit(Unit)
    }

    private fun getMediaDuration(media: MediaItemEntity): Long {
        val mediaDuration = media.durationMs
        return when {
            media.type == "VIDEO" && mediaDuration != null && mediaDuration > 0 -> mediaDuration
            media.type == "IMAGE" -> 5000L
            else -> 3000L
        }
    }

    fun addClipToTrack(trackId: UUID, mediaItemId: UUID?, startTimeMs: Long, durationMs: Long, label: String = "Clip") {
        val track = _state.value.tracks.find { it.id == trackId } ?: return
        if (track.isLocked) return

        val clip = TimelineClipState(
            id = UUID.randomUUID(),
            trackId = trackId,
            mediaItemId = mediaItemId,
            label = label,
            startTimeMs = snapToGrid(startTimeMs),
            endTimeMs = snapToGrid(startTimeMs + durationMs),
            color = track.type.toColor()
        )
        executeAction(TimelineAction.AddClip(clip, trackId))
        _timelineChanged.tryEmit(Unit)
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

    /**
     * Creates an overlay track and clip spanning the given time range.
     */
    fun addOverlayToTimeline(overlayType: String, displayName: String) {
        val maxOrder = _state.value.tracks.maxOfOrNull { it.order } ?: -1
        val trackId = UUID.randomUUID()
        val track = TimelineTrackState(
            id = trackId,
            type = TrackType.OVERLAY,
            label = displayName,
            order = maxOrder + 1,
            clips = emptyList()
        )
        executeAction(TimelineAction.AddTrack(track))

        val duration = _state.value.totalDurationMs.coerceAtLeast(5000L)
        val clip = TimelineClipState(
            id = UUID.randomUUID(),
            trackId = trackId,
            mediaItemId = null,
            label = displayName,
            startTimeMs = 0L,
            endTimeMs = duration,
            color = TrackType.OVERLAY.toColor()
        )
        executeAction(TimelineAction.AddClip(clip, trackId))
        _timelineChanged.tryEmit(Unit)
    }

    /**
     * Creates a text track and clip spanning the given time range.
     */
    fun addTextToTimeline(text: String) {
        val maxOrder = _state.value.tracks.maxOfOrNull { it.order } ?: -1
        val trackId = UUID.randomUUID()
        val track = TimelineTrackState(
            id = trackId,
            type = TrackType.TEXT,
            label = "Text",
            order = maxOrder + 1,
            clips = emptyList()
        )
        executeAction(TimelineAction.AddTrack(track))

        val duration = _state.value.totalDurationMs.coerceAtLeast(5000L)
        val clip = TimelineClipState(
            id = UUID.randomUUID(),
            trackId = trackId,
            mediaItemId = null,
            label = text.take(20),
            startTimeMs = 0L,
            endTimeMs = duration,
            color = TrackType.TEXT.toColor()
        )
        executeAction(TimelineAction.AddClip(clip, trackId))
        _timelineChanged.tryEmit(Unit)
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

    fun setClipSpeed(clipId: UUID, speed: Float) {
        updateClipProperty(clipId) { it.copy(speed = speed.coerceIn(0.25f, 4.0f)) }
    }

    fun setClipVolume(clipId: UUID, volume: Float) {
        updateClipProperty(clipId) { it.copy(volume = volume.coerceIn(0f, 2.0f)) }
    }

    fun setClipEntryTransition(clipId: UUID, type: String?, durationMs: Long?) {
        updateClipProperty(clipId) { it.copy(entryTransitionType = type, entryTransitionDurationMs = durationMs) }
    }

    fun setClipExitTransition(clipId: UUID, type: String?, durationMs: Long?) {
        updateClipProperty(clipId) { it.copy(exitTransitionType = type, exitTransitionDurationMs = durationMs) }
    }

    fun setClipKenBurns(
        clipId: UUID,
        startX: Float, startY: Float, startScale: Float,
        endX: Float, endY: Float, endScale: Float
    ) {
        updateClipProperty(clipId) {
            it.copy(
                kenBurnsStartX = startX, kenBurnsStartY = startY, kenBurnsStartScale = startScale,
                kenBurnsEndX = endX, kenBurnsEndY = endY, kenBurnsEndScale = endScale
            )
        }
    }

    private fun updateClipProperty(clipId: UUID, transform: (TimelineClipState) -> TimelineClipState) {
        val currentState = _state.value
        val newTracks = currentState.tracks.map { track ->
            track.copy(clips = track.clips.map { clip ->
                if (clip.id == clipId) transform(clip) else clip
            })
        }
        _state.value = currentState.copy(tracks = newTracks)
        persistState()
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
            val allCurrentClipIds = mutableSetOf<UUID>()
            val allCurrentTrackIds = mutableSetOf<UUID>()

            for (track in currentState.tracks) {
                allCurrentTrackIds.add(track.id)
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
                    allCurrentClipIds.add(clip.id)
                    clipDao.insert(clip.toEntity())
                }
            }

            // Delete clips/tracks that were removed from state
            val dbTracks = trackDao.getByProjectId(projectId).first()
            for (dbTrack in dbTracks) {
                if (dbTrack.id !in allCurrentTrackIds) {
                    clipDao.deleteByTrackId(dbTrack.id)
                    trackDao.delete(dbTrack)
                } else {
                    val dbClips = clipDao.getByTrackId(dbTrack.id).first()
                    for (dbClip in dbClips) {
                        if (dbClip.id !in allCurrentClipIds) {
                            clipDao.deleteById(dbClip.id)
                        }
                    }
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
        color = trackType.toColor(),
        speed = speed,
        volume = volume,
        entryTransitionType = entryTransitionType,
        entryTransitionDurationMs = entryTransitionDurationMs,
        exitTransitionType = exitTransitionType,
        exitTransitionDurationMs = exitTransitionDurationMs
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
        trimEndMs = trimEndMs,
        speed = speed,
        volume = volume,
        entryTransitionType = entryTransitionType,
        entryTransitionDurationMs = entryTransitionDurationMs,
        exitTransitionType = exitTransitionType,
        exitTransitionDurationMs = exitTransitionDurationMs
    )
}
