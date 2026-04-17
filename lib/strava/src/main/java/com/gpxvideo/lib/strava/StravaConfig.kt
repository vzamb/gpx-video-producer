package com.gpxvideo.lib.strava

object StravaConfig {
    // Credentials are injected from local.properties at build time
    // (see lib/strava/build.gradle.kts) so the client secret never lands in VCS.
    // Register an app at https://www.strava.com/settings/api and set:
    //   strava.clientId=...
    //   strava.clientSecret=...
    // in your local.properties.
    const val CLIENT_ID: String = BuildConfig.STRAVA_CLIENT_ID
    const val CLIENT_SECRET: String = BuildConfig.STRAVA_CLIENT_SECRET
    const val REDIRECT_URI = "gpxvideo://strava/callback"

    const val AUTH_URL = "https://www.strava.com/oauth/mobile/authorize"
    const val TOKEN_URL = "https://www.strava.com/oauth/token"
    const val API_BASE = "https://www.strava.com/api/v3"

    const val SCOPE = "activity:read_all"

    val isConfigured: Boolean get() = CLIENT_ID.isNotBlank() && CLIENT_SECRET.isNotBlank()
}
