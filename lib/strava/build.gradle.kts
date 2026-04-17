plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

import java.util.Properties

val stravaProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val stravaClientId: String = stravaProps.getProperty("strava.clientId", "")
val stravaClientSecret: String = stravaProps.getProperty("strava.clientSecret", "")

android {
    namespace = "com.gpxvideo.lib.strava"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "STRAVA_CLIENT_ID", "\"$stravaClientId\"")
        buildConfigField("String", "STRAVA_CLIENT_SECRET", "\"$stravaClientSecret\"")
    }

    buildFeatures {
        buildConfig = true
    }

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    // OkHttp
    implementation(libs.okhttp)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Kotlinx Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Security (EncryptedSharedPreferences for OAuth tokens)
    implementation(libs.androidx.security.crypto)

    // Browser (Custom Tabs)
    implementation(libs.androidx.browser)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
}
