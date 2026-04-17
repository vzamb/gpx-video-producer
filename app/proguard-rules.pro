# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# --- Kotlin / Coroutines ---
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keepclassmembernames class kotlinx.** { volatile <fields>; }

# --- Kotlin Serialization ---
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
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Room ---
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface *
-keepclassmembers class * { @androidx.room.* <methods>; @androidx.room.* <fields>; }

# --- Hilt / Dagger ---
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
-keep,allowobfuscation @interface dagger.hilt.android.lifecycle.HiltViewModel
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

# --- Media3 (Transformer, Effect, ExoPlayer) ---
# Transformer + effects use reflection for some extension factories.
-keep class androidx.media3.transformer.** { *; }
-keep class androidx.media3.effect.** { *; }
-keep class androidx.media3.exoplayer.** { *; }

# --- AndroidSVG ---
-keep class com.caverock.androidsvg.** { *; }

# --- Coil 3 ---
-keep class coil3.** { *; }

# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**

# --- Domain model classes serialized to/from JSON & Room ---
-keep class com.gpxvideo.core.model.** { *; }
-keep class com.gpxvideo.core.database.entity.** { *; }
-keep class com.gpxvideo.lib.strava.** { *; }
