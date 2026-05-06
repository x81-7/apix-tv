# =====================================================================
# ProGuard / R8 Rules for APiX TV
# Strategy: aggressive obfuscation on player/security/network/viewmodel,
# strict KEEP for AdMob, ExoPlayer/Media3, Activities, JSON data classes.
# =====================================================================

# ===== AGGRESSIVE OBFUSCATION =====
-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose
-allowaccessmodification
-repackageclasses ''

# Strip log calls
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Common attributes (signatures + annotations needed by Gson/Retrofit/Compose)
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes EnclosingMethod,InnerClasses
-keepattributes InnerClasses

# =====================================================================
# 1) AdMob — must NOT be touched
# =====================================================================
-keep class com.google.android.gms.ads.** { *; }
-keep interface com.google.android.gms.ads.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# =====================================================================
# 2) ExoPlayer / Media3 — must NOT be touched
# =====================================================================
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# =====================================================================
# 3) Android system: Activities, Application, Lifecycle
# =====================================================================
-keep public class * extends android.app.Activity
-keep public class * extends androidx.activity.ComponentActivity
-keep public class * extends androidx.appcompat.app.AppCompatActivity
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# Project Activities (manifest-declared) — full keep
-keep class com.apix.app.SplashActivity { *; }
-keep class com.apix.app.HomeActivity { *; }
-keep class com.apix.app.SubMenuActivity { *; }
-keep class com.apix.app.MainActivity { *; }
-keep class com.apix.app.PlayerActivity { *; }
-keep class com.apix.app.WebViewActivity { *; }
-keep class com.apix.app.WebAdActivity { *; }
-keep class com.apix.app.AboutActivity { *; }
-keep class com.apix.app.GateActivity { *; }
-keep class com.apix.app.ActivationActivity { *; }
-keep class com.apix.app.ComposeActivity { *; }
-keep class com.apix.app.KillScreenActivity { *; }
-keep class com.apix.app.ApixApplication { *; }
-keep class com.apix.app.NotificationService { *; }
-keep class com.apix.app.BootReceiver { *; }

# ViewModels (allow internal renaming, keep class name for instantiation)
-keep public class * extends androidx.lifecycle.ViewModel { <init>(...); }

# =====================================================================
# 4) JSON data classes (Gson reflects on FIELD names)
# =====================================================================
-keepclassmembers class com.apix.app.StreamConfig { <fields>; }
-keepclassmembers class com.apix.app.StreamConfig$* { <fields>; }
-keepclassmembers class com.apix.app.RemoteModels { <fields>; }
-keepclassmembers class com.apix.app.RemoteModels$* { <fields>; }
-keepclassmembers class com.apix.app.data.** { <fields>; }
-keep class com.apix.app.data.Models { *; }
-keep class com.apix.app.data.Models$* { *; }
# Gson runtime
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# =====================================================================
# 5) Retrofit / OkHttp (kept intact in case future code uses them)
# =====================================================================
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# =====================================================================
# 6) Coroutines / Compose / Coil
# =====================================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
-keep class coil.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# =====================================================================
# 7) Security helpers — keep ONLY what is reflected from outside.
#    Internals are free to be obfuscated.
# =====================================================================
-keepclassmembers class com.apix.app.security.GuardRunner {
    public static java.lang.String runAll(android.content.Context);
}
-keepclassmembers class com.apix.app.security.HandshakeClient$Verdict { <fields>; }
-keepclassmembers class com.apix.app.security.HandshakeClient {
    public static com.apix.app.security.HandshakeClient$Verdict handshake(android.content.Context, java.lang.String, java.lang.String, java.lang.String);
}
-keepclassmembers class com.apix.app.security.DeviceIntegrity {
    public static *;
}
-keepclassmembers class com.apix.app.security.Obf {
    public static java.lang.String d(java.lang.String);
}

# String obfuscator — used reflectively-ish from many places, keep the API
-keep class com.apix.app.util.StringObfuscator {
    public static java.lang.String d(java.lang.String);
    public static java.lang.String encode(java.lang.String);
}

# EncryptedSharedPreferences (reflection)
-keep class androidx.security.crypto.** { *; }
-keepclassmembers class com.apix.app.SecureCacheManager { public static *; }

# =====================================================================
# 8) Anti-decompilation polish
# =====================================================================
-renamesourcefileattribute ''
-keepattributes !SourceFile,!LineNumberTable
-adaptresourcefilecontents
-adaptresourcefilenames
