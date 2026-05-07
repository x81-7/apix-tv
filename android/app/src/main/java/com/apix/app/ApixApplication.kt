package com.apix.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.apix.app.security.KeysVault
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.apix.app.db.SecureStorageManager // 🔴 استدعاء قاعدة البيانات المشفرة
import com.apix.app.security.KeysVault // 🔴 استدعاء القبو الفولاذي

/**
 * Custom Application with global Coil ImageLoader.
 * - Large persistent disk cache (up to 500 MB) → images stay on device
 * - Aggressive memory cache
 * - Long OkHttp timeouts for slow networks
 *
 * + Secured: Initializes C++ NDK Vault & SQLCipher Database on startup.
 */
class ApixApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        
        // 1. تفعيل القبو الفولاذي (C++ NDK) وقت الإقلاع
        try {
            // استدعاء سريع لضمان تحميل مكتبة C++ المحمية في الذاكرة فوراً
            KeysVault.getEncryptionSecretKey()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. تهيئة قاعدة البيانات المشفرة (SQLCipher) لتكون جاهزة وسريعة
        try {
            SecureStorageManager.getInstance(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // إعداداتك السابقة كما هي بدون تغيير
        coil.Coil.setImageLoader(newImageLoader())
        
        try { RewardedAdHelper.initIfNeeded(applicationContext, null) } catch (_: Throwable) {}
        
        try { RealtimeNotificationManager.start(applicationContext) } catch (_: Throwable) {}
    }

    override fun newImageLoader(): ImageLoader {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttp)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("apix_image_cache"))
                    .maxSizeBytes(500L * 1024 * 1024) // 500 MB persistent
                    .build()
            }
            .respectCacheHeaders(false) // always reuse cached image if present
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }
}
