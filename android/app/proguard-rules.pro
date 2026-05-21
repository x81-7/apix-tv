# =====================================================================
# APiX TV — Final Optimized Production Rules
# =====================================================================

# ===== AGGRESSIVE OPTIMIZATION =====
-optimizationpasses 7
-allowaccessmodification
-mergeinterfacesaggressively
-repackageclasses 'a'
-dontusemixedcaseclassnames
-overloadaggressively
-useuniqueclassmembernames
-optimizations !code/simplification/arithmetic,!code/allocation/variable

# ===== LOG REMOVAL =====
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# ===== CORE ATTRIBUTES =====
-renamesourcefileattribute ""
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# ===== 1-7 (Libraries: Ads, Media3, Gson, Retrofit, Compose) =====
-keep class com.google.android.gms.ads.** { *; }
-keep interface com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.**

-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

-keepnames class * extends android.app.Activity
-keepnames class * extends androidx.appcompat.app.AppCompatActivity
-keepnames class * extends androidx.fragment.app.Fragment
-keepnames class * extends android.app.Service
-keepnames class * extends android.content.BroadcastReceiver
-keepnames class * extends android.app.Application

-keepclassmembers class * extends androidx.lifecycle.ViewModel { <init>(...); }

-keepnames class com.apix.app.data.**
-keepclassmembers class com.apix.app.data.** { <fields>; }
-keepclassmembers class * { @com.google.gson.annotations.SerializedName <fields>; }
-keep class com.google.gson.reflect.TypeToken { *; }

-keepclassmembers class * { @retrofit2.http.* <methods>; }
-dontwarn retrofit2.**,okhttp3.**,okio.**

-keep class androidx.compose.runtime.Composer { *; }
-dontwarn kotlinx.coroutines.**,androidx.compose.**

# ===== 8-10 (SECURITY LAYER - DO NOT TOUCH) =====
-keepclassmembers class com.apix.app.security.GuardRunner { public static java.lang.String runAll(android.content.Context); }
-keepclassmembers class com.apix.app.security.HandshakeClient { public static com.apix.app.security.HandshakeClient$Verdict handshake(...); }
-keepclassmembers class com.apix.app.security.DeviceIntegrity { public static boolean runCheck(...); }
-keepclassmembers class com.apix.app.util.StringObfuscator { public static java.lang.String d(java.lang.String); public static java.lang.String encode(java.lang.String); }

-keep class androidx.security.crypto.EncryptedSharedPreferences { *; }
-keep class androidx.security.crypto.MasterKey { *; }

-adaptresourcefilenames
-adaptresourcefilecontents
-keepclasseswithmembernames class * { native <methods>; }
-keep class com.apix.app.security.g4 { *; }
-keep class com.apix.app.security.g5 { *; }

-keep class androidx.room.** { *; }
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**
-keep class com.apix.app.data.** { *; }
