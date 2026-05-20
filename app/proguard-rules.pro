# VLC - keep all native + JNI related
-keep class org.videolan.** { *; }
-dontwarn org.videolan.**
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.libvlc.interfaces.** { *; }

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
