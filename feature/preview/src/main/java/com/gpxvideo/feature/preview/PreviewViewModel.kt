package com.gpxvideo.feature.preview

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpxvideo.core.database.dao.MediaItemDao
import com.gpxvideo.core.database.dao.TimelineClipDao
import com.gpxvideo.core.database.dao.TimelineTrackDao
import com.gpxvideo.core.database.entity.MediaItemEntity
import com.gpxvideo.core.database.entity.TimelineClipEntity
import com.gpxvideo.core.model.TrackType
import com.gpxvideo.feature.timeline.ClipContentMode
import com.gpxvideo.feature.timeline.TimelineClipState
import com.gpxvideo.feature.timeline.TimelineTrackState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class PreviewUiState(
    val isLoading: Boolean = true,
    val playbackSpeed: Float = 1f,
    val showControls: Boolean = true,
    val overlays: List<OverlayRenderData> = emptyList()
)

@HiltViewModel(assistedFactory = PreviewViewModel.Factory::class)
class PreviewViewModel @AssistedInject constructor(
    @Assisted private val projectIdStr: String,
    val previewEngine: PreviewEngine,
    private val trackDao: TimelineTrackDao,
    private val clipDao: TimelineClipDao,
    private val mediaItemDao: MediaItemDao
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(projectId: String): PreviewViewModel
    }

    private val projectId: UUID = UUID.fromString(projectIdStr)

    private val _uiState = MutableStateFlow(PreviewUiState())
    val uiState: StateFlow<PreviewUiState> = _uiState.asStateFlow()

    val currentPositionMs: StateFlow<Long> = previewEngine.currentPositionMs
    val isPlaying: StateFlow<Boolean> = previewEngine.isPlaying
    val duration: StateFlow<Long> = previewEngine.duration

    init {
        previewEngine.initialize()
        loadProjectMedia()
    }

    fun loadProjectMedia() {
        viewModelScope.launch {
            val mediaItems = mediaItemDao.getByProjectId(projectId).first()
            val mediaMap = mediaItems.associateBy { it.id }

            val tracks = trackDao.getByProjectId(projectId).first()
            val visualTracks = tracks.filter { it.type == TrackType.VIDEO.name || it.type == TrackType.IMAGE.name }

            val previewClips = mutableListOf<PreviewClip>()
            for (track in visualTracks) {
                val clips = clipDao.getByTrackId(track.id).first()
                for (clip in clips.sortedBy { it.startTimeMs }) {
                    val mediaItem = clip.mediaItemId?.let { mediaMap[it] }
                    if (mediaItem != null) {
                        previewClips.add(clip.toPreviewClip(mediaItem))
                    }
                }
            }

            applyPreviewClips(previewClips, targetPositionMs = 0L, resumePlayback = false)
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun reloadMedia(
        tracks: List<TimelineTrackState>,
        mediaItems: List<MediaItemEntity>,
        targetPositionMs: Long = currentPositionMs.value
    ) {
        val mediaMap = mediaItems.associateBy { it.id }
        val previewClips = tracks
            .filter { it.type == TrackType.VIDEO || it.type == TrackType.IMAGE }
            .sortedBy { it.order }
            .flatMap { track ->
                track.clips.sortedBy { it.startTimeMs }.mapNotNull { clip ->
                    clip.mediaItemId?.let(mediaMap::get)?.let { mediaItem ->
                        clip.toPreviewClip(mediaItem)
                    }
                }
            }
        applyPreviewClips(
            previewClips = previewClips,
            targetPositionMs = targetPositionMs,
            resumePlayback = isPlaying.value
        )
        _uiState.value = _uiState.value.copy(isLoading = false)
    }

    fun play() = previewEngine.play()
    fun pause() = previewEngine.pause()

    fun togglePlayback() {
        if (previewEngine.isPlaying.value) pause() else play()
    }

    fun seekTo(positionMs: Long) = previewEngine.seekTo(positionMs)

    fun captureCurrentFrame(): android.graphics.Bitmap? = previewEngine.captureFrame()

    /**
     * Capture a frame guaranteed free of any color adjustments applied to the
     * preview TextureView.  Used by the effects panel so filter previews start
     * from an unmodified image.
     */
    suspend fun captureCleanFrame(): android.graphics.Bitmap? = previewEngine.captureCleanFrame()

    fun setPlaybackSpeed(speed: Float) {
        previewEngine.setPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
    }

    fun toggleControls() {
        _uiState.value = _uiState.value.copy(showControls = !_uiState.value.showControls)
    }

    override fun onCleared() {
        super.onCleared()
        previewEngine.pause()
    }

    private fun TimelineClipEntity.toPreviewClip(mediaItem: MediaItemEntity): PreviewClip {
        val sourceAR = computeSourceAspectRatio(mediaItem)
        return PreviewClip(
            uri = pathToUri(mediaItem.localCopyPath),
            startMs = trimStartMs,
            endMs = resolvePreviewEndMs(
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                sourceDurationMs = mediaItem.durationMs
            ),
            speed = speed,
            displayTransform = PreviewDisplayTransform(
                contentMode = runCatching { ClipContentMode.valueOf(contentMode) }
                    .getOrDefault(ClipContentMode.FIT),
                positionX = positionX,
                positionY = positionY,
                scale = scale,
                rotationDegrees = rotation,
                brightness = brightness,
                contrast = contrast,
                saturation = saturation,
                sourceVideoAspectRatio = sourceAR
            )
        )
    }

    private fun TimelineClipState.toPreviewClip(mediaItem: MediaItemEntity): PreviewClip {
        val sourceAR = computeSourceAspectRatio(mediaItem)
        return PreviewClip(
            uri = pathToUri(mediaItem.localCopyPath),
            startMs = trimStartMs,
            endMs = resolvePreviewEndMs(
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                sourceDurationMs = mediaItem.durationMs
            ),
            speed = speed,
            displayTransform = PreviewDisplayTransform(
                contentMode = contentMode,
                positionX = positionX,
                positionY = positionY,
                scale = scale,
                rotationDegrees = rotation,
                brightness = brightness,
                contrast = contrast,
                saturation = saturation,
                sourceVideoAspectRatio = sourceAR
            )
        )
    }

    /**
     * Computes the display aspect ratio of a media item, accounting for the
     * rotation flag stored in the container metadata (e.g. 90° / 270° for
     * portrait videos recorded with the phone held upright).
     */
    private fun computeSourceAspectRatio(mediaItem: MediaItemEntity): Float {
        val w = mediaItem.width
        val h = mediaItem.height
        if (w <= 0 || h <= 0) return 0f
        return if (mediaItem.rotation == 90 || mediaItem.rotation == 270) {
            h.toFloat() / w.toFloat()
        } else {
            w.toFloat() / h.toFloat()
        }
    }

    private fun applyPreviewClips(
        previewClips: List<PreviewClip>,
        targetPositionMs: Long,
        resumePlayback: Boolean
    ) {
        previewEngine.setMediaSources(previewClips)
        val durationMs = previewClips.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
        val clampedPositionMs = targetPositionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        if (previewClips.isNotEmpty()) {
            previewEngine.seekTo(clampedPositionMs)
        }
        if (resumePlayback && previewClips.isNotEmpty()) {
            previewEngine.play()
        } else {
            previewEngine.pause()
        }
    }

    /**
     * Computes the source-media end position for ExoPlayer clipping.
     *
     * `trimStartMs` = offset into the source where playback begins.
     * `endTimeMs - startTimeMs` = visible duration on the timeline (already accounts for any
     * trimming the user did).
     *
     * The result is `trimStartMs + visibleDuration`, clamped to the source length.
     */
    private fun resolvePreviewEndMs(
        startTimeMs: Long,
        endTimeMs: Long,
        trimStartMs: Long,
        @Suppress("UNUSED_PARAMETER") trimEndMs: Long,
        sourceDurationMs: Long?
    ): Long {
        val clipDurationMs = (endTimeMs - startTimeMs).coerceAtLeast(1L)
        val requestedEndMs = trimStartMs + clipDurationMs
        val sourceDuration = sourceDurationMs?.takeIf { it > 0L }
        val effectiveEndMs = sourceDuration?.let { requestedEndMs.coerceAtMost(it) } ?: requestedEndMs
        return effectiveEndMs.coerceAtLeast(trimStartMs + 1L)
    }

    private fun pathToUri(path: String): Uri {
        return if (path.contains("://")) Uri.parse(path) else Uri.fromFile(File(path))
    }
}
