plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.gpxvideo.lib.mediautils"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
}
