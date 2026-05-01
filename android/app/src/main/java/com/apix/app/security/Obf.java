package com.apix.app.security;

/**
 * Tiny XOR + Base64-ish string deobfuscator.
 *
 * Used to keep sensitive plain-text out of the dex (e.g. error messages,
 * URLs, header keys). At build time we run encoded strings through the same
 * routine; at runtime we decode lazily.
 *
 * NOT a security primitive — just raises the bar against `strings` grep
 * and naive decompilation.
 */
public final class Obf {
    private Obf() {}

    private static final byte[] K = new byte[] {
            0x4A, 0x37, 0x7E, 0x12, 0x5C, 0x09, 0x33, 0x6B
    };

    /** Decode a Base64 string of XOR-encoded bytes. */
    public static String d(String b64) {
        if (b64 == null || b64.isEmpty()) return "";
        try {
            byte[] raw = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP);
            byte[] out = new byte[raw.length];
            for (int i = 0; i < raw.length; i++) {
                out[i] = (byte) (raw[i] ^ K[i % K.length]);
            }
            return new String(out, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /** Encode (helper used at build time / via tests). */
    public static String e(String plain) {
        if (plain == null) return "";
        byte[] raw = plain.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] out = new byte[raw.length];
        for (int i = 0; i < raw.length; i++) {
            out[i] = (byte) (raw[i] ^ K[i % K.length]);
        }
        return android.util.Base64.encodeToString(out, android.util.Base64.NO_WRAP);
    }
}
