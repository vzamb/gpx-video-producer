package com.gpxvideo.lib.gpxparser

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class GpxParserTest {

    private fun loadResource(name: String) =
        javaClass.classLoader!!.getResourceAsStream(name)!!

    @Test
    fun `parse sample GPX - track count`() = runTest {
        val data = GpxParser.parse(loadResource("sample.gpx"))
        assertEquals(1, data.tracks.size)
    }

    @Test
    fun `parse sample GPX - track name`() = runTest {
        val data = GpxParser.parse(loadResource("sample.gpx"))
        assertEquals("Morning Run", data.tracks[0].name)
    }

    @Test
    fun `parse sample GPX - point count`() = runTest {
        val data = GpxParser.parse(loadResource("sample.gpx"))
        val points = data.tracks[0].segments[0].points
        assertEquals(10, points.size)
    }

    @Test
    fun `parse sample GPX - first point coordinates`() = runTest {
        val data = GpxParser.parse(loadResource("sample.gpx"))
        val first = data.tracks[0].segments[0].points[0]
        assertEquals(47.60620, first.latitude, 0.00001)
        assertEquals(-122.33210, first.longitude, 0.00001)
    }

    @Test
    fun `parse sample GPX - elevation values`() = runTest {
        val data = GpxParser.parse(loadResource("sample.gpx"))
        val points = data.tracks[0].segments[0].points
        assertEquals(50.0, points[0].elevation)
        assertEquals(60.0, points[4].elevation)
        assertEquals(48.0, points[9].elevation)
    }

    @Test
    fun `parse sample GPX - time values`() = runTest {
        val data = GpxParser.parse(loadResource("sample.gpx"))
        val points = data.tracks[0].segments[0].points
        assertEquals(Instant.parse("2024-01-15T10:00:00Z"), points[0].time)
        assertEquals(Instant.parse("2024-01-15T10:04:30Z"), points[9].time)
    }

    @Test
    fun `parse sample GPX - heart rate extraction`() = runTest {
        val data = GpxParser.parse(loadResource("sample.gpx"))
        val points = data.tracks[0].segments[0].points
        assertEquals(120, points[0].heartRate)
        assertEquals(140, points[4].heartRate)
        assertEquals(128, points[9].heartRate)
    }

    @Test
    fun `parse sample GPX - cadence extraction`() = runTest {
        val data = GpxParser.parse(loadResource("sample.gpx"))
        val points = data.tracks[0].segments[0].points
        assertEquals(80, points[0].cadence)
        assertEquals(88, points[4].cadence)
    }

    @Test
    fun `parse sample GPX - computed bounds`() = runTest {
        val data = GpxParser.parse(loadResource("sample.gpx"))
        assertEquals(47.60620, data.bounds.minLatitude, 0.00001)
        assertEquals(47.61600, data.bounds.maxLatitude, 0.00001)
        assertEquals(-122.33210, data.bounds.minLongitude, 0.00001)
        assertEquals(-122.32160, data.bounds.maxLongitude, 0.00001)
    }

    @Test
    fun `parse sample GPX - total distance is positive`() = runTest {
        val data = GpxParser.parse(loadResource("sample.gpx"))
        assertTrue(data.totalDistance > 0, "Total distance should be positive")
    }

    @Test
    fun `parse sample GPX - elevation gain is positive`() = runTest {
        val data = GpxParser.parse(loadResource("sample.gpx"))
        assertTrue(data.totalElevationGain > 0, "Elevation gain should be positive")
    }

    @Test
    fun `parse sample GPX - duration is 4m30s`() = runTest {
        val data = GpxParser.parse(loadResource("sample.gpx"))
        assertEquals(270, data.totalDuration.seconds)
    }

    @Test
    fun `parse sample GPX - waypoints`() = runTest {
        val data = GpxParser.parse(loadResource("sample.gpx"))
        assertEquals(2, data.waypoints.size)
        assertEquals("Start Point", data.waypoints[0].name)
        assertEquals("End Point", data.waypoints[1].name)
        assertNotNull(data.waypoints[0].description)
    }

    @Test
    fun `parse sample GPX - start and end time`() = runTest {
        val data = GpxParser.parse(loadResource("sample.gpx"))
        assertEquals(Instant.parse("2024-01-15T10:00:00Z"), data.startTime)
        assertEquals(Instant.parse("2024-01-15T10:04:30Z"), data.endTime)
    }
}
