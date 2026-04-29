package com.apix.pc.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.apix.pc.data.Category
import com.apix.pc.data.Channel
import com.apix.pc.data.SecureCache
import com.apix.pc.data.SideMenu
import com.apix.pc.data.SupabaseClient
import com.apix.pc.data.StreamConfig
import com.apix.pc.security.BanVerdict
import com.apix.pc.security.HandshakeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * App-level state. Hydrates from the local encrypted cache first (fast cold
 * start, no network) then refreshes from Supabase. Same approach as Android.
 */
class AppViewModel(private val scope: CoroutineScope) {
    val categories = mutableStateListOf<Category>()
    val channels = mutableStateListOf<Channel>()
    val sideMenus = mutableStateListOf<SideMenu>()
    val selectedCategoryId = mutableStateOf<String?>(null)
    val loading = mutableStateOf(true)
    val activeStream = mutableStateOf<StreamConfig?>(null)
    val errorMsg = mutableStateOf<String?>(null)

    // Gate / bypass settings (mirrors Android GateActivity)
    val gateEnabled = mutableStateOf(true)
    val bypassCode = mutableStateOf("2026")
    val gateTitle = mutableStateOf("تشغيل يدوي")
    val gateSubtitle = mutableStateOf("أدخل بيانات البث")
    val unlocked = mutableStateOf(false)

    /** Set when the security handshake bans this device. AppRoot navigates
     *  to a kill screen when this is non-null. */
    val banVerdict = mutableStateOf<BanVerdict?>(null)

    fun load() {
        SecureCache.load()?.let { hydrateFromJson(it) }
        scope.launch {
            // Run security handshake in parallel with the data fetch — same
            // Android/iOS contract. Sends platform=windows + hardware-bound
            // device id; edge function captures IP from x-forwarded-for.
            val verdict = withContext(Dispatchers.IO) { HandshakeClient.handshake() }
            if (verdict.status == "PERMA_BAN" || verdict.status == "TEMP_BAN" ||
                verdict.status == "TAMPERED_MOD") {
                banVerdict.value = verdict
                loading.value = false
                return@launch
            }
            // Try the aggregated cached-data Edge Function first (1 round trip
            // + ETag/304). Fall back to per-table REST if the function is down.
            val bundle = withContext(Dispatchers.IO) { SupabaseClient.fetchBundle() }
            val cats: List<Category>
            val chs: List<Channel>
            val menus: List<SideMenu>
            val subs: List<Pair<String, Channel>>
            val settings: Map<String, JSONObject>
            if (bundle != null && bundle.notModified) {
                // Server says nothing changed since our last cache — keep the
                // hydrated UI as-is, just refresh gate config (already in cache).
                loading.value = false
                return@launch
            } else if (bundle != null) {
                cats = bundle.categories
                chs = bundle.channels
                menus = bundle.sideMenus
                subs = bundle.subChannels
                settings = bundle.settings
            } else {
                cats = withContext(Dispatchers.IO) { SupabaseClient.fetchCategories() }
                chs  = withContext(Dispatchers.IO) { SupabaseClient.fetchChannels() }
                menus = withContext(Dispatchers.IO) { SupabaseClient.fetchSideMenus() }
                subs  = withContext(Dispatchers.IO) { SupabaseClient.fetchSubChannels() }
                settings = withContext(Dispatchers.IO) { SupabaseClient.fetchSettings() }
            }
            settings["gateConfig"]?.optJSONObject("value")?.let { v ->
                gateEnabled.value = v.optBoolean("enabled", true)
                bypassCode.value = v.optString("bypassCode", "2026")
                gateTitle.value = v.optString("title", "تشغيل يدوي")
                gateSubtitle.value = v.optString("subtitle", "أدخل بيانات البث")
            }
            if (cats.isNotEmpty() || chs.isNotEmpty()) {
                categories.clear(); categories.addAll(cats)
                channels.clear(); channels.addAll(chs)
                // Attach sub-channels to their menus.
                sideMenus.clear()
                sideMenus.addAll(menus.map { m ->
                    m.copy(channels = subs.filter { it.first == m.id }.map { it.second }
                        .sortedBy { it.sortOrder })
                })
                if (selectedCategoryId.value == null) {
                    selectedCategoryId.value = cats.firstOrNull()?.id
                }
                persistCache(cats, chs)
            } else if (channels.isEmpty()) {
                // Offline mode with no cache — show friendly message but
                // don't block the UI. If cache exists, we already hydrated.
                errorMsg.value = "تعذر الاتصال بالخادم. تحقق من الإنترنت."
            }
            loading.value = false
        }
    }

    fun channelsFor(categoryId: String?): List<Channel> =
        if (categoryId == null) channels.toList()
        else channels.filter { it.categoryId == categoryId }.sortedBy { it.sortOrder }

    fun sideMenuById(id: String?): SideMenu? = sideMenus.firstOrNull { it.id == id }

    fun openChannel(c: Channel) {
        val s = c.effectiveStream ?: return
        val url = s.url ?: return
        activeStream.value = StreamConfig(
            url = url,
            title = c.name,
            playerType = c.effectivePlayer,
            drmScheme = s.drmScheme,
            drmKeyId = s.drmKeyId,
            drmKey = s.drmKey,
            drmLicenseUrl = s.drmLicenseUrl,
            userAgent = s.userAgent,
            referer = s.referer,
            customHeaders = s.customHeaders
        )
    }

    fun openStream(s: StreamConfig) { activeStream.value = s }
    fun closePlayer() { activeStream.value = null }

    private fun hydrateFromJson(raw: String) = runCatching {
        val o = JSONObject(raw)
        val cArr = o.optJSONArray("channels") ?: JSONArray()
        val catArr = o.optJSONArray("categories") ?: JSONArray()
        channels.clear()
        for (i in 0 until cArr.length()) channels += Channel.fromJson(cArr.getJSONObject(i))
        categories.clear()
        for (i in 0 until catArr.length()) {
            val co = catArr.getJSONObject(i)
            categories += Category(co.optString("id"), co.optString("name"),
                co.optInt("sort_order"), co.optBoolean("hidden"), emptyList())
        }
        loading.value = false
    }

    private fun persistCache(cats: List<Category>, chs: List<Channel>) {
        // Reconcile per-channel: load prior cache, replace ONLY the channels
        // whose cache_version changed, keep the rest. Mirrors Android + iOS.
        val prior: Map<String, JSONObject> = runCatching {
            val raw = SecureCache.load() ?: return@runCatching emptyMap<String, JSONObject>()
            val o = JSONObject(raw)
            val arr = o.optJSONArray("channels") ?: return@runCatching emptyMap<String, JSONObject>()
            buildMap {
                for (i in 0 until arr.length()) {
                    val j = arr.getJSONObject(i)
                    put(j.optString("id"), j)
                }
            }
        }.getOrDefault(emptyMap())

        val catArr = JSONArray()
        cats.forEach { catArr.put(JSONObject()
            .put("id", it.id).put("name", it.name)
            .put("sort_order", it.sortOrder).put("hidden", it.hidden)) }

        val chArr = JSONArray()
        chs.filter { it.offlineCacheEnabled }.forEach { c ->
            val existing = prior[c.id]
            if (existing != null && existing.optLong("cache_version", -1L) == c.cacheVersion) {
                chArr.put(existing)  // still fresh
            } else {
                chArr.put(JSONObject()
                    .put("id", c.id).put("name", c.name)
                    .put("image_url", c.imageUrl ?: "")
                    .put("category_id", c.categoryId ?: "")
                    .put("sort_order", c.sortOrder).put("hidden", c.hidden)
                    .put("action_type", c.actionType)
                    .put("cache_version", c.cacheVersion))
            }
        }
        SecureCache.save(JSONObject()
            .put("categories", catArr).put("channels", chArr).toString())
    }
}
