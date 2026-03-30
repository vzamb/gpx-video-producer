package com.gpxvideo.feature.overlays

import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.GpxPoint
import com.gpxvideo.core.model.SyncMode
import com.gpxvideo.lib.gpxparser.GpxStatistics
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

data class SyncKeyframe(
    val videoTimeMs: Long,
    val gpxPointIndex: Int
)

data class InterpolatedPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double,
    val speed: Double,
    val heartRate: Int?,
    val cadence: Int?,
    val power: Int?,
    val temperature: Double?,
    val grade: Double,
    val elapsedTime: Duration,
    val elapsedDistance: Double,
    val progress: Float
)

class GpxTimeSyncEngine(
    private val gpxData: GpxData,
    private val syncMode: SyncMode,
    private val timeOffsetMs: Long = 0,
    private val keyframes: List<SyncKeyframe> = emptyList()
) {
    private val allPoints: List<GpxPoint>
    private val cumulativeDistances: List<Double>
    private val cumulativeTimes: List<Long>
    private val totalDistance: Double
    private val totalTimeMs: Long
    private val gpxStartInstant: Instant?

    init {
        allPoints = gpxData.tracks.flatMap { it.segments.flatMap { s -> s.points } }
        cumulativeDistances = computeCumulativeDistances()
        cumulativeTimes = computeCumulativeTimes()
        totalDistance = if (cumulativeDistances.isNotEmpty()) cumulativeDistances.last() else 0.0
        totalTimeMs = if (cumulativeTimes.isNotEmpty()) cumulativeTimes.last() else 0L
        gpxStartInstant = allPoints.firstOrNull()?.time
    }

    fun precomputeLookupTable() {
        // Already pre-computed in init; this is a no-op hook for future caching
    }

    fun getPointAtVideoTime(videoTimeMs: Long): InterpolatedPoint {
        if (allPoints.isEmpty()) return emptyPoint()
        if (allPoints.size == 1) return pointToInterpolated(0, 0.0)

        val gpxTimeMs = videoTimeToGpxTimeMs(videoTimeMs)
        val clampedMs = gpxTimeMs.coerceIn(0L, totalTimeMs)

        val segIdx = binarySearchSegment(cumulativeTimes, clampedMs)
        val i = segIdx.coerceIn(0, allPoints.size - 2)

        val t0 = cumulativeTimes[i]
        val t1 = cumulativeTimes[i + 1]
        val fraction = if (t1 > t0) (clampedMs - t0).toDouble() / (t1 - t0) else 0.0

        return interpolate(i, fraction)
    }

    fun getProgressAtVideoTime(videoTimeMs: Long): Float {
        if (allPoints.size < 2 || totalDistance <= 0) return 0f
        val point = getPointAtVideoTime(videoTimeMs)
        return point.progress
    }

    fun getDistanceAtVideoTime(videoTimeMs: Long): Double {
        if (allPoints.size < 2) return 0.0
        val point = getPointAtVideoTime(videoTimeMs)
        return point.elapsedDistance
    }

    private fun videoTimeToGpxTimeMs(videoTimeMs: Long): Long {
        return when (syncMode) {
            SyncMode.GPX_TIMESTAMP -> videoTimeMs + timeOffsetMs

            SyncMode.MANUAL_KEYFRAMES -> {
                if (keyframes.isEmpty()) return videoTimeMs
                val sorted = keyframes.sortedBy { it.videoTimeMs }

                if (videoTimeMs <= sorted.first().videoTimeMs) {
                    return cumulativeTimes.getOrElse(sorted.first().gpxPointIndex) { 0L }
                }
                if (videoTimeMs >= sorted.last().videoTimeMs) {
                    return cumulativeTimes.getOrElse(sorted.last().gpxPointIndex) { totalTimeMs }
                }

                val kfIdx = binarySearchKeyframe(sorted, videoTimeMs)
                val kf0 = sorted[kfIdx]
                val kf1 = sorted[kfIdx + 1]

                val vFraction = if (kf1.videoTimeMs > kf0.videoTimeMs) {
                    (videoTimeMs - kf0.videoTimeMs).toDouble() / (kf1.videoTimeMs - kf0.videoTimeMs)
                } else 0.0

                val gpxTime0 = cumulativeTimes.getOrElse(kf0.gpxPointIndex) { 0L }
                val gpxTime1 = cumulativeTimes.getOrElse(kf1.gpxPointIndex) { totalTimeMs }
                (gpxTime0 + (gpxTime1 - gpxTime0) * vFraction).toLong()
            }
        }
    }

    private fun interpolate(index: Int, fraction: Double): InterpolatedPoint {
        val p0 = allPoints[index]
        val p1 = allPoints[(index + 1).coerceAtMost(allPoints.size - 1)]
        val f = fraction.coerceIn(0.0, 1.0)

        val lat = p0.latitude + (p1.latitude - p0.latitude) * f
        val lon = p0.longitude + (p1.longitude - p0.longitude) * f
        val ele = lerp(p0.elevation ?: 0.0, p1.elevation ?: 0.0, f)

        val segDist = cumulativeDistances[index + 1] - cumulativeDistances[index]
        val segTime = cumulativeTimes[index + 1] - cumulativeTimes[index]
        val speed = if (segTime > 0) segDist / (segTime / 1000.0) else (p0.speed ?: 0.0)

        val hr = lerpNullableInt(p0.heartRate, p1.heartRate, f)
        val cad = lerpNullableInt(p0.cadence, p1.cadence, f)
        val pow = lerpNullableInt(p0.power, p1.power, f)
        val temp = lerpNullableDouble(p0.temperature, p1.temperature, f)

        val grade = if (segDist > 1.0) {
            GpxStatistics.computeGrade(p0, p1, segDist)
        } else 0.0

        val elapsedDist = cumulativeDistances[index] + segDist * f
        val elapsedTimeMs = cumulativeTimes[index] + (segTime * f).toLong()
        val progress = if (totalDistance > 0) (elapsedDist / totalDistance).toFloat() else 0f

        return InterpolatedPoint(
            latitude = lat,
            longitude = lon,
            elevation = ele,
            speed = speed,
            heartRate = hr,
            cadence = cad,
            power = pow,
            temperature = temp,
            grade = grade,
            elapsedTime = Duration.ofMillis(elapsedTimeMs),
            elapsedDistance = elapsedDist,
            progress = progress.coerceIn(0f, 1f)
        )
    }

    private fun pointToInterpolated(index: Int, distance: Double): InterpolatedPoint {
        val p = allPoints[index]
        return InterpolatedPoint(
            latitude = p.latitude,
            longitude = p.longitude,
            elevation = p.elevation ?: 0.0,
            speed = p.speed ?: 0.0,
            heartRate = p.heartRate,
            cadence = p.cadence,
            power = p.power,
            temperature = p.temperature,
            grade = 0.0,
            elapsedTime = Duration.ofMillis(cumulativeTimes.getOrElse(index) { 0L }),
            elapsedDistance = distance,
            progress = if (totalDistance > 0) (distance / totalDistance).toFloat() else 0f
        )
    }

    private fun emptyPoint() = InterpolatedPoint(
        latitude = 0.0, longitude = 0.0, elevation = 0.0,
        speed = 0.0, heartRate = null, cadence = null, power = null,
        temperature = null, grade = 0.0,
        elapsedTime = Duration.ZERO, elapsedDistance = 0.0, progress = 0f
    )

    private fun computeCumulativeDistances(): List<Double> {
        if (allPoints.size < 2) return if (allPoints.isNotEmpty()) listOf(0.0) else emptyList()
        val dists = ArrayList<Double>(allPoints.size)
        dists.add(0.0)
        for (i in 1 until allPoints.size) {
            val prev = allPoints[i - 1]
            val curr = allPoints[i]
            dists.add(
                dists.last() + GpxStatistics.computeDistance(
                    prev.latitude, prev.longitude, curr.latitude, curr.longitude
                )
            )
        }
        return dists
    }

    private fun computeCumulativeTimes(): List<Long> {
        if (allPoints.isEmpty()) return emptyList()
        val startTime = allPoints.firstOrNull()?.time
        if (startTime == null) {
            // No timestamps: distribute evenly assuming 1 second per point
            return List(allPoints.size) { it * 1000L }
        }
        return allPoints.map { point ->
            point.time?.let { Duration.between(startTime, it).toMillis() } ?: 0L
        }
    }

    companion object {
        /**
         * Binary search for the segment index where [value] falls between
         * cumulative[index] and cumulative[index + 1].
         */
        internal fun binarySearchSegment(cumulative: List<Long>, value: Long): Int {
            if (cumulative.size < 2) return 0
            var lo = 0
            var hi = cumulative.size - 2
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (cumulative[mid] <= value) lo = mid
                else hi = mid - 1
            }
            return lo
        }

        private fun binarySearchKeyframe(sorted: List<SyncKeyframe>, videoTimeMs: Long): Int {
            var lo = 0
            var hi = sorted.size - 2
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (sorted[mid].videoTimeMs <= videoTimeMs) lo = mid
                else hi = mid - 1
            }
            return lo
        }

        private fun lerp(a: Double, b: Double, f: Double) = a + (b - a) * f

        private fun lerpNullableInt(a: Int?, b: Int?, f: Double): Int? {
            if (a == null && b == null) return null
            val va = a ?: b ?: return null
            val vb = b ?: a ?: return null
            return (va + (vb - va) * f).toInt()
        }

        private fun lerpNullableDouble(a: Double?, b: Double?, f: Double): Double? {
            if (a == null && b == null) return null
            val va = a ?: b ?: return null
            val vb = b ?: a ?: return null
            return va + (vb - va) * f
        }
    }
}
