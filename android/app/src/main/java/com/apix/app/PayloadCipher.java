package com.apix.app;

import android.util.Base64;
import org.json.JSONObject;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class PayloadCipher {

    private static final int IV_LEN   = 12;
    private static final int TAG_BITS = 128;

    private PayloadCipher() {}

    private static byte[] hexToBytes(String hex) {
        String h = hex.trim().replaceAll("[^0-9a-fA-F]", "");
        byte[] out = new byte[h.length() / 2];
        for (int i = 0; i < out.length; i++)
            out[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    private static byte[] toKeyBytes(String raw) {
        if (raw == null || raw.isEmpty()) throw new IllegalStateException("key missing");
        if (raw.length() == 64 && raw.matches("[0-9a-fA-F]+")) return hexToBytes(raw);
        byte[] dec = Base64.decode(raw, Base64.DEFAULT);
        if (dec.length != 32) throw new IllegalStateException("key must be 32 bytes");
        return dec;
    }

    // فك تشفير بيانات السيرفر (ENCRYPTION_SECRET_KEY)
    public static String decryptEnvelope(String envelopeJson) throws Exception {
        String raw = com.apix.app.security.g4.ka();
        return decrypt(envelopeJson, toKeyBytes(raw));
    }

    // فك تشفير روابط apix.png (EXTERNAL_PANEL_DECRYPTION_KEY)
    public static String decryptExternal(String envelopeJson) throws Exception {
        String raw = com.apix.app.security.g4.kd();
        return decrypt(envelopeJson, toKeyBytes(raw));
    }

    private static String decrypt(String envelopeJson, byte[] keyBytes) throws Exception {
        JSONObject obj = new JSONObject(envelopeJson);
        byte[] iv = Base64.decode(obj.getString("iv"),   Base64.DEFAULT);
        byte[] ct = Base64.decode(obj.getString("data"), Base64.DEFAULT);
        if (iv.length != IV_LEN) throw new IllegalStateException("bad iv");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,
               new SecretKeySpec(keyBytes, "AES"),
               new GCMParameterSpec(TAG_BITS, iv));
        return new String(c.doFinal(ct), java.nio.charset.StandardCharsets.UTF_8);
    }
}