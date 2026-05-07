package com.apix.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Custom Application with global Coil ImageLoader.
 * - Large persistent disk cache (up to 500 MB) → images stay on device
 * - Aggressive memory cache
 * - Long OkHttp timeouts for slow networks
 *
 * Effect: on first launch all channel/sub-channel images are downloaded once,
 * then reused forever (until cache eviction or app data clear). Later refreshes
 * only update the stream URLs, not images.
 */
class ApixApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Warm Coil singleton so Compose AsyncImage uses our custom loader
        coil.Coil.setImageLoader(newImageLoader())
        // Initialize AdMob SDK eagerly so the first rewarded ad request is fast
        try { RewardedAdHelper.initIfNeeded(applicationContext, null) } catch (_: Throwable) {}
        // Start in-process realtime notification listener (no foreground service / no
        // persistent "ready" notification). Works while the app process is alive.
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
