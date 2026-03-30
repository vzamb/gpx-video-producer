package com.gpxvideo.lib.gpxparser

import com.gpxvideo.core.model.GpxPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs

class GpxStatisticsTest {

    @Test
    fun `haversine distance - known coordinates`() {
        // New York (40.7128, -74.0060) to Los Angeles (34.0522, -118.2437)
        // Expected: ~3944 km
        val distance = GpxStatistics.computeDistance(40.7128, -74.0060, 34.0522, -118.2437)
        val distKm = distance / 1000.0
        assertTrue(distKm > 3900 && distKm < 4000, "NY to LA should be ~3944 km, got $distKm")
    }

    @Test
    fun `haversine distance - same point returns zero`() {
        val distance = GpxStatistics.computeDistance(47.6062, -122.3321, 47.6062, -122.3321)
        assertEquals(0.0, distance, 0.001)
    }

    @Test
    fun `haversine distance - short distance accuracy`() {
        // ~111m for 0.001 degrees latitude at the equator
        val distance = GpxStatistics.computeDistance(0.0, 0.0, 0.001, 0.0)
        assertTrue(distance > 100 && distance < 120, "0.001° lat should be ~111m, got $distance")
    }

    @Test
    fun `compute point speed`() {
        val p1 = GpxPoint(
            latitude = 47.6062, longitude = -122.3321,
            time = Instant.parse("2024-01-15T10:00:00Z")
        )
        val p2 = GpxPoint(
            latitude = 47.6072, longitude = -122.3311,
            time = Instant.parse("2024-01-15T10:00:30Z")
        )
        val speed = GpxStatistics.computePointSpeed(p1, p2)
        assertTrue(speed > 0, "Speed should be positive")
        // ~130m in 30s = ~4.3 m/s
        assertTrue(speed > 3 && speed < 6, "Speed should be ~4.3 m/s, got $speed")
    }

    @Test
    fun `compute point speed - missing time returns zero`() {
        val p1 = GpxPoint(latitude = 47.6062, longitude = -122.3321)
        val p2 = GpxPoint(latitude = 47.6072, longitude = -122.3311)
        assertEquals(0.0, GpxStatistics.computePointSpeed(p1, p2))
    }

    @Test
    fun `compute grade`() {
        val p1 = GpxPoint(latitude = 0.0, longitude = 0.0, elevation = 100.0)
        val p2 = GpxPoint(latitude = 0.0, longitude = 0.0, elevation = 110.0)
        val grade = GpxStatistics.computeGrade(p1, p2, 200.0)
        assertEquals(5.0, grade, 0.001) // 10m rise over 200m = 5%
    }

    @Test
    fun `compute grade - downhill is negative`() {
        val p1 = GpxPoint(latitude = 0.0, longitude = 0.0, elevation = 110.0)
        val p2 = GpxPoint(latitude = 0.0, longitude = 0.0, elevation = 100.0)
        val grade = GpxStatistics.computeGrade(p1, p2, 200.0)
        assertEquals(-5.0, grade, 0.001)
    }

    @Test
    fun `compute grade - missing elevation returns zero`() {
        val p1 = GpxPoint(latitude = 0.0, longitude = 0.0)
        val p2 = GpxPoint(latitude = 0.0, longitude = 0.0, elevation = 100.0)
        assertEquals(0.0, GpxStatistics.computeGrade(p1, p2, 200.0))
    }

    @Test
    fun `smooth elevation - short list returned as-is`() {
        val points = listOf(
            GpxPoint(0.0, 0.0, elevation = 100.0),
            GpxPoint(0.0, 0.0, elevation = 110.0),
            GpxPoint(0.0, 0.0, elevation = 105.0)
        )
        val result = GpxStatistics.smoothElevation(points, windowSize = 5)
        assertEquals(3, result.size)
        assertEquals(100.0, result[0])
    }

    @Test
    fun `smooth elevation - reduces noise`() {
        val points = listOf(
            GpxPoint(0.0, 0.0, elevation = 100.0),
            GpxPoint(0.0, 0.0, elevation = 200.0),
            GpxPoint(0.0, 0.0, elevation = 100.0),
            GpxPoint(0.0, 0.0, elevation = 200.0),
            GpxPoint(0.0, 0.0, elevation = 100.0),
            GpxPoint(0.0, 0.0, elevation = 200.0),
            GpxPoint(0.0, 0.0, elevation = 100.0)
        )
        val smoothed = GpxStatistics.smoothElevation(points, windowSize = 5)
        // Middle values should be closer to the mean (150) than the raw values (100 or 200)
        val middleDeviation = abs(smoothed[3] - 150.0)
        assertTrue(middleDeviation < 20, "Smoothing should reduce noise, deviation was $middleDeviation")
    }

    @Test
    fun `elevation gain computation from sample data`() {
        val points = listOf(
            GpxPoint(0.0, 0.0, elevation = 100.0),
            GpxPoint(0.0, 0.0, elevation = 110.0),
            GpxPoint(0.0, 0.0, elevation = 105.0),
            GpxPoint(0.0, 0.0, elevation = 120.0)
        )
        val smoothed = GpxStatistics.smoothElevation(points, windowSize = 3)
        var gain = 0.0
        for (i in 1 until smoothed.size) {
            val diff = smoothed[i] - smoothed[i - 1]
            if (diff > 0) gain += diff
        }
        assertTrue(gain > 0, "Elevation gain should be positive")
    }
}
