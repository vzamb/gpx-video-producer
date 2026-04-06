package com.gpxvideo.core.model

import java.time.Instant
import java.util.UUID

data class Project(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val description: String? = null,
    val sportType: SportType = SportType.OTHER,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val thumbnailPath: String? = null,
    val outputSettings: OutputSettings = OutputSettings(),
    val templateId: UUID? = null
)

enum class SportType(val displayName: String, val icon: String) {
    CYCLING("Cycling", "🚴"),
    RUNNING("Running", "🏃"),
    HIKING("Hiking", "🥾"),
    TRAIL_RUNNING("Trail Running", "🏔️"),
    SKIING("Skiing", "⛷️"),
    SNOWBOARDING("Snowboarding", "🏂"),
    SWIMMING("Swimming", "🏊"),
    KAYAKING("Kayaking", "🛶"),
    CLIMBING("Climbing", "🧗"),
    MULTI_SPORT("Multi-Sport", "🏅"),
    OTHER("Other", "🏋️")
}

data class OutputSettings(
    val resolution: Resolution = Resolution.FHD_1080P,
    val frameRate: Int = 30,
    val format: ExportFormat = ExportFormat.MP4_H264,
    val bitrateBps: Long = 10_000_000L,
    val audioCodec: AudioCodec = AudioCodec.AAC
)

enum class Resolution(val width: Int, val height: Int, val displayName: String) {
    HD_720P(1280, 720, "720p HD"),
    FHD_1080P(1920, 1080, "1080p Full HD"),
    QHD_1440P(2560, 1440, "1440p QHD"),
    UHD_4K(3840, 2160, "4K UHD")
}

enum class ExportFormat(val displayName: String, val extension: String) {
    MP4_H264("MP4 (H.264)", "mp4"),
    MP4_H265("MP4 (H.265/HEVC)", "mp4"),
    WEBM_VP9("WebM (VP9)", "webm")
}

enum class AudioCodec(val displayName: String) {
    AAC("AAC"),
    OPUS("Opus")
}

/** Synchronization approach for GPX ↔ video alignment. */
enum class StoryMode(val displayName: String, val description: String) {
    /** Mode A: Match video Exif timestamps with GPX real-world timestamps. */
    DOCUMENTARY(
        "Real-Time Sync",
        "Telemetry matches the exact moment each clip was recorded"
    ),
    /** Mode B: Map entire GPX track proportionally across total video duration. */
    HYPER_LAPSE(
        "Journey Summary",
        "Telemetry spans the full activity across your video"
    )
}

/** Pre-configured, uneditable aesthetic overlay layouts. */
enum class StoryTemplate(val displayName: String, val description: String) {
    /** Minimalist small data cards in bottom-left corner. */
    CINEMATIC(
        "Cinematic",
        "Minimalist data cards nestled in the corner"
    ),
    /** Massive distance tracking centered on screen. */
    HERO(
        "Hero",
        "Massive distance tracking, centered and bold"
    ),
    /** Vertical side-panel with comprehensive metrics and mini map. */
    PRO_DASHBOARD(
        "Pro Dashboard",
        "Full metrics panel with route map"
    )
}
