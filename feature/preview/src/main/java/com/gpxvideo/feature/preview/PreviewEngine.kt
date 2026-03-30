package com.gpxvideo.feature.preview

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
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
    val speed: Float = 1f
)

@Singleton
class PreviewEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

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
                    if (playbackState == Player.STATE_READY) {
                        _duration.value = player.duration.coerceAtLeast(0)
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        _isPlaying.value = false
                        stopPositionPolling()
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
    }

    fun setMediaSource(uri: Uri) {
        ensureInitialized()
        val item = MediaItem.fromUri(uri)
        exoPlayer?.setMediaItem(item)
        exoPlayer?.prepare()
    }

    @OptIn(UnstableApi::class)
    fun setMediaSources(clips: List<PreviewClip>) {
        ensureInitialized()
        if (clips.isEmpty()) return
        val items = clips.map { clip ->
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
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackParameters(PlaybackParameters(speed))
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    private fun ensureInitialized() {
        if (exoPlayer == null) initialize()
    }

    private fun startPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    _currentPositionMs.value = player.currentPosition
                    _duration.value = player.duration.coerceAtLeast(0)
                }
                delay(16)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = null
    }
}
