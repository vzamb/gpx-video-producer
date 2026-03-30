package com.gpxvideo.feature.timeline

data class KenBurnsConfig(
    val startScale: Float = 1.0f,
    val endScale: Float = 1.3f,
    val startX: Float = 0.5f,
    val startY: Float = 0.5f,
    val endX: Float = 0.5f,
    val endY: Float = 0.4f,
    val easingType: EasingType = EasingType.EASE_IN_OUT
)

enum class EasingType {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT;

    fun toDisplayName(): String = when (this) {
        LINEAR -> "Linear"
        EASE_IN -> "Ease In"
        EASE_OUT -> "Ease Out"
        EASE_IN_OUT -> "Ease In/Out"
    }
}
