package com.gpxvideo.core.model

import java.util.UUID

data class Timeline(
    val id: UUID = UUID.randomUUID(),
    val projectId: UUID,
    val totalDurationMs: Long = 0,
    val tracks: List<TimelineTrack> = emptyList()
)

data class TimelineTrack(
    val id: UUID = UUID.randomUUID(),
    val timelineId: UUID,
    val type: TrackType,
    val order: Int,
    val isLocked: Boolean = false,
    val isVisible: Boolean = true,
    val clips: List<TimelineClip> = emptyList()
)

enum class TrackType { VIDEO, AUDIO, OVERLAY, TEXT, IMAGE }

data class TimelineClip(
    val id: UUID = UUID.randomUUID(),
    val trackId: UUID,
    val mediaItemId: UUID? = null,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val trimStartMs: Long = 0,
    val trimEndMs: Long = 0,
    val transitions: ClipTransitions = ClipTransitions(),
    val adjustments: ClipAdjustments = ClipAdjustments()
)

data class ClipTransitions(
    val entryTransition: Transition? = null,
    val exitTransition: Transition? = null
)

data class Transition(
    val type: TransitionType,
    val durationMs: Long = 500
)

enum class TransitionType {
    CUT, FADE, DISSOLVE, SLIDE_LEFT, SLIDE_RIGHT, WIPE_LEFT, WIPE_RIGHT
}

data class ClipAdjustments(
    val volume: Float = 1.0f,
    val speed: Float = 1.0f,
    val brightness: Float = 0.0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val rotation: Float = 0.0f,
    val scale: Float = 1.0f,
    val positionX: Float = 0.5f,
    val positionY: Float = 0.5f,
    val opacity: Float = 1.0f
)
