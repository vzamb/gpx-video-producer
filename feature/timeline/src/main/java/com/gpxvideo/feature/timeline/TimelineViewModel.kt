package com.gpxvideo.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpxvideo.core.database.dao.MediaItemDao
import com.gpxvideo.core.database.dao.OverlayDao
import com.gpxvideo.core.database.dao.TimelineClipDao
import com.gpxvideo.core.database.dao.TimelineTrackDao
import com.gpxvideo.core.database.entity.MediaItemEntity
import com.gpxvideo.core.database.entity.TimelineClipEntity
import com.gpxvideo.core.database.entity.TimelineTrackEntity
import com.gpxvideo.core.model.MapStyle
import com.gpxvideo.core.model.DynamicField
import com.gpxvideo.core.model.OverlayConfig
import com.gpxvideo.core.model.OverlaySize
import com.gpxvideo.core.model.OverlayStyle
import com.gpxvideo.core.model.StatField
import com.gpxvideo.core.model.StatsLayout
import com.gpxvideo.core.model.SyncMode
import com.gpxvideo.core.model.TrackType
import com.gpxvideo.feature.overlays.OverlayFormatPreset
import com.gpxvideo.feature.overlays.OverlayRepository
import com.gpxvideo.feature.overlays.OverlayStylePreset
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
    private val mediaItemDao: MediaItemDao,
    private val overlayDao: OverlayDao,
    private val overlayRepository: OverlayRepository
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
        viewModelScope.launch {
            loadTimelineFromDatabase()
        }
        watchMediaItems()
    }

    private suspend fun loadTimelineFromDatabase() {
        val trackEntities = trackDao.getByProjectId(projectId).first()
        val clipsByTrackId = trackEntities.associate { entity ->
            entity.id to clipDao.getByTrackId(entity.id).first()
        }
        val overlayEntities = overlayDao.getByProjectId(projectId).first()
        val validClipIds = clipsByTrackId.values.flatten().mapTo(mutableSetOf()) { it.id }
        val staleTrackEntities = trackEntities.filter { entity ->
            val trackType = TrackType.valueOf(entity.type)
            trackType.isRemovableClipTrack() && clipsByTrackId[entity.id].isNullOrEmpty()
        }
        val orphanOverlayEntities = overlayEntities.filter { it.timelineClipId !in validClipIds }

        if (staleTrackEntities.isNotEmpty() || orphanOverlayEntities.isNotEmpty()) {
            cleanupStaleTimelineData(staleTrackEntities, orphanOverlayEntities)
        }

        val staleTrackIds = staleTrackEntities.mapTo(mutableSetOf()) { it.id }
        val orphanOverlayIds = orphanOverlayEntities.mapTo(mutableSetOf()) { it.id }
        val overlaysByClipId = overlayEntities
            .filterNot { it.id in orphanOverlayIds }
            .associateBy { it.timelineClipId }
        val trackStates = trackEntities
            .filterNot { it.id in staleTrackIds }
            .map { entity ->
            val trackType = TrackType.valueOf(entity.type)
            val clips = clipsByTrackId[entity.id].orEmpty()
            TimelineTrackState(
                id = entity.id,
                type = trackType,
                label = trackType.toLabel(),
                order = entity.order,
                isLocked = entity.isLocked,
                isVisible = entity.isVisible,
                clips = clips.map { clip ->
                    clip.toClipState(trackType, overlaysByClipId[clip.id]?.name)
                }
            )
        }
        val maxEnd = trackStates.flatMap { it.clips }.maxOfOrNull { it.endTimeMs } ?: 0L
        val currentState = _state.value
        _state.value = currentState.copy(
            tracks = trackStates,
            totalDurationMs = maxEnd,
            canUndo = undoManager.canUndo,
            canRedo = undoManager.canRedo
        )
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
        loadTimelineFromDatabase()
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
        loadTimelineFromDatabase()
    }

    private fun getMediaDuration(media: MediaItemEntity): Long {
        val mediaDuration = media.durationMs
        return when {
            media.type == "VIDEO" && mediaDuration != null && mediaDuration > 0 -> mediaDuration
            media.type == "IMAGE" -> 3000L
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
        if (track.type.isSequentialVisualTrack()) {
            normalizeTrackLayout(track.id)
        }
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
        if (track.type.isSequentialVisualTrack()) {
            normalizeTrackLayout(track.id)
        }
    }

    fun trimClip(clipId: UUID, newStartMs: Long, newEndMs: Long) {
        val (clip, track) = findClipAndTrack(clipId) ?: return
        if (track.isLocked) return
        if (newEndMs <= newStartMs) return

        val snappedStart = snapToGrid(newStartMs).coerceAtLeast(0)
        val snappedEnd = snapToGrid(newEndMs)

        val startDelta = snappedStart - clip.startTimeMs
        val endDelta = clip.endTimeMs - snappedEnd

        val action = TimelineAction.TrimClip(
            clipId = clipId,
            oldStart = clip.startTimeMs,
            oldEnd = clip.endTimeMs,
            oldTrimStart = clip.trimStartMs,
            oldTrimEnd = clip.trimEndMs,
            newStart = snappedStart,
            newEnd = snappedEnd,
            newTrimStart = (clip.trimStartMs + startDelta).coerceAtLeast(0),
            newTrimEnd = (clip.trimEndMs + endDelta).coerceAtLeast(0)
        )
        executeAction(action)
        if (track.type.isSequentialVisualTrack()) {
            normalizeTrackLayout(track.id)
        }
    }

    // ── Atomic trim drag (single undo step for entire gesture) ──────────

    private var trimDragSnapshot: TimelineClipState? = null

    fun beginTrimDrag(clipId: UUID) {
        trimDragSnapshot = _state.value.tracks.flatMap { it.clips }.find { it.id == clipId }
    }

    /** Update trim without pushing to undo stack — called on each drag frame. */
    fun trimClipDirect(clipId: UUID, newStartMs: Long, newEndMs: Long) {
        val currentState = _state.value
        val clip = currentState.tracks.flatMap { it.clips }.find { it.id == clipId } ?: return
        val track = currentState.tracks.find { t -> t.clips.any { it.id == clipId } } ?: return
        if (track.isLocked || newEndMs <= newStartMs) return

        val startDelta = newStartMs - clip.startTimeMs
        val endDelta = clip.endTimeMs - newEndMs

        val updatedClip = clip.copy(
            startTimeMs = newStartMs,
            endTimeMs = newEndMs,
            trimStartMs = (clip.trimStartMs + startDelta).coerceAtLeast(0),
            trimEndMs = (clip.trimEndMs + endDelta).coerceAtLeast(0)
        )

        var newTracks = currentState.tracks.map { t ->
            t.copy(clips = t.clips.map { c -> if (c.id == clipId) updatedClip else c }
                .sortedBy { it.startTimeMs })
        }
        if (track.type.isSequentialVisualTrack()) {
            newTracks = newTracks.map { t ->
                if (t.id == track.id) t.copy(clips = packSequential(t.clips)) else t
            }
        }
        val maxEnd = newTracks.flatMap { it.clips }.maxOfOrNull { it.endTimeMs } ?: 0L
        _state.value = currentState.copy(tracks = newTracks, totalDurationMs = maxEnd)
    }

    /** Commit the entire drag as a single undo action. */
    fun commitTrimDrag(clipId: UUID) {
        val initial = trimDragSnapshot ?: return
        trimDragSnapshot = null
        val current = _state.value.tracks.flatMap { it.clips }.find { it.id == clipId } ?: return
        if (initial.startTimeMs == current.startTimeMs &&
            initial.endTimeMs == current.endTimeMs &&
            initial.trimStartMs == current.trimStartMs &&
            initial.trimEndMs == current.trimEndMs) return

        val action = TimelineAction.TrimClip(
            clipId = clipId,
            oldStart = initial.startTimeMs,
            oldEnd = initial.endTimeMs,
            oldTrimStart = initial.trimStartMs,
            oldTrimEnd = initial.trimEndMs,
            newStart = current.startTimeMs,
            newEnd = current.endTimeMs,
            newTrimStart = current.trimStartMs,
            newTrimEnd = current.trimEndMs
        )
        undoManager.pushWithoutExecute(action)
        _state.value = _state.value.copy(
            canUndo = undoManager.canUndo,
            canRedo = undoManager.canRedo
        )
        persistState()
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
        if (track.type.isSequentialVisualTrack()) {
            normalizeTrackLayout(track.id)
        }
    }

    fun deleteClip(clipId: UUID) {
        val (clip, track) = findClipAndTrack(clipId) ?: return
        if (track.isLocked) return

        if (track.type.isRemovableClipTrack() && track.clips.size == 1) {
            deleteTrack(track.id)
            return
        }

        executeAction(TimelineAction.RemoveClip(clip, track.id))
        if (track.type.isSequentialVisualTrack()) {
            normalizeTrackLayout(track.id)
        }
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
        if (track.type.isSequentialVisualTrack()) {
            normalizeTrackLayout(track.id)
        }
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
        addOverlayToTimeline(
            overlayType = overlayType,
            displayName = displayName,
            stylePreset = OverlayStylePreset.CLEAN,
            formatPreset = OverlayFormatPreset.CARD
        )
    }

    fun addOverlayToTimeline(
        overlayType: String,
        displayName: String,
        stylePreset: OverlayStylePreset,
        formatPreset: OverlayFormatPreset
    ) {
        viewModelScope.launch {
            val maxOrder = _state.value.tracks.maxOfOrNull { it.order } ?: -1
            val trackId = UUID.randomUUID()
            val track = TimelineTrackState(
                id = trackId,
                type = TrackType.OVERLAY,
                label = displayName,
                order = maxOrder + 1,
                clips = emptyList()
            )
            val duration = _state.value.totalDurationMs.coerceAtLeast(5000L)
            val clip = TimelineClipState(
                id = UUID.randomUUID(),
                trackId = trackId,
                mediaItemId = null,
                label = displayName,
                startTimeMs = 0L,
                endTimeMs = duration,
                color = TrackType.OVERLAY.toColor(),
                positionX = 0.65f,
                positionY = 0.08f
            )
            executeAction(TimelineAction.AddTrackWithClip(track, clip))
            createOverlayEntity(
                clipId = clip.id,
                overlayType = overlayType,
                displayName = displayName,
                stylePreset = stylePreset,
                formatPreset = formatPreset
            )?.let { overlayRepository.addOverlay(it) }
        }
    }

    /**
     * Creates a text track and clip spanning the given time range.
     */
    fun addTextToTimeline(text: String) {
        viewModelScope.launch {
            val maxOrder = _state.value.tracks.maxOfOrNull { it.order } ?: -1
            val trackId = UUID.randomUUID()
            val track = TimelineTrackState(
                id = trackId,
                type = TrackType.TEXT,
                label = "Text",
                order = maxOrder + 1,
                clips = emptyList()
            )
            val duration = _state.value.totalDurationMs.coerceAtLeast(5000L)
            val clip = TimelineClipState(
                id = UUID.randomUUID(),
                trackId = trackId,
                mediaItemId = null,
                label = text.take(20),
                startTimeMs = 0L,
                endTimeMs = duration,
                color = TrackType.TEXT.toColor(),
                positionX = 0.08f,
                positionY = 0.08f
            )
            executeAction(TimelineAction.AddTrackWithClip(track, clip))
            overlayRepository.addOverlay(
                OverlayConfig.TextLabel(
                    projectId = projectId,
                    timelineClipId = clip.id,
                    name = clip.label,
                    text = text
                )
            )
        }
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

    fun updateClipColorAdjustments(
        clipId: UUID,
        brightness: Float,
        contrast: Float,
        saturation: Float
    ) {
        updateClipProperty(clipId) {
            it.copy(
                brightness = brightness.coerceIn(-0.4f, 0.4f),
                contrast = contrast.coerceIn(0.5f, 1.8f),
                saturation = saturation.coerceIn(0f, 1.8f)
            )
        }
    }

    fun updateClipLabel(clipId: UUID, label: String) {
        updateClipProperty(clipId) { it.copy(label = label) }
    }

    fun updateClipTransform(
        clipId: UUID,
        positionX: Float,
        positionY: Float,
        scale: Float,
        opacity: Float
    ) {
        updateClipProperty(clipId) {
            it.copy(
                positionX = positionX.coerceIn(0f, 1f),
                positionY = positionY.coerceIn(0f, 1f),
                scale = scale.coerceIn(0.4f, 3f),
                opacity = opacity.coerceIn(0f, 1f)
            )
        }
    }

    fun setClipContentMode(clipId: UUID, contentMode: ClipContentMode) {
        updateClipProperty(clipId) { it.copy(contentMode = contentMode) }
    }

    fun updateClipFrameTransform(
        clipId: UUID,
        positionX: Float,
        positionY: Float,
        scale: Float,
        rotation: Float
    ) {
        updateClipProperty(clipId) {
            it.copy(
                positionX = positionX.coerceIn(0f, 1f),
                positionY = positionY.coerceIn(0f, 1f),
                scale = scale.coerceIn(1f, 3f),
                rotation = rotation.coerceIn(-180f, 180f)
            )
        }
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
        val oldClip = currentState.tracks.flatMap { it.clips }.find { it.id == clipId } ?: return
        val newClip = transform(oldClip)
        if (oldClip == newClip) return
        val action = TimelineAction.UpdateClipProperty(clipId, oldClip, newClip)
        executeAction(action)
    }

    fun undo() {
        undoManager.undo(_state.value)?.let { newState ->
            applyAndNormalize(newState)
        }
    }

    fun redo() {
        undoManager.redo(_state.value)?.let { newState ->
            applyAndNormalize(newState)
        }
    }

    /** After undo/redo, repack sequential tracks to eliminate overlap. */
    private fun applyAndNormalize(newState: TimelineState) {
        val normalizedTracks = newState.tracks.map { track ->
            if (track.type.isSequentialVisualTrack()) {
                track.copy(clips = packSequential(track.clips))
            } else track
        }
        val maxEnd = normalizedTracks.flatMap { it.clips }.maxOfOrNull { it.endTimeMs } ?: 0L
        _state.value = newState.copy(
            tracks = normalizedTracks,
            totalDurationMs = maxEnd,
            canUndo = undoManager.canUndo,
            canRedo = undoManager.canRedo
        )
        persistState()
    }

    private fun executeAction(action: TimelineAction) {
        val newState = undoManager.execute(action, _state.value)
        _state.value = newState.copy(
            canUndo = undoManager.canUndo,
            canRedo = undoManager.canRedo
        )
        persistState()
        _timelineChanged.tryEmit(Unit)
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

            // Delete clips/tracks that were removed from state.
            // NOTE: Overlays are intentionally NOT deleted here so that undo can
            // restore them.  Orphan overlays (pointing to deleted clips) are
            // invisible and harmless.
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

    private suspend fun cleanupStaleTimelineData(
        staleTrackEntities: List<TimelineTrackEntity>,
        orphanOverlayEntities: List<com.gpxvideo.core.database.entity.OverlayEntity>
    ) {
        orphanOverlayEntities.forEach { overlayDao.delete(it) }
        staleTrackEntities.forEach { track ->
            clipDao.deleteByTrackId(track.id)
            trackDao.delete(track)
        }
    }

    private fun normalizeTrackLayout(trackId: UUID) {
        val currentState = _state.value
        val normalizedTracks = currentState.tracks.map { track ->
            if (track.id == trackId && track.type.isSequentialVisualTrack()) {
                track.copy(clips = packSequential(track.clips))
            } else {
                track
            }
        }
        _state.value = currentState.withTracks(normalizedTracks).copy(
            canUndo = undoManager.canUndo,
            canRedo = undoManager.canRedo
        )
        persistState()
    }

    private fun packSequential(clips: List<TimelineClipState>): List<TimelineClipState> {
        var timelineCursor = 0L
        return clips.sortedBy { it.startTimeMs }.map { clip ->
            val durationMs = (clip.endTimeMs - clip.startTimeMs).coerceAtLeast(1L)
            val packedClip = clip.copy(
                startTimeMs = timelineCursor,
                endTimeMs = timelineCursor + durationMs
            )
            timelineCursor += durationMs
            packedClip
        }
    }

    companion object {
        private const val SNAP_GRID_MS = 100L

        fun snapToGrid(ms: Long): Long {
            return ((ms + SNAP_GRID_MS / 2) / SNAP_GRID_MS) * SNAP_GRID_MS
        }
    }

    private fun createOverlayEntity(
        clipId: UUID,
        overlayType: String,
        displayName: String,
        stylePreset: OverlayStylePreset,
        formatPreset: OverlayFormatPreset
    ): OverlayConfig? {
        val config = when {
        overlayType == "static_altitude_profile" -> OverlayConfig.StaticAltitudeProfile(
            projectId = projectId,
            timelineClipId = clipId,
            name = displayName
        )
        overlayType == "static_map" -> OverlayConfig.StaticMap(
            projectId = projectId,
            timelineClipId = clipId,
            name = displayName
        )
        overlayType == "static_stats" -> OverlayConfig.StaticStats(
            projectId = projectId,
            timelineClipId = clipId,
            name = displayName
        )
        overlayType.startsWith("static_stat:") -> {
            val fieldName = overlayType.removePrefix("static_stat:")
            val field = runCatching { StatField.valueOf(fieldName) }.getOrNull() ?: StatField.TOTAL_DISTANCE
            OverlayConfig.StaticStats(
                projectId = projectId,
                timelineClipId = clipId,
                name = displayName,
                fields = listOf(field),
                layout = StatsLayout.SINGLE,
                size = OverlaySize(0.18f, 0.09f)
            )
        }
        overlayType == "moving_altitude_profile" -> OverlayConfig.DynamicAltitudeProfile(
            projectId = projectId,
            timelineClipId = clipId,
            name = displayName,
            syncMode = SyncMode.CLIP_PROGRESS
        )
        overlayType == "moving_map" -> OverlayConfig.DynamicMap(
            projectId = projectId,
            timelineClipId = clipId,
            name = displayName,
            followPosition = false,
            syncMode = SyncMode.CLIP_PROGRESS
        )
        overlayType == "moving_stat" -> OverlayConfig.DynamicStat(
            projectId = projectId,
            timelineClipId = clipId,
            name = displayName,
            syncMode = SyncMode.CLIP_PROGRESS
        )
        overlayType.startsWith("dynamic_stat:") -> {
            val fieldName = overlayType.removePrefix("dynamic_stat:")
            val field = runCatching { DynamicField.valueOf(fieldName) }.getOrNull() ?: DynamicField.CURRENT_SPEED
            OverlayConfig.DynamicStat(
                projectId = projectId,
                timelineClipId = clipId,
                name = displayName,
                field = field
            )
        }
        overlayType == "dynamic_altitude_profile" -> OverlayConfig.DynamicAltitudeProfile(
            projectId = projectId,
            timelineClipId = clipId,
            name = displayName
        )
        overlayType == "dynamic_map" -> OverlayConfig.DynamicMap(
            projectId = projectId,
            timelineClipId = clipId,
            name = displayName
        )
        overlayType == "dynamic_stat" -> OverlayConfig.DynamicStat(
            projectId = projectId,
            timelineClipId = clipId,
            name = displayName
        )
        else -> null
    }

        return config
            ?.applyStylePreset(stylePreset)
            ?.applyFormatPreset(formatPreset)
    }
}

private fun TimelineClipEntity.toClipState(trackType: TrackType, labelOverride: String?): TimelineClipState {
    return TimelineClipState(
        id = id,
        trackId = trackId,
        mediaItemId = mediaItemId,
        label = labelOverride ?: "Clip",
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
        exitTransitionDurationMs = exitTransitionDurationMs,
        contentMode = runCatching { ClipContentMode.valueOf(contentMode) }
            .getOrDefault(ClipContentMode.FIT),
        positionX = positionX,
        positionY = positionY,
        rotation = rotation,
        scale = scale,
        opacity = opacity,
        brightness = brightness,
        contrast = contrast,
        saturation = saturation
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
        exitTransitionDurationMs = exitTransitionDurationMs,
        contentMode = contentMode.name,
        positionX = positionX,
        positionY = positionY,
        rotation = rotation,
        scale = scale,
        opacity = opacity,
        brightness = brightness,
        contrast = contrast,
        saturation = saturation
    )
}

private fun OverlayConfig.applyStylePreset(stylePreset: OverlayStylePreset): OverlayConfig {
    val style = when (stylePreset) {
        OverlayStylePreset.CLEAN -> OverlayStyle(
            backgroundColor = null,
            borderColor = null,
            borderWidth = 0f,
            cornerRadius = 0f,
            opacity = 1f,
            fontColor = 0xFFF6F8FBL,
            shadowEnabled = true,
            shadowColor = 0xCC000000L,
            shadowRadius = 6f
        )
        OverlayStylePreset.BROADCAST -> OverlayStyle(
            backgroundColor = null,
            borderColor = null,
            borderWidth = 0f,
            cornerRadius = 0f,
            opacity = 1f,
            fontColor = 0xFFFFFFFFL,
            shadowEnabled = true,
            shadowColor = 0xCC000000L,
            shadowRadius = 8f
        )
        OverlayStylePreset.GLASS -> OverlayStyle(
            backgroundColor = null,
            borderColor = null,
            borderWidth = 0f,
            cornerRadius = 0f,
            opacity = 1f,
            fontColor = 0xFFFFFFFFL,
            shadowEnabled = true,
            shadowColor = 0x99000000L,
            shadowRadius = 10f
        )
    }

    return when (this) {
        is OverlayConfig.StaticAltitudeProfile -> copy(
            style = style,
            lineColor = when (stylePreset) {
                OverlayStylePreset.CLEAN -> 0xFF4DD0E1L
                OverlayStylePreset.BROADCAST -> 0xFFFFC857L
                OverlayStylePreset.GLASS -> 0xFF81E6D9L
            },
            fillColor = when (stylePreset) {
                OverlayStylePreset.CLEAN -> 0x6638BDF8L
                OverlayStylePreset.BROADCAST -> 0x66FF8A00L
                OverlayStylePreset.GLASS -> 0x6648BB78L
            }
        )
        is OverlayConfig.StaticMap -> copy(
            style = style,
            routeColor = when (stylePreset) {
                OverlayStylePreset.CLEAN -> 0xFF38BDF8L
                OverlayStylePreset.BROADCAST -> 0xFFFF7A00L
                OverlayStylePreset.GLASS -> 0xFF7DD3FCL
            },
            mapStyle = when (stylePreset) {
                OverlayStylePreset.CLEAN -> MapStyle.MINIMAL
                OverlayStylePreset.BROADCAST -> MapStyle.DARK
                OverlayStylePreset.GLASS -> MapStyle.TERRAIN
            }
        )
        is OverlayConfig.StaticStats -> copy(style = style)
        is OverlayConfig.DynamicAltitudeProfile -> copy(
            style = style,
            lineColor = when (stylePreset) {
                OverlayStylePreset.CLEAN -> 0xFF38BDF8L
                OverlayStylePreset.BROADCAST -> 0xFFFFC857L
                OverlayStylePreset.GLASS -> 0xFF93C5FDL
            },
            markerColor = when (stylePreset) {
                OverlayStylePreset.CLEAN -> 0xFF38BDF8L
                OverlayStylePreset.BROADCAST -> 0xFFFF7A00L
                OverlayStylePreset.GLASS -> 0xFFFFFFFFL
            },
            trailColor = when (stylePreset) {
                OverlayStylePreset.CLEAN -> 0x6638BDF8L
                OverlayStylePreset.BROADCAST -> 0x66FFB703L
                OverlayStylePreset.GLASS -> 0x6648BB78L
            }
        )
        is OverlayConfig.DynamicMap -> copy(
            style = style,
            routeColor = when (stylePreset) {
                OverlayStylePreset.CLEAN -> 0xFF38BDF8L
                OverlayStylePreset.BROADCAST -> 0xFFFF7A00L
                OverlayStylePreset.GLASS -> 0xFF7DD3FCL
            },
            mapStyle = when (stylePreset) {
                OverlayStylePreset.CLEAN -> MapStyle.MINIMAL
                OverlayStylePreset.BROADCAST -> MapStyle.DARK
                OverlayStylePreset.GLASS -> MapStyle.TERRAIN
            }
        )
        is OverlayConfig.DynamicStat -> copy(style = style)
        is OverlayConfig.TextLabel -> copy(style = style.copy(fontSize = 24f))
    }
}

private fun OverlayConfig.applyFormatPreset(formatPreset: OverlayFormatPreset): OverlayConfig = when (this) {
    is OverlayConfig.StaticAltitudeProfile -> copy(
        size = when (formatPreset) {
            OverlayFormatPreset.COMPACT -> OverlaySize(0.42f, 0.16f)
            OverlayFormatPreset.CARD -> OverlaySize(0.50f, 0.18f)
            OverlayFormatPreset.WIDE -> OverlaySize(0.62f, 0.18f)
        }
    )
    is OverlayConfig.StaticMap -> copy(
        size = when (formatPreset) {
            OverlayFormatPreset.COMPACT -> OverlaySize(0.24f, 0.24f)
            OverlayFormatPreset.CARD -> OverlaySize(0.30f, 0.28f)
            OverlayFormatPreset.WIDE -> OverlaySize(0.40f, 0.24f)
        }
    )
    is OverlayConfig.StaticStats -> {
        // Preserve single-field layout/size — don't override with multi-stat grid
        if (fields.size <= 1) this
        else copy(
            size = when (formatPreset) {
                OverlayFormatPreset.COMPACT -> OverlaySize(0.26f, 0.14f)
                OverlayFormatPreset.CARD -> OverlaySize(0.32f, 0.18f)
                OverlayFormatPreset.WIDE -> OverlaySize(0.46f, 0.18f)
            },
            layout = when (formatPreset) {
                OverlayFormatPreset.COMPACT -> StatsLayout.GRID_2X1
                OverlayFormatPreset.CARD -> StatsLayout.GRID_2X2
                OverlayFormatPreset.WIDE -> StatsLayout.GRID_4X2
            }
        )
    }
    is OverlayConfig.DynamicAltitudeProfile -> copy(
        size = when (formatPreset) {
            OverlayFormatPreset.COMPACT -> OverlaySize(0.42f, 0.16f)
            OverlayFormatPreset.CARD -> OverlaySize(0.50f, 0.18f)
            OverlayFormatPreset.WIDE -> OverlaySize(0.62f, 0.18f)
        }
    )
    is OverlayConfig.DynamicMap -> copy(
        size = when (formatPreset) {
            OverlayFormatPreset.COMPACT -> OverlaySize(0.24f, 0.24f)
            OverlayFormatPreset.CARD -> OverlaySize(0.30f, 0.28f)
            OverlayFormatPreset.WIDE -> OverlaySize(0.40f, 0.24f)
        }
    )
    is OverlayConfig.DynamicStat -> copy(
        size = when (formatPreset) {
            OverlayFormatPreset.COMPACT -> OverlaySize(0.18f, 0.10f)
            OverlayFormatPreset.CARD -> OverlaySize(0.24f, 0.12f)
            OverlayFormatPreset.WIDE -> OverlaySize(0.32f, 0.12f)
        }
    )
    is OverlayConfig.TextLabel -> this
}

private fun TimelineState.withTracks(tracks: List<TimelineTrackState>): TimelineState {
    val maxEnd = tracks.flatMap { it.clips }.maxOfOrNull { it.endTimeMs } ?: 0L
    val selectedClipExists = selectedClipId?.let { clipId ->
        tracks.any { track -> track.clips.any { it.id == clipId } }
    } ?: true
    val selectedTrackExists = selectedTrackId?.let { trackId ->
        tracks.any { it.id == trackId }
    } ?: true

    return copy(
        tracks = tracks,
        totalDurationMs = maxEnd,
        selectedClipId = selectedClipId?.takeIf { selectedClipExists },
        selectedTrackId = selectedTrackId?.takeIf { selectedTrackExists }
    )
}

private fun TrackType.isSequentialVisualTrack(): Boolean {
    return this == TrackType.VIDEO || this == TrackType.IMAGE
}

private fun TrackType.isRemovableClipTrack(): Boolean {
    return this == TrackType.OVERLAY || this == TrackType.TEXT || this == TrackType.AUDIO
}
