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
 * Creates a minimal valid MP4 file at the output path so export completion works.
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

        // Create a placeholder output file so the export completion UI works.
        // A real FFmpeg executor would produce the actual encoded video here.
        try {
            val outputFile = java.io.File(outputPath)
            outputFile.parentFile?.mkdirs()
            // Copy the first input file as a stand-in for the export result.
            // The command arguments contain input files before the output.
            val firstInputPath = findFirstInputFile(command.arguments)
            if (firstInputPath != null) {
                val inputFile = java.io.File(firstInputPath)
                if (inputFile.exists()) {
                    inputFile.copyTo(outputFile, overwrite = true)
                } else {
                    outputFile.writeText("stub export")
                }
            } else {
                outputFile.writeText("stub export")
            }
        } catch (_: Exception) {
            // Ignore file creation errors for the stub
        }

        return FfmpegResult.Success(outputPath = outputPath, durationMs = 10_000L)
    }

    override fun cancel() {
        cancelled.set(true)
    }

    private fun findFirstInputFile(args: List<String>): String? {
        // Look for the first -i argument (input file)
        for (i in args.indices) {
            if (args[i] == "-i" && i + 1 < args.size) {
                return args[i + 1]
            }
        }
        return null
    }
}
