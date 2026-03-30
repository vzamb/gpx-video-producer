plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.gpxvideo.lib.ffmpeg"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    // TODO: Add ffmpeg-kit dependency in Phase 10 (Export Pipeline)
    // implementation(libs.ffmpeg.kit.full)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}
