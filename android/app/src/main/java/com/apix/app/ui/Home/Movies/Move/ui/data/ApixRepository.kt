package com.lagradost.cloudstream3.apix.data

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.AutoDownloadMode
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.InternalAPI
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.apix.app.ui.Home.Movies.Move.Player.PlayerSubtitleHelper
import com.apix.app.ui.Home.Movies.Move.Player.SubtitleData
import com.lagradost.cloudstream3.plugins.support.RepositoryData
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Result of a full playback resolution: every server found across every installed extension. */
data class ApixPlayback(
    val links: List<ExtractorLink>,
    val subtitles: List<SubtitleData>,
)

class ApixRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** TIMPD/TMDB token baked into the APK by CI (GitHub secret); worker value is only a fallback. */
    private fun tmdbToken(fallback: String = ""): String {
        val fromBuild = BuildConfig.APIX_TMDB_TOKEN.trim()
        return if (fromBuild.isNotBlank()) fromBuild else fallback.trim()
    }

    // ---------------------------------------------------------------- catalog

    /**
     * Loads the catalog. The fully-built catalog (worker data + TIMPD sections) is cached
     * on disk, so a cold start within [CACHE_TTL_MS] does **zero** network work — no worker
     * call, no TIMPD call, no extension re-download.
     */
    suspend fun loadCatalog(
        forceRefresh: Boolean = false,
        onProgress: (Float, String) -> Unit = { _, _ -> },
    ): ApixCatalog {
        onProgress(0.05f, "تهيئة")

        val cachedAt = prefs.getLong(KEY_CACHED_AT, 0L)
        val cachedJson = prefs.getString(KEY_CATALOG, null)
        val fresh = !forceRefresh &&
            cachedJson != null &&
            System.currentTimeMillis() - cachedAt < CACHE_TTL_MS

        if (fresh) {
            val cached = runCatching { parseCatalog(cachedJson!!) }.getOrNull()
            if (cached != null && cached.sections.any { it.items.isNotEmpty() }) {
                onProgress(1f, "جاهز (مخزّن)")
                return cached
            }
        }

        // Resolve worker URL: pref → BuildConfig (baked at CI build time) → bundled asset.
        var workerUrl = prefs.getString(KEY_WORKER_URL, DEFAULT_WORKER_URL).orEmpty().trim()
        if (workerUrl.isBlank() && BuildConfig.APIX_WORKER_URL.isNotBlank()) {
            workerUrl = BuildConfig.APIX_WORKER_URL.trim()
            prefs.edit().putString(KEY_WORKER_URL, workerUrl).apply()
        }
        if (workerUrl.isBlank()) {
            workerUrl = runCatching {
                context.assets.open("worker_url.txt").bufferedReader().use { it.readText().trim() }
            }.getOrNull().orEmpty()
            if (workerUrl.isNotBlank()) prefs.edit().putString(KEY_WORKER_URL, workerUrl).apply()
        }

        onProgress(0.20f, "الاتصال بالوركر")
        val remoteJson = if (workerUrl.isNotBlank()) {
            runCatching { fetchAndDecrypt(workerUrl) }.getOrNull()
        } else null
        val remoteCatalog = remoteJson?.takeIf { it.isNotBlank() }
            ?.let { runCatching { parseCatalog(it) }.getOrNull() }

        onProgress(0.45f, "تجهيز الاضافات")
        var catalog = remoteCatalog
            ?: runCatching { cachedJson?.let { parseCatalog(it) } }.getOrNull()
            ?: ApixCatalog(workerUrl = workerUrl)
        if (catalog.workerUrl.isBlank()) catalog = catalog.copy(workerUrl = workerUrl)

        // Keep the known-good repository available until the APiX worker owns the extension list.
        // If the worker already supplies it, do not duplicate it; otherwise append the static source.
        val staticExtension = ApixExtension(
            name = STATIC_PLUGIN_LIST_NAME,
            url = STATIC_PLUGIN_LIST_URL,
            enabled = true,
        )
        if (catalog.extensions.none { it.url.trim() == STATIC_PLUGIN_LIST_URL }) {
            catalog = catalog.copy(extensions = (catalog.extensions + staticExtension).distinctBy { it.url.trim() })
        }

        val token = tmdbToken(catalog.tmdbToken.ifBlank { STATIC_TMDB_TOKEN })
        if ((!catalog.hasDisplayItems() || catalog.categorySections().isEmpty()) && token.isNotBlank()) {
            onProgress(0.65f, "جلب المحتوى من TIMPD")
            val fetched = runCatching { fetchTmdbSections(token, page = 1) }.getOrDefault(emptyList())
            if (fetched.isNotEmpty()) {
                catalog = catalog.copy(
                    sections = fetched,
                    studios = if (catalog.studios.isEmpty()) listOf(
                        ApixStudio("tmdb", "TIMPD", accent = "#D4AF37", overview = "محتوى محدث تلقائياً")
                    ) else catalog.studios,
                    featuredIds = if (catalog.featuredIds.isEmpty())
                        fetched.flatMap { it.items }.take(6).map { it.id } else catalog.featuredIds,
                    tmdbToken = token,
                    hasTmdb = true,
                )
            }
        }

        // Never leak demo/example.com playback URLs to the player — force plugin resolution.
        catalog = catalog.copy(sections = catalog.sections.map { sec ->
            sec.copy(items = sec.items.map { it.stripFakePlayback() })
        })

        onProgress(0.90f, "حفظ الكتالوج")
        prefs.edit()
            .putString(KEY_CATALOG, serializeCatalog(catalog))
            .putLong(KEY_CACHED_AT, System.currentTimeMillis())
            .apply()
        onProgress(1.0f, "جاهز")
        return catalog
    }

    /** Fetches the next TIMPD page for one kind (the "المزيد" button). */
    suspend fun loadMore(sectionId: String, page: Int, catalog: ApixCatalog): List<ApixItem> {
        val token = tmdbToken(catalog.tmdbToken)
        if (token.isBlank()) return emptyList()
        val category = ApixSectionCategory.fromKey(sectionId) ?: return emptyList()
        return runCatching { fetchTmdbSection(token, category, page) }.getOrDefault(emptyList())
    }

    /** Persists a catalog snapshot (used after "load more" / episode enrichment). */
    fun persist(catalog: ApixCatalog) {
        runCatching {
            prefs.edit()
                .putString(KEY_CATALOG, serializeCatalog(catalog))
                .putLong(KEY_CACHED_AT, System.currentTimeMillis())
                .apply()
        }
    }

    private fun ApixItem.stripFakePlayback(): ApixItem {
        val bad = playbackUrl.contains("example.com", ignoreCase = true)
        val cleanedSeasons = seasons.map { s ->
            s.copy(episodes = s.episodes.map { e ->
                if (e.playbackUrl.contains("example.com", true)) e.copy(playbackUrl = "") else e
            })
        }
        return copy(playbackUrl = if (bad) "" else playbackUrl, seasons = cleanedSeasons)
    }

    // ------------------------------------------------------------------ TMDB

    private fun tmdbCall(
        token: String,
        path: String,
        language: String = TMDB_LANG,
    ): JSONObject? = runCatching {
        val isBearer = token.count { it == '.' } >= 2 || token.startsWith("ey")
        val sep = if (path.contains('?')) '&' else '?'
        val safety = "&include_adult=false"
        val extra = if (!isBearer) "${sep}api_key=$token&language=$language$safety"
        else "${sep}language=$language$safety"
        val conn = URL("https://api.themoviedb.org$path$extra").openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 12_000
        if (isBearer) conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        val txt = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        JSONObject(txt)
    }.getOrNull()

    private fun mapTmdb(arr: JSONArray?, kind: ApixKind, prefix: String): List<ApixItem> {
        arr ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optString("title").ifBlank { o.optString("name") }
            if (title.isBlank()) return@mapNotNull null
            if (isBlockedContent(o)) return@mapNotNull null
            val date = o.optString("release_date").ifBlank { o.optString("first_air_date") }
            val poster = o.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
                ?.let { "https://image.tmdb.org/t/p/w500$it" }.orEmpty()
            val backdrop = o.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }
                ?.let { "https://image.tmdb.org/t/p/w780$it" }.orEmpty()
            ApixItem(
                id = "${prefix}_${o.optInt("id")}",
                tmdbId = o.optInt("id"),
                title = title,
                subtitle = when (kind) {
                    ApixKind.MOVIE -> "فيلم"
                    ApixKind.SERIES -> "مسلسل"
                    ApixKind.ANIME -> "أنمي"
                },
                overview = o.optString("overview"),
                posterUrl = poster,
                backdropUrl = backdrop,
                year = if (date.length >= 4) date.substring(0, 4) else "",
                rating = if (o.has("vote_average"))
                    String.format(Locale.US, "%.1f", o.optDouble("vote_average")) else "",
                kind = kind,
                studioId = "tmdb",
                tags = buildList {
                    add("TIMPD")
                    if (o.optJSONArray("genre_ids")?.let { arr -> (0 until arr.length()).any { arr.optInt(it) == 16 } } == true) add("animation")
                },
                originalLanguage = o.optString("original_language"),
            )
        }
    }

    private fun fetchTmdbSection(token: String, category: ApixSectionCategory, page: Int): List<ApixItem> {
        val path = when (category) {
            ApixSectionCategory.MOVIES_ARABIC -> "/3/discover/movie?with_original_language=ar&sort_by=popularity.desc&vote_count.gte=20"
            ApixSectionCategory.MOVIES_FOREIGN -> "/3/discover/movie?without_original_language=ar,ko&sort_by=popularity.desc&vote_count.gte=20"
            ApixSectionCategory.MOVIES_ANIMATION -> "/3/discover/movie?with_genres=16&sort_by=popularity.desc&vote_count.gte=20"
            ApixSectionCategory.MOVIES_KOREAN -> "/3/discover/movie?with_original_language=ko&sort_by=popularity.desc&vote_count.gte=20"
            ApixSectionCategory.SERIES_ARABIC -> "/3/discover/tv?with_original_language=ar&sort_by=popularity.desc&vote_count.gte=20"
            ApixSectionCategory.SERIES_FOREIGN -> "/3/discover/tv?without_original_language=ar,ko&without_genres=$BLOCKED_GENRES,16&sort_by=popularity.desc&vote_count.gte=20"
            ApixSectionCategory.SERIES_KOREAN -> "/3/discover/tv?with_original_language=ko&without_genres=$BLOCKED_GENRES,16&sort_by=popularity.desc&vote_count.gte=20"
            ApixSectionCategory.ANIME_FOREIGN -> "/3/discover/tv?with_genres=16&without_original_language=ja,ko&sort_by=popularity.desc&vote_count.gte=10"
            ApixSectionCategory.ANIME_JAPANESE -> "/3/discover/tv?with_genres=16&with_original_language=ja&sort_by=popularity.desc&vote_count.gte=10"
            ApixSectionCategory.ANIME_KOREAN -> "/3/discover/tv?with_genres=16&with_original_language=ko&sort_by=popularity.desc&vote_count.gte=10"
            ApixSectionCategory.MOVIE_COLLECTIONS -> "/3/discover/movie?sort_by=popularity.desc&vote_count.gte=20"
        } + "&without_genres=$BLOCKED_GENRES&without_keywords=$BLOCKED_KEYWORDS&page=$page"
        val kind = when (category) {
            ApixSectionCategory.MOVIES_ARABIC, ApixSectionCategory.MOVIES_FOREIGN, ApixSectionCategory.MOVIES_ANIMATION, ApixSectionCategory.MOVIES_KOREAN, ApixSectionCategory.MOVIE_COLLECTIONS -> ApixKind.MOVIE
            ApixSectionCategory.SERIES_ARABIC, ApixSectionCategory.SERIES_FOREIGN, ApixSectionCategory.SERIES_KOREAN -> ApixKind.SERIES
            ApixSectionCategory.ANIME_FOREIGN, ApixSectionCategory.ANIME_JAPANESE, ApixSectionCategory.ANIME_KOREAN -> ApixKind.ANIME
        }
        return mapTmdb(tmdbCall(token, path)?.optJSONArray("results"), kind, category.key)
    }

    private fun fetchTmdbKind(token: String, kind: ApixKind, page: Int): List<ApixItem> {
        val categories = when (kind) {
            ApixKind.MOVIE -> listOf(ApixSectionCategory.MOVIES_ARABIC, ApixSectionCategory.MOVIES_FOREIGN, ApixSectionCategory.MOVIES_ANIMATION, ApixSectionCategory.MOVIES_KOREAN)
            ApixKind.SERIES -> listOf(ApixSectionCategory.SERIES_ARABIC, ApixSectionCategory.SERIES_FOREIGN, ApixSectionCategory.SERIES_KOREAN)
            ApixKind.ANIME -> listOf(ApixSectionCategory.ANIME_FOREIGN, ApixSectionCategory.ANIME_JAPANESE, ApixSectionCategory.ANIME_KOREAN)
        }
        return categories.flatMap { fetchTmdbSection(token, it, page) }
    }

    private fun fetchTmdbSections(token: String, page: Int): List<ApixSection> = buildList {
        val categories = listOf(
            ApixSectionCategory.MOVIES_ARABIC, ApixSectionCategory.MOVIES_FOREIGN, ApixSectionCategory.MOVIES_ANIMATION, ApixSectionCategory.MOVIES_KOREAN,
            ApixSectionCategory.SERIES_ARABIC, ApixSectionCategory.SERIES_FOREIGN, ApixSectionCategory.SERIES_KOREAN,
            ApixSectionCategory.ANIME_FOREIGN, ApixSectionCategory.ANIME_JAPANESE, ApixSectionCategory.ANIME_KOREAN,
        )
        categories.forEach { category ->
            val items = fetchTmdbSection(token, category, page)
            if (items.isNotEmpty()) add(ApixSection(category.key, category.title, when (category) {
                ApixSectionCategory.MOVIES_ARABIC, ApixSectionCategory.MOVIES_FOREIGN, ApixSectionCategory.MOVIES_ANIMATION, ApixSectionCategory.MOVIES_KOREAN -> ApixKind.MOVIE
                ApixSectionCategory.SERIES_ARABIC, ApixSectionCategory.SERIES_FOREIGN, ApixSectionCategory.SERIES_KOREAN -> ApixKind.SERIES
                else -> ApixKind.ANIME
            }, items))
        }
        val collections = fetchTmdbSection(token, ApixSectionCategory.MOVIE_COLLECTIONS, page)
        if (collections.isNotEmpty()) add(ApixSection(ApixSectionCategory.MOVIE_COLLECTIONS.key, ApixSectionCategory.MOVIE_COLLECTIONS.title, ApixKind.MOVIE, collections))
    }

    /**
     * Full TIMPD search (multi endpoint) used by the dedicated search screen.
     * Returns items of all kinds, the screen groups them by [ApixKind].
     */
    suspend fun searchContent(query: String, page: Int, catalog: ApixCatalog): List<ApixItem> {
        val token = tmdbToken(catalog.tmdbToken)
        if (token.isBlank() || query.isBlank()) return emptyList()
        val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val base = "/3/search/multi?query=$encoded&page=$page"

        // TIMPD matches alternative titles, but only returns them in the requested language.
        // We ask twice (English + Arabic) so an English query still surfaces an Arabic-named
        // work — and every item keeps BOTH titles for the extension search.
        val english = collectSearchItems(tmdbCall(token, base, TMDB_LANG))
        val arabic = collectSearchItems(tmdbCall(token, base, "ar-SA"))

        val arabicByTmdb = arabic.associateBy { it.tmdbId }
        val englishByTmdb = english.associateBy { it.tmdbId }

        val merged = LinkedHashMap<String, ApixItem>()
        english.forEach { item ->
            val other = arabicByTmdb[item.tmdbId]?.title.orEmpty()
            merged[item.id] = item.copy(
                altTitles = listOfNotNull(other.takeIf { it.isNotBlank() && !it.equals(item.title, true) })
            )
        }
        arabic.forEach { item ->
            val existing = merged[item.id]
            if (existing == null) {
                val other = englishByTmdb[item.tmdbId]?.title.orEmpty()
                merged[item.id] = item.copy(
                    altTitles = listOfNotNull(other.takeIf { it.isNotBlank() && !it.equals(item.title, true) })
                )
            } else if (!item.title.equals(existing.title, true) &&
                item.title !in existing.altTitles
            ) {
                merged[item.id] = existing.copy(altTitles = existing.altTitles + item.title)
            }
        }
        return merged.values.toList()
    }

    /** Splits a /search/multi payload into movie / series / anime items. */
    private fun collectSearchItems(root: JSONObject?): List<ApixItem> {
        val arr = root?.optJSONArray("results") ?: return emptyList()
        val movies = JSONArray()
        val series = JSONArray()
        val anime = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            when (o.optString("media_type")) {
                "movie" -> movies.put(o)
                "tv" -> {
                    val genres = o.optJSONArray("genre_ids")
                    val isAnime = (0 until (genres?.length() ?: 0)).any { genres?.optInt(it) == 16 }
                    if (isAnime) anime.put(o) else series.put(o)
                }
            }
        }
        return mapTmdb(movies, ApixKind.MOVIE, "movie") +
            mapTmdb(series, ApixKind.SERIES, "series") +
            mapTmdb(anime, ApixKind.ANIME, "anime")
    }

    /** Loads seasons + episodes for a TIMPD series/anime item. */
    suspend fun fetchSeasons(item: ApixItem, catalog: ApixCatalog): List<ApixSeason> {
        if (item.kind == ApixKind.MOVIE) return emptyList()
        val token = tmdbToken(catalog.tmdbToken)
        val id = item.tmdbId.takeIf { it > 0 }
            ?: item.id.substringAfterLast('_').toIntOrNull()
            ?: return emptyList()
        if (token.isBlank()) return emptyList()

        val detail = tmdbCall(token, "/3/tv/$id") ?: return emptyList()
        val seasonsArr = detail.optJSONArray("seasons") ?: return emptyList()
        val numbers = (0 until seasonsArr.length())
            .mapNotNull { seasonsArr.optJSONObject(it)?.optInt("season_number", -1) }
            .filter { it >= 0 }
            .sorted()
            .take(12)

        return numbers.mapNotNull { n ->
            val s = tmdbCall(token, "/3/tv/$id/season/$n") ?: return@mapNotNull null
            val eps = s.optJSONArray("episodes").orEmpty().objList { e ->
                ApixEpisode(
                    id = "${item.id}_s${n}e${e.optInt("episode_number")}",
                    number = e.optInt("episode_number", 1),
                    season = n,
                    title = e.optString("name").ifBlank { "الحلقة ${e.optInt("episode_number")}" },
                    duration = e.optInt("runtime", 0).takeIf { it > 0 }?.let { "$it دقيقة" }.orEmpty(),
                    overview = e.optString("overview"),
                    stillUrl = e.optString("still_path").takeIf { it.isNotBlank() && it != "null" }
                        ?.let { "https://image.tmdb.org/t/p/w300$it" }.orEmpty(),
                )
            }
            if (eps.isEmpty()) null
            else ApixSeason(number = n, title = s.optString("name").ifBlank { "الموسم $n" }, episodes = eps)
        }
    }

    // ------------------------------------------------------------ extensions

    /** Number of extensions currently downloaded on disk. */
    fun installedExtensionCount(): Int =
        runCatching { PluginManager.getPluginsOnline().size }.getOrDefault(0)

    /** Number of providers (sources) currently registered by loaded extensions. */
    fun loadedProviderCount(): Int =
        runCatching { APIHolder.allProviders.distinctBy { it.name + it.mainUrl }.size }.getOrDefault(0)

    /** True when the extension list already matches what we downloaded before AND files exist. */
    fun extensionsUpToDate(catalog: ApixCatalog): Boolean {
        val sig = catalog.extensions.filter { it.enabled }.joinToString("|") { it.url }.hashCode().toString()
        val savedSig = prefs.getString(KEY_EXT_SIG, null)
        val savedAt = prefs.getLong(KEY_EXT_AT, 0L)
        val cachedOk = sig == savedSig && System.currentTimeMillis() - savedAt < EXT_TTL_MS
        // If nothing is actually installed we must download again, no matter the signature.
        return cachedOk && installedExtensionCount() > 0
    }

    /**
     * Registers every repo from the worker, downloads missing plugins and loads them.
     * @return the number of providers available after the sync.
     */
    @OptIn(InternalAPI::class)
    suspend fun syncExtensions(activity: Activity, catalog: ApixCatalog, force: Boolean = false): Int {
        // Cheap: register whatever is already on disk first so we can tell if anything is loaded.
        runCatching { PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_loadAllOnlinePlugins(activity) }
        // Cache is only trusted when it actually produced providers.
        val skipDownload = !force && extensionsUpToDate(catalog) && loadedProviderCount() > 0
        if (!skipDownload) {

            catalog.extensions
                .filter { it.enabled && it.url.isNotBlank() }
                .forEach { extension ->
                    runCatching {
                        val parsedUrl = RepositoryManager.parseRepoUrl(extension.url) ?: extension.url
                        RepositoryManager.addRepository(
                            RepositoryData(extension.name.ifBlank { parsedUrl }, parsedUrl)
                        )
                    }.onFailure { Log.d(TAG, "repo ${extension.url} failed: ${it.message}") }
                }
            runCatching {
                PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_downloadNotExistingPluginsAndLoad(
                    activity, AutoDownloadMode.All,
                )
            }.onFailure { Log.d(TAG, "download plugins failed: ${it.message}") }
            val sig = catalog.extensions.filter { it.enabled }
                .joinToString("|") { it.url }.hashCode().toString()
            prefs.edit()
                .putString(KEY_EXT_SIG, sig)
                .putLong(KEY_EXT_AT, System.currentTimeMillis())
                .apply()
        }
        // Always load what is already on disk (cheap, no network).
        runCatching { PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_loadAllOnlinePlugins(activity) }
        return loadedProviderCount()
    }

    // ------------------------------------------------------------- playback

    /** Number of "fast lane" providers searched before the player starts playing. */
    private val FAST_LANE = 3

    /**
     * Streaming resolution used by the player.
     *
     * Phase 1 asks the [FAST_LANE] providers that historically returned the best quality and
     * pushes their servers/subtitles immediately (best quality first, Arabic subtitles first),
     * so playback starts within seconds. Phase 2 keeps searching every remaining extension in
     * the background and appends whatever it finds, still ordered by quality.
     *
     * Download-only mirrors are never emitted.
     */
    suspend fun resolvePlaybackStreaming(
        item: ApixItem,
        season: Int? = null,
        episode: Int? = null,
        onLink: (ExtractorLink) -> Unit,
        onSub: (SubtitleData) -> Unit,
        onProgress: (String) -> Unit = {},
    ) {
        val seenLinks = Collections.synchronizedSet(mutableSetOf<String>())
        val seenSubs = Collections.synchronizedSet(mutableSetOf<String>())

        val all = APIHolder.allProviders.distinctBy { it.name + it.mainUrl }
        val matching = all.filter { api -> api.supportedTypes.any { it in item.targetTypes() } }
        val providers = matching.ifEmpty { all }
        if (providers.isEmpty()) return

        val ranked = providers.sortedByDescending { ApixLinkPolicy.providerScore(context, it.name) }
        val searchItem = buildSearchItem(item)
        val queries = buildQueries(item, searchItem)

        val done = java.util.concurrent.atomic.AtomicInteger(0)
        val found = java.util.concurrent.atomic.AtomicInteger(0)
        val total = ranked.size

        suspend fun run(api: MainAPI) {
            // buffer per provider so we can emit best-quality-first instead of arrival order
            val buffer = Collections.synchronizedList(mutableListOf<ExtractorLink>())
            val subBuffer = Collections.synchronizedList(mutableListOf<SubtitleData>())
            runCatching {
                collectFromProvider(api, queries, searchItem, season, episode,
                    onLink = { link ->
                        if (link.url.isNotBlank() &&
                            !ApixLinkPolicy.isDownloadOnly(link) &&
                            seenLinks.add(link.url)
                        ) buffer.add(link)
                    },
                    onSub = { sub -> if (sub.url.isNotBlank() && seenSubs.add(sub.url)) subBuffer.add(sub) },
                )
            }.onFailure { Log.d(TAG, "provider ${api.name} failed: ${it.message}") }

            // Arabic subtitles first so the player auto-selects one, then the rest.
            subBuffer.sortedByDescending { ApixLinkPolicy.isArabic(it) }.forEach(onSub)
            val sorted = buffer.sortedByDescending { ApixLinkPolicy.score(it) }
            sorted.forEach { onLink(it) }
            if (sorted.isNotEmpty()) {
                ApixLinkPolicy.rememberProvider(context, api.name, sorted.first().quality)
                found.addAndGet(sorted.size)
            }
            onProgress("${done.incrementAndGet()}/$total مصدر — ${found.get()} سيرفر")
        }

        // phase 1 — best known providers, playback starts from these
        ranked.take(FAST_LANE).amap { run(it) }
        // phase 2 — everything else, in the background while the video is already playing
        ranked.drop(FAST_LANE).amap { run(it) }
    }

    private fun buildSearchItem(item: ApixItem): ApixItem {
        val extraTitles = if (item.altTitles.isEmpty()) fetchAltTitles(item) else item.altTitles
        val titles = (listOf(item.title) + extraTitles + item.altTitles)
            .map { it.trim() }.filter { it.isNotBlank() }.distinct()
        return item.copy(altTitles = titles.drop(1))
    }

    /**
     * Queries always describe the **work** (movie / series), never a single episode title —
     * the season/episode is picked afterwards from the loaded page.
     */
    private fun buildQueries(item: ApixItem, searchItem: ApixItem): List<String> {
        val titles = listOf(searchItem.title) + searchItem.altTitles
        return (
            titles.map { listOf(it, item.year).filter { p -> p.isNotBlank() }.joinToString(" ") } + titles
            ).map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    /**
     * Searches **every** installed extension in parallel and returns every server it can find,
     * so the player's server list is populated instead of a single link.
     */
    suspend fun resolvePlayback(
        item: ApixItem,
        season: Int? = null,
        episode: Int? = null,
        onProgress: (String) -> Unit = {},
    ): ApixPlayback {
        val links = Collections.synchronizedList(mutableListOf<ExtractorLink>())
        val subs = Collections.synchronizedList(mutableListOf<SubtitleData>())
        val seenLinks = Collections.synchronizedSet(mutableSetOf<String>())
        val seenSubs = Collections.synchronizedSet(mutableSetOf<String>())

        val all = APIHolder.allProviders.distinctBy { it.name + it.mainUrl }
        val matching = all.filter { api -> api.supportedTypes.any { it in item.targetTypes() } }
        // Some providers declare no/odd types — fall back to every provider instead of failing.
        val providers = matching.ifEmpty { all }

        if (providers.isEmpty()) return ApixPlayback(emptyList(), emptyList())

        // Catalog items only carry their English title — pull the Arabic/original ones once
        // so extensions that list the work under its Arabic name are still matched.
        val extraTitles = if (item.altTitles.isEmpty()) fetchAltTitles(item) else item.altTitles

        // Search every known title of the work (English + Arabic/original) so a site that
        // only lists the Arabic name is still matched by an English query, and vice versa.
        val titles = (listOf(item.title) + extraTitles + item.altTitles)
            .map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val queries = (
            titles.map { listOf(it, item.year).filter { p -> p.isNotBlank() }.joinToString(" ") } + titles
            ).map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val searchItem = item.copy(altTitles = titles.drop(1))


        val done = java.util.concurrent.atomic.AtomicInteger(0)
        val total = providers.size
        providers.amap { api ->
            runCatching {
                collectFromProvider(api, queries, searchItem, season, episode,
                    onLink = { link ->
                        if (link.url.isNotBlank() && seenLinks.add(link.url)) {
                            links.add(link)
                            onProgress("${api.name} — ${links.size} سيرفر")
                        }
                    },
                    onSub = { sub ->
                        if (sub.url.isNotBlank() && seenSubs.add(sub.url)) subs.add(sub)
                    })
            }.onFailure { Log.d(TAG, "provider ${api.name} failed: ${it.message}") }
            val finished = done.incrementAndGet()
            onProgress("$finished/$total مصدر — ${links.size} سيرفر")
        }

        return ApixPlayback(
            links.sortedByDescending { it.quality },
            subs.toList(),
        )
    }

    /** Arabic + original-language titles for a TIMPD item (used for extension search only). */
    private fun fetchAltTitles(item: ApixItem): List<String> {
        val token = tmdbToken()
        val id = item.tmdbId.takeIf { it > 0 }
            ?: item.id.substringAfterLast('_').toIntOrNull() ?: return emptyList()
        if (token.isBlank()) return emptyList()
        val path = if (item.kind == ApixKind.MOVIE) "/3/movie/$id" else "/3/tv/$id"
        val out = mutableListOf<String>()
        tmdbCall(token, path, "ar-SA")?.let { o ->
            out += o.optString("title").ifBlank { o.optString("name") }
        }
        tmdbCall(token, path, TMDB_LANG)?.let { o ->
            out += o.optString("original_title").ifBlank { o.optString("original_name") }
        }
        return out.map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(item.title, true) }
            .distinct()
    }

    private fun ApixItem.targetTypes(): Set<TvType> = when (kind) {
        ApixKind.MOVIE -> setOf(TvType.Movie, TvType.AnimeMovie)
        ApixKind.SERIES -> setOf(TvType.TvSeries, TvType.AsianDrama)
        ApixKind.ANIME -> setOf(TvType.Anime, TvType.OVA, TvType.AnimeMovie)
    }

    private suspend fun collectFromProvider(
        api: MainAPI,
        queries: List<String>,
        item: ApixItem,
        season: Int?,
        episode: Int?,
        onLink: (ExtractorLink) -> Unit,
        onSub: (SubtitleData) -> Unit,
    ) {
        val raw = ArrayList<SearchResponse>()
        for (q in queries) {
            val found = runCatching { withTimeoutOrNull(SEARCH_TIMEOUT) { api.search(q) } }.getOrNull()
            if (found != null) raw.addAll(found)
        }
        val results = raw
            .distinctBy { it.url }
            .filter { response ->
                val name = response.name.lowercase(Locale.ROOT)
                val known = (listOf(item.title) + item.altTitles)
                    .map { it.lowercase(Locale.ROOT).trim() }.filter { it.isNotBlank() }
                known.any { title ->
                    name.contains(title) || title.contains(name) || name.similarTo(title) > 0.6
                }
            }
            .take(4)

        for (result in results) {
            val loaded = runCatching { withTimeoutOrNull(LOAD_TIMEOUT) { api.load(result.url) } }
                .getOrNull() ?: continue
            val candidates = playbackDataCandidates(loaded, season, episode).take(2)
            for (data in candidates) {
                if (data.isBlank()) continue
                runCatching {
                    withTimeoutOrNull(LINK_TIMEOUT) {
                        api.loadLinks(
                            data, false,
                            { file ->
                                val sub = PlayerSubtitleHelper.getSubtitleData(file)
                                if (sub.isAllowedPlaybackSubtitle()) onSub(sub)
                            },
                            { link -> onLink(link) },
                        )
                    }
                }
            }
        }
    }

    private fun playbackDataCandidates(
        response: LoadResponse,
        season: Int?,
        episode: Int?,
    ): List<String> = when (response) {
        is MovieLoadResponse -> listOf(response.dataUrl)
        is TvSeriesLoadResponse -> response.episodes.pick(season, episode)
        is AnimeLoadResponse -> response.episodes[DubStatus.Subbed]
            .orEmpty()
            .ifEmpty { response.episodes.values.flatten() }
            .pick(season, episode)
        else -> listOf(response.url)
    }

    private fun <T> List<T>.pick(season: Int?, episode: Int?): List<String> {
        @Suppress("UNCHECKED_CAST")
        val eps = this as List<com.lagradost.cloudstream3.Episode>
        val exact = eps.filter { e ->
            (episode == null || e.episode == episode) &&
                (season == null || e.season == null || e.season == season)
        }
        return (exact.ifEmpty { eps })
            .sortedWith(compareBy({ it.season ?: Int.MAX_VALUE }, { it.episode ?: Int.MAX_VALUE }))
            .map { it.data }
    }

    // ------------------------------------------------------------- settings

    fun readFavorites(): Set<String> = prefs.getString(KEY_FAVORITES, null)
        ?.let { runCatching { parseStringSet(it) }.getOrElse { emptySet() } }
        .orEmpty()

    fun toggleFavorite(id: String): Set<String> {
        val current = readFavorites().toMutableSet()
        if (!current.add(id)) current.remove(id)
        prefs.edit().putString(KEY_FAVORITES, encodeStringSet(current)).apply()
        return current
    }

    fun getLanguage(): String = prefs.getString(KEY_LANGUAGE, "ar") ?: "ar"
    fun setLanguage(value: String) { prefs.edit().putString(KEY_LANGUAGE, value).apply() }
    fun getDownloadsEnabled(): Boolean = prefs.getBoolean(KEY_DOWNLOADS, true)
    fun setDownloadsEnabled(value: Boolean) { prefs.edit().putBoolean(KEY_DOWNLOADS, value).apply() }
    fun getBypassIsp(): Boolean = prefs.getBoolean(KEY_BYPASS_ISP, false)
    fun setBypassIsp(value: Boolean) { prefs.edit().putBoolean(KEY_BYPASS_ISP, value).apply() }
    fun getWorkerUrl(): String = prefs.getString(KEY_WORKER_URL, DEFAULT_WORKER_URL).orEmpty()
    fun setWorkerUrl(value: String) { prefs.edit().putString(KEY_WORKER_URL, value).apply() }

    // --------------------------------------------------------- network/crypto

    private fun fetchText(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", "application/json,text/plain,*/*")
        if (BuildConfig.APIX_HMAC_SECRET.isNotBlank()) {
            conn.setRequestProperty("X-Apix-Auth", BuildConfig.APIX_HMAC_SECRET)
        }
        conn.connect()
        val input = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        BufferedReader(InputStreamReader(input)).use { return it.readText() }
    }

    private fun fetchAndDecrypt(url: String): String {
        val raw = fetchText(url)
        val trimmed = raw.trimStart()
        if (!trimmed.startsWith("{")) return raw
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return raw
        val enc = obj.optString("enc", "")
        if (enc.isBlank()) return raw
        val keyHex = BuildConfig.APIX_ENCRYPTION_KEY.trim()
        require(keyHex.isNotBlank()) { "Missing APIX_ENCRYPTION_KEY" }
        return decryptGcm(enc, keyHex, BuildConfig.APIX_KEY_SALT)
    }

    private fun ApixCatalog.hasDisplayItems(): Boolean = sections.any { it.items.isNotEmpty() }

    private fun decryptGcm(b64: String, keyHex: String, salt: String): String {
        val blob = Base64.decode(b64, Base64.DEFAULT)
        require(blob.size > 12) { "ciphertext too short" }
        val iv = blob.copyOfRange(0, 12)
        val ct = blob.copyOfRange(12, blob.size)
        val md = MessageDigest.getInstance("SHA-256")
        md.update(hexDecode(keyHex))
        md.update(salt.toByteArray(Charsets.UTF_8))
        val finalKey = md.digest()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(finalKey, "AES"), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    private fun hexDecode(s: String): ByteArray {
        val clean = s.trim()
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            out[i] = ((Character.digit(clean[i * 2], 16) shl 4) +
                Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
        return out
    }

    // ---------------------------------------------------------- (de)serialize

    private fun parseCatalog(raw: String): ApixCatalog {
        val root = JSONObject(raw)
        val studios = root.optJSONArray("studios").orEmpty().objList { obj ->
            ApixStudio(
                id = obj.optString("id"),
                name = obj.optString("name"),
                logo = obj.optString("logo"),
                accent = obj.optString("accent", "#FFD700"),
                overview = obj.optString("overview"),
            )
        }
        val sections = root.optJSONArray("sections").orEmpty().objList { obj ->
            ApixSection(
                id = obj.optString("id"),
                title = obj.optString("title"),
                kind = obj.optString("kind", "MOVIE").toKind(),
                items = obj.optJSONArray("items").orEmpty().objList { parseItem(it) },
            )
        }
        val tmdbObject = root.optJSONObject("tmdb")
        return ApixCatalog(
            updatedAt = root.optString("updatedAt"),
            workerUrl = root.optString("workerUrl"),
            featuredIds = root.optJSONArray("featuredIds").orEmpty().stringList(),
            studios = studios,
            sections = sections,
            extensions = root.optJSONArray("extensions").orEmpty().objList { obj ->
                ApixExtension(
                    name = obj.optString("name"),
                    url = obj.optString("url"),
                    enabled = obj.optBoolean("enabled", true),
                )
            },
            hasTmdb = tmdbObject?.optBoolean("hasToken", false) ?: root.optBoolean("hasTmdb", false),
            tmdbToken = tmdbObject?.optString("token").orEmpty(),
            pages = root.optJSONObject("pages")?.let { p ->
                buildMap {
                    ApixSectionCategory.entries.forEach { category ->
                        val value = p.optInt(category.key, 0)
                        if (value > 0) put(category.key, value)
                    }
                }
            } ?: emptyMap(),
        )
    }

    private fun parseItem(obj: JSONObject): ApixItem {
        val seasons = obj.optJSONArray("seasons").orEmpty().objList { season ->
            ApixSeason(
                number = season.optInt("number", 1),
                title = season.optString("title"),
                episodes = season.optJSONArray("episodes").orEmpty().objList { ep ->
                    ApixEpisode(
                        id = ep.optString("id"),
                        number = ep.optInt("number", 1),
                        season = ep.optInt("season", season.optInt("number", 1)),
                        title = ep.optString("title"),
                        duration = ep.optString("duration"),
                        overview = ep.optString("overview"),
                        stillUrl = ep.optString("stillUrl"),
                        playbackUrl = ep.optString("playbackUrl"),
                    )
                },
            )
        }
        return ApixItem(
            id = obj.optString("id"),
            tmdbId = obj.optInt("tmdbId", 0),
            title = obj.optString("title"),
            subtitle = obj.optString("subtitle"),
            overview = obj.optString("overview"),
            posterUrl = obj.optString("posterUrl"),
            backdropUrl = obj.optString("backdropUrl"),
            year = obj.optString("year"),
            rating = obj.optString("rating"),
            duration = obj.optString("duration"),
            genre = obj.optString("genre"),
            studioId = obj.optString("studioId"),
            kind = obj.optString("kind", "MOVIE").toKind(),
            tags = obj.optJSONArray("tags").orEmpty().stringList(),
            altTitles = obj.optJSONArray("altTitles").orEmpty().stringList(),
            similarIds = obj.optJSONArray("similarIds").orEmpty().stringList(),
            playbackUrl = obj.optString("playbackUrl"),
            seasons = seasons,
            trailerQuery = obj.optString("trailerQuery").takeIf { it.isNotBlank() },
            originalLanguage = obj.optString("originalLanguage"),
        )
    }

    private fun serializeCatalog(catalog: ApixCatalog): String = JSONObject().apply {
        put("updatedAt", catalog.updatedAt)
        put("workerUrl", catalog.workerUrl)
        put("hasTmdb", catalog.hasTmdb)
        put("featuredIds", JSONArray(catalog.featuredIds))
        put("pages", JSONObject().apply { catalog.pages.forEach { (k, v) -> put(k, v) } })
        put("studios", JSONArray().apply {
            catalog.studios.forEach {
                put(JSONObject().apply {
                    put("id", it.id); put("name", it.name); put("logo", it.logo)
                    put("accent", it.accent); put("overview", it.overview)
                })
            }
        })
        put("extensions", JSONArray().apply {
            catalog.extensions.forEach {
                put(JSONObject().apply {
                    put("name", it.name); put("url", it.url); put("enabled", it.enabled)
                })
            }
        })
        put("sections", JSONArray().apply {
            catalog.sections.forEach { sec ->
                put(JSONObject().apply {
                    put("id", sec.id); put("title", sec.title); put("kind", sec.kind.name)
                    put("items", JSONArray().apply { sec.items.forEach { put(serializeItem(it)) } })
                })
            }
        })
    }.toString()

    private fun serializeItem(item: ApixItem): JSONObject = JSONObject().apply {
        put("id", item.id); put("tmdbId", item.tmdbId); put("title", item.title)
        put("subtitle", item.subtitle); put("overview", item.overview)
        put("posterUrl", item.posterUrl); put("backdropUrl", item.backdropUrl)
        put("year", item.year); put("rating", item.rating); put("duration", item.duration)
        put("genre", item.genre); put("studioId", item.studioId); put("kind", item.kind.name)
        put("tags", JSONArray(item.tags)); put("similarIds", JSONArray(item.similarIds))
        put("altTitles", JSONArray(item.altTitles))
        put("playbackUrl", item.playbackUrl)
        put("originalLanguage", item.originalLanguage)
        put("seasons", JSONArray().apply {
            item.seasons.forEach { s ->
                put(JSONObject().apply {
                    put("number", s.number); put("title", s.title)
                    put("episodes", JSONArray().apply {
                        s.episodes.forEach { e ->
                            put(JSONObject().apply {
                                put("id", e.id); put("number", e.number); put("season", e.season)
                                put("title", e.title); put("duration", e.duration)
                                put("overview", e.overview); put("stillUrl", e.stillUrl)
                                put("playbackUrl", e.playbackUrl)
                            })
                        }
                    })
                })
            }
        })
    }

    private fun parseStringSet(raw: String): Set<String> =
        JSONArray(raw).let { arr -> buildSet { for (i in 0 until arr.length()) add(arr.optString(i)) } }

    private fun encodeStringSet(set: Set<String>): String = JSONArray(set.toList()).toString()

    /** True when the TIMPD entry is adult / otherwise unwanted content. */
    private fun isBlockedContent(o: JSONObject): Boolean {
        if (o.optBoolean("adult", false)) return true
        val genres = o.optJSONArray("genre_ids")
        val genreIds = (0 until (genres?.length() ?: 0)).map { genres!!.optInt(it) }
        // 10402 = Music, 99 = Documentary — kept out of the main catalog
        if (genreIds.any { it == BLOCKED_GENRE_DOC || it == BLOCKED_GENRE_MUSIC }) return true
        val text = (o.optString("title") + " " + o.optString("name") + " " +
            o.optString("original_title") + " " + o.optString("original_name") + " " +
            o.optString("overview")).lowercase(Locale.ROOT)
        return BLOCKED_WORDS.any { text.contains(it) }
    }

    companion object {
        private const val TAG = "ApixRepository"

        /** All TIMPD metadata is fetched in English only. */
        private const val TMDB_LANG = "en-US"

        private const val STATIC_PLUGIN_LIST_URL =
            "https://raw.githubusercontent.com/Abodabodd/re-3arabi/refs/heads/main/plugins.json"
        private const val STATIC_PLUGIN_LIST_NAME = "Abodabodd Arabic Extensions"
        private const val STATIC_TMDB_TOKEN = "17e13ed1cd1d260680b35ec2e61259db"

        /** Documentary (99) + Music (10402) are filtered out of the browse rows. */
        private const val BLOCKED_GENRE_DOC = 99
        private const val BLOCKED_GENRE_MUSIC = 10402
        private const val BLOCKED_GENRES = "99,10402"

        /**
         * TIMPD keyword ids removed from every browse request:
         * 190370 gay, 158718 lgbt, 258067 lesbian, 210024 (anime-adult), 13141 softcore,
         * 190043 erotic movie, 155477 erotic, 230416 sex scene, 6270 pornography.
         */
        private const val BLOCKED_KEYWORDS =
            "190370,158718,258067,13141,190043,155477,230416,6270"

        /** Extra text guard for anything the keyword filter misses. */
        private val BLOCKED_WORDS = listOf(
            "porn", "erotic", "softcore", "hentai", "xxx", "sex scene",
            "gay ", "lesbian", "lgbt", "transgender", "queer", "drag queen",
        )
    }

}

private fun String.toKind(): ApixKind =
    runCatching { ApixKind.valueOf(uppercase(Locale.ROOT)) }.getOrDefault(ApixKind.MOVIE)

/** Very small token-overlap similarity, enough to accept "Dune Part Two" vs "Dune: Part Two". */
private fun String.similarTo(other: String): Double {
    val a = split(' ', ':', '-', '.').filter { it.length > 2 }.toSet()
    val b = other.split(' ', ':', '-', '.').filter { it.length > 2 }.toSet()
    if (a.isEmpty() || b.isEmpty()) return 0.0
    return a.intersect(b).size.toDouble() / minOf(a.size, b.size)
}

private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()
private fun <T> JSONArray.objList(block: (JSONObject) -> T): List<T> =
    buildList { for (i in 0 until length()) add(block(optJSONObject(i) ?: JSONObject())) }
private fun JSONArray.stringList(): List<String> =
    buildList { for (i in 0 until length()) add(optString(i)) }

private const val PREFS = "apix_ui"
private const val KEY_CATALOG = "cache_catalog"
private const val KEY_CACHED_AT = "cache_at"
private const val KEY_EXT_SIG = "ext_signature"
private const val KEY_EXT_AT = "ext_synced_at"
private const val KEY_FAVORITES = "favorites"
private const val KEY_LANGUAGE = "language"
private const val KEY_DOWNLOADS = "downloads"
private const val KEY_BYPASS_ISP = "bypass_isp"
private const val KEY_WORKER_URL = "worker_url"
private const val DEFAULT_WORKER_URL = ""

private const val CACHE_TTL_MS = 6L * 60 * 60 * 1000      // 6 hours
private const val EXT_TTL_MS = 24L * 60 * 60 * 1000       // 1 day
private const val SEARCH_TIMEOUT = 10_000L
private const val LOAD_TIMEOUT = 12_000L
private const val LINK_TIMEOUT = 15_000L
