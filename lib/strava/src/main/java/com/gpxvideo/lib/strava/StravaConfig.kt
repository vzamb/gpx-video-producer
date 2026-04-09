package com.gpxvideo.lib.strava

object StravaConfig {
    // Users must register their own Strava API app at https://www.strava.com/settings/api
    // and provide these values via BuildConfig or local.properties
    const val CLIENT_ID = "151201"
    const val CLIENT_SECRET = "2122083bc80cd1e221c26e1afd5b650b2ef08b44"
    const val REDIRECT_URI = "gpxvideo://strava/callback"

    const val AUTH_URL = "https://www.strava.com/oauth/mobile/authorize"
    const val TOKEN_URL = "https://www.strava.com/oauth/token"
    const val API_BASE = "https://www.strava.com/api/v3"

    const val SCOPE = "activity:read_all"
}
