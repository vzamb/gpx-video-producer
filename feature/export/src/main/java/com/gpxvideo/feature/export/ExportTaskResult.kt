package com.gpxvideo.feature.export

sealed class ExportTaskResult {
    data class Success(val outputPath: String, val durationMs: Long) : ExportTaskResult()
    data class Error(val message: String, val returnCode: Int, val logs: String) : ExportTaskResult()
    data object Cancelled : ExportTaskResult()
}
