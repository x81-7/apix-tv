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

# Keep Gson classes
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep Google Play Services (AdMob)
-keep class com.google.android.gms.** { *; }

# Keep our model classes (needed for Gson deserialization)
-keep class com.apix.app.StreamConfig { *; }
-keep class com.apix.app.StreamConfig$* { *; }
-keep class com.apix.app.RemoteModels { *; }
-keep class com.apix.app.RemoteModels$* { *; }
-keep class com.apix.app.data.** { *; }

# Keep verifier - obfuscate internals but keep public API
-keep class com.apix.app.AppVerifier {
    public static ** getInstance(android.content.Context);
    public void startMonitor();
    public void stopMonitor();
    public java.lang.String runCheck();
    public void runCheckAsync(**);
    public java.lang.String getCurrentAppHash();
}

# Heavily obfuscate verifier internals
-keepclassmembers class com.apix.app.AppVerifier {
    private <methods>;
}

# Aggressively obfuscate security guards (signature/dex/anti-hook)
-keep class com.apix.app.security.GuardRunner {
    public static java.lang.String runAll(android.content.Context);
}
-keep class com.apix.app.security.HandshakeClient$Verdict { *; }
-keep class com.apix.app.security.HandshakeClient {
    public static com.apix.app.security.HandshakeClient$Verdict handshake(android.content.Context, java.lang.String, java.lang.String, java.lang.String);
}
-keep class com.apix.app.security.DeviceIntegrity {
    public static java.lang.String deviceId(android.content.Context);
    public static java.lang.String signatureHash(android.content.Context);
    public static java.lang.String dexChecksum(android.content.Context);
    public static java.lang.String environmentDanger(android.content.Context);
    public static boolean consumeFreshInstall(android.content.Context);
}
-keepclassmembers class com.apix.app.security.** {
    private <methods>;
    private <fields>;
}
# Keep SecureCacheManager public API (encrypted prefs need stable class)
-keep class com.apix.app.SecureCacheManager {
    public static *;
}
-keep class androidx.security.crypto.** { *; }

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
