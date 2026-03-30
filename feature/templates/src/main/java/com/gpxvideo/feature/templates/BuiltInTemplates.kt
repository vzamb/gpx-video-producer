package com.gpxvideo.feature.templates

import com.gpxvideo.core.model.Anchor
import com.gpxvideo.core.model.OutputSettings
import com.gpxvideo.core.model.OverlayPosition
import com.gpxvideo.core.model.OverlayPreset
import com.gpxvideo.core.model.OverlaySize
import com.gpxvideo.core.model.OverlayStyle
import com.gpxvideo.core.model.Resolution
import com.gpxvideo.core.model.SportType
import com.gpxvideo.core.model.StylePreset
import com.gpxvideo.core.model.Template
import com.gpxvideo.core.model.TemplateTrack
import com.gpxvideo.core.model.TrackType
import com.gpxvideo.core.model.TransitionType
import java.time.Instant
import java.util.UUID

object BuiltInTemplates {

    private val epoch = Instant.EPOCH

    val all: List<Template> = listOf(
        cyclingClassic(),
        trailRunner(),
        hikersJournal(),
        skiDay(),
        minimalist(),
        fullDashboard(),
        photoSlideshow(),
        raceRecap()
    )

    private fun deterministicId(name: String): UUID =
        UUID.nameUUIDFromBytes("builtin-template-$name".toByteArray())

    private fun cyclingClassic() = Template(
        id = deterministicId("cycling-classic"),
        name = "Cycling Classic",
        description = "Clean cycling overlay with map, altitude profile, speed, and heart rate. Perfect for road and gravel rides.",
        sportType = SportType.CYCLING,
        isBuiltIn = true,
        createdAt = epoch,
        trackLayout = listOf(
            TemplateTrack(type = TrackType.VIDEO, order = 0, label = "Main Video"),
            TemplateTrack(type = TrackType.OVERLAY, order = 1, label = "Data Overlays")
        ),
        overlayPresets = listOf(
            OverlayPreset(
                overlayType = "dynamic_map",
                defaultPosition = OverlayPosition(x = 0.78f, y = 0.02f, anchor = Anchor.TOP_RIGHT),
                defaultSize = OverlaySize(width = 0.2f, height = 0.2f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCC000000,
                    cornerRadius = 12f,
                    opacity = 0.95f,
                    fontColor = 0xFFFFFFFF
                ),
                config = mapOf("mapStyle" to "MINIMAL", "showTrail" to "true", "followPosition" to "true")
            ),
            OverlayPreset(
                overlayType = "dynamic_altitude_profile",
                defaultPosition = OverlayPosition(x = 0.3f, y = 0.86f, anchor = Anchor.BOTTOM_CENTER),
                defaultSize = OverlaySize(width = 0.4f, height = 0.12f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCC000000,
                    cornerRadius = 8f,
                    opacity = 0.9f,
                    fontColor = 0xFFFFFFFF
                )
            ),
            OverlayPreset(
                overlayType = "dynamic_stat",
                defaultPosition = OverlayPosition(x = 0.85f, y = 0.75f, anchor = Anchor.BOTTOM_RIGHT),
                defaultSize = OverlaySize(width = 0.13f, height = 0.07f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCC000000,
                    cornerRadius = 8f,
                    fontColor = 0xFFFFFFFF,
                    fontSize = 16f
                ),
                config = mapOf("field" to "CURRENT_SPEED")
            ),
            OverlayPreset(
                overlayType = "dynamic_stat",
                defaultPosition = OverlayPosition(x = 0.85f, y = 0.84f, anchor = Anchor.BOTTOM_RIGHT),
                defaultSize = OverlaySize(width = 0.13f, height = 0.07f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCC000000,
                    cornerRadius = 8f,
                    fontColor = 0xFFFFFFFF,
                    fontSize = 16f
                ),
                config = mapOf("field" to "CURRENT_HEART_RATE")
            )
        ),
        outputSettings = OutputSettings(resolution = Resolution.FHD_1080P, frameRate = 30),
        stylePreset = StylePreset(
            primaryColor = 0xFFFF5722,
            secondaryColor = 0xFF2196F3,
            accentColor = 0xFFFFC107,
            fontFamily = "Inter",
            backgroundOverlayColor = 0xCC000000,
            transitionType = TransitionType.DISSOLVE,
            transitionDurationMs = 500
        )
    )

    private fun trailRunner() = Template(
        id = deterministicId("trail-runner"),
        name = "Trail Runner",
        description = "Earthy tones with altitude profile, pace, and elevation gain overlays for trail running adventures.",
        sportType = SportType.TRAIL_RUNNING,
        isBuiltIn = true,
        createdAt = epoch,
        trackLayout = listOf(
            TemplateTrack(type = TrackType.VIDEO, order = 0, label = "Main Video"),
            TemplateTrack(type = TrackType.OVERLAY, order = 1, label = "Data Overlays")
        ),
        overlayPresets = listOf(
            OverlayPreset(
                overlayType = "dynamic_altitude_profile",
                defaultPosition = OverlayPosition(x = 0.25f, y = 0.83f, anchor = Anchor.BOTTOM_CENTER),
                defaultSize = OverlaySize(width = 0.5f, height = 0.15f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCC2E1B0E,
                    cornerRadius = 10f,
                    opacity = 0.9f,
                    fontColor = 0xFFFFF3E0
                )
            ),
            OverlayPreset(
                overlayType = "dynamic_stat",
                defaultPosition = OverlayPosition(x = 0.02f, y = 0.92f, anchor = Anchor.BOTTOM_LEFT),
                defaultSize = OverlaySize(width = 0.14f, height = 0.07f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCC2E1B0E,
                    cornerRadius = 8f,
                    fontColor = 0xFFFF9800,
                    fontSize = 16f
                ),
                config = mapOf("field" to "CURRENT_PACE")
            ),
            OverlayPreset(
                overlayType = "dynamic_stat",
                defaultPosition = OverlayPosition(x = 0.02f, y = 0.83f, anchor = Anchor.BOTTOM_LEFT),
                defaultSize = OverlaySize(width = 0.14f, height = 0.07f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCC2E1B0E,
                    cornerRadius = 8f,
                    fontColor = 0xFF4CAF50,
                    fontSize = 16f
                ),
                config = mapOf("field" to "CURRENT_ELEVATION")
            )
        ),
        outputSettings = OutputSettings(resolution = Resolution.FHD_1080P, frameRate = 30),
        stylePreset = StylePreset(
            primaryColor = 0xFFFF9800,
            secondaryColor = 0xFF4CAF50,
            accentColor = 0xFF8D6E63,
            fontFamily = "Inter",
            backgroundOverlayColor = 0xCC2E1B0E,
            transitionType = TransitionType.DISSOLVE,
            transitionDurationMs = 600
        )
    )

    private fun hikersJournal() = Template(
        id = deterministicId("hikers-journal"),
        name = "Hiker's Journal",
        description = "Nature-inspired layout with a large map, stats panel, and text track for captions. Ideal for scenic hikes.",
        sportType = SportType.HIKING,
        isBuiltIn = true,
        createdAt = epoch,
        trackLayout = listOf(
            TemplateTrack(type = TrackType.VIDEO, order = 0, label = "Main Video"),
            TemplateTrack(type = TrackType.OVERLAY, order = 1, label = "Map & Stats"),
            TemplateTrack(type = TrackType.TEXT, order = 2, label = "Captions")
        ),
        overlayPresets = listOf(
            OverlayPreset(
                overlayType = "static_map",
                defaultPosition = OverlayPosition(x = 0.72f, y = 0.05f, anchor = Anchor.TOP_RIGHT),
                defaultSize = OverlaySize(width = 0.26f, height = 0.4f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCC1B3A1B,
                    cornerRadius = 12f,
                    opacity = 0.9f,
                    fontColor = 0xFFC8E6C9
                ),
                config = mapOf("mapStyle" to "TERRAIN", "showStartEnd" to "true")
            ),
            OverlayPreset(
                overlayType = "static_stats",
                defaultPosition = OverlayPosition(x = 0.72f, y = 0.5f, anchor = Anchor.CENTER_RIGHT),
                defaultSize = OverlaySize(width = 0.26f, height = 0.2f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCC1B3A1B,
                    cornerRadius = 12f,
                    opacity = 0.9f,
                    fontColor = 0xFFC8E6C9,
                    fontSize = 12f
                ),
                config = mapOf(
                    "fields" to "TOTAL_DISTANCE,TOTAL_TIME,TOTAL_ELEVATION_GAIN,AVG_SPEED",
                    "layout" to "GRID_2X2"
                )
            )
        ),
        outputSettings = OutputSettings(resolution = Resolution.FHD_1080P, frameRate = 24),
        stylePreset = StylePreset(
            primaryColor = 0xFF4CAF50,
            secondaryColor = 0xFF81C784,
            accentColor = 0xFFA5D6A7,
            fontFamily = "Inter",
            backgroundOverlayColor = 0xCC1B3A1B,
            transitionType = TransitionType.FADE,
            transitionDurationMs = 800
        )
    )

    private fun skiDay() = Template(
        id = deterministicId("ski-day"),
        name = "Ski Day",
        description = "Cool blue and white theme with prominent speed gauge, altitude, and temperature for skiing and snowboarding.",
        sportType = SportType.SKIING,
        isBuiltIn = true,
        createdAt = epoch,
        trackLayout = listOf(
            TemplateTrack(type = TrackType.VIDEO, order = 0, label = "Main Video"),
            TemplateTrack(type = TrackType.OVERLAY, order = 1, label = "Data Overlays")
        ),
        overlayPresets = listOf(
            OverlayPreset(
                overlayType = "dynamic_stat",
                defaultPosition = OverlayPosition(x = 0.04f, y = 0.7f, anchor = Anchor.BOTTOM_LEFT),
                defaultSize = OverlaySize(width = 0.2f, height = 0.12f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCC0D47A1,
                    cornerRadius = 12f,
                    fontColor = 0xFFE3F2FD,
                    fontSize = 22f,
                    shadowEnabled = true,
                    shadowColor = 0x80000000,
                    shadowRadius = 6f
                ),
                config = mapOf("field" to "CURRENT_SPEED")
            ),
            OverlayPreset(
                overlayType = "dynamic_stat",
                defaultPosition = OverlayPosition(x = 0.04f, y = 0.84f, anchor = Anchor.BOTTOM_LEFT),
                defaultSize = OverlaySize(width = 0.15f, height = 0.07f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCC0D47A1,
                    cornerRadius = 8f,
                    fontColor = 0xFFE3F2FD,
                    fontSize = 16f
                ),
                config = mapOf("field" to "CURRENT_ELEVATION")
            ),
            OverlayPreset(
                overlayType = "dynamic_stat",
                defaultPosition = OverlayPosition(x = 0.04f, y = 0.93f, anchor = Anchor.BOTTOM_LEFT),
                defaultSize = OverlaySize(width = 0.15f, height = 0.07f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCC0D47A1,
                    cornerRadius = 8f,
                    fontColor = 0xFFE3F2FD,
                    fontSize = 16f
                ),
                config = mapOf("field" to "CURRENT_TEMPERATURE")
            )
        ),
        outputSettings = OutputSettings(resolution = Resolution.FHD_1080P, frameRate = 30),
        stylePreset = StylePreset(
            primaryColor = 0xFF2196F3,
            secondaryColor = 0xFFE3F2FD,
            accentColor = 0xFF64B5F6,
            fontFamily = "Inter",
            backgroundOverlayColor = 0xCC0D47A1,
            transitionType = TransitionType.DISSOLVE,
            transitionDurationMs = 400
        )
    )

    private fun minimalist() = Template(
        id = deterministicId("minimalist"),
        name = "Minimalist",
        description = "Clean and distraction-free with just a small map and elapsed time. Works for any sport.",
        sportType = null,
        isBuiltIn = true,
        createdAt = epoch,
        trackLayout = listOf(
            TemplateTrack(type = TrackType.VIDEO, order = 0, label = "Main Video"),
            TemplateTrack(type = TrackType.OVERLAY, order = 1, label = "Overlays")
        ),
        overlayPresets = listOf(
            OverlayPreset(
                overlayType = "dynamic_map",
                defaultPosition = OverlayPosition(x = 0.02f, y = 0.02f, anchor = Anchor.TOP_LEFT),
                defaultSize = OverlaySize(width = 0.15f, height = 0.15f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xB3000000,
                    cornerRadius = 10f,
                    opacity = 0.85f,
                    fontColor = 0xFFFFFFFF
                ),
                config = mapOf("mapStyle" to "DARK", "showTrail" to "true", "followPosition" to "true")
            ),
            OverlayPreset(
                overlayType = "dynamic_stat",
                defaultPosition = OverlayPosition(x = 0.02f, y = 0.93f, anchor = Anchor.BOTTOM_LEFT),
                defaultSize = OverlaySize(width = 0.12f, height = 0.05f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xB3000000,
                    cornerRadius = 6f,
                    fontColor = 0xFFFFFFFF,
                    fontSize = 14f
                ),
                config = mapOf("field" to "ELAPSED_TIME")
            )
        ),
        outputSettings = OutputSettings(resolution = Resolution.FHD_1080P, frameRate = 30),
        stylePreset = StylePreset(
            primaryColor = 0xFFFFFFFF,
            secondaryColor = 0xFF9E9E9E,
            accentColor = 0xFFBDBDBD,
            fontFamily = "Inter",
            backgroundOverlayColor = 0xB3000000,
            transitionType = TransitionType.CUT,
            transitionDurationMs = 0
        )
    )

    private fun fullDashboard() = Template(
        id = deterministicId("full-dashboard"),
        name = "Full Dashboard",
        description = "Comprehensive data display with map, altitude, speed, heart rate, cadence, and distance. For data lovers.",
        sportType = null,
        isBuiltIn = true,
        createdAt = epoch,
        trackLayout = listOf(
            TemplateTrack(type = TrackType.VIDEO, order = 0, label = "Main Video"),
            TemplateTrack(type = TrackType.OVERLAY, order = 1, label = "Dashboard Overlays")
        ),
        overlayPresets = listOf(
            OverlayPreset(
                overlayType = "dynamic_map",
                defaultPosition = OverlayPosition(x = 0.02f, y = 0.02f, anchor = Anchor.TOP_LEFT),
                defaultSize = OverlaySize(width = 0.18f, height = 0.18f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCC1A1A2E,
                    cornerRadius = 10f,
                    opacity = 0.95f,
                    fontColor = 0xFFFFFFFF
                ),
                config = mapOf("mapStyle" to "DARK", "showTrail" to "true", "followPosition" to "true")
            ),
            OverlayPreset(
                overlayType = "dynamic_altitude_profile",
                defaultPosition = OverlayPosition(x = 0.22f, y = 0.02f, anchor = Anchor.TOP_LEFT),
                defaultSize = OverlaySize(width = 0.3f, height = 0.1f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCC1A1A2E,
                    cornerRadius = 8f,
                    fontColor = 0xFFFFFFFF,
                    fontSize = 10f
                )
            ),
            OverlayPreset(
                overlayType = "dynamic_stat",
                defaultPosition = OverlayPosition(x = 0.02f, y = 0.88f, anchor = Anchor.BOTTOM_LEFT),
                defaultSize = OverlaySize(width = 0.11f, height = 0.06f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCC1A1A2E,
                    cornerRadius = 6f,
                    fontColor = 0xFF4FC3F7,
                    fontSize = 13f
                ),
                config = mapOf("field" to "CURRENT_SPEED")
            ),
            OverlayPreset(
                overlayType = "dynamic_stat",
                defaultPosition = OverlayPosition(x = 0.15f, y = 0.88f, anchor = Anchor.BOTTOM_LEFT),
                defaultSize = OverlaySize(width = 0.11f, height = 0.06f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCC1A1A2E,
                    cornerRadius = 6f,
                    fontColor = 0xFFEF5350,
                    fontSize = 13f
                ),
                config = mapOf("field" to "CURRENT_HEART_RATE")
            ),
            OverlayPreset(
                overlayType = "dynamic_stat",
                defaultPosition = OverlayPosition(x = 0.28f, y = 0.88f, anchor = Anchor.BOTTOM_LEFT),
                defaultSize = OverlaySize(width = 0.11f, height = 0.06f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCC1A1A2E,
                    cornerRadius = 6f,
                    fontColor = 0xFF66BB6A,
                    fontSize = 13f
                ),
                config = mapOf("field" to "CURRENT_CADENCE")
            ),
            OverlayPreset(
                overlayType = "dynamic_stat",
                defaultPosition = OverlayPosition(x = 0.02f, y = 0.95f, anchor = Anchor.BOTTOM_LEFT),
                defaultSize = OverlaySize(width = 0.11f, height = 0.06f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCC1A1A2E,
                    cornerRadius = 6f,
                    fontColor = 0xFFFFFFFF,
                    fontSize = 13f
                ),
                config = mapOf("field" to "ELAPSED_TIME")
            ),
            OverlayPreset(
                overlayType = "dynamic_stat",
                defaultPosition = OverlayPosition(x = 0.15f, y = 0.95f, anchor = Anchor.BOTTOM_LEFT),
                defaultSize = OverlaySize(width = 0.11f, height = 0.06f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCC1A1A2E,
                    cornerRadius = 6f,
                    fontColor = 0xFFFFFFFF,
                    fontSize = 13f
                ),
                config = mapOf("field" to "ELAPSED_DISTANCE")
            )
        ),
        outputSettings = OutputSettings(resolution = Resolution.FHD_1080P, frameRate = 30),
        stylePreset = StylePreset(
            primaryColor = 0xFF4FC3F7,
            secondaryColor = 0xFFEF5350,
            accentColor = 0xFF66BB6A,
            fontFamily = "Inter",
            backgroundOverlayColor = 0xCC1A1A2E,
            transitionType = TransitionType.CUT,
            transitionDurationMs = 0
        )
    )

    private fun photoSlideshow() = Template(
        id = deterministicId("photo-slideshow"),
        name = "Photo Slideshow",
        description = "Image-focused layout with fade transitions and a stats summary at the end. Great for trip recaps.",
        sportType = null,
        isBuiltIn = true,
        createdAt = epoch,
        trackLayout = listOf(
            TemplateTrack(type = TrackType.IMAGE, order = 0, label = "Photos"),
            TemplateTrack(type = TrackType.AUDIO, order = 1, label = "Music"),
            TemplateTrack(type = TrackType.OVERLAY, order = 2, label = "Stats Overlay")
        ),
        overlayPresets = listOf(
            OverlayPreset(
                overlayType = "static_stats",
                defaultPosition = OverlayPosition(x = 0.5f, y = 0.5f, anchor = Anchor.CENTER),
                defaultSize = OverlaySize(width = 0.5f, height = 0.3f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCC212121,
                    cornerRadius = 16f,
                    opacity = 0.95f,
                    fontColor = 0xFFFFFFFF,
                    fontSize = 16f
                ),
                config = mapOf(
                    "fields" to "TOTAL_DISTANCE,TOTAL_TIME,TOTAL_ELEVATION_GAIN,AVG_SPEED",
                    "layout" to "GRID_2X2"
                )
            )
        ),
        outputSettings = OutputSettings(resolution = Resolution.FHD_1080P, frameRate = 24),
        stylePreset = StylePreset(
            primaryColor = 0xFFFFFFFF,
            secondaryColor = 0xFF757575,
            accentColor = 0xFFFFC107,
            fontFamily = "Inter",
            backgroundOverlayColor = 0xCC212121,
            transitionType = TransitionType.FADE,
            transitionDurationMs = 1000
        )
    )

    private fun raceRecap() = Template(
        id = deterministicId("race-recap"),
        name = "Race Recap",
        description = "Sporty red and yellow theme with pace, heart rate, and a stats panel highlighting your finish time.",
        sportType = SportType.RUNNING,
        isBuiltIn = true,
        createdAt = epoch,
        trackLayout = listOf(
            TemplateTrack(type = TrackType.VIDEO, order = 0, label = "Main Video"),
            TemplateTrack(type = TrackType.OVERLAY, order = 1, label = "Data Overlays")
        ),
        overlayPresets = listOf(
            OverlayPreset(
                overlayType = "dynamic_stat",
                defaultPosition = OverlayPosition(x = 0.02f, y = 0.88f, anchor = Anchor.BOTTOM_LEFT),
                defaultSize = OverlaySize(width = 0.15f, height = 0.08f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCCB71C1C,
                    cornerRadius = 10f,
                    fontColor = 0xFFFFF9C4,
                    fontSize = 18f,
                    shadowEnabled = true,
                    shadowColor = 0x80000000,
                    shadowRadius = 4f
                ),
                config = mapOf("field" to "CURRENT_PACE")
            ),
            OverlayPreset(
                overlayType = "dynamic_stat",
                defaultPosition = OverlayPosition(x = 0.19f, y = 0.88f, anchor = Anchor.BOTTOM_LEFT),
                defaultSize = OverlaySize(width = 0.15f, height = 0.08f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCCB71C1C,
                    cornerRadius = 10f,
                    fontColor = 0xFFFFF9C4,
                    fontSize = 18f
                ),
                config = mapOf("field" to "CURRENT_HEART_RATE")
            ),
            OverlayPreset(
                overlayType = "static_stats",
                defaultPosition = OverlayPosition(x = 0.78f, y = 0.7f, anchor = Anchor.CENTER_RIGHT),
                defaultSize = OverlaySize(width = 0.2f, height = 0.25f),
                defaultStyle = OverlayStyle(
                    backgroundColor = 0xCCB71C1C,
                    cornerRadius = 12f,
                    fontColor = 0xFFFFF9C4,
                    fontSize = 13f
                ),
                config = mapOf(
                    "fields" to "TOTAL_TIME,TOTAL_DISTANCE,AVG_PACE,AVG_HEART_RATE",
                    "layout" to "GRID_2X2"
                )
            )
        ),
        outputSettings = OutputSettings(resolution = Resolution.FHD_1080P, frameRate = 30),
        stylePreset = StylePreset(
            primaryColor = 0xFFF44336,
            secondaryColor = 0xFFFFEB3B,
            accentColor = 0xFFFF9800,
            fontFamily = "Inter",
            backgroundOverlayColor = 0xCCB71C1C,
            transitionType = TransitionType.DISSOLVE,
            transitionDurationMs = 500
        )
    )
}
