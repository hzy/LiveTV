# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# Compose - keep runtime
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# Keep our app classes (prevent stripping)
-keep class dev.faraway.livetv.** { *; }

# AndroidX
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
