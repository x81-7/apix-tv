package com.apix.app;

import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * LocalStreamServer — an on-device IPTV proxy.
 *
 * <p>Goal: never hand the real upstream IPTV URLs (origin host, tokens, keys)
 * to the ExoPlayer media stack or to any external player, so they cannot be
 * sniffed from logs or the player's network layer. Everything the player sees
 * is {@code http://127.0.0.1:8080/...}.
 *
 * <p>How it works:
 * <ul>
 *   <li>{@code /play?url=<b64>} — fetches an HLS (.m3u8) or DASH (.mpd) manifest
 *       from the real origin and rewrites every inner reference
 *       (segments / keys / sub-playlists) to point back at this proxy via
 *       {@code /chunk?url=<b64>}. Relative paths are resolved against the
 *       original manifest URL before being re-encoded.</li>
 *   <li>{@code /chunk?url=<b64>} — streams an arbitrary upstream resource
 *       (segment / key / nested manifest) straight through. Nested manifests
 *       are themselves rewritten so multi-variant playlists keep working.</li>
 * </ul>
 *
 * <p>Smart bypass: progressive files (.mp4 / .mkv) are NOT proxied — see
 * {@link #shouldBypass(String)} — because buffering large monolithic files
 * through the local server would blow up memory. Those play directly.
 *
 * <p>The original URL is base64-url-encoded in the {@code url} query param so
 * query strings / tokens inside it survive transport intact.
 */
public final class LocalStreamServer extends NanoHTTPD {

    private static final String TAG  = "LocalProxy";
    public  static final int    PORT = 8080;
    public  static final String HOST = "127.0.0.1";

    private static LocalStreamServer INSTANCE;

    /** Optional per-stream upstream headers (UA / Referer / Cookie / Origin). */
    private static volatile Map<String, String> upstreamHeaders = new HashMap<>();

    private LocalStreamServer() {
        super(HOST, PORT);
    }

    /** Start (idempotent) and return the running instance. */
    public static synchronized LocalStreamServer ensureStarted() {
        try {
            if (INSTANCE == null) {
                INSTANCE = new LocalStreamServer();
            }
            if (!INSTANCE.isAlive()) {
                INSTANCE.start(SOCKET_READ_TIMEOUT, false);
                Log.d(TAG, "started on http://" + HOST + ":" + PORT);
            }
        } catch (Exception e) {
            Log.e(TAG, "start failed", e);
        }
        return INSTANCE;
    }

    public static synchronized void shutdownServer() {
        try {
            if (INSTANCE != null && INSTANCE.isAlive()) INSTANCE.stop();
        } catch (Exception ignored) {}
    }

    /** Set the upstream request headers used for every proxied fetch. */
    public static void setHeaders(Map<String, String> headers) {
        upstreamHeaders = headers != null ? headers : new HashMap<>();
    }

    /**
     * True when the URL must skip the proxy and play directly (progressive
     * containers). Keeps the local server limited to manifest-based streams.
     */
    public static boolean shouldBypass(String url) {
        if (url == null) return true;
        String u = url.toLowerCase();
        int q = u.indexOf('?');
        String path = q >= 0 ? u.substring(0, q) : u;
        return path.endsWith(".mp4") || path.endsWith(".mkv");
    }

    /**
     * Wrap a real stream URL into a local-proxy URL. Returns the original URL
     * unchanged when it should be bypassed (.mp4 / .mkv).
     */
    public static String wrap(String realUrl) {
        if (shouldBypass(realUrl)) return realUrl;
        ensureStarted();
        return "http://" + HOST + ":" + PORT + "/play?url=" + enc(realUrl);
    }

    // ───────────────────────────── serve ─────────────────────────────

    @Override
    public Response serve(IHTTPSession session) {
        try {
            String uri = session.getUri();
            Map<String, String> params = session.getParms();
            String b64 = params.get("url");
            String real = b64 != null ? dec(b64) : null;
            if (real == null || real.isEmpty()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST,
                        "text/plain", "missing url");
            }

            if ("/play".equals(uri)) {
                return serveManifest(real);
            } else if ("/chunk".equals(uri)) {
                // Nested manifests must be rewritten too; binaries pass through.
                if (isManifest(real)) return serveManifest(real);
                return servePassthrough(real);
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found");
        } catch (Exception e) {
            Log.e(TAG, "serve error", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,
                    "text/plain", "proxy error");
        }
    }

    // ─────────────────────── manifest rewriting ───────────────────────

    private Response serveManifest(String manifestUrl) throws Exception {
        HttpURLConnection conn = openUpstream(manifestUrl);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            return newFixedLengthResponse(Response.Status.lookup(code) != null
                    ? Response.Status.lookup(code) : Response.Status.INTERNAL_ERROR,
                    "text/plain", "upstream " + code);
        }
        String contentType = conn.getContentType();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        } finally {
            conn.disconnect();
        }

        boolean dash = manifestUrl.toLowerCase().contains(".mpd")
                || (contentType != null && contentType.contains("dash"));
        String rewritten = dash
                ? rewriteDash(sb.toString(), manifestUrl)
                : rewriteHls(sb.toString(), manifestUrl);

        String ct = dash ? "application/dash+xml" : "application/vnd.apple.mpegurl";
        Response resp = newFixedLengthResponse(Response.Status.OK, ct, rewritten);
        resp.addHeader("Access-Control-Allow-Origin", "*");
        return resp;
    }

    /** Rewrite an HLS playlist: URIs on their own lines + URI="..." attributes. */
    private String rewriteHls(String body, String base) {
        String[] lines = body.split("\n", -1);
        StringBuilder out = new StringBuilder(body.length() + 256);
        for (String raw : lines) {
            String line = raw;
            if (line.isEmpty()) { out.append('\n'); continue; }

            if (line.startsWith("#")) {
                // Rewrite URI="..." occurrences (EXT-X-KEY, MEDIA, MAP, etc.)
                line = rewriteAttrUris(line, base);
                out.append(line).append('\n');
                continue;
            }
            // A bare resource line (segment or sub-playlist).
            String abs = resolve(base, line.trim());
            out.append("/chunk?url=").append(enc(abs)).append('\n');
        }
        return out.toString();
    }

    private String rewriteAttrUris(String line, String base) {
        int idx = 0;
        StringBuilder sb = new StringBuilder();
        while (true) {
            int u = line.indexOf("URI=\"", idx);
            if (u < 0) { sb.append(line.substring(idx)); break; }
            int start = u + 5;
            int end = line.indexOf('"', start);
            if (end < 0) { sb.append(line.substring(idx)); break; }
            String inner = line.substring(start, end);
            String abs = resolve(base, inner);
            sb.append(line, idx, start)
              .append("/chunk?url=").append(enc(abs));
            sb.append('"');
            idx = end + 1;
        }
        return sb.toString();
    }

    /** Rewrite DASH MPD: BaseURL + media/initialization template attributes. */
    private String rewriteDash(String body, String base) {
        String out = body;
        // <BaseURL>...</BaseURL>
        out = replaceTagContent(out, "BaseURL", base);
        // media="..." initialization="..." in SegmentTemplate
        out = replaceXmlAttr(out, "media", base);
        out = replaceXmlAttr(out, "initialization", base);
        return out;
    }

    private String replaceTagContent(String xml, String tag, String base) {
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        StringBuilder sb = new StringBuilder();
        int idx = 0;
        while (true) {
            int o = xml.indexOf(open, idx);
            if (o < 0) { sb.append(xml.substring(idx)); break; }
            int c = xml.indexOf(close, o);
            if (c < 0) { sb.append(xml.substring(idx)); break; }
            String inner = xml.substring(o + open.length(), c).trim();
            String abs = resolve(base, inner);
            sb.append(xml, idx, o + open.length())
              .append("http://").append(HOST).append(':').append(PORT)
              .append("/chunk?url=").append(enc(abs));
            idx = c;
        }
        return sb.toString();
    }

    private String replaceXmlAttr(String xml, String attr, String base) {
        String key = attr + "=\"";
        StringBuilder sb = new StringBuilder();
        int idx = 0;
        while (true) {
            int a = xml.indexOf(key, idx);
            if (a < 0) { sb.append(xml.substring(idx)); break; }
            int start = a + key.length();
            int end = xml.indexOf('"', start);
            if (end < 0) { sb.append(xml.substring(idx)); break; }
            String inner = xml.substring(start, end);
            // DASH templates contain $Number$ / $RepresentationID$ — keep them,
            // but still resolve relative host. We only re-root absolute-ish ones.
            String abs = resolve(base, inner);
            sb.append(xml, idx, start)
              .append("http://").append(HOST).append(':').append(PORT)
              .append("/chunk?url=").append(enc(abs));
            sb.append('"');
            idx = end + 1;
        }
        return sb.toString();
    }

    // ───────────────────────── passthrough ─────────────────────────

    private Response servePassthrough(String url) throws Exception {
        HttpURLConnection conn = openUpstream(url);
        int code = conn.getResponseCode();
        InputStream in = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
        if (in == null) in = new ByteArrayInputStream(new byte[0]);
        String ct = conn.getContentType();
        if (ct == null) ct = "application/octet-stream";
        long len = conn.getContentLengthLong();
        Response resp = (len >= 0)
                ? newFixedLengthResponse(Response.Status.OK, ct, in, len)
                : newChunkedResponse(Response.Status.OK, ct, in);
        resp.addHeader("Access-Control-Allow-Origin", "*");
        return resp;
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private HttpURLConnection openUpstream(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setInstanceFollowRedirects(true);
        Map<String, String> h = upstreamHeaders;
        if (h != null) {
            for (Map.Entry<String, String> e : h.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
        }
        return conn;
    }

    private static boolean isManifest(String url) {
        String u = url.toLowerCase();
        int q = u.indexOf('?');
        String path = q >= 0 ? u.substring(0, q) : u;
        return path.endsWith(".m3u8") || path.endsWith(".mpd");
    }

    /** Resolve a possibly-relative reference against the manifest base URL. */
    private static String resolve(String base, String ref) {
        if (ref == null || ref.isEmpty()) return base;
        String low = ref.toLowerCase();
        if (low.startsWith("http://") || low.startsWith("https://")) return ref;
        try {
            return URI.create(base).resolve(ref).toString();
        } catch (Exception e) {
            return ref;
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static String dec(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
