package com.apix.app.data

/**
 * Cinema / VOD domain models.
 *
 * The app NEVER talks to Supabase or an IPTV origin directly. Everything is
 * fetched through the Cloudflare Worker(s):
 *   • the live-TV/data gateway (WORKER_URL)  → categories/channels
 *   • the dedicated cinema worker            → movies/series catalog & resolve
 *   • an optional client "external source"   → a raw JSON feed (e.g. GitHub Raw)
 *
 * All three converge into [HomeData], which the Compose Home screen renders.
 */

/** A single poster item (movie, series, or live entry presented as a poster). */
data class MediaItem(
    val id: String = "",
    val title: String = "",
    val poster: String = "",
    val backdrop: String = "",
    val description: String = "",
    val rating: String = "",
    val year: String = "",
    // "vod" | "series" | "live"
    val section: String = "vod",
    // Optional direct stream URL (when the feed already supplies one).
    val directUrl: String? = null,
    // Optional pre-supplied headers/proxy hint for direct feeds.
    val useLocalProxy: Boolean = false,
    val extension: String = "mp4"
)

/** A horizontal row of items (TvLazyRow). */
data class HomeRow(
    val id: String = "",
    val title: String = "",
    val items: List<MediaItem> = emptyList()
)

/** The whole Home payload: a hero carousel + ordered rows. */
data class HomeData(
    val hero: List<MediaItem> = emptyList(),
    val rows: List<HomeRow> = emptyList()
) {
    val isEmpty: Boolean get() = hero.isEmpty() && rows.isEmpty()
}

/**
 * Parses a client-provided "external source" JSON feed into [HomeData].
 *
 * Supported shape (see template.json downloadable from the panel):
 * {
 *   "hero": [ { "id","title","poster","backdrop","description","rating","year",
 *               "section","url","useLocalProxy","ext" }, ... ],
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
            HomeData(hero = hero, rows = rows)
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
                        directUrl = o.optString("url", o.optString("stream_url", "")).ifBlank { null },
                        useLocalProxy = o.optBoolean("useLocalProxy", false),
                        extension = o.optString("ext", o.optString("container_extension", "mp4"))
                    )
                )
            }
        }
    }
}
