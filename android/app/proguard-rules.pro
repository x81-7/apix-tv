# =====================================================================
# APiX TV — THE ULTIMATE R8 RULES (ROOT FLATTENING + DICTIONARY)
# =====================================================================

# 1. CORE AGGRESSIVE OBFUSCATION & FLATTENING
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''
-flattenpackagehierarchy ''
-mergeinterfacesaggressively
-dontusemixedcaseclassnames
-overloadaggressively
-useuniqueclassmembernames
-adaptclassstrings

# استخدام القاموس المخصص لجعل أسماء الملفات مربكة جداً (I, l, O, 0)
-obfuscationdictionary dictionary.txt
-classobfuscationdictionary dictionary.txt
-packageobfuscationdictionary dictionary.txt

# 2. SILENT KILLER: REMOVE ALL LOGS
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
    public static java.lang.String getStackTraceString(java.lang.Throwable);
}

# 3. METADATA STRIPPING & EXCEPTIONS (STABILITY)
-renamesourcefileattribute ""
-keepattributes Exceptions,Signature,*Annotation*,InnerClasses,EnclosingMethod

# 4. GOOGLE ADS (STRICT MINIMUM)
-keep class com.google.android.gms.ads.MobileAds { *; }
-dontwarn com.google.android.gms.**

# 5. MEDIA3 / EXOPLAYER (TIGHTENED)
-keep class androidx.media3.common.PlaybackException { *; }
-keep class androidx.media3.exoplayer.ExoPlayer { *; }
-dontwarn androidx.media3.**

# 6. ANDROID LIFECYCLE (CONSTRUCTORS ONLY)
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

-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# 7. GSON & DATA MODELS (INVISIBLE MODELS)
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# 8. ROOM & SQLCIPHER (EXPERT RESTRICTION)
-keep @androidx.room.Database class *
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao class *
-keep class net.sqlcipher.database.SQLiteDatabase { *; }
-dontwarn net.sqlcipher.**

# 9. RETROFIT & OKHTTP
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}

# 10. COMPOSE STABILITY
-keep class androidx.compose.runtime.Composer { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**
-dontwarn androidx.compose.**

# 11. ANDROID SECURITY CRYPTO
-keep class androidx.security.crypto.EncryptedSharedPreferences { *; }
-keep class androidx.security.crypto.MasterKey { *; }

# 12. NDK JNI BRIDGE (THE LONE SURVIVOR)
-adaptresourcefilenames
-adaptresourcefilecontents
# نحافظ على جسر C++ فقط لكي لا ينهار التطبيق
-keep class com.apix.app.x { 
    native <methods>; 
}

# JNI callback target used by tostinfo.cpp in Debug diagnostics. Its exact
# binary name and callback method must survive any non-debuggable minified build.
-keep class com.apix.app.security.TostInfo {
    public static void showToastStatic(java.lang.String);
    native <methods>;
}

# Net owns the independent nvp.cpp JNI surface; keep native method names stable.
-keepclasseswithmembernames class com.apix.app.Net {
    native <methods>;
}
