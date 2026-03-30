package com.gpxvideo.feature.preview

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpxvideo.core.database.dao.MediaItemDao
import com.gpxvideo.core.database.dao.TimelineClipDao
import com.gpxvideo.core.database.dao.TimelineTrackDao
import com.gpxvideo.core.database.entity.MediaItemEntity
import com.gpxvideo.core.database.entity.TimelineClipEntity
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
            val videoTracks = tracks.filter { it.type == "VIDEO" }

            val previewClips = mutableListOf<PreviewClip>()
            for (track in videoTracks) {
                val clips = clipDao.getByTrackId(track.id).first()
                for (clip in clips.sortedBy { it.startTimeMs }) {
                    val mediaItem = clip.mediaItemId?.let { mediaMap[it] }
                    if (mediaItem != null) {
                        previewClips.add(clip.toPreviewClip(mediaItem))
                    }
                }
            }

            if (previewClips.isNotEmpty()) {
                previewEngine.setMediaSources(previewClips)
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    /** Called by the screen when timeline content changes. */
    fun reloadMedia() {
        loadProjectMedia()
    }

    fun play() = previewEngine.play()
    fun pause() = previewEngine.pause()

    fun togglePlayback() {
        if (previewEngine.isPlaying.value) pause() else play()
    }

    fun seekTo(positionMs: Long) = previewEngine.seekTo(positionMs)

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
        val clipDurationMs = endTimeMs - startTimeMs
        // trimStartMs = how far into the source media to start
        // If trimEndMs is 0 (default), use the full clip duration from the source
        val effectiveEndMs = if (trimEndMs > 0L) {
            trimEndMs
        } else {
            val sourceDuration = mediaItem.durationMs ?: clipDurationMs
            (trimStartMs + clipDurationMs).coerceAtMost(sourceDuration)
        }
        return PreviewClip(
            uri = pathToUri(mediaItem.localCopyPath),
            startMs = trimStartMs,
            endMs = effectiveEndMs,
            speed = speed
        )
    }

    private fun pathToUri(path: String): Uri {
        return if (path.contains("://")) Uri.parse(path) else Uri.fromFile(File(path))
    }
}
