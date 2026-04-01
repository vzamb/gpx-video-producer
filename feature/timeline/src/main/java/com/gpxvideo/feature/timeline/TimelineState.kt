package com.gpxvideo.feature.timeline

import androidx.compose.ui.graphics.Color
import com.gpxvideo.core.model.TrackType
import java.util.UUID

data class TimelineState(
    val tracks: List<TimelineTrackState> = emptyList(),
    val totalDurationMs: Long = 0,
    val playheadPositionMs: Long = 0,
    val isPlaying: Boolean = false,
    val zoomLevel: Float = 1.0f,
    val scrollOffsetPx: Float = 0f,
    val selectedClipId: UUID? = null,
    val selectedTrackId: UUID? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

data class TimelineTrackState(
    val id: UUID,
    val type: TrackType,
    val label: String,
    val order: Int,
    val isLocked: Boolean = false,
    val isVisible: Boolean = true,
    val clips: List<TimelineClipState> = emptyList()
)

data class TimelineClipState(
    val id: UUID,
    val trackId: UUID,
    val mediaItemId: UUID?,
    val label: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val trimStartMs: Long = 0,
    val trimEndMs: Long = 0,
    val thumbnailPath: String? = null,
    val color: Color,
    val speed: Float = 1.0f,
    val volume: Float = 1.0f,
    val entryTransitionType: String? = null,
    val entryTransitionDurationMs: Long? = null,
    val exitTransitionType: String? = null,
    val exitTransitionDurationMs: Long? = null,
    val contentMode: ClipContentMode = ClipContentMode.FIT,
    val positionX: Float = 0.5f,
    val positionY: Float = 0.5f,
    val rotation: Float = 0.0f,
    val scale: Float = 1.0f,
    val opacity: Float = 1.0f,
    val brightness: Float = 0.0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val kenBurnsStartX: Float? = null,
    val kenBurnsStartY: Float? = null,
    val kenBurnsStartScale: Float? = null,
    val kenBurnsEndX: Float? = null,
    val kenBurnsEndY: Float? = null,
    val kenBurnsEndScale: Float? = null
)

enum class ClipContentMode {
    FIT,
    FILL,
    CROP
}

fun TrackType.toColor(): Color = when (this) {
    TrackType.VIDEO -> Color(0xFF2196F3)
    TrackType.IMAGE -> Color(0xFF9C27B0)
    TrackType.AUDIO -> Color(0xFF4CAF50)
    TrackType.OVERLAY -> Color(0xFFFF9800)
    TrackType.TEXT -> Color(0xFFFFC107)
}

fun TrackType.toLabel(): String = when (this) {
    TrackType.VIDEO -> "Video"
    TrackType.IMAGE -> "Image"
    TrackType.AUDIO -> "Audio"
    TrackType.OVERLAY -> "Overlay"
    TrackType.TEXT -> "Text"
}
