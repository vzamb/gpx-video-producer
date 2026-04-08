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
    val aspectRatio: SocialAspectRatio = SocialAspectRatio.LANDSCAPE_16_9,
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

enum class SocialAspectRatio(
    val displayName: String,
    val description: String,
    val width: Int,
    val height: Int,
    val icon: String
) {
    LANDSCAPE_16_9("16:9", "YouTube, Twitter", 1920, 1080, "📺"),
    PORTRAIT_9_16("9:16", "Reels, TikTok, Shorts", 1080, 1920, "📱"),
    SQUARE_1_1("1:1", "Instagram Post", 1080, 1080, "⬜"),
    PORTRAIT_4_5("4:5", "Instagram Feed", 1080, 1350, "📷")
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
    /** Static: show final totals — no animation, all stats at their end value. */
    STATIC(
        "Static",
        "Show final activity totals"
    ),
    /** Fast Forward: proportionally map entire GPX across video duration (animated). */
    FAST_FORWARD(
        "Fast Forward",
        "Animate the full activity across the video"
    ),
    /** Live Sync: match video timestamps to GPX timestamps per clip. */
    LIVE_SYNC(
        "Live Sync",
        "Real-time telemetry synced to each clip"
    )
}

/**
 * Per-clip GPX sync point for Live Sync mode.
 * Maps a video clip to a position on the GPX track.
 */
data class ClipSyncPoint(
    val clipId: java.util.UUID,
    /** Index into GPX points array, or -1 for auto (timestamp-based). */
    val gpxPointIndex: Int = -1,
    /** Distance along the track in meters where this clip starts. */
    val gpxDistanceMeters: Double = 0.0,
    /** Whether this clip has been manually synced by the user. */
    val isSynced: Boolean = false
)

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
    ),
    /** Futuristic HUD with scan lines, geometric brackets, and prominent title. */
    FUTURISTIC(
        "Futuristic",
        "Sci-fi HUD with title and animated telemetry"
    )
}
