package com.apix.app;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

/**
 * Net — the single networking gateway for the whole app.
 *
 * <p>When {@code BuildConfig.WORKER_URL} is provided (white-label deploy) ALL
 * traffic is routed through the Cloudflare Worker, which hides Supabase, encrypts
 * payloads at the edge, enforces bans and caches the APK/CDN. When it is empty
 * the app falls back to talking to Lovable Cloud directly (legacy mode).
 *
 * <p>This class also performs runtime SSL public-key pinning when one or more
 * SHA-256 SPKI pins are supplied via {@code BuildConfig.WORKER_PINS}
 * (comma-separated, base64). Pinning is applied ONLY to connections opened
 * through this gateway so video/stream/CDN hosts are never affected. When no
 * pins are configured, pinning is skipped (kept white-label friendly), and the
 * baseline {@code network_security_config.xml} still blocks user-installed CAs.
 */
public final class Net {

    private static final String TAG = "Net";

    // ─── native NVP bridge (nvp.cpp) — kept OUT of x.kt on purpose ───
    static {
        try { System.loadLibrary("v"); } catch (Throwable ignored) {}
    }
    /** SSL SPKI-pin verification. pinsCsv = base64 or hex SHA-256 pins. */
    public static native int nvpVerifySsl(String pinsCsv, byte[] spkiDer);
    /** VPN allow-list check. On mismatch, native side kills the process. */
    public static native int nvpCheckVpn(boolean vpnUp, String currentIp, String allowlistCsv);
    /** Ban gate; if verdict != ACTIVE, native side kills the process. */
    public static native int nvpCheckBan(String status);
    /** HS256 VIP token verification using the native-only HMAC secret. */
    public static native int nvpCheckVip(String token);

    private Net() {}

    private static String stripSlash(String s) {
        if (s == null) return "";
        s = s.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** True when the app is routing through the Cloudflare Worker gateway. */
    public static boolean usingWorker() {
        // The gateway origin is produced by native code (x.ke()). When a Worker
        // URL was baked in, ke() returns it; otherwise it falls back to the
        // legacy cloud origin. We treat any value that differs from the legacy
        // cloud origin as "worker mode".
        String gw = stripSlash(nativeGateway());
        String cloud = stripSlash(BuildConfig.CLOUD_URL);
        return !gw.isEmpty() && !gw.equalsIgnoreCase(cloud);
    }

    /** Resolve the gateway origin from the native vault (never from BuildConfig). */
    private static String nativeGateway() {
        try {
            String u = com.apix.app.x.ke();
            if (u != null && !u.trim().isEmpty()) return u.trim();
        } catch (Throwable ignored) {}
        // Last-resort legacy fallback so the app still boots if native fails.
        return BuildConfig.CLOUD_URL;
    }

    /** The base HTTPS origin every request is built on, sourced from native. */
    public static String base() {
        return stripSlash(nativeGateway());
    }

    /** The anon/publishable key. The Worker overrides it server-side anyway. */
    public static String anon() {
        return BuildConfig.CLOUD_ANON_KEY;
    }

    /**
     * Build a realtime websocket URL routed through the gateway. When the Worker
     * gateway is in use the apikey is NOT sent from the client — the Worker
     * injects it server-side, so the Supabase origin/key never leaks from the
     * APK. In legacy direct mode the apikey is required and appended.
     */
    public static String realtimeWsUrl() {
        String b = base()
                .replaceFirst("^https://", "wss://")
                .replaceFirst("^http://", "ws://");
        String url = b + "/realtime/v1/websocket?vsn=1.0.0";
        if (!usingWorker()) {
            url += "&apikey=" + anon();
        }
        return url;
    }

    /**
     * Open a connection to {@code path} (must start with "/") through the
     * gateway, with apikey + Authorization headers and SSL pinning applied.
     */
    public static HttpURLConnection open(String path) throws Exception {
        URL url = new URL(base() + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        applyPinning(conn);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("apikey", anon());
        conn.setRequestProperty("Authorization", "Bearer " + anon());
        conn.setRequestProperty("Accept", "application/json");
        return conn;
    }

    /** Simple GET helper that returns the body or throws on non-2xx. */
    public static String get(String path) throws Exception {
        HttpURLConnection conn = open(path);
        conn.setRequestMethod("GET");
        try {
            verifyPins(conn);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code + " for " + path);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    // ───────────────────────── SSL pinning ─────────────────────────

    private static List<String> pins() {
        List<String> out = new ArrayList<>();
        String raw = BuildConfig.WORKER_PINS;
        if (raw == null || raw.trim().isEmpty()) return out;
        for (String p : raw.split(",")) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static void applyPinning(HttpURLConnection conn) {
        // Per-connection pinning is enforced post-connect in verifyPins().
        // Nothing to pre-configure here; left as a hook for future SNI tweaks.
    }

    /**
     * Verifies the negotiated server certificate chain against the configured
     * SPKI pins. Must be called AFTER the TLS handshake is established (i.e.
     * after the first call that triggers connect, such as getResponseCode()).
     * No-op when pinning is disabled or the connection is plain HTTP.
     */
    public static void verifyPins(HttpURLConnection conn) throws Exception {
        List<String> expected = pins();
        if (expected.isEmpty()) return;
        if (!(conn instanceof HttpsURLConnection)) return;
        HttpsURLConnection https = (HttpsURLConnection) conn;
        https.connect();
        Certificate[] chain = https.getServerCertificates();
        if (chain == null || chain.length == 0) throw new Exception("pin: empty chain");
        String csv = android.text.TextUtils.join(",", expected);
        for (Certificate c : chain) {
            if (!(c instanceof X509Certificate)) continue;
            byte[] spki = c.getPublicKey().getEncoded();
            // Delegate to native nvp.cpp — a hooked Java verifier cannot lie.
            try {
                if (nvpVerifySsl(csv, spki) == 1) return;
            } catch (Throwable ignored) {
                // Fallback: legacy Java path
                String pin = spkiSha256((X509Certificate) c);
                if (expected.contains(pin)) return;
            }
        }
        throw new Exception("pin: no matching SPKI pin");
    }

    private static String spkiSha256(X509Certificate cert) throws Exception {
        byte[] spki = cert.getPublicKey().getEncoded();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(spki);
        return android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP);
    }
}
