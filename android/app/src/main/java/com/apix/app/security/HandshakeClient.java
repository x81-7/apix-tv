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
        public String status = "ACTIVE";        // ACTIVE / TEMP_BAN / PERMA_BAN / TAMPERED_MOD / ENVIRONMENT_DANGER / VPN_BLOCK / ERROR
        public String banUntil;
        public String reason;
        public String telegramUrl;
        public String message;
        public boolean wipe;                    // server orders local channel-cache wipe
        public String mode = "OK";              // OK / SILENT / MESSAGE — how to enforce the ban
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
            try { body.put("vpn_active", DeviceIntegrity.isVpnActive(ctx)); } catch (Throwable ignored) {}

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
            v.wipe = jo.optBoolean("wipe", false);
            v.mode = jo.optString("mode", "OK");
            // Persist the VPN allow-list encrypted for lightweight offline checks.
            try {
                if (jo.has("vpn_allowed_ips")) {
                    org.json.JSONArray arr = jo.optJSONArray("vpn_allowed_ips");
                    ctx.getSharedPreferences("vpn_cache", Context.MODE_PRIVATE).edit()
                        .putBoolean("vpn_block_enabled", jo.optBoolean("vpn_block_enabled", false))
                        .putString("vpn_allowed_ips", arr != null ? arr.toString() : "[]")
                        .apply();
                }
            } catch (Throwable ignored) {}
            // Propagate the admin debug-toast toggle to TostInfo (native + java).
            try {
                if (jo.has("debug_kill_toasts")) {
                    com.apix.app.security.TostInfo.setDebugEnabled(ctx, jo.optBoolean("debug_kill_toasts", false));
                }
            } catch (Throwable ignored) {}
            // Enforcement is intentionally deferred to the caller so it can
            // persist the verdict and wipe every cache before native termination.
            // Native VPN gate — enforced from nvp.cpp with `_exit(0)`.
            try {
                boolean vpnUp = DeviceIntegrity.isVpnActive(ctx);
                if (vpnUp) {
                    android.content.SharedPreferences sp = ctx.getSharedPreferences("vpn_cache", Context.MODE_PRIVATE);
                    if (sp.getBoolean("vpn_block_enabled", false)) {
                        String allowed = sp.getString("vpn_allowed_ips", "[]");
                        // Convert JSON array to CSV for the native call.
                        String csv = allowed
                                .replace("[", "").replace("]", "")
                                .replace("\"", "").replace(" ", "");
                        String ip = jo.optString("client_ip", "");
                        com.apix.app.Net.nvpCheckVpn(true, ip, csv);
                    }
                }
            } catch (Throwable ignored) {}
            Log.i("HS", "verdict=" + v.status + " device=" + deviceId);
        } catch (Throwable t) {
            Log.w("HS", "handshake error", t);
            v.status = "ERROR"; // fail-open on network errors
        }
        return v;
    }

    private HandshakeClient() {}
}
