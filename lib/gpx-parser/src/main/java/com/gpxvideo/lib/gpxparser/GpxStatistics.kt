package com.gpxvideo.lib.gpxparser

import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.GpxPoint
import java.time.Duration
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GpxStats(
    val totalDistance: Double,
    val totalElevationGain: Double,
    val totalElevationLoss: Double,
    val totalDuration: Duration,
    val movingDuration: Duration,
    val avgSpeed: Double,
    val maxSpeed: Double,
    val avgPace: Double,
    val bestPace: Double,
    val avgHeartRate: Double?,
    val maxHeartRate: Int?,
    val avgCadence: Double?,
    val avgPower: Double?,
    val maxPower: Int?,
    val avgTemperature: Double?,
    val avgGrade: Double,
    val maxGrade: Double
)

object GpxStatistics {

    private const val EARTH_RADIUS = 6371000.0
    private const val PAUSE_SPEED_THRESHOLD = 0.5
    private const val PAUSE_DURATION_THRESHOLD_SECONDS = 30L

    fun computeFullStats(data: GpxData): GpxStats {
        val allPoints = data.tracks.flatMap { it.segments.flatMap { s -> s.points } }
        if (allPoints.isEmpty()) return emptyStats()

        val totalDistance = data.totalDistance

        var elevGain = 0.0
        var elevLoss = 0.0
        for (track in data.tracks) {
            for (segment in track.segments) {
                val elevations = smoothElevation(segment.points)
                for (i in 1 until elevations.size) {
                    val diff = elevations[i] - elevations[i - 1]
                    if (diff > 0) elevGain += diff
                    else elevLoss += -diff
                }
            }
        }

        val totalDuration = data.totalDuration

        var movingMillis = 0L
        var maxSpeed = 0.0
        var pauseStartIndex: Int? = null

        for (track in data.tracks) {
            for (segment in track.segments) {
                pauseStartIndex = null
                for (i in 1 until segment.points.size) {
                    val p1 = segment.points[i - 1]
                    val p2 = segment.points[i]
                    if (p1.time == null || p2.time == null) continue

                    val dist = computeDistance(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
                    val dt = Duration.between(p1.time, p2.time)
                    if (dt.isZero || dt.isNegative) continue

                    val speed = dist / (dt.toMillis() / 1000.0)
                    if (speed > maxSpeed) maxSpeed = speed

                    if (speed >= PAUSE_SPEED_THRESHOLD) {
                        if (pauseStartIndex != null) {
                            val pauseStart = segment.points[pauseStartIndex].time!!
                            val pauseEnd = p1.time
                            val pauseDuration = Duration.between(pauseStart, pauseEnd)
                            if (pauseDuration.seconds <= PAUSE_DURATION_THRESHOLD_SECONDS) {
                                movingMillis += pauseDuration.toMillis()
                            }
                            pauseStartIndex = null
                        }
                        movingMillis += dt.toMillis()
                    } else {
                        if (pauseStartIndex == null) {
                            pauseStartIndex = i - 1
                        }
                    }
                }
            }
        }

        val movingDuration = Duration.ofMillis(movingMillis)
        val avgSpeed = if (movingMillis > 0) totalDistance / (movingMillis / 1000.0) else 0.0
        val avgPace = if (avgSpeed > 0) (1000.0 / avgSpeed) / 60.0 else 0.0
        val bestPace = if (maxSpeed > 0) (1000.0 / maxSpeed) / 60.0 else 0.0

        val heartRates = allPoints.mapNotNull { it.heartRate }
        val avgHeartRate = if (heartRates.isNotEmpty()) heartRates.average() else null
        val maxHeartRate = heartRates.maxOrNull()

        val cadences = allPoints.mapNotNull { it.cadence }
        val avgCadence = if (cadences.isNotEmpty()) cadences.average() else null

        val powers = allPoints.mapNotNull { it.power }
        val avgPower = if (powers.isNotEmpty()) powers.average() else null
        val maxPower = powers.maxOrNull()

        val temps = allPoints.mapNotNull { it.temperature }
        val avgTemperature = if (temps.isNotEmpty()) temps.average() else null

        val grades = mutableListOf<Double>()
        for (track in data.tracks) {
            for (segment in track.segments) {
                for (i in 1 until segment.points.size) {
                    val p1 = segment.points[i - 1]
                    val p2 = segment.points[i]
                    val dist = computeDistance(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
                    if (dist > 1.0) {
                        grades.add(computeGrade(p1, p2, dist))
                    }
                }
            }
        }
        val avgGrade = if (grades.isNotEmpty()) grades.average() else 0.0
        val maxGrade = if (grades.isNotEmpty()) grades.maxOf { abs(it) } else 0.0

        return GpxStats(
            totalDistance = totalDistance,
            totalElevationGain = elevGain,
            totalElevationLoss = elevLoss,
            totalDuration = totalDuration,
            movingDuration = movingDuration,
            avgSpeed = avgSpeed,
            maxSpeed = maxSpeed,
            avgPace = avgPace,
            bestPace = bestPace,
            avgHeartRate = avgHeartRate,
            maxHeartRate = maxHeartRate,
            avgCadence = avgCadence,
            avgPower = avgPower,
            maxPower = maxPower,
            avgTemperature = avgTemperature,
            avgGrade = avgGrade,
            maxGrade = maxGrade
        )
    }

    fun computePointSpeed(p1: GpxPoint, p2: GpxPoint): Double {
        if (p1.time == null || p2.time == null) return 0.0
        val dist = computeDistance(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
        val dt = Duration.between(p1.time, p2.time).toMillis() / 1000.0
        return if (dt > 0) dist / dt else 0.0
    }

    fun computeDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS * c
    }

    fun computeGrade(p1: GpxPoint, p2: GpxPoint, distance: Double): Double {
        val ele1 = p1.elevation ?: return 0.0
        val ele2 = p2.elevation ?: return 0.0
        if (distance <= 0) return 0.0
        return ((ele2 - ele1) / distance) * 100.0
    }

    fun smoothElevation(points: List<GpxPoint>, windowSize: Int = 5): List<Double> {
        val elevations = points.map { it.elevation ?: 0.0 }
        if (elevations.size < windowSize) return elevations

        val half = windowSize / 2
        return elevations.mapIndexed { index, _ ->
            val start = maxOf(0, index - half)
            val end = minOf(elevations.size - 1, index + half)
            elevations.subList(start, end + 1).average()
        }
    }

    private fun emptyStats() = GpxStats(
        totalDistance = 0.0,
        totalElevationGain = 0.0,
        totalElevationLoss = 0.0,
        totalDuration = Duration.ZERO,
        movingDuration = Duration.ZERO,
        avgSpeed = 0.0,
        maxSpeed = 0.0,
        avgPace = 0.0,
        bestPace = 0.0,
        avgHeartRate = null,
        maxHeartRate = null,
        avgCadence = null,
        avgPower = null,
        maxPower = null,
        avgTemperature = null,
        avgGrade = 0.0,
        maxGrade = 0.0
    )
}
