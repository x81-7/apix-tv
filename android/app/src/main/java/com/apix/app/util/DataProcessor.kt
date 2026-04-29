package com.apix.app.util

import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.apix.app.BuildConfig
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Generic data processor.
 * (intentionally non-descriptive — name/symbols obfuscated)
 */
internal object DataProcessor {

    private const val IV_LEN = 12
    private const val TAG_LEN = 128

    private fun hxToBy(s: String): ByteArray {
        val clean = s.replace(Regex("[^0-9a-fA-F]"), "")
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) out[i] = Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16).toByte()
        return out
    }

    private fun b64uTo(s: String): ByteArray {
        var t = s.replace('-', '+').replace('_', '/')
        while (t.length % 4 != 0) t += '='
        return Base64.decode(t, Base64.DEFAULT)
    }

    /** process(input) — returns decoded JSON or null if invalid. */
    fun process(arrX: String?): JSONObject? {
        if (arrX.isNullOrBlank()) return null
        return runCatching {
            val str1 = BuildConfig.X_DP_K
            val kBy = hxToBy(str1)
            require(kBy.size == 32) { "bad k len" }
            val raw = b64uTo(arrX)
            require(raw.size > IV_LEN) { "bad payload" }
            val iv = raw.copyOfRange(0, IV_LEN)
            val ct = raw.copyOfRange(IV_LEN, raw.size)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(kBy, "AES"), GCMParameterSpec(TAG_LEN, iv))
            val plain = c.doFinal(ct)
            JSONObject(String(plain, Charsets.UTF_8))
        }.getOrNull()
    }

    /** Extract encrypted payload string from any incoming intent. */
    fun extract(intent: Intent?): String? {
        intent ?: return null
        val data: Uri? = intent.data
        if (data != null) {
            val scheme = data.scheme?.lowercase()
            if (scheme == "apix") {
                // apix://<payload>      → host carries the payload
                // apix:///<payload>     → first path segment
                data.host?.takeIf { it.isNotBlank() }?.let { return it }
                data.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
                data.getQueryParameter("id")?.takeIf { it.isNotBlank() }?.let { return it }
            } else {
                // https://apix-panal.vercel.app/watch.html?id=<payload>&app=com.apix.app
                // For web launcher links, the payload ALWAYS lives in the `id` query param.
                data.getQueryParameter("id")?.takeIf { it.isNotBlank() }?.let { return it }
                data.getQueryParameter("payload")?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        intent.getStringExtra("payload")?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }
}
