package com.lagradost.cloudstream3.apix.data

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ApixViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ApixRepository(app.applicationContext)
    var state = androidx.compose.runtime.mutableStateOf(ApixUiState())
        private set

    init { reload() }

    fun reload(force: Boolean = false) {
        viewModelScope.launch {
            state.value = state.value.copy(
                loading = true, error = null,
                booting = true, bootProgress = 0.02f, bootLabel = "بدء التحميل",
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    repo.loadCatalog(forceRefresh = force) { p, label ->
                        state.value = state.value.copy(bootProgress = p.coerceIn(0f, 1f), bootLabel = label)
                    }
                }
            }.onSuccess { catalog ->
                // Only keep booting when extensions actually still need to be downloaded.
                val needsExtensions = catalog.extensions.any { it.enabled } &&
                    !repo.extensionsUpToDate(catalog)
                state.value = state.value.copy(
                    loading = false,
                    catalog = catalog,
                    favorites = repo.readFavorites(),
                    workerSyncedAt = System.currentTimeMillis(),
                    workerStatus = if (catalog.workerUrl.isNotBlank()) "متصل بالوركر" else "تحميل محلي",
                    language = repo.getLanguage(),
                    downloadsEnabled = repo.getDownloadsEnabled(),
                    bypassIsp = repo.getBypassIsp(),
                    bootLabel = if (needsExtensions) "تحميل الاضافات" else "جاهز",
                    bootProgress = if (needsExtensions) 0.92f else 1f,
                    booting = needsExtensions,
                )
            }.onFailure { err ->
                state.value = state.value.copy(
                    loading = false, booting = false, bootProgress = 1f,
                    error = err.message ?: "فشل تحميل البيانات",
                )
            }
        }
    }

    /**
     * Loads extensions. When the same extension list was already downloaded recently this only
     * re-registers the plugins already on disk — no re-download on every app open.
     */
    fun syncExtensions(activity: Activity, force: Boolean = false) {
        val catalog = state.value.catalog
        if (catalog.extensions.none { it.enabled }) {
            state.value = state.value.copy(booting = false, bootProgress = 1f, bootLabel = "لا توجد إضافات")
            return
        }
        val cached = repo.extensionsUpToDate(catalog) && !force
        viewModelScope.launch {
            state.value = state.value.copy(
                booting = true,
                bootLabel = if (cached) "تحميل الاضافات المخزنة" else "تنزيل الاضافات (${catalog.extensions.size})",
                bootProgress = 0.95f,
            )
            runCatching {
                withContext(Dispatchers.IO) { repo.syncExtensions(activity, catalog, force) }
            }.onSuccess { count ->
                state.value = state.value.copy(
                    providerCount = count,
                    workerStatus = "تم تحميل $count مصدر من الإضافات",
                    bootLabel = "تم تحميل $count إضافة/مصدر",
                    bootProgress = 1f, booting = false,
                )
            }.onFailure { err ->
                state.value = state.value.copy(
                    workerStatus = "الإضافات: ${err.message}",
                    bootLabel = "فشل تحميل الاضافات", bootProgress = 1f, booting = false,
                )
            }
        }
    }

    // ------------------------------------------------------------------ search

    fun setSearchQuery(value: String) {
        state.value = state.value.copy(searchQuery = value)
    }

    /** Runs a fresh TIMPD search (page 1). */
    fun search(query: String) {
        val q = query.trim()
        if (q.isBlank()) {
            state.value = state.value.copy(
                searchQuery = q, searchResults = emptyList(), searchPage = 1,
                searchLoading = false, searchDone = false,
            )
            return
        }
        state.value = state.value.copy(searchQuery = q, searchLoading = true, searchDone = false)
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                runCatching { repo.searchContent(q, 1, state.value.catalog) }.getOrDefault(emptyList())
            }
            val local = state.value.catalog.allItems().filter {
                it.title.contains(q, true) || it.subtitle.contains(q, true)
            }
            val merged = (results + local).distinctBy { it.id }
            state.value = state.value.copy(
                searchResults = merged, searchPage = 1,
                searchLoading = false, searchDone = merged.isEmpty(),
            )
        }
    }

    /** "المزيد" inside the search screen. */
    fun searchMore() {
        val s = state.value
        if (s.searchLoading || s.searchQuery.isBlank() || s.searchDone) return
        val next = s.searchPage + 1
        state.value = s.copy(searchLoading = true)
        viewModelScope.launch {
            val more = withContext(Dispatchers.IO) {
                runCatching { repo.searchContent(s.searchQuery, next, s.catalog) }.getOrDefault(emptyList())
            }
            val existing = state.value.searchResults.map { it.id }.toSet()
            val fresh = more.filterNot { it.id in existing }
            state.value = state.value.copy(
                searchResults = state.value.searchResults + fresh,
                searchPage = next,
                searchLoading = false,
                searchDone = fresh.isEmpty(),
            )
        }
    }


    /** "المزيد" — appends the next TIMPD page to every section of [kind]. */
    fun loadMore(sectionId: String) {
        if (state.value.loadingMore) return
        val catalog = state.value.catalog
        val next = (catalog.pages[sectionId] ?: 1) + 1
        state.value = state.value.copy(loadingMore = true)
        viewModelScope.launch {
            val more = withContext(Dispatchers.IO) { repo.loadMore(sectionId, next, catalog) }
            if (more.isEmpty()) {
                state.value = state.value.copy(loadingMore = false)
                return@launch
            }
            val existing = catalog.allItems().map { it.id }.toSet()
            val fresh = more.filterNot { it.id in existing }
            var appended = false
            val sections = catalog.sections.map { sec ->
                if (sec.id == sectionId) {
                    appended = true
                    sec.copy(items = sec.items + fresh)
                } else sec
            }.toMutableList()
            if (!appended) return@launch
            val updated = catalog.copy(sections = sections, pages = catalog.pages + (sectionId to next))
            withContext(Dispatchers.IO) { repo.persist(updated) }
            state.value = state.value.copy(catalog = updated, loadingMore = false)
        }
    }

    /** Fetches seasons/episodes for a series or anime the first time its detail page opens. */
    fun ensureEpisodes(item: ApixItem) {
        if (item.kind == ApixKind.MOVIE) return
        if (item.id in state.value.enrichedIds || item.seasons.isNotEmpty()) return
        state.value = state.value.copy(detailLoading = true)
        viewModelScope.launch {
            val seasons = withContext(Dispatchers.IO) {
                runCatching { repo.fetchSeasons(item, state.value.catalog) }.getOrDefault(emptyList())
            }
            val catalog = state.value.catalog
            val updated = catalog.copy(sections = catalog.sections.map { sec ->
                sec.copy(items = sec.items.map { if (it.id == item.id) it.copy(seasons = seasons) else it })
            })
            withContext(Dispatchers.IO) { repo.persist(updated) }
            state.value = state.value.copy(
                catalog = updated,
                // search results live outside the catalog — keep them in sync too
                searchResults = state.value.searchResults.map {
                    if (it.id == item.id) it.copy(seasons = seasons) else it
                },
                detailLoading = false,
                enrichedIds = state.value.enrichedIds + item.id,
            )
        }
    }


    /** Resolves every available server across all installed extensions. */
    suspend fun resolvePlayback(
        item: ApixItem,
        season: Int? = null,
        episode: Int? = null,
        onProgress: (String) -> Unit = {},
    ): ApixPlayback = withContext(Dispatchers.IO) {
        repo.resolvePlayback(item, season, episode, onProgress)
    }

    suspend fun resolvePlaybackStreaming(
        item: ApixItem,
        season: Int? = null,
        episode: Int? = null,
        onLink: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit,
        onSub: (com.lagradost.cloudstream3.ui.player.SubtitleData) -> Unit,
        onProgress: (String) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        repo.resolvePlaybackStreaming(item, season, episode, onLink, onSub, onProgress)
    }

    fun toggleFavorite(id: String) {
        state.value = state.value.copy(favorites = repo.toggleFavorite(id))
    }

    fun setLanguage(value: String) {
        repo.setLanguage(value)
        state.value = state.value.copy(language = value)
    }

    fun setDownloadsEnabled(value: Boolean) {
        repo.setDownloadsEnabled(value)
        state.value = state.value.copy(downloadsEnabled = value)
    }

    fun setBypassIsp(value: Boolean) {
        repo.setBypassIsp(value)
        state.value = state.value.copy(bypassIsp = value)
    }

    fun setWorkerUrl(value: String) {
        repo.setWorkerUrl(value)
        reload(force = true)
    }
}

