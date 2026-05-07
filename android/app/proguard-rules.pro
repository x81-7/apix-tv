# =====================================================================
# APiX TV — Hardened R8 Rules (The Production Zenith)
# =====================================================================

# ===== CORE OPTIMIZATION & AGGRESSIVE OBFUSCATION =====
-optimizationpasses 5
-allowaccessmodification
-repackageclasses 'a'
-dontusemixedcaseclassnames
-overloadaggressively
-useuniqueclassmembernames

# ===== REMOVE LOGS =====
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# ===== ATTRIBUTES (SAFE MINIMUM) =====
# [CRITICAL FIX]: *Annotation* restored to prevent Retrofit/Gson/DI crashes!
-renamesourcefileattribute ""
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# =====================================================================
# 1) Google Ads (STRICT KEEP)
# =====================================================================
-keep class com.google.android.gms.ads.** { *; }
-keep interface com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.**

# =====================================================================
# 2) Media3 / ExoPlayer (STRICT KEEP)
# =====================================================================
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# =====================================================================
# 3) Android Components (NAMES & CONSTRUCTORS ONLY)
# =====================================================================
-keepnames class * extends android.app.Activity
-keepnames class * extends androidx.appcompat.app.AppCompatActivity
-keepnames class * extends androidx.fragment.app.Fragment
-keepnames class * extends android.app.Service
-keepnames class * extends android.content.BroadcastReceiver
-keepnames class * extends android.app.Application

-keepclassmembers class * extends android.app.Activity { public <init>(); }
-keepclassmembers class * extends androidx.appcompat.app.AppCompatActivity { public <init>(); }
-keepclassmembers class * extends androidx.fragment.app.Fragment { public <init>(); }
-keepclassmembers class * extends android.app.Service { public <init>(); }
-keepclassmembers class * extends android.content.BroadcastReceiver { public <init>(); }
-keepclassmembers class * extends android.app.Application { public <init>(); }

# =====================================================================
# 4) ViewModels
# =====================================================================
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# =====================================================================
# 5) Gson & Generics (BALANCED SECURITY & STABILITY)
# =====================================================================
# [STABILITY FIX]: Keep names so nested models and reflection don't crash
-keepnames class com.apix.app.data.**
-keepclassmembers class com.apix.app.data.** {
    <fields>;
}
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# =====================================================================
# 6) Retrofit & OkHttp
# =====================================================================
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# =====================================================================
# 7) Compose & Coroutines
# =====================================================================
# [STABILITY FIX]: Prevent silent white screens in some Compose versions
-keep class androidx.compose.runtime.Composer { *; }

-dontwarn kotlinx.coroutines.**
-dontwarn androidx.compose.**

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler

# =====================================================================
# 8) Security Layer (MILITARY GRADE)
# =====================================================================
-keepclassmembers class com.apix.app.security.GuardRunner {
    public static java.lang.String runAll(android.content.Context);
}
-keepclassmembers class com.apix.app.security.HandshakeClient {
    public static com.apix.app.security.HandshakeClient$Verdict handshake(...);
}
-keepclassmembers class com.apix.app.security.DeviceIntegrity {
    public static boolean runCheck(...); 
}
-keepclassmembers class com.apix.app.util.StringObfuscator {
    public static java.lang.String d(java.lang.String);
    public static java.lang.String encode(java.lang.String);
}

# =====================================================================
# 9) Android Security
# =====================================================================
-keep class androidx.security.crypto.EncryptedSharedPreferences { *; }
-keep class androidx.security.crypto.MasterKey { *; }

# =====================================================================
# 10) Anti Reverse Engineering
# =====================================================================
-adaptresourcefilenames
-adaptresourcefilecontents
# =====================================================================
# 11) C++ NDK Vault & JNI Bridge (CRITICAL)
# =====================================================================
# Prevents renaming of native methods in KeysVault to avoid UnsatisfiedLinkError
-keep class com.apix.app.security.KeysVault {
    native <methods>;
}
-keepclassmembers class com.apix.app.security.KeysVault {
    <methods>;
}

# =====================================================================
# 12) SQLCipher & Room Persistence (ENCRYPTED DB)
# =====================================================================
# Keeps SQLCipher and SQLite framework from being obfuscated
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-keep class androidx.sqlite.db.** { *; }
-keep class androidx.room.** { *; }
-keep class com.apix.app.db.** { *; }
-dontwarn net.sqlcipher.**

# =====================================================================
# 13) HMAC Signing & Payload Security
# =====================================================================
# Protects the HMAC signature generator from being stripped or renamed
-keep class com.apix.app.security.HmacSigner { *; }
-keep class com.apix.app.PayloadCipher { *; }

# =====================================================================
# 14) WebView Bridge (Android Interface)
# =====================================================================
# Ensures JavaScript-to-Java communication remains intact
-keepclassmembers class com.apix.app.MainActivity$AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class com.apix.app.ui.screens.HybridPlayerScreenKt** {
    @android.webkit.JavascriptInterface <methods>;
}

# =====================================================================
# 15) General Hardening
# =====================================================================
-dontnote net.sqlcipher.**
-dontnote com.apix.app.security.**
-keepattributes SourceFile,LineNumberTable
