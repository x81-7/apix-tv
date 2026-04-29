package com.apix.pc.data

import android.util.Base64Stub
import org.json.JSONObject
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM payload cipher — desktop side.
 *
 * Decrypts the { "iv":"<b64>", "data":"<b64>" } envelope returned by the
 * Supabase `cached-data` Edge Function. The 32-byte key MUST match the
 * server's ENCRYPTION_SECRET_KEY env var.
 *
 * Key sourcing order:
 *   1. JVM system property `apix.encryption.key`
 *   2. Environment variable `ENCRYPTION_SECRET_KEY`
 *   3. Compile-time fallback (overridden at CI build by injecting the prop)
 *
 * The fallback constant is intentionally a placeholder — production builds
 * MUST pass `-Dapix.encryption.key=<hex>` via Gradle JVM args or set the
 * env var so installations bundled by GitHub Actions decrypt correctly.
 */
object PayloadCipher {

    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    // 32-byte all-zero placeholder. Replaced at build time by CI (env var).
    private const val FALLBACK_KEY_HEX =
        "0000000000000000000000000000000000000000000000000000000000000000"

    private val keyBytes: ByteArray by lazy {
        val raw = (System.getProperty("apix.encryption.key")
            ?: System.getenv("ENCRYPTION_SECRET_KEY")
            ?: FALLBACK_KEY_HEX).trim()
        decodeKey(raw)
    }

    private fun decodeKey(raw: String): ByteArray {
        if (raw.isEmpty()) error("ENCRYPTION_SECRET_KEY not configured")
        if (raw.length == 64 && raw.matches(Regex("[0-9a-fA-F]+"))) {
            val out = ByteArray(32)
            for (i in 0 until 32) {
                out[i] = raw.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return out
        }
        val dec = Base64.getDecoder().decode(raw)
        require(dec.size == 32) { "ENCRYPTION_SECRET_KEY must decode to 32 bytes" }
        return dec
    }

    /** Decrypt an envelope JSON string `{ "iv":"...", "data":"..." }` -> plaintext UTF-8. */
    fun decryptEnvelope(envelope: String): String {
        val obj = JSONObject(envelope)
        val iv = Base64.getDecoder().decode(obj.getString("iv"))
        val ct = Base64.getDecoder().decode(obj.getString("data"))
        require(iv.size == IV_LEN) { "bad iv length" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            GCMParameterSpec(TAG_BITS, iv),
        )
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }
}
