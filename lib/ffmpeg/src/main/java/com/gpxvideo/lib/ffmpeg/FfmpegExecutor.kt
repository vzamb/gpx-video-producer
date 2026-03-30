package com.gpxvideo.lib.ffmpeg

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

data class FfmpegProgress(
    val frameNumber: Long,
    val timeMs: Long,
    val percentage: Float,
    val fps: Float,
    val bitrate: Long,
    val speed: Float
)

sealed class FfmpegResult {
    data class Success(val outputPath: String, val durationMs: Long) : FfmpegResult()
    data class Error(val message: String, val returnCode: Int, val logs: String) : FfmpegResult()
    data object Cancelled : FfmpegResult()
}

interface FfmpegExecutor {
    suspend fun execute(
        command: FfmpegCommand,
        onProgress: (FfmpegProgress) -> Unit = {}
    ): FfmpegResult

    fun cancel()
}

/**
 * Stub implementation that simulates FFmpeg execution with realistic progress.
 * Replace with a real ffmpeg-kit backed implementation when the dependency is available.
 */
class StubFfmpegExecutor @Inject constructor() : FfmpegExecutor {
    private val cancelled = AtomicBoolean(false)

    override suspend fun execute(
        command: FfmpegCommand,
        onProgress: (FfmpegProgress) -> Unit
    ): FfmpegResult {
        cancelled.set(false)
        val totalSteps = 20
        val stepDelayMs = 250L // ~5 seconds total

        for (i in 0..totalSteps) {
            if (cancelled.get()) {
                return FfmpegResult.Cancelled
            }
            val pct = i.toFloat() / totalSteps
            onProgress(
                FfmpegProgress(
                    frameNumber = (i * 30).toLong(),
                    timeMs = (pct * 10_000).toLong(),
                    percentage = pct,
                    fps = 30f,
                    bitrate = 10_000_000L,
                    speed = 1.5f
                )
            )
            delay(stepDelayMs)
        }

        val outputPath = command.arguments.last()
        return FfmpegResult.Success(outputPath = outputPath, durationMs = 10_000L)
    }

    override fun cancel() {
        cancelled.set(true)
    }
}
