package com.gpxvideo.core.model

import java.time.Duration
import java.time.Instant
import java.util.UUID

data class GpxFile(
    val id: UUID = UUID.randomUUID(),
    val projectId: UUID,
    val name: String,
    val filePath: String,
    val parsedData: GpxData? = null
)

data class GpxData(
    val tracks: List<GpxTrack>,
    val waypoints: List<GpxWaypoint> = emptyList(),
    val bounds: GeoBounds,
    val totalDistance: Double,
    val totalElevationGain: Double,
    val totalElevationLoss: Double,
    val totalDuration: Duration,
    val startTime: Instant? = null,
    val endTime: Instant? = null
)

data class GpxTrack(
    val name: String? = null,
    val segments: List<GpxSegment>
)

data class GpxSegment(
    val points: List<GpxPoint>
)

data class GpxPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val time: Instant? = null,
    val heartRate: Int? = null,
    val cadence: Int? = null,
    val power: Int? = null,
    val temperature: Double? = null,
    val speed: Double? = null
)

data class GpxWaypoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val name: String? = null,
    val description: String? = null,
    val time: Instant? = null
)

data class GeoBounds(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double
)
