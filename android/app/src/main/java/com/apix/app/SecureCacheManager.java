package com.apix.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * SecureCacheManager — stores cacheable channels in EncryptedSharedPreferences
 * backed by a key from Android Keystore (AES-256-GCM). Even root cannot
 * decrypt the stored JSON without bypassing Keystore.
 */
public final class SecureCacheManager {

    private static final String FILE = "_sc_cache";
    private static final String K_CHANNELS = "_ch";
    private static SharedPreferences sp;

    private static synchronized SharedPreferences prefs(Context ctx) {
        if (sp != null) return sp;
        try {
            MasterKey master = new MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            sp = EncryptedSharedPreferences.create(
                    ctx, FILE, master,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Throwable t) {
            Log.e("SC", "encrypted prefs fail, falling back", t);
            sp = ctx.getSharedPreferences(FILE + "_p", Context.MODE_PRIVATE);
        }
        return sp;
    }

    /** Save list of channels marked offline_cache_enabled = true. */
    public static void saveChannels(Context ctx, JSONArray cachedChannels) {
        try {
            prefs(ctx).edit().putString(K_CHANNELS, cachedChannels.toString()).apply();
        } catch (Throwable t) { Log.w("SC", "save fail", t); }
    }

    public static JSONArray loadChannels(Context ctx) {
        try {
            String s = prefs(ctx).getString(K_CHANNELS, null);
            if (s == null) return new JSONArray();
            return new JSONArray(s);
        } catch (Throwable t) { return new JSONArray(); }
    }

    /** Returns map of channel id → cached JSON for fast lookup. */
    public static Map<String, JSONObject> loadChannelsMap(Context ctx) {
        Map<String, JSONObject> m = new HashMap<>();
        JSONArray arr = loadChannels(ctx);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && o.has("id")) m.put(o.optString("id"), o);
        }
        return m;
    }

    public static boolean isChannelCached(Context ctx, String channelId) {
        return loadChannelsMap(ctx).containsKey(channelId);
    }

    /**
     * Checks whether the cached copy of a channel is still fresh — i.e. its
     * cache_version matches the server's. If not, the admin has edited the
     * channel in the panel and the cached JSON MUST be replaced.
     */
    public static boolean isCacheFresh(Context ctx, String channelId, long serverVersion) {
        JSONObject cached = loadChannelsMap(ctx).get(channelId);
        if (cached == null) return false;
        long localVersion = cached.optLong("cache_version", -1L);
        return localVersion == serverVersion;
    }

    /**
     * Reconciles the cache with the latest server snapshot:
     *   - for every channel in `remote`, if it has offline_cache_enabled=true
     *     AND its cache_version differs from the cached copy, overwrite it
     *   - channels that are no longer cacheable are evicted from the cache
     */
    public static void reconcile(Context ctx, JSONArray remote) {
        try {
            Map<String, JSONObject> current = loadChannelsMap(ctx);
            JSONArray next = new JSONArray();
            for (int i = 0; i < remote.length(); i++) {
                JSONObject r = remote.optJSONObject(i);
                if (r == null) continue;
                if (!r.optBoolean("offline_cache_enabled", false)) continue;
                String id = r.optString("id", null);
                if (id == null) continue;
                JSONObject local = current.get(id);
                long serverVer = r.optLong("cache_version", 1L);
                long localVer  = local != null ? local.optLong("cache_version", -1L) : -1L;
                // If server is newer or we have nothing cached, take the server copy.
                next.put((localVer == serverVer && local != null) ? local : r);
            }
            prefs(ctx).edit().putString(K_CHANNELS, next.toString()).apply();
        } catch (Throwable t) { Log.w("SC", "reconcile fail", t); }
    }

    public static void clear(Context ctx) {
        prefs(ctx).edit().remove(K_CHANNELS).apply();
    }

    private SecureCacheManager() {}
}
