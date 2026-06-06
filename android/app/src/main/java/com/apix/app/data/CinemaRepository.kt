package com.apix.app.data

import android.util.Log
import com.apix.app.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * CinemaRepository — builds the cinema [HomeData] for the Home screen.
 *
 * The whole catalog now comes from ONE unified endpoint that already returns the
 * exact JSON shape the app understands (matches [HomeData] / [MediaItem]):
 *
 *     POST /functions/v1/cinema-gateway { action: "home" }
 *       → { success, app_mode, hero[], rows[] }
 *
 * This is the "Cloudflare worker behaves like a real API" contract — no client
 * side reshaping needed. An optional client "external source" raw JSON feed
 * ([AppSettings.externalSourceUrl]) is merged on top when present.
 */
object CinemaRepository {

    private const val TAG = "CinemaRepo"
    private const val GATEWAY_PATH = "/functions/v1/cinema-gateway"

    /** Fetch + merge the cinema home payload. Never throws. */
    suspend fun loadHome(settings: AppSettings): HomeData = withContext(Dispatchers.IO) {
        val gateway = runCatching { loadFromGateway() }.getOrDefault(HomeData(appMode = settings.appMode))
        val external = if (settings.externalSourceUrl.isNotBlank()) {
            runCatching { loadFromExternal(settings.externalSourceUrl) }.getOrDefault(HomeData())
        } else HomeData()

        // External feed takes priority for the hero; rows are concatenated.
        val hero = external.hero.ifEmpty { gateway.hero }
        val rows = (external.rows + gateway.rows).filter { it.items.isNotEmpty() }
        // app_mode always comes from the worker (single source of truth).
        HomeData(hero = hero, rows = rows, appMode = gateway.appMode)
    }

    /**
     * Resolve a catalog item into something playable.
     *  • Xtream/direct items → returns the direct URL (scrape = false).
     *  • TMDB items          → returns the scraper embed URL (scrape = true);
     *                          the caller must run HiddenWebViewScraper on it.
     */
    suspend fun resolve(item: MediaItem, season: Int = 1, episode: Int = 1): ResolveResult? =
        withContext(Dispatchers.IO) {
            item.directUrl?.let { return@withContext ResolveResult(url = it, scrape = false) }
            try {
                val body = JSONObject()
                    .put("action", "resolve")
                    .put("section", item.section)
                    .put("id", item.id.substringAfterLast('_'))
                    .put("tmdb_id", item.tmdbId)
                    .put("season", season)
                    .put("episode", episode)
                    .put("ext", item.extension)
                    .toString()
                val resp = JSONObject(Net.post(GATEWAY_PATH, body))
                if (!resp.optBoolean("success", false)) return@withContext null
                val url = resp.optString("url", "")
                if (url.isBlank()) return@withContext null
                ResolveResult(
                    url = url,
                    scrape = resp.optBoolean("scrape", false),
                    referer = resp.optString("referer", "").ifBlank { null }
                )
            } catch (e: Exception) {
                Log.w(TAG, "resolve failed", e); null
            }
        }

    // ── Unified gateway home (matches HomeData exactly) ─────────────────────
    private fun loadFromGateway(): HomeData {
        val body = JSONObject().put("action", "home").toString()
        val raw = Net.post(GATEWAY_PATH, body)
        return CinemaJson.parseHome(raw)
    }

    // ── External client feed (raw JSON, e.g. GitHub Raw) ────────────────────
    private fun loadFromExternal(url: String): HomeData {
        val raw = Net.getAbsolute(url)
        return CinemaJson.parseHome(raw)
    }
}
