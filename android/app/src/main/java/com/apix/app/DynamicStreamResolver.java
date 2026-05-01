package com.apix.app;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Smart Stream Resolver — Aggressive Edition.
 *
 * - Retries up to 4 times with exponential backoff before giving up.
 * - Performs deep recursive JSON parsing to find the playable URL anywhere
 *   inside the response (any nested object/array).
 * - Extracts headers (Referer, User-Agent, Cookie, Origin) from any depth.
 * - Only after all retries fail does the caller fall back to backupUrl.
 */
public final class DynamicStreamResolver {

    private static final String TAG = "DynRes";
    private static final int TIMEOUT_MS = 7000;
    private static final int MAX_ATTEMPTS = 4;
    private static final long[] BACKOFF_MS = { 0L, 400L, 900L, 1700L };
    private static final String DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile";

    private static final String[] URL_KEYS = {
            "url", "stream", "stream_url", "streamUrl", "src", "source",
            "playUrl", "play_url", "link", "manifest", "manifestUrl",
            "hls", "dash", "file", "video", "videoUrl", "media", "mediaUrl"
    };
    private static final String[] UA_KEYS = { "userAgent", "user_agent", "User-Agent", "ua" };
    private static final String[] REF_KEYS = { "Referer", "referer", "referrer", "Referrer" };
    private static final String[] HDR_KEYS = { "otherHeaders", "headers", "extraHeaders", "requestHeaders" };

    public static class Resolved {
        public String url;
        public String userAgent;
        public String referer;
        public Map<String, String> headers = new HashMap<>();
        public boolean forceHls;
        /** True if probing/JSON parsing actually succeeded. False = unchanged passthrough. */
        public boolean resolved;
    }

    private DynamicStreamResolver() {}

    public static Resolved resolve(String rawUrl) {
        Resolved out = new Resolved();
        if (rawUrl == null || rawUrl.isEmpty()) {
            out.url = "";
            return out;
        }
        String workingUrl = rawUrl.contains("|") ? rawUrl.split("\\|", 2)[0] : rawUrl;
        out.url = workingUrl;
        if (looksLikeHls(workingUrl)) out.forceHls = true;

        if (!shouldProbe(workingUrl)) return out;

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                try { Thread.sleep(BACKOFF_MS[attempt]); } catch (InterruptedException ignore) {}
            }
            try {
                if (probeOnce(workingUrl, out)) {
                    out.resolved = true;
                    return out;
                }
            } catch (Exception e) {
                Log.w(TAG, "attempt " + (attempt + 1) + " failed: " + e.getMessage());
            }
        }
        Log.w(TAG, "all " + MAX_ATTEMPTS + " resolver attempts failed for: " + workingUrl);
        return out;
    }

    private static boolean probeOnce(String url, Resolved out) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", DEFAULT_UA);
            conn.setRequestProperty("Accept", "application/json,text/plain,*/*");

            int code = conn.getResponseCode();
            if (code < 200 || code >= 400) return false;

            String contentType = conn.getContentType();
            boolean jsonHinted = contentType != null && contentType.toLowerCase().contains("json");

            String body = readBoundedBody(conn.getInputStream(), 128 * 1024);
            if (body == null || body.isEmpty()) return false;

            String trimmed = body.trim();
            if (trimmed.startsWith("#EXTM3U")) {
                out.forceHls = true;
                return true;
            }
            if (!jsonHinted && !(trimmed.startsWith("{") || trimmed.startsWith("["))) {
                return false;
            }

            JsonElement el = JsonParser.parseString(trimmed);
            // Deep-walk to find URL + headers anywhere in the tree.
            boolean foundUrl = walkAndExtract(el, out, 0);
            return foundUrl;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Recursive deep walk — returns true once a URL is found. */
    private static boolean walkAndExtract(JsonElement el, Resolved out, int depth) {
        if (el == null || depth > 8) return false;
        boolean found = false;

        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            // Try direct URL keys first.
            String candidate = firstNonEmpty(obj, URL_KEYS);
            if (candidate != null && looksLikeStreamUrl(candidate)) {
                out.url = candidate;
                if (looksLikeHls(candidate)) out.forceHls = true;
                found = true;
            }
            // Headers / UA / Referer at this level.
            String ua = firstNonEmpty(obj, UA_KEYS);
            if (ua != null && out.userAgent == null) out.userAgent = ua;
            String ref = firstNonEmpty(obj, REF_KEYS);
            if (ref != null && out.referer == null) out.referer = ref;
            JsonElement hdrsEl = pickElement(obj, HDR_KEYS);
            if (hdrsEl != null) extractHeaders(hdrsEl, out.headers);

            // Recurse into all children regardless.
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                if (walkAndExtract(e.getValue(), out, depth + 1)) found = true;
            }
        } else if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            for (JsonElement child : arr) {
                if (walkAndExtract(child, out, depth + 1)) found = true;
            }
        }
        return found;
    }

    private static void extractHeaders(JsonElement hdrsEl, Map<String, String> headers) {
        if (hdrsEl.isJsonPrimitive()) {
            parseEncodedHeaders(hdrsEl.getAsString(), headers);
        } else if (hdrsEl.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : hdrsEl.getAsJsonObject().entrySet()) {
                if (e.getValue().isJsonPrimitive()) {
                    headers.put(e.getKey(), e.getValue().getAsString());
                }
            }
        } else if (hdrsEl.isJsonArray()) {
            for (JsonElement child : hdrsEl.getAsJsonArray()) {
                if (child.isJsonObject()) extractHeaders(child, headers);
            }
        }
    }

    private static boolean looksLikeStreamUrl(String s) {
        if (s == null || s.length() < 8) return false;
        String l = s.toLowerCase();
        return l.startsWith("http://") || l.startsWith("https://");
    }

    private static boolean shouldProbe(String url) {
        String lower = url.toLowerCase();
        if (lower.contains(".m3u8") && !lower.contains("#hls")) return false;
        if (lower.contains(".mpd")) return false;
        if (lower.endsWith(".ts") || lower.endsWith(".mp4")) return false;
        return true;
    }

    private static boolean looksLikeHls(String url) {
        String l = url.toLowerCase();
        return l.contains(".m3u8") || l.contains("#hls") || l.contains("/hls/");
    }

    private static String readBoundedBody(InputStream in, int max) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[2048];
        int total = 0, n;
        while ((n = r.read(buf)) > 0) {
            sb.append(buf, 0, n);
            total += n;
            if (total >= max) break;
        }
        return sb.toString();
    }

    private static String firstNonEmpty(JsonObject obj, String... keys) {
        for (String k : keys) {
            if (obj.has(k) && !obj.get(k).isJsonNull()) {
                JsonElement e = obj.get(k);
                if (e.isJsonPrimitive()) {
                    String s = e.getAsString();
                    if (s != null && !s.isEmpty()) return s;
                }
            }
        }
        return null;
    }

    private static JsonElement pickElement(JsonObject obj, String... keys) {
        for (String k : keys) {
            if (obj.has(k) && !obj.get(k).isJsonNull()) return obj.get(k);
        }
        return null;
    }

    static void parseEncodedHeaders(String raw, Map<String, String> out) {
        if (raw == null || raw.isEmpty()) return;
        String[] pairs = raw.contains("|") ? raw.split("\\|") : raw.split("&");
        for (String pair : pairs) {
            if (pair == null || pair.isEmpty()) continue;
            int sep = pair.indexOf(':');
            if (sep < 0) sep = pair.indexOf('=');
            if (sep <= 0 || sep >= pair.length() - 1) continue;
            String k = pair.substring(0, sep).trim();
            String v = pair.substring(sep + 1).trim();
            if (!k.isEmpty()) out.put(k, v);
        }
    }
}
