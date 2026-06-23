package com.apix.app.security;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Talks to the Supabase device-handshake edge function.
 * Returns ban verdict to the splash screen.
 */
public final class HandshakeClient {

    public static final class Verdict {
        public String status = "ACTIVE";        // ACTIVE / TEMP_BAN / PERMA_BAN / TAMPERED_MOD / ENVIRONMENT_DANGER / ERROR
        public String banUntil;
        public String reason;
        public String telegramUrl;
        public String message;
    }

    public static Verdict handshake(Context ctx, String supabaseUrl, String anonKey, String appVersion) {
        Verdict v = new Verdict();
        try {
            String deviceId = DeviceIntegrity.deviceId(ctx);
            String sig = DeviceIntegrity.signatureHash(ctx);
            String dex = DeviceIntegrity.dexChecksum(ctx);
            String danger = DeviceIntegrity.environmentDanger(ctx);
            boolean fresh = DeviceIntegrity.consumeFreshInstall(ctx);

            JSONObject body = new JSONObject();
            body.put("device_id", deviceId);
            if (sig != null) body.put("signature_hash", sig);
            if (dex != null) body.put("dex_checksum", dex);
            if (appVersion != null) body.put("app_version", appVersion);
            body.put("is_fresh_install", fresh);
            if (danger != null) {
                body.put("environment_danger", true);
                body.put("danger_details", danger);
            }

            URL url = new URL(supabaseUrl + "/functions/v1/device-handshake");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Authorization", "Bearer " + anonKey);
            c.setRequestProperty("apikey", anonKey);
            c.setDoOutput(true);
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);

            try (OutputStream os = c.getOutputStream()) {
                os.write(body.toString().getBytes("UTF-8"));
            }

            com.apix.app.Net.verifyPins(c);
            int code = c.getResponseCode();
            java.io.InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[2048]; int n;
            while ((n = is.read(buf)) > 0) baos.write(buf, 0, n);
            String resp = baos.toString("UTF-8");
            JSONObject jo = new JSONObject(resp);
            // If response is the AES-GCM envelope { iv, data }, decrypt it.
            if (code >= 200 && code < 300 && jo.has("iv") && jo.has("data")) {
                try {
                    String plain = com.apix.app.PayloadCipher.decryptEnvelope(resp);
                    jo = new JSONObject(plain);
                } catch (Throwable dec) {
                    Log.w("HS", "envelope decrypt failed", dec);
                    // Don't trust the encrypted envelope as-is — treat as ERROR (fail-open).
                    v.status = "ERROR";
                    return v;
                }
            }
            // If the (decrypted) JSON doesn't carry a status, treat as ERROR
            // rather than silently passing through with the default "ACTIVE".
            if (!jo.has("status")) {
                Log.w("HS", "missing status in handshake response: " + resp);
                v.status = "ERROR";
                return v;
            }
            v.status = jo.optString("status", "ERROR");
            v.banUntil = jo.optString("ban_until", null);
            v.reason = jo.optString("ban_reason", null);
            v.telegramUrl = jo.optString("telegram_url", null);
            v.message = jo.optString("message", null);
            Log.i("HS", "verdict=" + v.status + " device=" + deviceId);
        } catch (Throwable t) {
            Log.w("HS", "handshake error", t);
            v.status = "ERROR"; // fail-open on network errors
        }
        return v;
    }

    private HandshakeClient() {}
}
