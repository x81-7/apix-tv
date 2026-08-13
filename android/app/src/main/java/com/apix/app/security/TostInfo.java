package com.apix.app.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * TostInfo — واجهة Java للتوست الأمني من NDK.
 * jniBind() يجب استدعاؤها مرة واحدة عند الإقلاع.
 * setDebugEnabled() تُفعَّل من لوحة التحكم فقط.
 */
public final class TostInfo {

    private static final String PREFS = "apix_dbg";
    private static final String KEY_ENABLED = "kill_toasts";

    private static Context appCtx;

    // استدعِ هذا مرة واحدة في SplashActivity
    public static void init(Context ctx) {
        appCtx = ctx.getApplicationContext();
        boolean enabled = BuildConfig.DEBUG
                && ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                      .getBoolean(KEY_ENABLED, false);
        jniSetDebugEnabled(enabled);
        jniSetRuntimeDebug(enabled);
        jniBind(); // يُمرر Class reference للـ NDK
    }

    // يُستدعى من لوحة التحكم لتفعيل/تعطيل رسائل القتل في Debug
    public static void setKillToastsEnabled(Context ctx, boolean enabled) {
        if (!BuildConfig.DEBUG) return; // لا أثر في Release أبداً
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
           .edit().putBoolean(KEY_ENABLED, enabled).apply();
        jniSetDebugEnabled(enabled);
        jniSetRuntimeDebug(enabled);
    }

    // يُستدعى من NDK عبر CallStaticVoidMethod
    public static void showToastStatic(String message) {
        if (!BuildConfig.DEBUG) return;
        Context ctx = appCtx;
        if (ctx == null) return;
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
        );
    }

    // JNI
    public static native void jniBind();
    public static native void jniSetDebugEnabled(boolean enabled);
    public static native void jniSetRuntimeDebug(boolean enabled);
    public static native void jniReport(String file, String func);
}