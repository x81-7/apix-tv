package com.apix.app.data

/**
 * Cinema / VOD domain models.
 *
 * The app NEVER talks to Supabase, TMDB or an IPTV origin directly. Everything
 * is fetched through the Cloudflare Worker(s):
 *   • the live-TV/data gateway (WORKER_URL)  → categories/channels
 *   • the dedicated cinema worker            → movies/series/anime catalog & resolve
 *   • an optional client "external source"   → a raw JSON feed (e.g. GitHub Raw)
 *
 * All converge into [HomeData], which the Compose Home screen renders. The JSON
 * shape returned by the cinema worker (action: "home") matches these classes
 * field-for-field so no translation layer is needed.
 */

/** A single poster item (movie, series, anime, or live entry). */
data class MediaItem(
    val id: String = "",
    val title: String = "",
    val poster: String = "",
    val backdrop: String = "",
    val description: String = "",
    val rating: String = "",
    val year: String = "",
    // "vod" | "series" | "anime" | "live"
    val section: String = "vod",
    // TMDB id (used by resolve to build the scraper embed URL).
    val tmdbId: String = "",
    // Optional direct stream URL (when the feed already supplies one).
    val directUrl: String? = null,
    // Optional pre-supplied proxy hint for direct feeds.
    val useLocalProxy: Boolean = false,
    val extension: String = "mp4"
)

/** A horizontal row of items (TvLazyRow). */
data class HomeRow(
    val id: String = "",
    val title: String = "",
    val items: List<MediaItem> = emptyList()
)

/** The whole Home payload: a hero carousel + ordered rows + the active app mode. */
data class HomeData(
    val hero: List<MediaItem> = emptyList(),
    val rows: List<HomeRow> = emptyList(),
    val appMode: String = "HYBRID"
) {
    val isEmpty: Boolean get() = hero.isEmpty() && rows.isEmpty()

    /** Flatten + de-dupe all items of a given section (for Movies/Series/Anime tabs). */
    fun itemsForSection(section: String): List<MediaItem> {
        val seen = HashSet<String>()
        val out = ArrayList<MediaItem>()
        for (row in rows) for (item in row.items) {
            if (item.section.equals(section, true) && seen.add(item.id)) out.add(item)
        }
        return out
    }
}

/** Result of resolving a catalog item into something playable. */
data class ResolveResult(
    val url: String,
    // true → the URL is a scraper embed page; run HiddenWebViewScraper on it.
    val scrape: Boolean,
    val referer: String? = null
)

/**
 * Parses the cinema worker "home" response (or a client external JSON feed) into
 * [HomeData]. Both share the same shape:
 * {
 *   "app_mode": "HYBRID",
 *   "hero": [ {item}, ... ],
 *   "rows": [ { "id","title","items":[ {item}, ... ] }, ... ]
 * }
 */
object CinemaJson {
    fun parseHome(raw: String): HomeData {
        return try {
            val root = org.json.JSONObject(raw)
            val hero = parseItems(root.optJSONArray("hero"))
            val rowsArr = root.optJSONArray("rows")
            val rows = buildList {
                if (rowsArr != null) {
                    for (i in 0 until rowsArr.length()) {
                        val r = rowsArr.optJSONObject(i) ?: continue
                        add(
                            HomeRow(
                                id = r.optString("id", "row_$i"),
                                title = r.optString("title", ""),
                                items = parseItems(r.optJSONArray("items"))
                            )
                        )
                    }
                }
            }
            HomeData(
                hero = hero,
                rows = rows,
                appMode = root.optString("app_mode", "HYBRID").uppercase()
            )
        } catch (_: Throwable) {
            HomeData()
        }
    }

    private fun parseItems(arr: org.json.JSONArray?): List<MediaItem> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    MediaItem(
                        id = o.optString("id", i.toString()),
                        title = o.optString("title", o.optString("name", "")),
                        poster = o.optString("poster", o.optString("image", o.optString("stream_icon", ""))),
                        backdrop = o.optString("backdrop", o.optString("cover", "")),
                        description = o.optString("description", o.optString("plot", "")),
                        rating = o.optString("rating", ""),
                        year = o.optString("year", o.optString("releaseDate", "")),
                        section = o.optString("section", "vod"),
                        tmdbId = o.optString("tmdb_id", o.optString("tmdbId", "")),
                        directUrl = o.optString("url", o.optString("stream_url", "")).ifBlank { null },
                        useLocalProxy = o.optBoolean("useLocalProxy", false),
                        extension = o.optString("ext", o.optString("container_extension", "mp4"))
                    )
                )
            }
        }
    }
}
