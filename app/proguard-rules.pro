# Production ProGuard / R8 rules for Nessus frontend

# Keep application class and main entry
-keep public class uk.co.cyberask.scanner.MainActivity

# === Moshi (reflection) - CRITICAL to prevent JSON crashes in release ===
# Keep all our model classes and their fields + constructors
-keep class uk.co.cyberask.scanner.data.** { *; }

# Moshi general
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class * implements com.squareup.moshi.JsonAdapter
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# Kotlin reflect / metadata used by Moshi-kotlin
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keepclassmembers class kotlin.Metadata {
    <fields>;
}

# === Retrofit ===
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations

# Keep all Retrofit service interfaces (methods + annotations)
-keep class uk.co.cyberask.scanner.data.NessusApiService { *; }

# === OkHttp ===
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# === Coroutines ===
-keep class kotlinx.coroutines.** { *; }

# === DataStore / Preferences (encrypted) ===
-keep class androidx.datastore.** { *; }

# === Security Crypto ===
-keep class androidx.security.crypto.** { *; }

# === Compose / Material (usually safe via consumer rules, but explicit) ===
-keep class androidx.compose.** { *; }
-keep class androidx.compose.material3.** { *; }

# === General good practices ===
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Prevent R8 from stripping BuildConfig if used
-keep class uk.co.cyberask.scanner.BuildConfig { *; }

# If using any enum or sealed for models
-keepclassmembers enum * { *; }
