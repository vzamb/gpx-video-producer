package com.gpxvideo.feature.export

import com.gpxvideo.core.model.ExportFormat
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.OverlayConfig
import com.gpxvideo.core.model.OutputSettings
import com.gpxvideo.core.model.Transition
import com.gpxvideo.feature.overlays.GpxTimeSyncEngine
import com.gpxvideo.lib.gpxparser.GpxStats
import java.util.UUID

data class ExportConfig(
    val projectId: UUID,
    val clips: List<ExportClip>,
    val overlays: List<ExportOverlay>,
    val outputSettings: OutputSettings,
    val outputPath: String,
    val gpxData: GpxData?,
    val gpxStats: GpxStats?,
    val syncEngine: GpxTimeSyncEngine?,
    val projectWidth: Int = 1920,
    val projectHeight: Int = 1080,
    val storyTemplate: String? = null,
    val activityTitle: String = ""
)

data class ExportClip(
    val filePath: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val speed: Float,
    val volume: Float,
    val transition: Transition?
)

data class ExportOverlay(
    val overlayConfig: OverlayConfig,
    val startTimeMs: Long,
    val endTimeMs: Long
)

enum class ExportPhase(val displayName: String) {
    PREPARING("Preparing..."),
    RENDERING_OVERLAYS("Rendering overlays..."),
    ENCODING("Encoding video..."),
    MIXING_AUDIO("Mixing audio..."),
    FINALIZING("Finalizing...")
}

fun OutputSettings.videoCodecName(): String = when (format) {
    ExportFormat.MP4_H264 -> "libx264"
    ExportFormat.MP4_H265 -> "libx265"
    ExportFormat.WEBM_VP9 -> "libvpx-vp9"
}

fun OutputSettings.audioCodecName(): String = when (format) {
    ExportFormat.MP4_H264, ExportFormat.MP4_H265 -> "aac"
    ExportFormat.WEBM_VP9 -> "libopus"
}
