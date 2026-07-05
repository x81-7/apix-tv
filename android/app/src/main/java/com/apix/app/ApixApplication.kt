package com.apix.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.apix.app.security.DeviceIntegrity
import com.apix.app.security.Enforcement
import com.apix.app.security.HandshakeClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ApixApplication : Application(), ImageLoaderFactory {

    @Volatile private var runtimeGuardBusy = false

    override fun onCreate() {
        super.onCreate()
        coil.Coil.setImageLoader(newImageLoader())
        try { RewardedAdHelper.initIfNeeded(applicationContext, null) } catch (_: Throwable) {}
        try { RealtimeNotificationManager.start(applicationContext) } catch (_: Throwable) {}
        // Instant, network-free enforcement of a previously cached ban.
        checkCachedBanEarly()
        // Continuously watch for proxy/VPN/Frida turned on AFTER launch.
        registerRuntimeGuard()
    }

    /**
     * If a ban verdict was cached previously, wipe local data and close
     * silently before any UI is shown. No visible kill screen.
     */
    private fun checkCachedBanEarly() {
        try {
            if (Enforcement.isBannedCached(applicationContext)) {
                Enforcement.wipeChannelCache(applicationContext)
                Enforcement.silentExit(applicationContext)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Detect sniffing tools (system proxy / VPN / Frida / hooks) that the user
     * may enable AFTER pulling channel data. On detection we ask the server to
     * ban the device, then wipe the cached channels and exit silently — the
     * device is recorded server-side and will never pass the handshake again.
     */
    private fun registerRuntimeGuard() {
        if (BuildConfig.DEBUG) return
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { runRuntimeGuard(activity) }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun runRuntimeGuard(activity: Activity) {
        if (runtimeGuardBusy) return
        runtimeGuardBusy = true
        Thread {
            try {
                // Native obfuscated sweep first — silently kills from native on
                // any live sniffing/instrumentation threat detected mid-session.
                try { x.guardOrDie() } catch (_: Throwable) {}
                val danger = try { DeviceIntegrity.environmentDanger(applicationContext) } catch (_: Throwable) { null }
                val vpnOn = try { DeviceIntegrity.isVpnActive(applicationContext) } catch (_: Throwable) { false }
                if (danger != null) {
                    val supaUrl = try { Net.base() } catch (_: Throwable) { null }
                    val anonKey = try { Net.anon() } catch (_: Throwable) { null }
                    var verdict: HandshakeClient.Verdict? = null
                    if (supaUrl != null && anonKey != null) {
                        verdict = try {
                            HandshakeClient.handshake(applicationContext, supaUrl, anonKey, BuildConfig.VERSION_NAME)
                        } catch (_: Throwable) { null }
                    }
                    // Whether or not the server round-trip succeeded, a live
                    // sniffing environment means we must destroy cached data.
                    val v = verdict ?: HandshakeClient.Verdict().apply {
                        status = "ENVIRONMENT_DANGER"; wipe = true; reason = danger
                    }
                    if (v.status != null && v.status != "ACTIVE" && v.status != "ERROR") {
                        Enforcement.enforce(applicationContext, v)
                    } else {
                        // Server hasn't (yet) confirmed the ban — still wipe locally.
                        Enforcement.wipeChannelCache(applicationContext)
                        Enforcement.silentExit(applicationContext)
                    }
                } else if (vpnOn) {
                    // VPN turned on mid-session: ask the server if this IP is on
                    // the allow-list. If not, bounce back to the splash gate which
                    // shows the "disable VPN" message instead of silently killing.
                    val supaUrl = try { Net.base() } catch (_: Throwable) { null }
                    val anonKey = try { Net.anon() } catch (_: Throwable) { null }
                    if (supaUrl != null && anonKey != null) {
                        val v = try {
                            HandshakeClient.handshake(applicationContext, supaUrl, anonKey, BuildConfig.VERSION_NAME)
                        } catch (_: Throwable) { null }
                        if (v != null && v.status == "VPN_BLOCK") {
                            try {
                                val i = android.content.Intent(applicationContext, SplashActivity::class.java)
                                i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                applicationContext.startActivity(i)
                            } catch (_: Throwable) {}
                        }
                    }
                }
            } catch (_: Throwable) {
            } finally {
                runtimeGuardBusy = false
            }
        }.apply { isDaemon = true }.start()
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