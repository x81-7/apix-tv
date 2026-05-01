package com.apix.app.vip;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.apix.app.PayloadCipher;
import com.apix.app.security.DeviceIntegrity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single source of truth for VIP state.
 *
 * Logic:
 *   1. Always check {@link VipCache} first — if active locally, return active
 *      WITHOUT touching the network (cost optimization).
 *   2. Server is contacted only when cache is empty, expired, or stale.
 *   3. Server response (encrypted via AES-GCM envelope) is stored locally.
 */
public final class VipChecker {

    private static final String TAG = "VipChecker";
    private static final ExecutorService io = Executors.newSingleThreadExecutor();

    public interface Callback { void onResult(boolean active, long expiresAt); }

    private final Context ctx;
    private final VipCache cache;
    private final String supabaseUrl;
    private final String anonKey;

    public VipChecker(Context ctx, String supabaseUrl, String anonKey) {
        this.ctx = ctx.getApplicationContext();
        this.cache = new VipCache(this.ctx);
        this.supabaseUrl = supabaseUrl;
        this.anonKey = anonKey;
    }

    /** Synchronous local-only check — instant, no network. */
    public boolean isActiveLocally() {
        return cache.isActiveNow();
    }

    /** Async full check (local first, server only if needed). */
    public void check(final Callback cb) {
        // 1. Trust local cache when fresh + active.
        if (cache.isActiveNow() && !cache.needsServerCheck()) {
            cb.onResult(true, cache.expiresAt());
            return;
        }
        // 2. Need server check.
        io.execute(() -> {
            ServerResult r = queryServer();
            if (r != null) {
                cache.store(r.active, r.expiresAt);
                post(() -> cb.onResult(r.active, r.expiresAt));
            } else {
                // Network failed → fall back to local cache (even if stale).
                boolean local = cache.isActiveNow();
                post(() -> cb.onResult(local, cache.expiresAt()));
            }
        });
    }

    private ServerResult queryServer() {
        try {
            String deviceId = DeviceIntegrity.deviceId(ctx);
            URL url = new URL(supabaseUrl + "/functions/v1/check-vip");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(7000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + anonKey);
            conn.setRequestProperty("apikey", anonKey);

            String body = "{\"device_id\":\"" + escape(deviceId) + "\"}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                Log.w(TAG, "server " + code);
                return null;
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line; while ((line = r.readLine()) != null) sb.append(line);
            }
            // Encrypted envelope: { iv, data }
            JSONObject env = new JSONObject(sb.toString());
            String plain;
            if (env.has("iv") && env.has("data")) {
                plain = PayloadCipher.decryptEnvelope(ctx, env);
            } else {
                plain = sb.toString();
            }
            JSONObject obj = new JSONObject(plain);
            boolean active = obj.optBoolean("active", false);
            String expStr = obj.optString("expiresAt", null);
            long exp = 0L;
            if (expStr != null && !"null".equals(expStr)) {
                try { exp = java.time.Instant.parse(expStr).toEpochMilli(); }
                catch (Exception ignore) {}
            }
            return new ServerResult(active, exp);
        } catch (Exception e) {
            Log.w(TAG, "queryServer failed: " + e.getMessage());
            return null;
        }
    }

    public void clearLocal() { cache.clear(); }

    private static void post(Runnable r) { new Handler(Looper.getMainLooper()).post(r); }
    private static String escape(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\""); }

    private static class ServerResult {
        final boolean active; final long expiresAt;
        ServerResult(boolean a, long e) { this.active = a; this.expiresAt = e; }
    }
}
