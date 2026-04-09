package com.gpxvideo.feature.gpx

import com.gpxvideo.core.model.GeoBounds
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.GpxPoint
import com.gpxvideo.core.model.GpxSegment
import com.gpxvideo.core.model.GpxTrack
import com.gpxvideo.lib.strava.StravaActivity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Singleton
class StravaStreamConverter @Inject constructor() {

    fun convert(activity: StravaActivity, streams: Map<String, JsonArray>): GpxData {
        val latlngData = streams["latlng"]?.map { it.jsonArray }
        val altitudeData = streams["altitude"]?.map { it.jsonPrimitive.double }
        val timeData = streams["time"]?.map { it.jsonPrimitive.int }
        val heartrateData = streams["heartrate"]?.map { runCatching { it.jsonPrimitive.int }.getOrNull() }
        val cadenceData = streams["cadence"]?.map { runCatching { it.jsonPrimitive.int }.getOrNull() }
        val wattsData = streams["watts"]?.map { runCatching { it.jsonPrimitive.int }.getOrNull() }
        val tempData = streams["temp"]?.map { runCatching { it.jsonPrimitive.double }.getOrNull() }
        val distanceData = streams["distance"]?.map { it.jsonPrimitive.double }

        val startTime = Instant.parse(activity.startDate)
        val pointCount = latlngData?.size ?: timeData?.size ?: 0

        if (pointCount == 0) {
            return emptyGpxData(activity)
        }

        val points = mutableListOf<GpxPoint>()
        var totalElevGain = 0.0
        var totalElevLoss = 0.0
        var prevElevation: Double? = null
        var totalDistance = 0.0

        for (i in 0 until pointCount) {
            val lat = latlngData?.getOrNull(i)?.get(0)?.jsonPrimitive?.double ?: 0.0
            val lon = latlngData?.getOrNull(i)?.get(1)?.jsonPrimitive?.double ?: 0.0
            val ele: Double? = altitudeData?.getOrNull(i)
            val timeSec: Int? = timeData?.getOrNull(i)
            val hr: Int? = heartrateData?.getOrNull(i)
            val cad: Int? = cadenceData?.getOrNull(i)
            val watts: Int? = wattsData?.getOrNull(i)
            val temp: Double? = tempData?.getOrNull(i)
            val dist: Double? = distanceData?.getOrNull(i)

            val pointTime = if (timeSec != null) startTime.plusSeconds(timeSec.toLong()) else null

            // Calculate speed from distance stream
            val speed: Double? = if (dist != null && i > 0) {
                val prevDist: Double = distanceData.getOrNull(i - 1) ?: 0.0
                val prevTime: Int = timeData?.getOrNull(i - 1) ?: 0
                val dt = (timeSec ?: 0) - prevTime
                if (dt > 0) (dist - prevDist) / dt.toDouble() else null
            } else null

            // Track elevation changes
            if (ele != null) {
                prevElevation?.let { prev ->
                    val diff = ele - prev
                    if (diff > 0) totalElevGain += diff else totalElevLoss -= diff
                }
                prevElevation = ele
            }

            if (dist != null && dist > totalDistance) totalDistance = dist

            points.add(
                GpxPoint(
                    latitude = lat,
                    longitude = lon,
                    elevation = ele,
                    time = pointTime,
                    heartRate = hr,
                    cadence = cad,
                    power = watts,
                    temperature = temp,
                    speed = speed
                )
            )
        }

        if (totalDistance == 0.0 && points.size > 1) {
            totalDistance = calculateTotalDistance(points)
        }

        val bounds = calculateBounds(points)

        val endTime = points.lastOrNull()?.time ?: startTime.plusSeconds(activity.elapsedTime.toLong())
        val duration = Duration.between(startTime, endTime)

        return GpxData(
            tracks = listOf(
                GpxTrack(
                    name = activity.name,
                    segments = listOf(GpxSegment(points = points))
                )
            ),
            bounds = bounds,
            totalDistance = totalDistance,
            totalElevationGain = totalElevGain,
            totalElevationLoss = totalElevLoss,
            totalDuration = duration,
            startTime = startTime,
            endTime = endTime
        )
    }

    private fun calculateBounds(points: List<GpxPoint>): GeoBounds {
        if (points.isEmpty()) return GeoBounds(0.0, 0.0, 0.0, 0.0)
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        for (p in points) {
            minLat = min(minLat, p.latitude)
            maxLat = max(maxLat, p.latitude)
            minLon = min(minLon, p.longitude)
            maxLon = max(maxLon, p.longitude)
        }
        return GeoBounds(minLat, maxLat, minLon, maxLon)
    }

    private fun calculateTotalDistance(points: List<GpxPoint>): Double {
        var total = 0.0
        for (i in 1 until points.size) {
            total += haversine(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude
            )
        }
        return total
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun emptyGpxData(activity: StravaActivity): GpxData {
        val startTime = Instant.parse(activity.startDate)
        return GpxData(
            tracks = listOf(GpxTrack(name = activity.name, segments = emptyList())),
            bounds = GeoBounds(0.0, 0.0, 0.0, 0.0),
            totalDistance = activity.distance,
            totalElevationGain = activity.totalElevationGain,
            totalElevationLoss = 0.0,
            totalDuration = Duration.ofSeconds(activity.elapsedTime.toLong()),
            startTime = startTime,
            endTime = startTime.plusSeconds(activity.elapsedTime.toLong())
        )
    }
}
