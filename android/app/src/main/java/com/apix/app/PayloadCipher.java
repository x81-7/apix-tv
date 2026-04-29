package com.apix.app;

import android.util.Base64;

import org.json.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM payload cipher.
 *
 * Decrypts envelopes of the form { "iv": "<b64>", "data": "<b64>" } returned
 * by the Supabase `cached-data` Edge Function. The key is injected at build
 * time via BuildConfig.ENCRYPTION_SECRET_KEY (hex, 64 chars / 32 bytes) and
 * MUST match the server-side env var.
 */
public final class PayloadCipher {

    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private PayloadCipher() {}

    private static byte[] hexToBytes(String hex) {
        String clean = hex.trim().replaceAll("[^0-9a-fA-F]", "");
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static byte[] keyBytes() {
        String raw = BuildConfig.ENCRYPTION_SECRET_KEY;
        if (raw == null || raw.isEmpty()) {
            throw new IllegalStateException("ENCRYPTION_SECRET_KEY missing");
        }
        // hex 64 chars
        if (raw.length() == 64 && raw.matches("[0-9a-fA-F]+")) {
            return hexToBytes(raw);
        }
        // base64 fallback
        byte[] dec = Base64.decode(raw, Base64.DEFAULT);
        if (dec.length != 32) {
            throw new IllegalStateException("ENCRYPTION_SECRET_KEY must be 32 bytes (got " + dec.length + ")");
        }
        return dec;
    }

    /** Decrypts {iv,data} envelope JSON string into plaintext UTF-8 string. */
    public static String decryptEnvelope(String envelopeJson) throws Exception {
        JSONObject obj = new JSONObject(envelopeJson);
        String ivB64 = obj.getString("iv");
        String dataB64 = obj.getString("data");
        byte[] iv = Base64.decode(ivB64, Base64.DEFAULT);
        byte[] ct = Base64.decode(dataB64, Base64.DEFAULT);
        if (iv.length != IV_LEN) throw new IllegalStateException("bad iv length");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes(), "AES"),
                new GCMParameterSpec(TAG_BITS, iv));
        byte[] plain = c.doFinal(ct);
        return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
    }
}
