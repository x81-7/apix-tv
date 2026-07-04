package com.apix.pc.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * REST client — same Supabase project as the Android app.
 * Reads `categories`, `channels`, `sub_channels`, `system_settings` directly.
 */
object SupabaseClient {

    // Keep in sync with android/app/build.gradle CLOUD_URL/CLOUD_ANON_KEY and
    // ios/APiXTV/Services/CloudConfig.swift. Points to the Lovable Cloud
    // project the admin panel writes to.
    private const val DEFAULT_URL = "" // Gateway-only: provide via CLOUD_URL / WORKER_URL env
    private const val DEFAULT_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhmcmNqd3lieGZ0eHNwdnBlZ2ZiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzczNzkyMzksImV4cCI6MjA5Mjk1NTIzOX0.xtoVGdA1zNJBRKerY16azg8NQSMXwK6Xmid7TERKAR0"

    // White-label gateway: route ALL traffic through the Cloudflare Worker
    // (hides Supabase, encrypts payloads, enforces bans) exactly like Android.
    // Values are baked into the packaged app via ApixConfig (WORKER_URL first,
    // CLOUD_URL fallback). An env var still overrides for local testing.
    val baseUrl: String = ApixConfig.baseUrl.ifBlank { DEFAULT_URL.trimEnd('/') }
    val anonKey: String = ApixConfig.anonKey.ifBlank { DEFAULT_KEY }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun get(path: String): String? = runCatching {
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/$path")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Accept", "application/json")
            .build()
        http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    }.getOrNull()

    /** Result of a cached-data bundle fetch. `notModified=true` means the
     *  server returned 304 — caller should keep using the local cache. */
    data class BundleResult(
        val notModified: Boolean,
        val categories: List<Category>,
        val channels: List<Channel>,
        val sideMenus: List<SideMenu>,
        val subChannels: List<Pair<String, Channel>>,
        val settings: Map<String, JSONObject>,
    )

    @Volatile private var bundleEtag: String? = null

    /**
     * Fetches the aggregated bundle from the `cached-data` Edge Function with
     * If-None-Match. Returns `null` on transport failure so callers can fall
     * back to per-table REST.
     */
    fun fetchBundle(): BundleResult? = runCatching {
        val builder = Request.Builder()
            .url("$baseUrl/functions/v1/cached-data")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Accept", "application/json")
        bundleEtag?.let { builder.header("If-None-Match", it) }
        http.newCall(builder.build()).execute().use { r ->
            r.header("ETag")?.let { bundleEtag = it }
            if (r.code == 304) {
                return@use BundleResult(true, emptyList(), emptyList(), emptyList(), emptyList(), emptyMap())
            }
            if (!r.isSuccessful) return@runCatching null
            val envelope = r.body?.string() ?: return@runCatching null
            // cached-data is AES-256-GCM encrypted ({iv,data}). Decrypt first.
            val raw = runCatching { PayloadCipher.decryptEnvelope(envelope) }
                .getOrElse { return@runCatching null }
            val obj = JSONObject(raw)
            val cats = obj.optJSONArray("categories") ?: JSONArray()
            val chans = obj.optJSONArray("channels") ?: JSONArray()
            val menus = obj.optJSONArray("side_menus") ?: JSONArray()
            val subs = obj.optJSONArray("sub_channels") ?: JSONArray()
            val settings = obj.optJSONArray("system_settings") ?: JSONArray()

            val categoriesList = mutableListOf<Category>()
            for (i in 0 until cats.length()) {
                val o = cats.getJSONObject(i)
                categoriesList += Category(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    sortOrder = o.optInt("sort_order", 0),
                    hidden = o.optBoolean("hidden", false),
                    channels = emptyList()
                )
            }
            val channelsList = mutableListOf<Channel>()
            for (i in 0 until chans.length()) channelsList += Channel.fromJson(chans.getJSONObject(i))
            val menusList = mutableListOf<SideMenu>()
            for (i in 0 until menus.length()) menusList += SideMenu.fromJson(menus.getJSONObject(i))
            val subsList = mutableListOf<Pair<String, Channel>>()
            for (i in 0 until subs.length()) {
                val o = subs.getJSONObject(i)
                val menuId = o.optString("side_menu_id").takeIf { it.isNotBlank() } ?: continue
                val ch = Channel(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    imageUrl = o.optString("image_url").takeIf { it.isNotBlank() },
                    categoryId = null,
                    sortOrder = o.optInt("sort_order", 0),
                    hidden = o.optBoolean("hidden", false),
                    actionType = "direct_play",
                    externalUrl = null,
                    sideMenuId = menuId,
                    windowsActionType = o.optString("windows_action_type").takeIf { it.isNotBlank() },
                    windowsStream = StreamSpec.fromJson(o.optJSONObject("windows_stream")),
                    webStream = StreamSpec.fromJson(o.optJSONObject("web_stream")),
                    offlineCacheEnabled = o.optBoolean("offline_cache_enabled", false),
                    pinCode = o.optString("pin_code").takeIf { it.isNotBlank() }
                )
                if (!ch.hidden) subsList += menuId to ch
            }
            val settingsMap = mutableMapOf<String, JSONObject>()
            for (i in 0 until settings.length()) {
                val o = settings.getJSONObject(i)
                val k = o.optString("key")
                if (k.isNotBlank()) settingsMap[k] = o
            }
            BundleResult(
                notModified = false,
                categories = categoriesList.filter { !it.hidden },
                channels = channelsList.filter { !it.hidden },
                sideMenus = menusList,
                subChannels = subsList,
                settings = settingsMap,
            )
        }
    }.getOrNull()

    fun fetchCategories(): List<Category> {
        val raw = get("categories?select=*&order=sort_order") ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<Category>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += Category(
                id = o.optString("id"),
                name = o.optString("name"),
                sortOrder = o.optInt("sort_order", 0),
                hidden = o.optBoolean("hidden", false),
                channels = emptyList()
            )
        }
        return out.filter { !it.hidden }
    }

    fun fetchChannels(): List<Channel> {
        val raw = get("channels?select=*,cache_version&order=sort_order") ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<Channel>()
        for (i in 0 until arr.length()) out += Channel.fromJson(arr.getJSONObject(i))
        return out.filter { !it.hidden }
    }

    fun fetchSideMenus(): List<SideMenu> {
        val raw = get("side_menus?select=*&order=sort_order") ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<SideMenu>()
        for (i in 0 until arr.length()) out += SideMenu.fromJson(arr.getJSONObject(i))
        return out
    }

    /** Sub-channels are returned as Channels with sideMenuId populated. */
    fun fetchSubChannels(): List<Pair<String, Channel>> {
        val raw = get("sub_channels?select=*,cache_version&order=sort_order") ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<Pair<String, Channel>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val menuId = o.optString("side_menu_id").takeIf { it.isNotBlank() } ?: continue
            val ch = Channel(
                id = o.optString("id"),
                name = o.optString("name"),
                imageUrl = o.optString("image_url").takeIf { it.isNotBlank() },
                categoryId = null,
                sortOrder = o.optInt("sort_order", 0),
                hidden = o.optBoolean("hidden", false),
                actionType = "direct_play",
                externalUrl = null,
                sideMenuId = menuId,
                windowsActionType = o.optString("windows_action_type").takeIf { it.isNotBlank() },
                windowsStream = StreamSpec.fromJson(o.optJSONObject("windows_stream")),
                webStream = StreamSpec.fromJson(o.optJSONObject("web_stream")),
                offlineCacheEnabled = o.optBoolean("offline_cache_enabled", false),
                pinCode = o.optString("pin_code").takeIf { it.isNotBlank() }
            )
            if (!ch.hidden) out += menuId to ch
        }
        return out
    }

    fun fetchSettings(): Map<String, JSONObject> {
        val raw = get("system_settings?select=*") ?: return emptyMap()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyMap()
        val out = mutableMapOf<String, JSONObject>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val k = o.optString("key")
            if (k.isNotBlank()) out[k] = o
        }
        return out
    }
}
