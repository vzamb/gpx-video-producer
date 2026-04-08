package com.gpxvideo.feature.preview

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.TextureView
import androidx.media3.common.PlaybackException
import androidx.media3.common.VideoSize
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.gpxvideo.feature.timeline.ClipContentMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class PreviewClip(
    val uri: Uri,
    val startMs: Long,
    val endMs: Long,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val displayTransform: PreviewDisplayTransform = PreviewDisplayTransform()
)

data class PreviewDisplayTransform(
    val contentMode: ClipContentMode = ClipContentMode.FIT,
    val positionX: Float = 0.5f,
    val positionY: Float = 0.5f,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    /** Known aspect ratio of the source video (width/height, rotation-corrected). 0 = unknown. */
    val sourceVideoAspectRatio: Float = 0f
)

private data class PreviewClipRange(
    val index: Int,
    val timelineStartMs: Long,
    val timelineEndMs: Long,
    val volume: Float = 1f,
    val displayTransform: PreviewDisplayTransform
)

@Singleton
class PreviewEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val TAG = "PreviewEngine"
    }

    private var exoPlayer: ExoPlayer? = null
    private var boundTextureView: TextureView? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var clipRanges: List<PreviewClipRange> = emptyList()

    /** True while setMediaSources is rebuilding — suppresses stale position updates */
    @Volatile
    private var isRebuilding = false

    /** Timestamp of last explicit seekTo — suppresses STATE_READY position override briefly */
    @Volatile
    private var lastSeekTimeNanos = 0L

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _videoAspectRatio = MutableStateFlow<Float?>(null)
    val videoAspectRatio: StateFlow<Float?> = _videoAspectRatio.asStateFlow()

    private val _activeDisplayTransform = MutableStateFlow(PreviewDisplayTransform())
    val activeDisplayTransform: StateFlow<PreviewDisplayTransform> = _activeDisplayTransform.asStateFlow()

    private var positionPollingJob: Job? = null

    fun initialize() {
        if (exoPlayer != null) return
        exoPlayer = ExoPlayer.Builder(context).build().also { player ->
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    if (playing) startPositionPolling() else stopPositionPolling()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (isRebuilding) return // ignore stale events during rebuild
                    if (playbackState == Player.STATE_READY) {
                        _duration.value = clipRanges.lastOrNull()?.timelineEndMs ?: 0L
                        // Only update position from player if no recent explicit seek
                        // (seeks set position directly; STATE_READY can override with stale value)
                        val timeSinceSeek = System.nanoTime() - lastSeekTimeNanos
                        if (timeSinceSeek > 500_000_000L) { // 500ms grace period
                            _currentPositionMs.value = currentTimelinePosition(player)
                        }
                        updateActiveDisplayTransform(player.currentMediaItemIndex)
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        _isPlaying.value = false
                        _currentPositionMs.value = clipRanges.lastOrNull()?.timelineEndMs ?: 0L
                        stopPositionPolling()
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateActiveDisplayTransform(player.currentMediaItemIndex)
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Preview playback failed", error)
                    _isPlaying.value = false
                    stopPositionPolling()
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    val width = videoSize.width
                    val height = videoSize.height
                    // Only update AR when a valid size is reported.
                    // ExoPlayer fires 0x0 during clip transitions — ignore those to avoid
                    // momentarily losing the correct aspect ratio.
                    if (width > 0 && height > 0) {
                        val ar = (width * videoSize.pixelWidthHeightRatio) / height.toFloat()
                        Log.d(TAG, "onVideoSizeChanged: ${width}x${height} pxRatio=${videoSize.pixelWidthHeightRatio} → AR=$ar")
                        _videoAspectRatio.value = ar
                    }
                }
            })
        }
    }

    fun release() {
        stopPositionPolling()
        exoPlayer?.release()
        exoPlayer = null
        _currentPositionMs.value = 0
        _isPlaying.value = false
        _duration.value = 0
        _videoAspectRatio.value = null
        _activeDisplayTransform.value = PreviewDisplayTransform()
    }

    fun setMediaSource(uri: Uri) {
        ensureInitialized()
        clipRanges = emptyList()
        val item = MediaItem.fromUri(uri)
        exoPlayer?.setMediaItem(item)
        exoPlayer?.prepare()
    }

    @OptIn(UnstableApi::class)
    fun setMediaSources(clips: List<PreviewClip>) {
        ensureInitialized()
        val validClips = clips.filter { it.endMs > it.startMs }
        if (validClips.isEmpty()) {
            clipRanges = emptyList()
            _duration.value = 0L
            _currentPositionMs.value = 0L
            _videoAspectRatio.value = null
            _activeDisplayTransform.value = PreviewDisplayTransform()
            _isPlaying.value = false
            exoPlayer?.clearMediaItems()
            return
        }

        // Save current position to restore after rebuild
        val savedPositionMs = _currentPositionMs.value

        isRebuilding = true
        stopPositionPolling()

        var timelineCursor = 0L
        clipRanges = validClips.mapIndexed { index, clip ->
            val durationMs = (clip.endMs - clip.startMs).coerceAtLeast(0L)
            val range = PreviewClipRange(
                index = index,
                timelineStartMs = timelineCursor,
                timelineEndMs = timelineCursor + durationMs,
                volume = clip.volume,
                displayTransform = clip.displayTransform
            )
            timelineCursor += durationMs
            range
        }
        val items = validClips.map { clip ->
            MediaItem.Builder()
                .setUri(clip.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startMs)
                        .setEndPositionMs(clip.endMs)
                        .build()
                )
                .build()
        }
        exoPlayer?.setMediaItems(items)
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = false

        val newDuration = clipRanges.lastOrNull()?.timelineEndMs ?: 0L
        _duration.value = newDuration

        // Restore position: clamp to new timeline instead of always resetting to 0
        val restoredPosition = savedPositionMs.coerceIn(0L, newDuration)
        _currentPositionMs.value = restoredPosition
        seekToInternal(restoredPosition)

        // Apply display transform for the clip at the restored position
        val activeRange = clipRanges.firstOrNull { restoredPosition < it.timelineEndMs }
            ?: clipRanges.lastOrNull()
        activeRange?.let {
            exoPlayer?.volume = it.volume
            _activeDisplayTransform.value = it.displayTransform
            if (it.displayTransform.sourceVideoAspectRatio > 0f) {
                _videoAspectRatio.value = it.displayTransform.sourceVideoAspectRatio
            }
        }

        isRebuilding = false
    }

    fun play() {
        val player = exoPlayer ?: return
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0, 0L)
        }
        player.prepare()
        player.playWhenReady = true
        player.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        lastSeekTimeNanos = System.nanoTime()
        // ExoPlayer ignores seekTo in STATE_ENDED — re-prepare first
        if (player.playbackState == Player.STATE_ENDED) {
            player.prepare()
            player.playWhenReady = false
        }
        if (clipRanges.isEmpty()) {
            player.seekTo(positionMs)
            _currentPositionMs.value = positionMs
            return
        }
        val clampedPosition = positionMs.coerceIn(0L, clipRanges.last().timelineEndMs)
        val range = clipRanges.firstOrNull { clampedPosition < it.timelineEndMs } ?: clipRanges.last()
        val localPosition = (clampedPosition - range.timelineStartMs).coerceAtLeast(0L)
        player.seekTo(range.index, localPosition)
        _currentPositionMs.value = clampedPosition
        _activeDisplayTransform.value = range.displayTransform
        if (range.displayTransform.sourceVideoAspectRatio > 0f) {
            _videoAspectRatio.value = range.displayTransform.sourceVideoAspectRatio
        }
    }

    /** Seek ExoPlayer without updating _currentPositionMs (used during rebuild). */
    private fun seekToInternal(positionMs: Long) {
        val player = exoPlayer ?: return
        if (clipRanges.isEmpty()) {
            player.seekTo(positionMs)
            return
        }
        val clamped = positionMs.coerceIn(0L, clipRanges.last().timelineEndMs)
        val range = clipRanges.firstOrNull { clamped < it.timelineEndMs } ?: clipRanges.last()
        val localPos = (clamped - range.timelineStartMs).coerceAtLeast(0L)
        player.seekTo(range.index, localPos)
    }

    /**
     * Update only the display transforms (brightness, contrast, saturation, etc.)
     * without reloading ExoPlayer media items or resetting playback position.
     */
    fun updateDisplayTransforms(clips: List<PreviewClip>) {
        val validClips = clips.filter { it.endMs > it.startMs }
        if (validClips.size != clipRanges.size) return // structural mismatch — use setMediaSources
        clipRanges = clipRanges.zip(validClips) { range, clip ->
            range.copy(displayTransform = clip.displayTransform, volume = clip.volume)
        }
        val currentPos = _currentPositionMs.value
        val activeRange = clipRanges.firstOrNull { currentPos < it.timelineEndMs }
            ?: clipRanges.lastOrNull()
        activeRange?.let {
            _activeDisplayTransform.value = it.displayTransform
            exoPlayer?.volume = it.volume
            if (it.displayTransform.sourceVideoAspectRatio > 0f) {
                _videoAspectRatio.value = it.displayTransform.sourceVideoAspectRatio
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackParameters(PlaybackParameters(speed))
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    fun bindToTextureView(textureView: TextureView) {
        ensureInitialized()
        boundTextureView = textureView
        exoPlayer?.setVideoTextureView(textureView)
    }

    fun unbindTextureView(textureView: TextureView) {
        exoPlayer?.clearVideoTextureView(textureView)
        if (boundTextureView == textureView) boundTextureView = null
    }

    fun captureFrame(): android.graphics.Bitmap? {
        return try { boundTextureView?.bitmap } catch (_: Exception) { null }
    }

    /**
     * Return a frame that is guaranteed free of any [PreviewDisplayTransform]
     * color adjustments.  Uses [android.media.MediaMetadataRetriever] on the
     * source file so the hardware-layer paint applied to the [TextureView] is
     * bypassed entirely.  Falls back to [captureFrame] when retrieval fails.
     *
     * This is a suspend function: ExoPlayer properties are read on the calling
     * (main) thread, then the heavy MediaMetadataRetriever work runs on
     * [kotlinx.coroutines.Dispatchers.IO].
     */
    suspend fun captureCleanFrame(): android.graphics.Bitmap? {
        val player = exoPlayer ?: return captureFrame()
        val idx = player.currentMediaItemIndex
        if (idx < 0 || idx >= player.mediaItemCount) return captureFrame()

        val uri = player.getMediaItemAt(idx).localConfiguration?.uri
            ?: return captureFrame()
        val positionUs = player.currentPosition * 1000L

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    when {
                        uri.scheme == "file" || uri.scheme == null ->
                            retriever.setDataSource(uri.path)
                        uri.scheme == "content" ->
                            retriever.setDataSource(context, uri)
                        else ->
                            retriever.setDataSource(uri.toString(), HashMap())
                    }
                    retriever.getFrameAtTime(
                        positionUs,
                        android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                } finally {
                    retriever.release()
                }
            } catch (_: Exception) {
                captureFrame()
            }
        }
    }

    private fun ensureInitialized() {
        if (exoPlayer == null) initialize()
    }

    private fun startPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = scope.launch {
            while (isActive) {
                if (!isRebuilding) {
                    exoPlayer?.let { player ->
                        _currentPositionMs.value = currentTimelinePosition(player)
                        _duration.value = clipRanges.lastOrNull()?.timelineEndMs ?: player.duration.coerceAtLeast(0)
                    }
                }
                delay(16)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = null
    }

    private fun currentTimelinePosition(player: ExoPlayer): Long {
        if (clipRanges.isEmpty()) return player.currentPosition.coerceAtLeast(0L)
        val currentItemIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        val range = clipRanges.getOrNull(currentItemIndex) ?: return player.currentPosition.coerceAtLeast(0L)
        return (range.timelineStartMs + player.currentPosition).coerceAtMost(
            clipRanges.lastOrNull()?.timelineEndMs ?: Long.MAX_VALUE
        )
    }

    private fun updateActiveDisplayTransform(currentItemIndex: Int) {
        val range = clipRanges.getOrNull(currentItemIndex.coerceAtLeast(0))
        val transform = range?.displayTransform ?: PreviewDisplayTransform()
        _activeDisplayTransform.value = transform
        // Apply per-clip volume
        exoPlayer?.volume = range?.volume ?: 1f
        // Eagerly set the video AR from metadata so the TextureView transform is correct
        // even before ExoPlayer fires onVideoSizeChanged for this clip.
        if (transform.sourceVideoAspectRatio > 0f) {
            _videoAspectRatio.value = transform.sourceVideoAspectRatio
        }
    }
}
