package com.gpxvideo.lib.strava

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StravaTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAt: Long,
    @SerialName("token_type") val tokenType: String = "Bearer",
    val athlete: StravaAthlete? = null
)

@Serializable
data class StravaAthlete(
    val id: Long,
    val firstname: String? = null,
    val lastname: String? = null,
    val profile: String? = null,
    @SerialName("profile_medium") val profileMedium: String? = null
) {
    val displayName: String get() = listOfNotNull(firstname, lastname).joinToString(" ").ifBlank { "Strava Athlete" }
}

@Serializable
data class StravaActivity(
    val id: Long,
    val name: String,
    val type: String,
    @SerialName("sport_type") val sportType: String? = null,
    val distance: Double,
    @SerialName("moving_time") val movingTime: Int,
    @SerialName("elapsed_time") val elapsedTime: Int,
    @SerialName("total_elevation_gain") val totalElevationGain: Double,
    @SerialName("start_date") val startDate: String,
    @SerialName("start_date_local") val startDateLocal: String,
    @SerialName("start_latlng") val startLatlng: List<Double>? = null,
    @SerialName("end_latlng") val endLatlng: List<Double>? = null,
    @SerialName("average_speed") val averageSpeed: Double? = null,
    @SerialName("max_speed") val maxSpeed: Double? = null,
    @SerialName("average_heartrate") val averageHeartrate: Double? = null,
    @SerialName("max_heartrate") val maxHeartrate: Double? = null,
    @SerialName("has_heartrate") val hasHeartrate: Boolean = false,
    @SerialName("average_cadence") val averageCadence: Double? = null,
    @SerialName("average_watts") val averageWatts: Double? = null,
    val map: StravaMap? = null,
    @SerialName("trainer") val isTrainer: Boolean = false,
    @SerialName("manual") val isManual: Boolean = false
)

@Serializable
data class StravaMap(
    val id: String,
    @SerialName("summary_polyline") val summaryPolyline: String? = null,
    @SerialName("resource_state") val resourceState: Int = 0
)

@Serializable
data class StravaStream(
    val type: String,
    val data: List<kotlinx.serialization.json.JsonElement>,
    @SerialName("series_type") val seriesType: String? = null,
    @SerialName("original_size") val originalSize: Int? = null,
    val resolution: String? = null
)
