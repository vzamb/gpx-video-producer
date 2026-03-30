package com.gpxvideo.lib.gpxparser

import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.GpxPoint
import com.gpxvideo.core.model.GpxSegment
import com.gpxvideo.core.model.GpxTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.Instant

object TcxParser {

    suspend fun parse(inputStream: InputStream): GpxData = withContext(Dispatchers.IO) {
        val parser = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }.newPullParser()
        parser.setInput(inputStream, null)

        val tracks = mutableListOf<GpxTrack>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "Activity") {
                parseActivity(parser)?.let { tracks.add(it) }
            }
            eventType = parser.next()
        }

        GpxParser.buildGpxData(tracks, emptyList())
    }

    private fun parseActivity(parser: XmlPullParser): GpxTrack? {
        val sport = parser.getAttributeValue(null, "Sport")
        val segments = mutableListOf<GpxSegment>()
        val depth = parser.depth

        while (true) {
            val eventType = parser.next()
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (eventType == XmlPullParser.START_TAG && parser.name == "Lap") {
                parseLap(parser)?.let { segments.add(it) }
            }
        }

        return if (segments.isNotEmpty()) GpxTrack(name = sport, segments = segments) else null
    }

    private fun parseLap(parser: XmlPullParser): GpxSegment? {
        val points = mutableListOf<GpxPoint>()
        val depth = parser.depth

        while (true) {
            val eventType = parser.next()
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (eventType == XmlPullParser.START_TAG && parser.name == "Trackpoint") {
                parseTrackpoint(parser)?.let { points.add(it) }
            }
        }

        return if (points.isNotEmpty()) GpxSegment(points = points) else null
    }

    private fun parseTrackpoint(parser: XmlPullParser): GpxPoint? {
        var lat: Double? = null
        var lon: Double? = null
        var altitude: Double? = null
        var time: Instant? = null
        var heartRate: Int? = null
        var cadence: Int? = null
        var power: Int? = null
        val depth = parser.depth

        while (true) {
            val eventType = parser.next()
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "Time" -> time = parseTime(readElementText(parser))
                    "Position" -> {
                        val pos = parsePosition(parser)
                        lat = pos.first
                        lon = pos.second
                    }
                    "AltitudeMeters" -> altitude = readElementText(parser)?.toDoubleOrNull()
                    "HeartRateBpm" -> heartRate = parseHeartRate(parser)
                    "Cadence" -> cadence = readElementText(parser)?.toIntOrNull()
                    "Extensions" -> power = parseTrackpointExtensions(parser)
                }
            }
        }

        if (lat == null || lon == null) return null

        return GpxPoint(
            latitude = lat,
            longitude = lon,
            elevation = altitude,
            time = time,
            heartRate = heartRate,
            cadence = cadence,
            power = power
        )
    }

    private fun parsePosition(parser: XmlPullParser): Pair<Double?, Double?> {
        var lat: Double? = null
        var lon: Double? = null
        val depth = parser.depth

        while (true) {
            val eventType = parser.next()
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "LatitudeDegrees" -> lat = readElementText(parser)?.toDoubleOrNull()
                    "LongitudeDegrees" -> lon = readElementText(parser)?.toDoubleOrNull()
                }
            }
        }

        return lat to lon
    }

    private fun parseHeartRate(parser: XmlPullParser): Int? {
        var value: Int? = null
        val depth = parser.depth

        while (true) {
            val eventType = parser.next()
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (eventType == XmlPullParser.START_TAG && parser.name == "Value") {
                value = readElementText(parser)?.toDoubleOrNull()?.toInt()
            }
        }

        return value
    }

    private fun parseTrackpointExtensions(parser: XmlPullParser): Int? {
        var watts: Int? = null
        val depth = parser.depth

        while (true) {
            val eventType = parser.next()
            if (eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (eventType == XmlPullParser.START_TAG) {
                val localName = parser.name.substringAfterLast(":")
                when (localName) {
                    "Watts", "watts" -> watts = readElementText(parser)?.toIntOrNull()
                }
            }
        }

        return watts
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

    private fun parseTime(text: String?): Instant? {
        if (text.isNullOrBlank()) return null
        return try {
            Instant.parse(text)
        } catch (_: Exception) {
            null
        }
    }
}
