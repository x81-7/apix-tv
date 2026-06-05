package com.apix.app.data

import android.util.Log
import com.apix.app.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * CinemaRepository — builds the cinema [HomeData] for the Home screen.
 *
 * Two converging sources, BOTH routed so Supabase/IPTV origins never leak:
 *  1) The dedicated cinema gateway (cinema-gateway edge function) reached via
 *     the Cloudflare Worker → /functions/v1/cinema-gateway (Xtream catalog).
 *  2) An optional client "external source" raw JSON feed ([AppSettings.externalSourceUrl]).
 *     When present it is merged ON TOP of the gateway rows.
 */
object CinemaRepository {

    private const val TAG = "CinemaRepo"
    private const val GATEWAY_PATH = "/functions/v1/cinema-gateway"

    /** Fetch + merge the cinema home payload. Never throws. */
    suspend fun loadHome(settings: AppSettings): HomeData = withContext(Dispatchers.IO) {
        val gateway = runCatching { loadFromGateway(settings.appMode) }.getOrDefault(HomeData())
        val external = if (settings.externalSourceUrl.isNotBlank()) {
            runCatching { loadFromExternal(settings.externalSourceUrl) }.getOrDefault(HomeData())
        } else HomeData()

        // External feed takes priority for the hero; rows are concatenated.
        val hero = external.hero.ifEmpty { gateway.hero }
        val rows = (external.rows + gateway.rows).filter { it.items.isNotEmpty() }
        HomeData(hero = hero, rows = rows)
    }

    /** Resolve a playable URL for a catalog item via the gateway. */
    suspend fun resolve(item: MediaItem): String? = withContext(Dispatchers.IO) {
        item.directUrl?.let { return@withContext it }
        try {
            val body = JSONObject()
                .put("action", "resolve")
                .put("section", item.section)
                .put("id", item.id)
                .put("ext", item.extension)
                .toString()
            val resp = JSONObject(Net.post(GATEWAY_PATH, body))
            if (resp.optBoolean("success", false)) resp.optString("url", null) else null
        } catch (e: Exception) {
            Log.w(TAG, "resolve failed", e); null
        }
    }

    // ── Gateway (Xtream via cinema-gateway) ─────────────────────────────────
    private fun loadFromGateway(appMode: String): HomeData {
        val sections = when (appMode.uppercase()) {
            "SPORTS_ONLY" -> listOf("live")
            "CINEMA_ONLY" -> listOf("vod", "series")
            else -> listOf("vod", "series", "live")
        }
        val rows = mutableListOf<HomeRow>()
        val hero = mutableListOf<MediaItem>()

        for (section in sections) {
            val items = runCatching { fetchStreams(section) }.getOrDefault(emptyList())
            if (items.isEmpty()) continue
            rows.add(HomeRow(id = section, title = sectionTitle(section), items = items.take(30)))
            if (hero.size < 5) hero.addAll(items.filter { it.backdrop.isNotBlank() || it.poster.isNotBlank() }.take(5 - hero.size))
        }
        return HomeData(hero = hero, rows = rows)
    }

    private fun fetchStreams(section: String): List<MediaItem> {
        val body = JSONObject()
            .put("action", "catalog")
            .put("section", section)
            .put("kind", "streams")
            .toString()
        val resp = JSONObject(Net.post(GATEWAY_PATH, body))
        if (!resp.optBoolean("success", false)) return emptyList()
        val arr: JSONArray = resp.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("stream_id", o.optString("series_id", o.optString("num", i.toString())))
                add(
                    MediaItem(
                        id = id,
                        title = o.optString("name", ""),
                        poster = o.optString("stream_icon", o.optString("cover", "")),
                        backdrop = o.optString("backdrop_path", ""),
                        rating = o.optString("rating", ""),
                        section = section,
                        extension = o.optString("container_extension", if (section == "live") "ts" else "mp4")
                    )
                )
            }
        }
    }

    private fun sectionTitle(section: String): String = when (section) {
        "vod" -> "أفلام"
        "series" -> "مسلسلات"
        "live" -> "بث مباشر"
        else -> section
    }

    // ── External client feed (raw JSON, e.g. GitHub Raw) ────────────────────
    private fun loadFromExternal(url: String): HomeData {
        val raw = Net.getAbsolute(url)
        return CinemaJson.parseHome(raw)
    }
}
