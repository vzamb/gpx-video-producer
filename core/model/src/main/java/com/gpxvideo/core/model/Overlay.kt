package com.gpxvideo.core.model

import java.util.UUID

data class OverlayPosition(
    val x: Float = 0.0f,
    val y: Float = 0.0f,
    val anchor: Anchor = Anchor.TOP_LEFT
)

enum class Anchor {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

data class OverlaySize(
    val width: Float = 0.2f,
    val height: Float = 0.2f
)

data class OverlayStyle(
    val backgroundColor: Long? = null,
    val borderColor: Long? = null,
    val borderWidth: Float = 0f,
    val cornerRadius: Float = 8f,
    val opacity: Float = 1.0f,
    val fontFamily: String? = null,
    val fontSize: Float = 14f,
    val fontColor: Long = 0xFFFFFFFF,
    val shadowEnabled: Boolean = false,
    val shadowColor: Long = 0x80000000,
    val shadowRadius: Float = 4f
)

sealed class OverlayConfig {
    abstract val id: UUID
    abstract val projectId: UUID
    abstract val name: String
    abstract val timelineClipId: UUID
    abstract val position: OverlayPosition
    abstract val size: OverlaySize
    abstract val style: OverlayStyle

    data class StaticAltitudeProfile(
        override val id: UUID = UUID.randomUUID(),
        override val projectId: UUID,
        override val name: String = "Altitude Profile",
        override val timelineClipId: UUID,
        override val position: OverlayPosition = OverlayPosition(),
        override val size: OverlaySize = OverlaySize(0.4f, 0.15f),
        override val style: OverlayStyle = OverlayStyle(),
        val lineColor: Long = 0xFF4CAF50,
        val fillColor: Long = 0x804CAF50,
        val showGrid: Boolean = true,
        val showLabels: Boolean = true
    ) : OverlayConfig()

    data class StaticMap(
        override val id: UUID = UUID.randomUUID(),
        override val projectId: UUID,
        override val name: String = "GPS Map",
        override val timelineClipId: UUID,
        override val position: OverlayPosition = OverlayPosition(),
        override val size: OverlaySize = OverlaySize(0.25f, 0.25f),
        override val style: OverlayStyle = OverlayStyle(),
        val routeColor: Long = 0xFFFF5722,
        val routeWidth: Float = 3f,
        val showStartEnd: Boolean = true,
        val mapStyle: MapStyle = MapStyle.MINIMAL
    ) : OverlayConfig()

    data class StaticStats(
        override val id: UUID = UUID.randomUUID(),
        override val projectId: UUID,
        override val name: String = "Statistics",
        override val timelineClipId: UUID,
        override val position: OverlayPosition = OverlayPosition(),
        override val size: OverlaySize = OverlaySize(0.3f, 0.2f),
        override val style: OverlayStyle = OverlayStyle(),
        val fields: List<StatField> = listOf(StatField.TOTAL_DISTANCE, StatField.TOTAL_TIME),
        val layout: StatsLayout = StatsLayout.GRID_2X2
    ) : OverlayConfig()

    data class DynamicAltitudeProfile(
        override val id: UUID = UUID.randomUUID(),
        override val projectId: UUID,
        override val name: String = "Live Altitude",
        override val timelineClipId: UUID,
        override val position: OverlayPosition = OverlayPosition(),
        override val size: OverlaySize = OverlaySize(0.4f, 0.15f),
        override val style: OverlayStyle = OverlayStyle(),
        val lineColor: Long = 0xFF4CAF50,
        val markerColor: Long = 0xFFFF5722,
        val trailColor: Long = 0x804CAF50,
        val syncMode: SyncMode = SyncMode.GPX_TIMESTAMP
    ) : OverlayConfig()

    data class DynamicMap(
        override val id: UUID = UUID.randomUUID(),
        override val projectId: UUID,
        override val name: String = "Live Map",
        override val timelineClipId: UUID,
        override val position: OverlayPosition = OverlayPosition(),
        override val size: OverlaySize = OverlaySize(0.25f, 0.25f),
        override val style: OverlayStyle = OverlayStyle(),
        val mapStyle: MapStyle = MapStyle.MINIMAL,
        val routeColor: Long = 0xFFFF5722,
        val showTrail: Boolean = true,
        val followPosition: Boolean = true,
        val syncMode: SyncMode = SyncMode.GPX_TIMESTAMP
    ) : OverlayConfig()

    data class DynamicStat(
        override val id: UUID = UUID.randomUUID(),
        override val projectId: UUID,
        override val name: String = "Live Stat",
        override val timelineClipId: UUID,
        override val position: OverlayPosition = OverlayPosition(),
        override val size: OverlaySize = OverlaySize(0.15f, 0.08f),
        override val style: OverlayStyle = OverlayStyle(),
        val field: DynamicField = DynamicField.CURRENT_SPEED,
        val syncMode: SyncMode = SyncMode.GPX_TIMESTAMP,
        val format: String = ""
    ) : OverlayConfig()
}

enum class MapStyle { MINIMAL, DARK, TERRAIN, SATELLITE }

enum class StatField(val displayName: String, val unit: String) {
    TOTAL_DISTANCE("Distance", "km"),
    TOTAL_TIME("Time", ""),
    MOVING_TIME("Moving Time", ""),
    TOTAL_ELEVATION_GAIN("Elevation Gain", "m"),
    TOTAL_ELEVATION_LOSS("Elevation Loss", "m"),
    AVG_SPEED("Avg Speed", "km/h"),
    MAX_SPEED("Max Speed", "km/h"),
    AVG_PACE("Avg Pace", "min/km"),
    BEST_PACE("Best Pace", "min/km"),
    AVG_HEART_RATE("Avg HR", "bpm"),
    MAX_HEART_RATE("Max HR", "bpm"),
    AVG_CADENCE("Avg Cadence", "rpm"),
    AVG_POWER("Avg Power", "W"),
    NORMALIZED_POWER("Norm. Power", "W"),
    CALORIES("Calories", "kcal"),
    AVG_TEMPERATURE("Avg Temp", "°C")
}

enum class StatsLayout { SINGLE, GRID_2X1, GRID_2X2, GRID_3X2, GRID_4X2, VERTICAL_LIST }

enum class SyncMode { GPX_TIMESTAMP, MANUAL_KEYFRAMES }

enum class DynamicField(val displayName: String, val defaultUnit: String) {
    CURRENT_SPEED("Speed", "km/h"),
    CURRENT_PACE("Pace", "min/km"),
    CURRENT_ELEVATION("Elevation", "m"),
    CURRENT_HEART_RATE("Heart Rate", "bpm"),
    CURRENT_CADENCE("Cadence", "rpm"),
    CURRENT_POWER("Power", "W"),
    CURRENT_TEMPERATURE("Temperature", "°C"),
    CURRENT_GRADE("Grade", "%"),
    ELAPSED_TIME("Elapsed Time", ""),
    ELAPSED_DISTANCE("Elapsed Distance", "km"),
    REMAINING_TIME("Remaining Time", ""),
    REMAINING_DISTANCE("Remaining Distance", "km")
}
