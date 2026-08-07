package com.apix.app.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.apix.app.BuildConfig;

/**
 * TostInfo — the single, centralized reporter for every security kill in the
 * app (native OR java). Direct {@code Toast.makeText(...)} calls from
 * detection code are forbidden — everything routes through here so that:
 *
 * <ul>
 *   <li>Release builds NEVER show a toast (silent kill), regardless of any
 *       server-side or local flag.</li>
 *   <li>Debug builds only show diagnostic toasts when the admin dashboard
 *       toggle {@code debug_kill_toasts} is on. Otherwise → silent kill.</li>
 *   <li>When enabled, the toast stays for 5 s so the developer can read
 *       the offending file/function, then the process terminates.</li>
 * </ul>
 *
 * Wire-up:
 *   {@link #init(Context)} must be called once from {@code ApixApplication}
 *   BEFORE any guard runs. It binds the JNI callback and seeds the runtime
 *   flag from SharedPreferences. Subsequent {@link #setDebugEnabled(Context,
 *   boolean)} calls (usually from handshake or cached-data sync) update the
 *   flag both in prefs and in the native atomic.
 */
public final class TostInfo {

    private static final String PREF = "apix_debug";
    private static final String KEY = "debug_kill_toasts";
    private static volatile Context APP_CTX = null;
    private static volatile boolean BOUND = false;

    static {
        try { System.loadLibrary("v"); } catch (Throwable ignored) {}
    }

    private static native void jniBind();
    private static native void jniSetDebugEnabled(boolean enabled);
    private static native void jniReport(String file, String func);

    private TostInfo() {}

    public static synchronized void init(Context ctx) {
        if (ctx == null) return;
        APP_CTX = ctx.getApplicationContext();
        if (!BOUND) {
            try { jniBind(); BOUND = true; } catch (Throwable ignored) {}
        }
        boolean flag = isEnabled(APP_CTX);
        try { jniSetDebugEnabled(flag); } catch (Throwable ignored) {}
    }

    /** True only when: it's a debug build AND admin toggle is on. */
    public static boolean isEnabled(Context ctx) {
        if (!BuildConfig.DEBUG) return false;
        if (ctx == null) return false;
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY, false);
    }

    /** Persist + propagate to native. */
    public static void setDebugEnabled(Context ctx, boolean enabled) {
        if (ctx == null) return;
        SharedPreferences sp = ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        sp.edit().putBoolean(KEY, enabled).apply();
        // Release builds are hard-gated in native + here.
        boolean effective = BuildConfig.DEBUG && enabled;
        try { jniSetDebugEnabled(effective); } catch (Throwable ignored) {}
    }

    /**
     * Called from Java guards (g1/g2/g3/…) when a threat fires.
     * Never returns — either shows a toast+sleep+kill (debug+enabled) or kills
     * the process silently (release OR toggle off).
     */
    public static void report(String file, String func) {
        boolean canShow = BuildConfig.DEBUG && isEnabled(APP_CTX);
        if (!canShow) {
            hardKill();
            return;
        }
        try { jniReport(file, func); return; } catch (Throwable ignored) {}
        // Fallback path if native bridge failed for any reason.
        try {
            final Context c = APP_CTX;
            if (c != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(c, "⚠ APiX Guard: " + file + " :: " + func, Toast.LENGTH_LONG).show());
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            }
        } catch (Throwable ignored) {}
        hardKill();
    }

    private static void hardKill() {
        try { android.os.Process.killProcess(android.os.Process.myPid()); } catch (Throwable ignored) {}
        System.exit(0);
    }

    /** Convenience: pipe context-less callers (native) to a UI toast. */
    @SuppressWarnings("unused") // called via JNI
    public static void showToastStatic(String msg) {
        final Context c = APP_CTX;
        if (c == null) return;
        try {
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(c, msg, Toast.LENGTH_LONG).show());
        } catch (Throwable ignored) {}
    }
}
