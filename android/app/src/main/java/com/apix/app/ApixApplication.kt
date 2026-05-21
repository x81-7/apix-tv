package com.apix.app

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ApixApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        coil.Coil.setImageLoader(newImageLoader())
        try { RewardedAdHelper.initIfNeeded(applicationContext, null) } catch (_: Throwable) {}
        try { RealtimeNotificationManager.start(applicationContext) } catch (_: Throwable) {}
        // 
        checkCachedBanEarly()
    }

    private fun checkCachedBanEarly() {
        try {
            val sp = getSharedPreferences("ban_cache", MODE_PRIVATE)
            val status    = sp.getString("last_status", "ACTIVE") ?: "ACTIVE"
            val lastCheck = sp.getLong("last_check", 0L)
            val banUntil  = sp.getString("ban_until", null)
            val reason    = sp.getString("ban_reason", "") ?: ""
            val telegram  = sp.getString("telegram_url", "") ?: ""

            if (lastCheck == 0L) return // 
            val isActiveBan = when (status) {
                "PERMA_BAN", "TAMPERED_MOD", "ENVIRONMENT_DANGER" -> true
                "TEMP_BAN" -> {
                    if (!banUntil.isNullOrEmpty()) {
                        try {
                            val until = java.time.Instant.parse(banUntil).toEpochMilli()
                            System.currentTimeMillis() < until
                        } catch (_: Exception) {
                            System.currentTimeMillis() - lastCheck < 24 * 60 * 60 * 1000L
                        }
                    } else {
                        System.currentTimeMillis() - lastCheck < 24 * 60 * 60 * 1000L
                    }
                }
                else -> false
            }

            if (!isActiveBan) return

            Handler(Looper.getMainLooper()).post {
                try {
                    val intent = Intent(this, KillScreenActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        putExtra("status",    status)
                        putExtra("ban_until", banUntil)
                        putExtra("reason",    reason)
                        putExtra("telegram",  telegram)
                    }
                    startActivity(intent)
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
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
                MemoryCache.Builder(this).maxSizePercent(0.25).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("apix_image_cache"))
                    .maxSizeBytes(500L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }
}