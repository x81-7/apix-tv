package com.apix.app.security;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/**
 * Central ban enforcement — replaces the old visible KillScreenActivity, whose
 * exposed layout made it a reverse-engineering target.
 *
 * Behaviour requested by the product owner:
 *   • Banned device  → WIPE all locally cached channels/streams + close SILENTLY.
 *   • The ban verdict is cached so subsequent launches enforce instantly
 *     (before any network) and this device can never pass again.
 */
public final class Enforcement {

    private Enforcement() {}

    /** SharedPreferences that hold the offline channel/stream cache. */
    private static final String CACHE_PREFS = "supabase_cache";
    private static final String BAN_PREFS   = "ban_cache";

    /** true when the cached verdict marks this device as actively banned. */
    public static boolean isBannedCached(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(BAN_PREFS, Context.MODE_PRIVATE);
            long lastCheck = sp.getLong("last_check", 0L);
            if (lastCheck == 0L) return false;
            String status = sp.getString("last_status", "ACTIVE");
            if (status == null) return false;
            switch (status) {
                case "BANNED":
                case "PERMA_BAN":
                case "TAMPERED_MOD":
                case "ENVIRONMENT_DANGER":
                    return true;
                case "TEMP_BAN": {
                    String until = sp.getString("ban_until", null);
                    if (until != null && !until.isEmpty()) {
                        try {
                            long u = java.time.Instant.parse(until).toEpochMilli();
                            return System.currentTimeMillis() < u;
                        } catch (Throwable ignored) {}
                    }
                    return System.currentTimeMillis() - lastCheck < 24 * 60 * 60 * 1000L;
                }
                default:
                    return false;
            }
        } catch (Throwable t) {
            return false;
        }
    }

    /** Persist the verdict so future cold-starts enforce without a round-trip. */
    public static void cacheVerdict(Context ctx, HandshakeClient.Verdict v) {
        if (v == null) return;
        try {
            ctx.getSharedPreferences(BAN_PREFS, Context.MODE_PRIVATE).edit()
                .putString("last_status", v.status)
                .putString("ban_until", v.banUntil)
                .putString("ban_reason", v.reason)
                .putString("telegram_url", v.telegramUrl)
                .putLong("last_check", System.currentTimeMillis())
                .apply();
        } catch (Throwable ignored) {}
    }

    /** Delete every locally stored channel / category / stream cache. */
    public static void wipeChannelCache(Context ctx) {
        try {
            ctx.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
                .edit().clear().apply();
        } catch (Throwable ignored) {}
        // Encrypted offline cache (SecureCacheManager, file "_sc_cache").
        try { com.apix.app.SecureCacheManager.clear(ctx); } catch (Throwable ignored) {}
        try {
            ctx.getSharedPreferences("_sc_cache", Context.MODE_PRIVATE).edit().clear().apply();
            ctx.getSharedPreferences("_sc_cache_p", Context.MODE_PRIVATE).edit().clear().apply();
        } catch (Throwable ignored) {}
        try {
            deleteRecursive(new File(ctx.getCacheDir(), "apix_image_cache"));
        } catch (Throwable ignored) {}
        try {
            File files = ctx.getFilesDir();
            if (files != null) {
                for (File f : files.listFiles() != null ? files.listFiles() : new File[0]) {
                    String n = f.getName().toLowerCase();
                    if (n.endsWith(".json") || n.contains("channel") || n.contains("cache") || n.contains("_sc_")) {
                        deleteRecursive(f);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }


    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    /**
     * Full enforcement: cache the verdict, wipe cached data if the server
     * ordered it, then close the app silently (no visible ban screen).
     */
    public static void enforce(Context ctx, HandshakeClient.Verdict v) {
        cacheVerdict(ctx, v);
        boolean wipe = v != null && (v.wipe
                || "BANNED".equals(v.status)
                || "PERMA_BAN".equals(v.status)
                || "TAMPERED_MOD".equals(v.status)
                || "ENVIRONMENT_DANGER".equals(v.status));
        if (wipe) wipeChannelCache(ctx);
        silentExit(ctx);
    }

    /** Silently terminate the process without any UI. */
    public static void silentExit(Context ctx) {
        try { com.apix.app.Net.nvpTerminate("ban"); } catch (Throwable ignored) {}
        // Native normally never returns. This fallback covers an ABI/JNI load
        // failure so a banned device cannot continue merely because libv failed.
        try { android.os.Process.killProcess(android.os.Process.myPid()); } catch (Throwable ignored) {}
    }
}
