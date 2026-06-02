package com.apix.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * CloudDataManager (Hybrid Cloud)
 * 1) Fetches encrypted JSON from GitHub raw URL.
 * 2) Fetches AES-256 data key from Supabase Edge Function (get-decryption-key).
 *    The function unwraps it server-side using the master key.
 * 3) Decrypts payload with AES-256-GCM and returns plaintext JSON.
 *
 * Caches the last successful key locally so the app keeps working offline
 * (local-first strategy). On key rotation the cloud call refreshes it.
 */
public class CloudDataManager {

    private static final String TAG = "CloudDataManager";
    private static final String PREFS = "cloud_data_prefs";
    private static final String KEY_CACHED_DATA = "cached_decrypted_json";
    private static final String KEY_CACHED_DATAKEY_B64 = "cached_data_key_b64";
    private static final String KEY_CACHED_KEY_VERSION = "cached_key_version";

    // ==== CONFIGURE THESE TWO FROM YOUR SUPABASE PROJECT ====
    private static final String SUPABASE_URL = Net.base();
    private static final String SUPABASE_ANON_KEY = Net.anon();

    public interface Callback {
        void onSuccess(String decryptedJson);
        void onError(String error);
    }

    /** Local-first: try cached key, fall back to cloud key. */
    public static void load(final Context ctx, final String encryptedJsonUrl, final Callback cb) {
        new Thread(() -> {
            try {
                String encryptedFile = httpGet(encryptedJsonUrl, null, null);
                JSONObject file = new JSONObject(encryptedFile);
                JSONObject payload = file.getJSONObject("payload");
                String iv = payload.getString("iv");
                String data = payload.getString("data");

                SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String cachedKey = sp.getString(KEY_CACHED_DATAKEY_B64, null);

                // Try local cached key first
                if (cachedKey != null) {
                    try {
                        String plain = aesGcmDecrypt(cachedKey, iv, data);
                        cacheData(sp, plain);
                        cb.onSuccess(plain);
                        // Refresh key in background (non-blocking)
                        refreshKeyAsync(sp);
                        return;
                    } catch (Exception localFail) {
                        Log.w(TAG, "Local key failed, fetching from cloud: " + localFail.getMessage());
                    }
                }

                // Fetch fresh key from Supabase
                String keyJson = callKeyEndpoint();
                JSONObject kj = new JSONObject(keyJson);
                if (!kj.optBoolean("success", false)) {
                    throw new Exception("Key endpoint error: " + kj.optString("error"));
                }
                String dataKeyB64 = kj.getString("dataKey");
                int keyVersion = kj.optInt("keyVersion", 0);

                String plain = aesGcmDecrypt(dataKeyB64, iv, data);

                sp.edit()
                  .putString(KEY_CACHED_DATAKEY_B64, dataKeyB64)
                  .putInt(KEY_CACHED_KEY_VERSION, keyVersion)
                  .apply();
                cacheData(sp, plain);
                cb.onSuccess(plain);
            } catch (Exception e) {
                Log.e(TAG, "load failed", e);
                // Last resort: serve previously cached plaintext
                SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String fallback = sp.getString(KEY_CACHED_DATA, null);
                if (fallback != null) {
                    cb.onSuccess(fallback);
                } else {
                    cb.onError(e.getMessage() == null ? "Unknown error" : e.getMessage());
                }
            }
        }).start();
    }

    private static void refreshKeyAsync(final SharedPreferences sp) {
        new Thread(() -> {
            try {
                String keyJson = callKeyEndpoint();
                JSONObject kj = new JSONObject(keyJson);
                if (kj.optBoolean("success", false)) {
                    sp.edit()
                      .putString(KEY_CACHED_DATAKEY_B64, kj.getString("dataKey"))
                      .putInt(KEY_CACHED_KEY_VERSION, kj.optInt("keyVersion", 0))
                      .apply();
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private static void cacheData(SharedPreferences sp, String plain) {
        sp.edit().putString(KEY_CACHED_DATA, plain).apply();
    }

    private static String callKeyEndpoint() throws Exception {
        String url = SUPABASE_URL + "/functions/v1/get-decryption-key";
        return httpGet(url, "Authorization", "Bearer " + SUPABASE_ANON_KEY);
    }

    private static String httpGet(String urlStr, String headerName, String headerValue) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        if (headerName != null) conn.setRequestProperty(headerName, headerValue);
        conn.setRequestProperty("apikey", SUPABASE_ANON_KEY);
        Net.verifyPins(conn);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new Exception("HTTP " + code + " for " + urlStr);
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static String aesGcmDecrypt(String keyB64, String ivB64, String dataB64) throws Exception {
        byte[] keyBytes = Base64.decode(keyB64, Base64.NO_WRAP);
        byte[] iv = Base64.decode(ivB64, Base64.NO_WRAP);
        byte[] ct = Base64.decode(dataB64, Base64.NO_WRAP);

        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));
        byte[] plain = cipher.doFinal(ct);
        return new String(plain, StandardCharsets.UTF_8);
    }
}
