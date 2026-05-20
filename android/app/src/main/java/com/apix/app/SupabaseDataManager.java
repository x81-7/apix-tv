package com.apix.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * SupabaseDataManager — fetches categories, channels, side menus, sub-channels
 * and system settings directly from Supabase REST API.
 *
 * Offline-first: caches full JSON locally. On pull-to-refresh only metadata
 * (streams, hidden state, keys) is re-fetched; images/names come from cache.
 */
public class SupabaseDataManager {

    private static final String TAG = "SupabaseData";
    private static final String PREFS = "supabase_cache";
    private static final String KEY_CATEGORIES = "categories_json";
    private static final String KEY_CHANNELS = "channels_json";
    private static final String KEY_SIDE_MENUS = "side_menus_json";
    private static final String KEY_SUB_CHANNELS = "sub_channels_json";
    private static final String KEY_SETTINGS = "settings_json";
    private static final String KEY_LAST_FETCH = "last_fetch_ts";
    private static final String KEY_BUNDLE_ETAG = "bundle_etag";
    private static final String KEY_CLOUD_URL = "cloud_url";

    private static final String SUPABASE_URL = BuildConfig.CLOUD_URL;
    private static final String SUPABASE_ANON_KEY = BuildConfig.CLOUD_ANON_KEY;

    public interface DataCallback {
        void onSuccess(DataBundle data);
        void onError(String error);
    }

    public static class DataBundle {
        public List<RemoteModels.Category> categories = new ArrayList<>();
        public Map<String, RemoteModels.SideMenu> sideMenus = new HashMap<>();
        public List<RemoteModels.Channel> allChannels = new ArrayList<>();
        public Map<String, String> settings = new HashMap<>();
    }

    /** Load cached data synchronously (for instant UI). Returns null if no cache. */
    public static DataBundle loadCached(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String cachedCloud = sp.getString(KEY_CLOUD_URL, null);
        if (cachedCloud != null && !cachedCloud.equals(SUPABASE_URL)) return null;
        String catsJson = sp.getString(KEY_CATEGORIES, null);
        if (catsJson == null) return null;
        try {
            return parseAll(
                catsJson,
                sp.getString(KEY_CHANNELS, "[]"),
                sp.getString(KEY_SIDE_MENUS, "[]"),
                sp.getString(KEY_SUB_CHANNELS, "[]"),
                sp.getString(KEY_SETTINGS, "[]")
            );
        } catch (Exception e) {
            Log.w(TAG, "Cache parse error", e);
            return null;
        }
    }

    /**
     * Fetch fresh data via the cached-data Edge Function. Sends If-None-Match;
     * on 304 we just return the local cache (zero DB hit, almost-zero egress).
     * On 200 we re-cache the bundle and the new ETag. Falls back to direct REST
     * on errors so the app still works if the function is down.
     */
    public static void fetchRemote(Context ctx, DataCallback cb) {
        new Thread(() -> {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            try {
                String cachedCloud = sp.getString(KEY_CLOUD_URL, null);
                if (cachedCloud == null || !cachedCloud.equals(SUPABASE_URL)) {
                    sp.edit().clear().putString(KEY_CLOUD_URL, SUPABASE_URL).apply();
                }
                String prevEtag = sp.getString(KEY_BUNDLE_ETAG, null);
                BundleResponse resp = fetchBundleEdge(prevEtag);

                if (resp.notModified) {
                    DataBundle bundle = loadCached(ctx);
                    if (bundle != null) {
                        cb.onSuccess(bundle);
                        return;
                    }
                    // No cache locally despite 304 — force a full pull next time.
                    sp.edit().remove(KEY_BUNDLE_ETAG).apply();
                }

                String catsJson  = resp.body != null ? resp.categoriesJson : null;
                String chansJson = resp.body != null ? resp.channelsJson : null;
                String menusJson = resp.body != null ? resp.menusJson : null;
                String subsJson  = resp.body != null ? resp.subsJson : null;
                String settingsJson = resp.body != null ? resp.settingsJson : null;

                if (catsJson == null) {
                    // Edge function unavailable → fall back to direct REST.
                    catsJson = restGet("/rest/v1/categories?select=*&order=sort_order");
                    chansJson = restGet("/rest/v1/channels?select=*&order=sort_order");
                    menusJson = restGet("/rest/v1/side_menus?select=*&order=sort_order");
                    subsJson = restGet("/rest/v1/sub_channels?select=*&order=sort_order");
                    settingsJson = restGet("/rest/v1/system_settings?select=*");
                }

                SharedPreferences.Editor ed = sp.edit()
                    .putString(KEY_CATEGORIES, catsJson)
                    .putString(KEY_CHANNELS, chansJson)
                    .putString(KEY_SIDE_MENUS, menusJson)
                    .putString(KEY_SUB_CHANNELS, subsJson)
                    .putString(KEY_SETTINGS, settingsJson)
                    .putString(KEY_CLOUD_URL, SUPABASE_URL)
                    .putLong(KEY_LAST_FETCH, System.currentTimeMillis());
                if (resp.etag != null) ed.putString(KEY_BUNDLE_ETAG, resp.etag);
                ed.apply();

                DataBundle bundle = parseAll(catsJson, chansJson, menusJson, subsJson, settingsJson);
                cb.onSuccess(bundle);
            } catch (Exception e) {
                Log.e(TAG, "Fetch error", e);
                cb.onError(e.getMessage() != null ? e.getMessage() : "خطأ غير معروف");
            }
        }).start();
    }

    private static class BundleResponse {
        boolean notModified;
        String etag;
        String body;
        String categoriesJson;
        String channelsJson;
        String menusJson;
        String subsJson;
        String settingsJson;
    }

    private static BundleResponse fetchBundleEdge(String prevEtag) {
        BundleResponse resp = new BundleResponse();
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(SUPABASE_URL + "/functions/v1/cached-data").openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("apikey", SUPABASE_ANON_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_ANON_KEY);
            conn.setRequestProperty("Accept", "application/json");
            try { com.apix.app.security.g5.INSTANCE.h(conn, "{}"); } catch (Throwable ignored) {}
            if (prevEtag != null) conn.setRequestProperty("If-None-Match", prevEtag);

            int code = conn.getResponseCode();
            String etag = conn.getHeaderField("ETag");
            if (etag != null) resp.etag = etag;

            if (code == 304) {
                resp.notModified = true;
                return resp;
            }
            if (code < 200 || code >= 300) return resp;

            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            String envelope = sb.toString();
            // The Edge Function always returns AES-256-GCM encrypted JSON
            // { "iv": "...", "data": "..." }. Decrypt before parsing.
            String plain;
            try {
                plain = PayloadCipher.decryptEnvelope(envelope);
            } catch (Exception decErr) {
                Log.e(TAG, "cached-data decrypt failed", decErr);
                return resp;
            }
            resp.body = plain;
            JSONObject obj = new JSONObject(plain);
            resp.categoriesJson = obj.optJSONArray("categories") != null ? obj.getJSONArray("categories").toString() : "[]";
            resp.channelsJson  = obj.optJSONArray("channels") != null ? obj.getJSONArray("channels").toString() : "[]";
            resp.menusJson     = obj.optJSONArray("side_menus") != null ? obj.getJSONArray("side_menus").toString() : "[]";
            resp.subsJson      = obj.optJSONArray("sub_channels") != null ? obj.getJSONArray("sub_channels").toString() : "[]";
            resp.settingsJson  = obj.optJSONArray("system_settings") != null ? obj.getJSONArray("system_settings").toString() : "[]";
        } catch (Exception e) {
            Log.w(TAG, "fetchBundleEdge failed; will fall back to REST", e);
        }
        return resp;
    }

    /** Fetch only allowed signatures from system_settings. */
    public static List<String> fetchSignatures(Context ctx) {
        return fetchHashList("security_signatures");
    }

    /** Fetch BLOCKED signatures from system_settings (manual ban list). */
    public static List<String> fetchBlockedSignatures(Context ctx) {
        return fetchHashList("security_blocked_signatures");
    }

    private static List<String> fetchHashList(String key) {
        try {
            String json = restGet("/rest/v1/system_settings?key=eq." + key + "&select=value");
            JSONArray arr = new JSONArray(json);
            if (arr.length() == 0) return Collections.emptyList();
            JSONObject row = arr.getJSONObject(0);
            JSONArray sigs = row.optJSONArray("value");
            if (sigs == null) return Collections.emptyList();
            List<String> result = new ArrayList<>();
            for (int i = 0; i < sigs.length(); i++) {
                JSONObject sig = sigs.getJSONObject(i);
                String hash = sig.optString("hash", "");
                boolean enabled = sig.optBoolean("enabled", true);
                if (enabled && !hash.isEmpty()) result.add(hash.toLowerCase());
            }
            return result;
        } catch (Exception e) {
            Log.w(TAG, "fetchHashList(" + key + ") error", e);
            return Collections.emptyList();
        }
    }

    /** Fetch app update settings. */
    public static JSONObject fetchAppUpdate() {
        try {
            String json = restGet("/rest/v1/system_settings?key=eq.appUpdate&select=value");
            JSONArray arr = new JSONArray(json);
            if (arr.length() == 0) return null;
            return arr.getJSONObject(0).optJSONObject("value");
        } catch (Exception e) {
            Log.w(TAG, "fetchAppUpdate error", e);
            return null;
        }
    }

    /** Fetch gate (front login screen) config. */
    public static JSONObject fetchGateConfig() {
        try {
            String json = restGet("/rest/v1/system_settings?key=eq.gateConfig&select=value");
            JSONArray arr = new JSONArray(json);
            if (arr.length() == 0) return null;
            return arr.getJSONObject(0).optJSONObject("value");
        } catch (Exception e) {
            Log.w(TAG, "fetchGateConfig error", e);
            return null;
        }
    }

    /** Fetch app settings (e.g. showSettingsSection). */
    public static JSONObject fetchAppSettings() {
        try {
            String json = restGet("/rest/v1/system_settings?key=eq.appSettings&select=value");
            JSONArray arr = new JSONArray(json);
            if (arr.length() == 0) return null;
            return arr.getJSONObject(0).optJSONObject("value");
        } catch (Exception e) {
            Log.w(TAG, "fetchAppSettings error", e);
            return null;
        }
    }

    public static JSONObject fetchAdConfig() {
        try {
            String json = restGet("/rest/v1/system_settings?key=eq.adConfig&select=value");
            JSONArray arr = new JSONArray(json);
            if (arr.length() == 0) return null;
            return arr.getJSONObject(0).optJSONObject("value");
        } catch (Exception e) {
            Log.w(TAG, "fetchAdConfig error", e);
            return null;
        }
    }

    /**
     * Fetches the developer-allow-list (UUIDs) from
     * `system_settings.developer_uuids` and caches it locally so the strict
     * emulator gate (DeviceIntegrity.shouldStrictBanEmulator) can read it
     * without a network call on next launch.
     */
    public static void syncDeveloperUUIDs(android.content.Context ctx) {
        try {
            String json = restGet("/rest/v1/system_settings?key=eq.developer_uuids&select=value");
            JSONArray arr = new JSONArray(json);
            String value = "[]";
            if (arr.length() > 0) {
                JSONArray inner = arr.getJSONObject(0).optJSONArray("value");
                if (inner != null) value = inner.toString();
            }
            ctx.getSharedPreferences("apix_dev_overrides", android.content.Context.MODE_PRIVATE)
                    .edit().putString("developer_uuids", value).apply();
        } catch (Exception e) {
            Log.w(TAG, "syncDeveloperUUIDs error", e);
        }
    }

    public static int fetchForcedCustomAdsCount() {
        try {
            String json = restGet("/rest/v1/system_settings?key=eq.forced_custom_ads_count&select=value");
            JSONArray arr = new JSONArray(json);
            if (arr.length() == 0) return 1;
            return Math.max(1, arr.getJSONObject(0).optInt("value", 1));
        } catch (Exception e) {
            Log.w(TAG, "fetchForcedCustomAdsCount error", e);
            return 1;
        }
    }

    public static JSONArray fetchVisibleCustomAds() {
        try {
            String json = restGet("/rest/v1/custom_ads?select=id,name,video_url,sort_order&hidden=is.false&order=sort_order.asc,created_at.asc");
            return new JSONArray(json);
        } catch (Exception e) {
            Log.w(TAG, "fetchVisibleCustomAds error", e);
            return new JSONArray();
        }
    }

    /** Fetch local custom-ads behavior config: { trigger: "off|app_open|on_channel|both", channelIds:[...] }. */
    public static JSONObject fetchLocalAdsConfig() {
        try {
            String json = restGet("/rest/v1/system_settings?key=eq.local_ads_config&select=value");
            JSONArray arr = new JSONArray(json);
            if (arr.length() == 0) return null;
            return arr.getJSONObject(0).optJSONObject("value");
        } catch (Exception e) {
            Log.w(TAG, "fetchLocalAdsConfig error", e);
            return null;
        }
    }

    /**
     * Fetch a single channel's latest stream directly from Supabase, bypassing
     * the local cache. Used when the panel has `offline_cache_enabled = false`
     * on a given channel — users must receive a fresh link every session.
     */
    public static void fetchChannelFresh(String channelId, FreshChannelCallback cb) {
        new Thread(() -> {
            try {
                String json = restGet("/rest/v1/channels?id=eq." + channelId + "&select=*");
                JSONArray arr = new JSONArray(json);
                if (arr.length() == 0) { cb.onError("not_found"); return; }
                RemoteModels.Channel ch = parseChannel(arr.getJSONObject(0));
                cb.onSuccess(ch);
            } catch (Exception e) {
                Log.w(TAG, "fetchChannelFresh error", e);
                cb.onError(e.getMessage() != null ? e.getMessage() : "error");
            }
        }).start();
    }

    public static void fetchSubChannelFresh(String subId, FreshSubCallback cb) {
        new Thread(() -> {
            try {
                String json = restGet("/rest/v1/sub_channels?id=eq." + subId + "&select=*");
                JSONArray arr = new JSONArray(json);
                if (arr.length() == 0) { cb.onError("not_found"); return; }
                RemoteModels.SubChannel sc = parseSubChannel(arr.getJSONObject(0));
                cb.onSuccess(sc);
            } catch (Exception e) {
                Log.w(TAG, "fetchSubChannelFresh error", e);
                cb.onError(e.getMessage() != null ? e.getMessage() : "error");
            }
        }).start();
    }

    public interface FreshChannelCallback {
        void onSuccess(RemoteModels.Channel channel);
        void onError(String error);
    }

    public interface FreshSubCallback {
        void onSuccess(RemoteModels.SubChannel sub);
        void onError(String error);
    }

    // ========== Internal ==========

    private static String restGet(String path) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(SUPABASE_URL + path).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("apikey", SUPABASE_ANON_KEY);
        conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_ANON_KEY);
        conn.setRequestProperty("Accept", "application/json");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static DataBundle parseAll(String catsJson, String chansJson, String menusJson, String subsJson, String settingsJson) throws Exception {
        DataBundle b = new DataBundle();

        JSONArray cats = new JSONArray(catsJson);
        JSONArray chans = new JSONArray(chansJson);
        JSONArray menus = new JSONArray(menusJson);
        JSONArray subs = new JSONArray(subsJson);
        JSONArray settings = new JSONArray(settingsJson);

        // Parse categories
        Map<String, RemoteModels.Category> catMap = new HashMap<>();
        for (int i = 0; i < cats.length(); i++) {
            JSONObject c = cats.getJSONObject(i);
            if (c.optBoolean("hidden", false)) continue;
            RemoteModels.Category cat = new RemoteModels.Category();
            cat.id = c.getString("id");
            cat.name = c.getString("name");
            cat.sortOrder = c.optInt("sort_order", 0);
            cat.hidden = false;
            cat.channels = new HashMap<>();
            catMap.put(cat.id, cat);
            b.categories.add(cat);
        }
        Collections.sort(b.categories, (a, bb) -> a.sortOrder - bb.sortOrder);

        // Parse channels and assign to categories
        for (int i = 0; i < chans.length(); i++) {
            JSONObject ch = chans.getJSONObject(i);
            RemoteModels.Channel channel = parseChannel(ch);
            if (!channel.hidden) b.allChannels.add(channel);
            String catId = ch.optString("category_id", "");
            if (catMap.containsKey(catId)) {
                catMap.get(catId).channels.put(channel.id, channel);
            }
        }

        // Parse side menus
        Map<String, RemoteModels.SideMenu> menuMap = new HashMap<>();
        for (int i = 0; i < menus.length(); i++) {
            JSONObject m = menus.getJSONObject(i);
            RemoteModels.SideMenu menu = new RemoteModels.SideMenu();
            menu.id = m.getString("id");
            menu.name = m.getString("name");
            menu.pinCode = m.optString("pin_code", null);
            if (menu.pinCode != null && (menu.pinCode.isEmpty() || "null".equals(menu.pinCode))) menu.pinCode = null;
            menu.channels = new HashMap<>();
            menuMap.put(menu.id, menu);
        }

        // Parse sub-channels
        for (int i = 0; i < subs.length(); i++) {
            JSONObject sc = subs.getJSONObject(i);
            String menuId = sc.optString("side_menu_id", "");
            RemoteModels.SubChannel sub = parseSubChannel(sc);
            if (menuMap.containsKey(menuId)) {
                menuMap.get(menuId).channels.put(sub.id, sub);
            }
        }
        b.sideMenus = menuMap;

        // Parse settings
        for (int i = 0; i < settings.length(); i++) {
            JSONObject s = settings.getJSONObject(i);
            String key = s.optString("key", "");
            if (!key.isEmpty()) {
                b.settings.put(key, s.opt("value") != null ? s.opt("value").toString() : "");
            }
        }

        return b;
    }

    private static RemoteModels.Channel parseChannel(JSONObject ch) throws Exception {
        RemoteModels.Channel c = new RemoteModels.Channel();
        c.id = ch.getString("id");
        c.name = ch.getString("name");
        c.imageUrl = ch.optString("image_url", null);
        c.sortOrder = ch.optInt("sort_order", 0);
        c.hidden = ch.optBoolean("hidden", false);
        c.actionType = ch.optString("action_type", "direct_play");
        c.sideMenuId = ch.optString("side_menu_id", null);
        c.externalUrl = ch.optString("external_url", null);
        c.preferredPlayer = ch.optString("preferred_player", null);
        c.androidActionType = ch.optString("android_action_type", null);
        c.iosActionType = ch.optString("ios_action_type", null);

        // Panel → per-channel offline cache toggle + forced aspect ratio.
        c.offlineCacheEnabled = ch.optBoolean("offline_cache_enabled", false);
        c.cacheVersion = ch.optLong("cache_version", 1L);
        // Per-channel PIN (panel `pin_code` column on channels table).
        c.pinCode = ch.optString("pin_code", null);
        if (c.pinCode != null && (c.pinCode.isEmpty() || "null".equals(c.pinCode))) c.pinCode = null;
        if (!ch.isNull("android_stream")) {
            JSONObject _as = ch.optJSONObject("android_stream");
            if (_as != null) {
                String ar = _as.optString("forcedAspectRatio", null);
                if (ar != null && !ar.isEmpty() && !"null".equals(ar)) c.forcedAspectRatio = ar;
                c.lockAspectRatio = _as.optBoolean("lockAspectRatio", false);
            }
        }

        // web_stream → RemoteModels.StreamConfig
        if (!ch.isNull("web_stream")) {
            JSONObject ws = ch.optJSONObject("web_stream");
            if (ws != null) {
                c.stream = new RemoteModels.StreamConfig();
                c.stream.url = ws.optString("url", null);
                c.stream.userAgent = ws.optString("userAgent", null);
                c.stream.referrer = ws.optString("referrer", null);
                c.stream.cookies = ws.optString("cookies", null);
            }
        }

        // android_stream → RemoteModels.AndroidStreamConfig
        if (!ch.isNull("android_stream")) {
            JSONObject as = ch.optJSONObject("android_stream");
            if (as != null) {
                c.androidStream = parseAndroidStream(as);
            }
        }

        // ios_stream → RemoteModels.IosStreamConfig (iOS will use this; we
        // still parse it in Android so realtime updates round-trip cleanly.)
        if (!ch.isNull("ios_stream")) {
            JSONObject is = ch.optJSONObject("ios_stream");
            if (is != null) {
                c.iosStream = parseIosStream(is);
            }
        }

        return c;
    }

    private static RemoteModels.SubChannel parseSubChannel(JSONObject sc) throws Exception {
        RemoteModels.SubChannel s = new RemoteModels.SubChannel();
        s.id = sc.getString("id");
        s.name = sc.getString("name");
        s.imageUrl = sc.optString("image_url", null);
        s.sortOrder = sc.optInt("sort_order", 0);
        s.hidden = sc.optBoolean("hidden", false);
        s.preferredPlayer = sc.optString("preferred_player", null);
        s.androidActionType = sc.optString("android_action_type", null);
        s.iosActionType = sc.optString("ios_action_type", null);
        // pin_code on a sub-channel locks just that one channel.
        s.pinCode = sc.optString("pin_code", null);
        if (s.pinCode != null && (s.pinCode.isEmpty() || "null".equals(s.pinCode))) s.pinCode = null;

        // Panel → per-channel offline cache toggle + forced aspect ratio.
        s.offlineCacheEnabled = sc.optBoolean("offline_cache_enabled", false);
        s.cacheVersion = sc.optLong("cache_version", 1L);
        if (!sc.isNull("android_stream")) {
            JSONObject _as = sc.optJSONObject("android_stream");
            if (_as != null) {
                String ar = _as.optString("forcedAspectRatio", null);
                if (ar != null && !ar.isEmpty() && !"null".equals(ar)) s.forcedAspectRatio = ar;
                s.lockAspectRatio = _as.optBoolean("lockAspectRatio", false);
            }
        }

        if (!sc.isNull("web_stream")) {
            JSONObject ws = sc.optJSONObject("web_stream");
            if (ws != null) {
                s.stream = new RemoteModels.StreamConfig();
                s.stream.url = ws.optString("url", null);
                s.stream.userAgent = ws.optString("userAgent", null);
                s.stream.referrer = ws.optString("referrer", null);
                s.stream.cookies = ws.optString("cookies", null);
            }
        }

        if (!sc.isNull("android_stream")) {
            JSONObject as = sc.optJSONObject("android_stream");
            if (as != null) {
                s.androidStream = parseAndroidStream(as);
            }
        }

        if (!sc.isNull("ios_stream")) {
            JSONObject is = sc.optJSONObject("ios_stream");
            if (is != null) {
                s.iosStream = parseIosStream(is);
            }
        }

        return s;
    }

    private static RemoteModels.IosStreamConfig parseIosStream(JSONObject is) {
        RemoteModels.IosStreamConfig cfg = new RemoteModels.IosStreamConfig();
        cfg.url = is.optString("url", null);
        cfg.userAgent = is.optString("userAgent", null);
        cfg.referrer = is.optString("referrer", null);
        cfg.origin = is.optString("origin", null);
        cfg.cookies = is.optString("cookies", null);
        cfg.backupUrl = is.optString("backupUrl", null);
        cfg.subtitleUrl = is.optString("subtitleUrl", null);
        cfg.drmScheme = is.optString("drmScheme", null);
        cfg.drmKeyId = is.optString("drmKeyId", null);
        cfg.drmKey = is.optString("drmKey", null);
        cfg.drmLicenseUrl = is.optString("drmLicenseUrl", null);

        if (!is.isNull("headers")) {
            JSONObject h = is.optJSONObject("headers");
            if (h != null) {
                cfg.headers = new HashMap<>();
                Iterator<String> keys = h.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    cfg.headers.put(key, h.optString(key, ""));
                }
            }
        }

        if (!is.isNull("customHeaders")) {
            JSONArray arr = is.optJSONArray("customHeaders");
            if (arr != null) {
                cfg.customHeaders = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject hdr = arr.optJSONObject(i);
                    if (hdr == null) continue;
                    RemoteModels.CustomHeader ch = new RemoteModels.CustomHeader();
                    ch.key = hdr.optString("key", "");
                    ch.value = hdr.optString("value", "");
                    cfg.customHeaders.add(ch);
                }
            }
        }
        return cfg;
    }

    private static RemoteModels.AndroidStreamConfig parseAndroidStream(JSONObject as) throws Exception {
        RemoteModels.AndroidStreamConfig cfg = new RemoteModels.AndroidStreamConfig();
        cfg.url = as.optString("url", null);
        cfg.webViewOrientation = as.optString("webViewOrientation", null);
        cfg.intentUri = as.optString("intentUri", null);
        cfg.drmLicenseUrl = as.optString("drmLicenseUrl", null);
        cfg.drmScheme = as.optString("drmScheme", null);
        cfg.drmKeyId = as.optString("drmKeyId", null);
        cfg.drmKey = as.optString("drmKey", null);
        cfg.drmClearKeyCombined = as.optString("drmClearKeyCombined", null);
        cfg.drmClearKeyMode = as.optString("drmClearKeyMode", null);
        cfg.backupUrl = as.optString("backupUrl", null);
        cfg.subtitleUrl = as.optString("subtitleUrl", null);
        cfg.forcedAspectRatio = as.optString("forcedAspectRatio", null);
        if (cfg.forcedAspectRatio != null && (cfg.forcedAspectRatio.isEmpty() || "null".equals(cfg.forcedAspectRatio))) cfg.forcedAspectRatio = null;
        cfg.lockAspectRatio = as.optBoolean("lockAspectRatio", false);

        if (!as.isNull("logoOverlay")) {
            JSONObject lo = as.optJSONObject("logoOverlay");
            if (lo != null) {
                cfg.logoOverlay = new RemoteModels.LogoOverlay();
                cfg.logoOverlay.url = lo.optString("url", null);
                cfg.logoOverlay.position = lo.optString("position", null);
                cfg.logoOverlay.offsetX = lo.optInt("offsetX", 0);
                cfg.logoOverlay.offsetY = lo.optInt("offsetY", 0);
                cfg.logoOverlay.width = lo.optInt("width", 80);
                cfg.logoOverlay.height = lo.optInt("height", 40);
                cfg.logoOverlay.opacity = (float) lo.optDouble("opacity", 1.0);
            }
        }

        // headers
        if (!as.isNull("headers")) {
            JSONObject h = as.optJSONObject("headers");
            if (h != null) {
                cfg.headers = new HashMap<>();
                Iterator<String> keys = h.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    cfg.headers.put(key, h.optString(key, ""));
                }
            }
        }

        // customHeaders
        if (!as.isNull("customHeaders")) {
            JSONArray arr = as.optJSONArray("customHeaders");
            if (arr != null) {
                cfg.customHeaders = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject hdr = arr.getJSONObject(i);
                    RemoteModels.CustomHeader ch = new RemoteModels.CustomHeader();
                    ch.key = hdr.optString("key", "");
                    ch.value = hdr.optString("value", "");
                    cfg.customHeaders.add(ch);
                }
            }
        }

        // drmLicenseHeaders
        if (!as.isNull("drmLicenseHeaders")) {
            JSONArray arr = as.optJSONArray("drmLicenseHeaders");
            if (arr != null) {
                cfg.drmLicenseHeaders = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject hdr = arr.getJSONObject(i);
                    RemoteModels.CustomHeader ch = new RemoteModels.CustomHeader();
                    ch.key = hdr.optString("key", "");
                    ch.value = hdr.optString("value", "");
                    cfg.drmLicenseHeaders.add(ch);
                }
            }
        }

        // servers
        if (!as.isNull("servers")) {
            JSONArray arr = as.optJSONArray("servers");
            if (arr != null) {
                cfg.servers = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject srv = arr.getJSONObject(i);
                    RemoteModels.Server server = new RemoteModels.Server();
                    server.name = srv.optString("name", "");
                    server.url = srv.optString("url", "");
                    cfg.servers.add(server);
                }
            }
        }

        // fallbackServers — full-power alternates with their own headers + DRM
        if (!as.isNull("fallbackServers")) {
            JSONArray arr = as.optJSONArray("fallbackServers");
            if (arr != null) {
                cfg.fallbackServers = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject f = arr.optJSONObject(i);
                    if (f == null) continue;
                    RemoteModels.FallbackServer fs = new RemoteModels.FallbackServer();
                    fs.id = f.optString("id", null);
                    fs.name = f.optString("name", null);
                    fs.url = f.optString("url", null);
                    fs.userAgent = f.optString("userAgent", null);
                    fs.referer = f.optString("referer", null);
                    fs.origin = f.optString("origin", null);
                    fs.cookie = f.optString("cookie", null);
                    fs.drmScheme = f.optString("drmScheme", null);
                    fs.drmLicenseUrl = f.optString("drmLicenseUrl", null);
                    fs.drmKeyId = f.optString("drmKeyId", null);
                    fs.drmKey = f.optString("drmKey", null);
                    fs.drmClearKeyCombined = f.optString("drmClearKeyCombined", null);
                    fs.drmClearKeyMode = f.optString("drmClearKeyMode", null);
                    JSONArray ch = f.optJSONArray("customHeaders");
                    if (ch != null) {
                        fs.customHeaders = new ArrayList<>();
                        for (int j = 0; j < ch.length(); j++) {
                            JSONObject h = ch.optJSONObject(j);
                            if (h == null) continue;
                            RemoteModels.CustomHeader cm = new RemoteModels.CustomHeader();
                            cm.key = h.optString("key", "");
                            cm.value = h.optString("value", "");
                            fs.customHeaders.add(cm);
                        }
                    }
                    JSONArray lh = f.optJSONArray("drmLicenseHeaders");
                    if (lh != null) {
                        fs.drmLicenseHeaders = new ArrayList<>();
                        for (int j = 0; j < lh.length(); j++) {
                            JSONObject h = lh.optJSONObject(j);
                            if (h == null) continue;
                            RemoteModels.CustomHeader cm = new RemoteModels.CustomHeader();
                            cm.key = h.optString("key", "");
                            cm.value = h.optString("value", "");
                            fs.drmLicenseHeaders.add(cm);
                        }
                    }
                    cfg.fallbackServers.add(fs);
                }
            }
        }
        if (!as.isNull("audioSources")) {
            JSONArray arr = as.optJSONArray("audioSources");
            if (arr != null) {
                cfg.audioSources = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject src = arr.getJSONObject(i);
                    RemoteModels.AudioSource audio = new RemoteModels.AudioSource();
                    audio.name = src.optString("name", "");
                    audio.url = src.optString("url", "");
                    cfg.audioSources.add(audio);
                }
            }
        }

        return cfg;
    }
}