package com.apix.pc.util

import org.json.JSONObject
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Desktop equivalent of `android/app/src/main/java/com/apix/app/util/DataProcessor.kt`.
 * Accepts:
 *   - apix://<payload>
 *   - https://apix-panal.vercel.app/watch.html?id=<payload>&app=com.apix.app
 *
 * The payload is the SAME AES-256-GCM Base64URL blob the Android app decrypts.
 * The decryption key (`X_DP_K`) is injected at build time via the same GitHub
 * Actions secret used by the Android pipeline.
 */
object DeepLinkArgs {

    private const val IV_LEN = 12
    private const val TAG_LEN = 128

    /** Same secret as Android `BuildConfig.X_DP_K`. CI may override via env. */
    private val DP_KEY_HEX: String =
        System.getenv("X_DP_K")
            ?: "7a3f8b9d4e2c1a5f6b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a"

    data class Parsed(val rawPayload: String?, val decoded: JSONObject?)

    fun parse(args: Array<String>): Parsed? {
        val first = args.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val payload = extractPayload(first) ?: return Parsed(null, null)
        val decoded = decode(payload)
        return Parsed(payload, decoded)
    }

    fun extractPayload(arg: String): String? {
        if (!arg.contains("://")) return arg
        val uri = runCatching { URI(arg) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null

        if (scheme == "apix") {
            uri.host?.takeIf { it.isNotBlank() }?.let { return it }
            uri.path?.trimStart('/')?.takeIf { it.isNotBlank() }?.let { return it }
        }

        // https://apix-panal.vercel.app/watch.html?id=<payload>
        uri.query
            ?.split('&')
            ?.firstOrNull { it.startsWith("id=") || it.startsWith("payload=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return null
    }

    fun decode(payload: String): JSONObject? = runCatching {
        if (DP_KEY_HEX.isBlank()) return@runCatching null
        val key = hexToBytes(DP_KEY_HEX)
        require(key.size == 32) { "bad key length" }
        val raw = base64UrlDecode(payload)
        require(raw.size > IV_LEN) { "bad payload" }
        val iv = raw.copyOfRange(0, IV_LEN)
        val ct = raw.copyOfRange(IV_LEN, raw.size)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LEN, iv))
        val plain = c.doFinal(ct)
        JSONObject(String(plain, Charsets.UTF_8))
    }.getOrNull()

    private fun hexToBytes(s: String): ByteArray {
        val clean = s.filter { it.isLetterOrDigit() }
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) out[i] = Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16).toByte()
        return out
    }

    private fun base64UrlDecode(s: String): ByteArray {
        var t = s.replace('-', '+').replace('_', '/')
        while (t.length % 4 != 0) t += '='
        return java.util.Base64.getDecoder().decode(t)
    }
}