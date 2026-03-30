# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Room
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * { @androidx.room.* <methods>; }

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.gpxvideo.**$$serializer { *; }
-keepclassmembers class com.gpxvideo.** {
    *** Companion;
}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel

# FFmpeg-kit (for future)
-keep class com.arthenica.** { *; }

# Coil
-keep class coil3.** { *; }

# Keep model classes
-keep class com.gpxvideo.core.model.** { *; }
-keep class com.gpxvideo.core.database.entity.** { *; }
