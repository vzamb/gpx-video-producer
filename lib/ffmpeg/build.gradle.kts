plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
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
    // ffmpeg-kit retired April 2025 — using Media3 Transformer instead
    // implementation(libs.ffmpeg.kit.full)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
}
