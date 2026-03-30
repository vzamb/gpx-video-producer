plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.gpxvideo.core.common"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
}
