package com.apix.app.data

import android.app.Application
import android.util.Log
import com.apix.app.SupabaseDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Repository that fetches data from the Supabase REST API.
 * The entire app — categories, side menus, sub-channels and app
 * settings — runs on Supabase. There is no other backend.
 */
object SupabaseRepository {

    private const val TAG = "SupabaseRepo"
    private var appContext: Application? = null

    fun init(app: Application) { appContext = app }

    suspend fun ensureAnonymousAuth() { /* No-op: Supabase uses anon key */ }

    fun observeCategories(): Flow<List<Category>> = flow {
        val ctx = appContext ?: throw IllegalStateException("Repository not initialized")
        val data = suspendCoroutine<SupabaseDataManager.DataBundle?> { cont ->
            SupabaseDataManager.fetchRemote(ctx, object : SupabaseDataManager.DataCallback {
                override fun onSuccess(data: SupabaseDataManager.DataBundle) { cont.resume(data) }
                override fun onError(error: String) { cont.resume(null) }
            })
        }
        if (data != null) {
            val cats = data.categories.map { rCat ->
                Category(
                    id = rCat.id, name = rCat.name, sortOrder = rCat.sortOrder,
                    channels = rCat.channels?.mapValues { (_, rCh) ->
                        Channel(id = rCh.id, name = rCh.name, imageUrl = rCh.imageUrl ?: "",
                            sortOrder = rCh.sortOrder, actionType = rCh.actionType ?: "direct_play",
                            hidden = rCh.hidden, sideMenuId = rCh.sideMenuId,
                            externalUrl = rCh.externalUrl, preferredPlayer = rCh.preferredPlayer,
                            androidActionType = rCh.androidActionType,
                            pinCode = rCh.pinCode,
                             forcedAspectRatio = rCh.forcedAspectRatio,
                             lockAspectRatio = rCh.lockAspectRatio,
                             useLocalProxy = rCh.useLocalProxy,
                            stream = rCh.stream?.let { s -> StreamConfig(s.url, s.userAgent, s.referrer, s.cookies) },
                            androidStream = convertAndroidStream(rCh.androidStream))
                    }, hidden = rCat.hidden)
            }
            emit(cats)
        }
    }.flowOn(Dispatchers.IO)

    fun observeAppSettings(): Flow<AppSettings> = flow {
        val ctx = appContext
        var show = true
        var mode = "HYBRID"
        try {
            if (ctx != null) {
                // Single source of truth: read from the same worker-cached bundle
                // that powers the rest of the app (cached-data via the Cloudflare
                // Worker). This avoids an extra direct /rest/v1 call (which would
                // leak the origin) and guarantees the hide/show flag is consistent
                // with the categories actually rendered.
                val data = suspendCoroutine<SupabaseDataManager.DataBundle?> { cont ->
                    SupabaseDataManager.fetchRemote(ctx, object : SupabaseDataManager.DataCallback {
                        override fun onSuccess(data: SupabaseDataManager.DataBundle) { cont.resume(data) }
                        override fun onError(error: String) { cont.resume(null) }
                    })
                }
                val raw = data?.settings?.get("appSettings")
                if (!raw.isNullOrBlank()) {
                    try {
                        val obj = org.json.JSONObject(raw)
                        if (obj.has("showSettingsSection")) {
                            show = obj.optBoolean("showSettingsSection", true)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "appSettings parse failed", e)
                    }
                }
                // App mode (cinema / live / hybrid) — stored under the appMode key.
                val rawMode = data?.settings?.get("appMode")
                if (!rawMode.isNullOrBlank()) {
                    try {
                        mode = org.json.JSONObject(rawMode).optString("mode", "HYBRID")
                            .uppercase()
                    } catch (e: Exception) {
                        Log.w(TAG, "appMode parse failed", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "observeAppSettings failed", e)
        }
        emit(AppSettings(showSettingsSection = show, appMode = mode))
    }.flowOn(Dispatchers.IO)

    fun observeSideMenus(): Flow<Map<String, SideMenu>> = flow {
        val ctx = appContext ?: throw IllegalStateException("Repository not initialized")
        val data = suspendCoroutine<SupabaseDataManager.DataBundle?> { cont ->
            SupabaseDataManager.fetchRemote(ctx, object : SupabaseDataManager.DataCallback {
                override fun onSuccess(data: SupabaseDataManager.DataBundle) { cont.resume(data) }
                override fun onError(error: String) { cont.resume(null) }
            })
        }
        if (data != null) {
            val menus = data.sideMenus.mapValues { (_, rMenu) ->
                SideMenu(id = rMenu.id, name = rMenu.name,
                    pinCode = rMenu.pinCode,
                    channels = rMenu.channels?.mapValues { (_, sc) ->
                        SubChannel(id = sc.id, name = sc.name, imageUrl = sc.imageUrl ?: "",
                            sortOrder = sc.sortOrder, hidden = sc.hidden,
                            preferredPlayer = sc.preferredPlayer,
                            pinCode = sc.pinCode,
                            androidActionType = sc.androidActionType,
                             forcedAspectRatio = sc.forcedAspectRatio,
                             lockAspectRatio = sc.lockAspectRatio,
                             useLocalProxy = sc.useLocalProxy,
                            stream = sc.stream?.let { s -> StreamConfig(s.url, s.userAgent, s.referrer, s.cookies) },
                            androidStream = convertAndroidStream(sc.androidStream))
                    })
            }
            emit(menus)
        }
    }.flowOn(Dispatchers.IO)

    private fun convertAndroidStream(r: com.apix.app.RemoteModels.AndroidStreamConfig?): AndroidStreamConfig? {
        if (r == null) return null
        return AndroidStreamConfig(
            url = r.url, webViewOrientation = r.webViewOrientation,
            headers = r.headers, intentUri = r.intentUri,
            drmLicenseUrl = r.drmLicenseUrl, drmScheme = r.drmScheme,
            drmKeyId = r.drmKeyId, drmKey = r.drmKey,
            drmClearKeyCombined = r.drmClearKeyCombined, drmClearKeyMode = r.drmClearKeyMode,
            backupUrl = r.backupUrl, subtitleUrl = r.subtitleUrl,
            customHeaders = r.customHeaders?.map { CustomHeader(it.key, it.value) },
            drmLicenseHeaders = r.drmLicenseHeaders?.map { CustomHeader(it.key, it.value) },
            servers = r.servers?.map { Server(it.name, it.url) },
            fallbackServers = r.fallbackServers?.map { fs ->
                FallbackServer(
                    id = fs.id, name = fs.name, url = fs.url,
                    userAgent = fs.userAgent, referer = fs.referer,
                    origin = fs.origin, cookie = fs.cookie,
                    drmScheme = fs.drmScheme, drmLicenseUrl = fs.drmLicenseUrl,
                    drmKeyId = fs.drmKeyId, drmKey = fs.drmKey,
                    drmClearKeyCombined = fs.drmClearKeyCombined,
                    drmClearKeyMode = fs.drmClearKeyMode,
                    customHeaders = fs.customHeaders?.map { CustomHeader(it.key, it.value) },
                    drmLicenseHeaders = fs.drmLicenseHeaders?.map { CustomHeader(it.key, it.value) }
                )
            },
             audioSources = r.audioSources?.map { AudioSource(it.name, it.url) },
             forcedAspectRatio = r.forcedAspectRatio,
             lockAspectRatio = r.lockAspectRatio,
             logoOverlay = r.logoOverlay?.let { LogoOverlay(it.url, it.position, it.offsetX, it.offsetY, it.width, it.height, it.opacity) }
        )
    }
}
