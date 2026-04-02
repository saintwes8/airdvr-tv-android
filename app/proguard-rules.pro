# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep data model classes for Gson serialization
-keep class com.airdvr.tv.data.models.** { *; }

# Keep API interface
-keep interface com.airdvr.tv.data.api.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepattributes EnclosingMethod

# Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ExoPlayer / Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Security Crypto
-keep class androidx.security.crypto.** { *; }

# Coil
-dontwarn coil.**

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep BuildConfig
-keep class com.airdvr.tv.BuildConfig { *; }
