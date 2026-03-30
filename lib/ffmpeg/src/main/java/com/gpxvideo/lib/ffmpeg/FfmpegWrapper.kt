package com.gpxvideo.lib.ffmpeg

/**
 * Wrapper around FFmpeg-kit for executing FFmpeg commands.
 * Provides coroutine-based API for video encoding, muxing, and filter operations.
 *
 * Currently uses [StubFfmpegExecutor]. When ffmpeg-kit is added,
 * replace the binding in [FfmpegModule] with a real implementation.
 */
object FfmpegWrapper {
    const val TAG = "FfmpegWrapper"
}
