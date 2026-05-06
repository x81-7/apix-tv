package com.apix.app.util

import android.util.Base64

/**
 * Lightweight runtime string obfuscator (XOR + Base64).
 *
 * Intent: keep API endpoints, Referers, ClearKeys and other sensitive
 * literals out of plain-text `strings` dump (R8 does NOT encrypt strings).
 *
 * Usage:
 *   val raw = "https://api.example.com/secret"
 *   // 1) Generate at build time: println(StringObfuscator.encode(raw, KEY))
 *   //    → paste the resulting Base64 into your code:
 *   val ENC = "aB3...="
 *   val real = StringObfuscator.d(ENC)
 *
 * Security notes:
 *  - This is OBFUSCATION, not encryption. A determined attacker can still
 *    extract the strings at runtime — but they will not appear in `strings`
 *    on the APK itself (which is what casual reverse-engineers grep first).
 *  - Combined with R8 renaming + repackaging this raises the bar
 *    significantly without breaking AdMob / ExoPlayer / JSON parsing.
 */
object StringObfuscator {

    // Rotating XOR key. Change per release if you want.
    private val KEY = byteArrayOf(
        0x4A, 0x17, 0x3C, 0x9E.toByte(), 0x21, 0x55, 0x88.toByte(), 0x0F,
        0xB2.toByte(), 0x6D, 0xC1.toByte(), 0x39, 0x74, 0xE5.toByte(), 0x52, 0xA8.toByte()
    )

    /** Decode an XOR+Base64 obfuscated string (call at runtime). */
    @JvmStatic
    fun d(encoded: String): String {
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        val out = ByteArray(data.size)
        for (i in data.indices) out[i] = (data[i].toInt() xor KEY[i % KEY.size].toInt()).toByte()
        return String(out, Charsets.UTF_8)
    }

    /** Helper to ENCODE a plain string (run once during dev — copy result into source). */
    @JvmStatic
    fun encode(plain: String): String {
        val data = plain.toByteArray(Charsets.UTF_8)
        val out = ByteArray(data.size)
        for (i in data.indices) out[i] = (data[i].toInt() xor KEY[i % KEY.size].toInt()).toByte()
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }
}
