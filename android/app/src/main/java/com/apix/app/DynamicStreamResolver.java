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
 * Smart Stream Resolver.
 *
 * Some "stream URLs" are actually JSON endpoints that wrap the real m3u8 link
 * along with custom headers. Before handing the URL to ExoPlayer, we probe it
 * once: if the response is JSON, we extract the real `url`, `userAgent`,
 * `Referer`, and `otherHeaders` (pipe/colon-encoded). Otherwise we pass the
 * URL through untouched.
 *
 * This must be called from a background thread (it does network I/O).
 */
public final class DynamicStreamResolver {

    private static final String TAG = "DynRes";
    private static final int TIMEOUT_MS = 6000;
    private static final String DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile";

    public static class Resolved {
        public String url;
        public String userAgent;
        public String referer;
        public Map<String, String> headers = new HashMap<>();
        /** True if the source URL ended with .m3u8/.png#hls/.json or response indicated HLS. */
        public boolean forceHls;
    }

    private DynamicStreamResolver() {}

    /**
     * Resolves a stream URL. Always returns a non-null Resolved with at least .url set.
     */
    public static Resolved resolve(String rawUrl) {
        Resolved out = new Resolved();
        if (rawUrl == null || rawUrl.isEmpty()) {
            out.url = "";
            return out;
        }
        // Strip the "url|header=value&..." legacy pipe form first.
        String workingUrl = rawUrl.contains("|") ? rawUrl.split("\\|", 2)[0] : rawUrl;
        out.url = workingUrl;
        // Pre-flag obvious HLS hints.
        if (looksLikeHls(workingUrl)) out.forceHls = true;

        // Heuristic: only probe when the URL likely points at a JSON endpoint.
        // Probing every m3u8 wastes time and risks 403s.
        if (!shouldProbe(workingUrl)) {
            return out;
        }

        HttpURLConnection conn = null;
        try {
            URL u = new URL(workingUrl);
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", DEFAULT_UA);
            conn.setRequestProperty("Accept", "application/json,text/plain,*/*");

            int code = conn.getResponseCode();
            if (code < 200 || code >= 400) return out;

            String contentType = conn.getContentType();
            boolean jsonHinted = contentType != null && contentType.toLowerCase().contains("json");

            // Read up to ~64KB — JSON responses are tiny; m3u8 starts with "#EXTM3U".
            String body = readBoundedBody(conn.getInputStream(), 64 * 1024);
            if (body == null || body.isEmpty()) return out;

            String trimmed = body.trim();
            if (trimmed.startsWith("#EXTM3U")) {
                // It's an HLS playlist, not JSON.
                out.forceHls = true;
                return out;
            }

            if (!jsonHinted && !(trimmed.startsWith("{") || trimmed.startsWith("["))) {
                // Looks like binary/segmented stream — leave URL untouched.
                return out;
            }

            JsonElement el = JsonParser.parseString(trimmed);
            JsonObject obj = null;
            if (el.isJsonArray()) {
                JsonArray arr = el.getAsJsonArray();
                if (arr.size() > 0 && arr.get(0).isJsonObject()) obj = arr.get(0).getAsJsonObject();
            } else if (el.isJsonObject()) {
                obj = el.getAsJsonObject();
            }
            if (obj == null) return out;

            String resolvedUrl = firstNonEmpty(obj,
                    "url", "stream", "stream_url", "src", "playUrl", "link", "manifest");
            if (resolvedUrl != null && !resolvedUrl.isEmpty()) {
                out.url = resolvedUrl;
                if (looksLikeHls(resolvedUrl)) out.forceHls = true;
            }

            String ua = firstNonEmpty(obj, "userAgent", "user_agent", "User-Agent");
            if (ua != null) out.userAgent = ua;

            String ref = firstNonEmpty(obj, "Referer", "referer", "referrer");
            if (ref != null) out.referer = ref;

            // otherHeaders may be a pipe/colon-encoded string or a plain object.
            JsonElement hdrsEl = pickElement(obj, "otherHeaders", "headers", "extraHeaders");
            if (hdrsEl != null) {
                if (hdrsEl.isJsonPrimitive()) {
                    parseEncodedHeaders(hdrsEl.getAsString(), out.headers);
                } else if (hdrsEl.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> e : hdrsEl.getAsJsonObject().entrySet()) {
                        if (e.getValue().isJsonPrimitive()) {
                            out.headers.put(e.getKey(), e.getValue().getAsString());
                        }
                    }
                }
            }

            // Some endpoints embed a DRM key as `keyId:key` pair — we don't touch it
            // here; the engine layer handles DRM.
        } catch (Exception e) {
            Log.w(TAG, "probe failed: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return out;
    }

    // ---- helpers ----------------------------------------------------------

    private static boolean shouldProbe(String url) {
        String lower = url.toLowerCase();
        // Avoid probing obvious media (m3u8/mpd/ts/mp4). Probe png, json, channel-id endpoints.
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

    /**
     * Parses the legacy "key1:val1|key2:val2" / "key1=val1&key2=val2" headers blob.
     */
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
