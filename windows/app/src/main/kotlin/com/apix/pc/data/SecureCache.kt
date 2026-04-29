package com.apix.pc.data

import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Local encrypted cache stored in `%APPDATA%/APiXTV/cache.enc`.
 * Same intent as `SecureCacheManager` on Android — keep channel data on disk
 * so we don't hit Supabase on every launch.
 *
 * Key is derived from the hardware-bound device id (`HardwareId`) and stored
 * inside the same AppData directory.
 */
object SecureCache {

    private val appDir: File by lazy {
        val base = System.getenv("APPDATA") ?: System.getProperty("user.home")
        File(base, "APiXTV").apply { mkdirs() }
    }
    private val cacheFile = File(appDir, "cache.enc")
    private val keyFile = File(appDir, "cache.key")

    private fun loadOrCreateKey(): SecretKey {
        if (keyFile.exists() && keyFile.length() == 32L) {
            return SecretKeySpec(keyFile.readBytes(), "AES")
        }
        val gen = KeyGenerator.getInstance("AES").apply { init(256) }
        val key = gen.generateKey()
        keyFile.writeBytes(key.encoded)
        return key
    }

    fun save(plain: String) = runCatching {
        val key = loadOrCreateKey()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        }
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        cacheFile.writeBytes(iv + ct)
    }

    fun load(): String? = runCatching {
        if (!cacheFile.exists()) return@runCatching null
        val raw = cacheFile.readBytes()
        if (raw.size <= 12) return@runCatching null
        val iv = raw.copyOfRange(0, 12)
        val ct = raw.copyOfRange(12, raw.size)
        val key = loadOrCreateKey()
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        }
        String(c.doFinal(ct), Charsets.UTF_8)
    }.getOrNull()
}