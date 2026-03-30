package com.gpxvideo.core.model

import java.time.Instant
import java.util.UUID

data class Template(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val description: String? = null,
    val sportType: SportType? = null,
    val thumbnailPath: String? = null,
    val isBuiltIn: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val trackLayout: List<TemplateTrack> = emptyList(),
    val overlayPresets: List<OverlayPreset> = emptyList(),
    val outputSettings: OutputSettings = OutputSettings(),
    val stylePreset: StylePreset = StylePreset()
)

data class TemplateTrack(
    val type: TrackType,
    val order: Int,
    val label: String? = null
)

data class OverlayPreset(
    val overlayType: String,
    val defaultPosition: OverlayPosition = OverlayPosition(),
    val defaultSize: OverlaySize = OverlaySize(),
    val defaultStyle: OverlayStyle = OverlayStyle(),
    val config: Map<String, String> = emptyMap()
)

data class StylePreset(
    val primaryColor: Long = 0xFFFF5722,
    val secondaryColor: Long = 0xFF4CAF50,
    val accentColor: Long = 0xFFFFC107,
    val fontFamily: String = "Inter",
    val backgroundOverlayColor: Long = 0x80000000,
    val transitionType: TransitionType = TransitionType.DISSOLVE,
    val transitionDurationMs: Long = 500
)
