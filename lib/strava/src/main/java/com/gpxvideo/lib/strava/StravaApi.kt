package com.gpxvideo.lib.strava

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StravaApi @Inject constructor(
    private val httpClient: OkHttpClient,
    private val auth: StravaAuth
) {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun authenticatedRequest(urlBuilder: () -> String): String? {
        val token = auth.refreshTokenIfNeeded() ?: return null
        val request = Request.Builder()
            .url(urlBuilder())
            .addHeader("Authorization", "Bearer $token")
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return null
        return response.body?.string()
    }

    suspend fun listActivities(page: Int = 1, perPage: Int = 30): Result<List<StravaActivity>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = authenticatedRequest {
                "${StravaConfig.API_BASE}/athlete/activities?page=$page&per_page=$perPage"
            } ?: throw IllegalStateException("Failed to fetch activities — not authenticated")

            json.decodeFromString<List<StravaActivity>>(body)
        }
    }

    /**
     * Fetch GPS streams for an activity.
     * Returns a map of stream type → data array.
     * Requested keys: latlng, altitude, time, heartrate, cadence, watts, temp, distance
     */
    suspend fun getActivityStreams(activityId: Long): Result<Map<String, JsonArray>> = withContext(Dispatchers.IO) {
        runCatching {
            val keys = "latlng,altitude,time,heartrate,cadence,watts,temp,distance"
            val body = authenticatedRequest {
                "${StravaConfig.API_BASE}/activities/$activityId/streams?keys=$keys&key_type=time"
            } ?: throw IllegalStateException("Failed to fetch streams — not authenticated")

            val streams = json.decodeFromString<List<StravaStream>>(body)
            streams.associate { it.type to it.data.let { data -> kotlinx.serialization.json.JsonArray(data) } }
        }
    }
}
