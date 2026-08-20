package com.apix.app.vip;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Encrypted local cache for VIP subscription state.
 *
 * Layout (encrypted via EncryptedSharedPreferences/AES-256):
 *   - active   : boolean
 *   - expires  : long  (epoch millis, 0 = unknown)
 *   - cachedAt : long
 */
public final class VipCache {

    private static final String TAG = "VipCache";
    private static final String FILE = "vip_cache_secure_v1";
    private static final String K_ACTIVE = "active";
    private static final String K_EXPIRES = "expires";
    private static final String K_CACHED_AT = "cached_at";
    /** Re-validate against server when local cache is older than this. */
    public static final long REVALIDATE_AFTER_MS = 5L * 60 * 1000;

    private final SharedPreferences prefs;

    public VipCache(Context ctx) {
        this.prefs = open(ctx.getApplicationContext());
    }

    private static SharedPreferences open(Context ctx) {
        try {
            MasterKey master = new MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    ctx, FILE, master,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            Log.w(TAG, "encrypted prefs unavailable, falling back to plaintext: " + e.getMessage());
            return ctx.getSharedPreferences(FILE + "_fallback", Context.MODE_PRIVATE);
        }
    }

    public void store(boolean active, long expiresAt) {
        prefs.edit()
                .putBoolean(K_ACTIVE, active)
                .putLong(K_EXPIRES, expiresAt)
                .putLong(K_CACHED_AT, System.currentTimeMillis())
                .apply();
    }

    public void clear() { prefs.edit().clear().apply(); }

    public boolean hasCache() { return prefs.contains(K_CACHED_AT); }

    /** True if local cache says VIP is currently active. */
    public boolean isActiveNow() {
        if (!hasCache()) return false;
        if (!prefs.getBoolean(K_ACTIVE, false)) return false;
        long exp = prefs.getLong(K_EXPIRES, 0L);
        long now = System.currentTimeMillis();
        if (exp > now) return true;
        // Some server responses intentionally omit expiresAt. Keep an
        // authenticated active verdict usable locally until the normal
        // revalidation window elapses instead of making ads reappear.
        if (exp <= 0L) {
            long cachedAt = prefs.getLong(K_CACHED_AT, 0L);
            return cachedAt > 0L && (now - cachedAt) <= REVALIDATE_AFTER_MS;
        }
        return false;
    }

    /** True if we should re-check server (cache empty, expired, or stale). */
    public boolean needsServerCheck() {
        if (!hasCache()) return true;
        long exp = prefs.getLong(K_EXPIRES, 0L);
        long cachedAt = prefs.getLong(K_CACHED_AT, 0L);
        long now = System.currentTimeMillis();
        // Expired locally → revalidate (in case admin renewed).
        if (exp <= now) return true;
        // Older than threshold → revalidate to catch revocations.
        return now - cachedAt > REVALIDATE_AFTER_MS;
    }

    public long expiresAt() { return prefs.getLong(K_EXPIRES, 0L); }
}
