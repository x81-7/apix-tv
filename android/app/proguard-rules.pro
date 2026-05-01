# ProGuard / R8 Rules for APiX App

# ===== AGGRESSIVE OBFUSCATION =====
-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose
-allowaccessmodification
-repackageclasses ''
-flattenpackagehierarchy ''

# Remove all Log calls in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# ===== KEEP RULES =====

# Keep ExoPlayer classes
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }

# Keep Gson runtime
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod,InnerClasses

# Keep Google Play Services (AdMob)
-keep class com.google.android.gms.** { *; }

# ===== DATA MODELS =====
# Gson reflects on field NAMES (we don't use @SerializedName everywhere).
# So: allow class renaming, but keep FIELD names intact for json mapping.
-keepclassmembers class com.apix.app.StreamConfig { <fields>; }
-keepclassmembers class com.apix.app.StreamConfig$* { <fields>; }
-keepclassmembers class com.apix.app.RemoteModels { <fields>; }
-keepclassmembers class com.apix.app.RemoteModels$* { <fields>; }
-keepclassmembers class com.apix.app.data.** { <fields>; }

# ===== SECURITY CLASSES — AGGRESSIVE OBFUSCATION =====
# Previously we kept full APIs; now we let R8 rename internals freely.
# We only keep what is reflectively referenced from outside.

# AppVerifier is invoked from Activities by direct call → no reflection,
# R8 can fully rename. (Removed prior keep block.)

# GuardRunner is the only entry point used by other security helpers.
# Allow R8 to obfuscate the class itself; keep just the method signature.
-keepclassmembers class com.apix.app.security.GuardRunner {
    public static java.lang.String runAll(android.content.Context);
}

# HandshakeClient → public verdict is reflected by JSON serialization,
# so keep its fields but allow class-name obfuscation.
-keepclassmembers class com.apix.app.security.HandshakeClient$Verdict { <fields>; }
-keepclassmembers class com.apix.app.security.HandshakeClient {
    public static com.apix.app.security.HandshakeClient$Verdict handshake(android.content.Context, java.lang.String, java.lang.String, java.lang.String);
}

# DeviceIntegrity has static utilities used by handshake — keep public methods only.
-keepclassmembers class com.apix.app.security.DeviceIntegrity {
    public static *;
}

# String deobfuscator — keep so XOR encoded literals can decode.
-keepclassmembers class com.apix.app.security.Obf {
    public static java.lang.String d(java.lang.String);
}

# SecureCacheManager — used by EncryptedSharedPreferences which IS reflective.
-keep class androidx.security.crypto.** { *; }
-keepclassmembers class com.apix.app.SecureCacheManager {
    public static *;
}

# Keep activities
-keep class com.apix.app.SplashActivity { *; }
-keep class com.apix.app.HomeActivity { *; }
-keep class com.apix.app.SubMenuActivity { *; }
-keep class com.apix.app.MainActivity { *; }
-keep class com.apix.app.PlayerActivity { *; }
-keep class com.apix.app.WebViewActivity { *; }
-keep class com.apix.app.ComposeActivity { *; }
-keep class com.apix.app.KillScreenActivity { *; }

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Coil
-keep class coil.** { *; }

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# ===== ANTI-DECOMPILATION =====
-renamesourcefileattribute ''
-keepattributes !SourceFile,!LineNumberTable
-adaptresourcefilecontents
-adaptresourcefilenames
