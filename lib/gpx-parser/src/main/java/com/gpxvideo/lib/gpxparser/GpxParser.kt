package com.gpxvideo.lib.gpxparser

import com.gpxvideo.core.model.GeoBounds
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.GpxPoint
import com.gpxvideo.core.model.GpxSegment
import com.gpxvideo.core.model.GpxTrack
import com.gpxvideo.core.model.GpxWaypoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.Duration
import java.time.Instant

object GpxParser {

    suspend fun parse(inputStream: InputStream): GpxData = withContext(Dispatchers.IO) {
        val parser = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }.newPullParser()
        parser.setInput(inputStream, null)

        val tracks = mutableListOf<GpxTrack>()
        val waypoints = mutableListOf<GpxWaypoint>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "trk" -> parseTrack(parser)?.let { tracks.add(it) }
                    "wpt" -> parseWaypoint(parser)?.let { waypoints.add(it) }
                }
            }
            eventType = parser.next()
        }

        buildGpxData(tracks, waypoints)
    }

    private fun parseTrack(parser: XmlPullParser): GpxTrack? {
        val segments = mutableListOf<GpxSegment>()
        var trackName: String? = null
        val depth = parser.depth

        while (true) {
            val eventType = parser.next()
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "name" -> trackName = readElementText(parser)
                    "trkseg" -> parseSegment(parser)?.let { segments.add(it) }
                }
            }
        }

        return if (segments.isNotEmpty()) GpxTrack(name = trackName, segments = segments) else null
    }

    private fun parseSegment(parser: XmlPullParser): GpxSegment? {
        val points = mutableListOf<GpxPoint>()
        val depth = parser.depth

        while (true) {
            val eventType = parser.next()
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (eventType == XmlPullParser.START_TAG && parser.name == "trkpt") {
                parseTrackPoint(parser)?.let { points.add(it) }
            }
        }

        return if (points.isNotEmpty()) GpxSegment(points = points) else null
    }

    private fun parseTrackPoint(parser: XmlPullParser): GpxPoint? {
        val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
        val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
        if (lat == null || lon == null) {
            skipElement(parser)
            return null
        }

        var elevation: Double? = null
        var time: Instant? = null
        var heartRate: Int? = null
        var cadence: Int? = null
        var power: Int? = null
        var temperature: Double? = null
        var speed: Double? = null
        val depth = parser.depth

        while (true) {
            val eventType = parser.next()
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (eventType == XmlPullParser.START_TAG) {
                val localName = parser.name.substringAfterLast(":")
                when (localName) {
                    "ele" -> elevation = readElementText(parser)?.toDoubleOrNull()
                    "time" -> time = parseTime(readElementText(parser))
                    "extensions" -> {
                        val ext = parseExtensions(parser)
                        heartRate = heartRate ?: ext.heartRate
                        cadence = cadence ?: ext.cadence
                        power = power ?: ext.power
                        temperature = temperature ?: ext.temperature
                        speed = speed ?: ext.speed
                    }
                    "hr" -> heartRate = heartRate ?: readElementText(parser)?.toIntOrNull()
                    "cad" -> cadence = cadence ?: readElementText(parser)?.toIntOrNull()
                    "power" -> power = power ?: readElementText(parser)?.toIntOrNull()
                    "atemp" -> temperature = temperature ?: readElementText(parser)?.toDoubleOrNull()
                }
            }
        }

        return GpxPoint(
            latitude = lat,
            longitude = lon,
            elevation = elevation,
            time = time,
            heartRate = heartRate,
            cadence = cadence,
            power = power,
            temperature = temperature,
            speed = speed
        )
    }

    private fun parseWaypoint(parser: XmlPullParser): GpxWaypoint? {
        val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
        val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
        if (lat == null || lon == null) {
            skipElement(parser)
            return null
        }

        var elevation: Double? = null
        var name: String? = null
        var description: String? = null
        var time: Instant? = null
        val depth = parser.depth

        while (true) {
            val eventType = parser.next()
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "ele" -> elevation = readElementText(parser)?.toDoubleOrNull()
                    "name" -> name = readElementText(parser)
                    "desc" -> description = readElementText(parser)
                    "time" -> time = parseTime(readElementText(parser))
                }
            }
        }

        return GpxWaypoint(lat, lon, elevation, name, description, time)
    }

    private data class Extensions(
        val heartRate: Int? = null,
        val cadence: Int? = null,
        val power: Int? = null,
        val temperature: Double? = null,
        val speed: Double? = null
    )

    private fun parseExtensions(parser: XmlPullParser): Extensions {
        var hr: Int? = null
        var cad: Int? = null
        var power: Int? = null
        var temp: Double? = null
        var speed: Double? = null
        val targetDepth = parser.depth

        while (true) {
            val eventType = parser.next()
            if (eventType == XmlPullParser.END_TAG && parser.depth == targetDepth) break
            if (eventType == XmlPullParser.START_TAG) {
                val localName = parser.name.substringAfterLast(":")
                when (localName) {
                    "hr" -> hr = hr ?: readElementText(parser)?.toIntOrNull()
                    "cad" -> cad = cad ?: readElementText(parser)?.toIntOrNull()
                    "power" -> power = power ?: readElementText(parser)?.toIntOrNull()
                    "atemp" -> temp = temp ?: readElementText(parser)?.toDoubleOrNull()
                    "speed" -> speed = speed ?: readElementText(parser)?.toDoubleOrNull()
                }
            }
        }

        return Extensions(hr, cad, power, temp, speed)
    }

    private fun readElementText(parser: XmlPullParser): String? {
        val targetDepth = parser.depth
        var text: String? = null
        while (true) {
            val eventType = parser.next()
            if (eventType == XmlPullParser.END_TAG && parser.depth == targetDepth) break
            if (eventType == XmlPullParser.TEXT) {
                text = parser.text?.trim()
            }
        }
        return text
    }

    private fun skipElement(parser: XmlPullParser) {
        val targetDepth = parser.depth
        while (true) {
            val eventType = parser.next()
            if (eventType == XmlPullParser.END_TAG && parser.depth == targetDepth) break
        }
    }

    private fun parseTime(text: String?): Instant? {
        if (text.isNullOrBlank()) return null
        return try {
            Instant.parse(text)
        } catch (_: Exception) {
            null
        }
    }

    internal fun buildGpxData(
        tracks: List<GpxTrack>,
        waypoints: List<GpxWaypoint>
    ): GpxData {
        val allPoints = tracks.flatMap { it.segments.flatMap { s -> s.points } }
        val allLocations = allPoints.map { it.latitude to it.longitude } +
            waypoints.map { it.latitude to it.longitude }

        val bounds = if (allLocations.isNotEmpty()) {
            GeoBounds(
                minLatitude = allLocations.minOf { it.first },
                maxLatitude = allLocations.maxOf { it.first },
                minLongitude = allLocations.minOf { it.second },
                maxLongitude = allLocations.maxOf { it.second }
            )
        } else {
            GeoBounds(0.0, 0.0, 0.0, 0.0)
        }

        var totalDistance = 0.0
        for (track in tracks) {
            for (segment in track.segments) {
                for (i in 1 until segment.points.size) {
                    totalDistance += GpxStatistics.computeDistance(
                        segment.points[i - 1].latitude, segment.points[i - 1].longitude,
                        segment.points[i].latitude, segment.points[i].longitude
                    )
                }
            }
        }

        var elevGain = 0.0
        var elevLoss = 0.0
        for (track in tracks) {
            for (segment in track.segments) {
                val elevations = GpxStatistics.smoothElevation(segment.points)
                for (i in 1 until elevations.size) {
                    val diff = elevations[i] - elevations[i - 1]
                    if (diff > 0) elevGain += diff
                    else elevLoss += -diff
                }
            }
        }

        val times = allPoints.mapNotNull { it.time }
        val startTime = times.minOrNull()
        val endTime = times.maxOrNull()
        val duration = if (startTime != null && endTime != null) {
            Duration.between(startTime, endTime)
        } else {
            Duration.ZERO
        }

        return GpxData(
            tracks = tracks,
            waypoints = waypoints,
            bounds = bounds,
            totalDistance = totalDistance,
            totalElevationGain = elevGain,
            totalElevationLoss = elevLoss,
            totalDuration = duration,
            startTime = startTime,
            endTime = endTime
        )
    }
}
