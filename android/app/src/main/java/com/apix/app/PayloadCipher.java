package com.apix.app;

import android.util.Base64;

import org.json.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM payload cipher.
 * المفتاح يأتي من NDK Vault (g4) بدلاً من BuildConfig
 */
public final class PayloadCipher {

    private static final int IV_LEN   = 12;
    private static final int TAG_BITS = 128;

    private PayloadCipher() {}

    private static byte[] hexToBytes(String hex) {
        String clean = hex.trim().replaceAll("[^0-9a-fA-F]", "");
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(
                clean.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static byte[] keyBytes() {
        // المفتاح من NDK Vault — لا يظهر في BuildConfig أبداً
        String raw = com.apix.app.security.g4.INSTANCE.ka();
        if (raw == null || raw.isEmpty()) {
            throw new IllegalStateException("key missing");
        }
        if (raw.length() == 64 && raw.matches("[0-9a-fA-F]+")) {
            return hexToBytes(raw);
        }
        byte[] dec = Base64.decode(raw, Base64.DEFAULT);
        if (dec.length != 32) {
            throw new IllegalStateException(
                "key must be 32 bytes (got " + dec.length + ")");
        }
        return dec;
    }

    public static String decryptEnvelope(String envelopeJson) throws Exception {
        JSONObject obj  = new JSONObject(envelopeJson);
        String ivB64    = obj.getString("iv");
        String dataB64  = obj.getString("data");
        byte[] iv       = Base64.decode(ivB64,   Base64.DEFAULT);
        byte[] ct       = Base64.decode(dataB64, Base64.DEFAULT);
        if (iv.length != IV_LEN)
            throw new IllegalStateException("bad iv length");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,
               new SecretKeySpec(keyBytes(), "AES"),
               new GCMParameterSpec(TAG_BITS, iv));
        byte[] plain = c.doFinal(ct);
        return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
    }
}