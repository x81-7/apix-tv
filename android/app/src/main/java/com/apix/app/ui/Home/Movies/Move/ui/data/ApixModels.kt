package com.lagradost.cloudstream3.apix.data

import androidx.compose.runtime.Immutable

enum class ApixKind { MOVIE, SERIES, ANIME }

enum class ApixTab { HOME, MOVIES, SERIES, ANIME, SETTINGS }

enum class ApixSectionCategory(val key: String, val title: String) {
    MOVIES_ARABIC("movies_ar", "أفلام عربية"),
    MOVIES_FOREIGN("movies_foreign", "أفلام أجنبية"),
    MOVIES_ANIMATION("movies_animation", "أفلام أنميشن"),
    MOVIES_KOREAN("movies_ko", "أفلام كورية"),
    SERIES_ARABIC("series_ar", "مسلسلات عربية"),
    SERIES_FOREIGN("series_foreign", "مسلسلات أجنبية"),
    SERIES_KOREAN("series_ko", "مسلسلات كورية"),
    ANIME_FOREIGN("anime_foreign", "أنميشن أجنبي"),
    ANIME_JAPANESE("anime_ja", "أنمي ياباني"),
    ANIME_KOREAN("anime_ko", "أنمي كوري"),
    MOVIE_COLLECTIONS("movie_collections", "سلاسل الأفلام");

    companion object {
        fun fromKey(value: String): ApixSectionCategory? = entries.firstOrNull { it.key == value }
    }
}

@Immutable
data class ApixCatalog(
    val updatedAt: String = "",
    val workerUrl: String = "",
    val featuredIds: List<String> = emptyList(),
    val studios: List<ApixStudio> = emptyList(),
    val sections: List<ApixSection> = emptyList(),
    val extensions: List<ApixExtension> = emptyList(),
    val hasTmdb: Boolean = false,
    val tmdbToken: String = "",
    /** Last loaded TIMPD page per category/section id, used by the "المزيد" button. */
    val pages: Map<String, Int> = emptyMap(),
) {
    fun allItems(): List<ApixItem> = sections.flatMap { it.items }
    fun itemById(id: String): ApixItem? = allItems().firstOrNull { it.id == id }
    fun featuredItems(): List<ApixItem> {
        val map = allItems().associateBy { it.id }
        val featured = featuredIds.mapNotNull { map[it] }.toMutableList()
        if (featured.isEmpty()) featured.addAll(allItems().take(6))
        return featured
    }
    fun sectionsOf(kind: ApixKind): List<ApixSection> = sections.filter { it.kind == kind }
    fun sectionById(id: String): ApixSection? = sections.firstOrNull { it.id == id }
    fun categorySections(kind: ApixKind): List<ApixSection> = sections.filter { it.kind == kind && ApixSectionCategory.fromKey(it.id) != null }
    fun categorySections(): List<ApixSection> = sections.filter { ApixSectionCategory.fromKey(it.id) != null }
    fun itemsOf(kind: ApixKind): List<ApixItem> = sections.filter { it.kind == kind }.flatMap { it.items }
    fun studioById(id: String): ApixStudio? = studios.firstOrNull { it.id == id }
}

@Immutable
data class ApixExtension(
    val name: String,
    val url: String,
    val enabled: Boolean = true,
)

@Immutable
data class ApixStudio(
    val id: String,
    val name: String,
    val logo: String = "",
    val accent: String = "#FFD700",
    val overview: String = "",
)

@Immutable
data class ApixSection(
    val id: String,
    val title: String,
    val kind: ApixKind,
    val items: List<ApixItem> = emptyList(),
)

@Immutable
data class ApixItem(
    val id: String,
    val tmdbId: Int = 0,
    val title: String,
    /** Same work's titles in other languages (Arabic/original) — used when searching extensions. */
    val altTitles: List<String> = emptyList(),
    val subtitle: String = "",
    val overview: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val year: String = "",
    val rating: String = "",
    val duration: String = "",
    val genre: String = "",
    val studioId: String = "",
    val kind: ApixKind = ApixKind.MOVIE,
    val tags: List<String> = emptyList(),
    val similarIds: List<String> = emptyList(),
    val playbackUrl: String = "",
    val seasons: List<ApixSeason> = emptyList(),
    val trailerQuery: String? = null,
    /** TMDB/worker original language, used to classify the modern category shelves. */
    val originalLanguage: String = "",
)

@Immutable
data class ApixSeason(
    val number: Int,
    val title: String = "",
    val episodes: List<ApixEpisode> = emptyList(),
)

@Immutable
data class ApixEpisode(
    val id: String,
    val number: Int,
    val season: Int = 1,
    val title: String,
    val duration: String = "",
    val overview: String = "",
    val stillUrl: String = "",
    val playbackUrl: String = "",
)

data class ApixUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val catalog: ApixCatalog = ApixCatalog(),
    val favorites: Set<String> = emptySet(),
    val workerSyncedAt: Long = 0L,
    val workerStatus: String = "",
    val language: String = "ar",
    val downloadsEnabled: Boolean = true,
    val bypassIsp: Boolean = false,
    val bootProgress: Float = 0f,
    val bootLabel: String = "",
    val booting: Boolean = true,
    /** True while a "المزيد" request is in flight. */
    val loadingMore: Boolean = false,
    /** ids of items whose seasons/episodes have been fetched from TIMPD. */
    val enrichedIds: Set<String> = emptySet(),
    val detailLoading: Boolean = false,
    /** How many providers were registered by the downloaded extensions. */
    val providerCount: Int = 0,
    /** Dedicated search screen state. */
    val searchQuery: String = "",
    val searchResults: List<ApixItem> = emptyList(),
    val searchLoading: Boolean = false,
    val searchPage: Int = 1,
    val searchDone: Boolean = false,
)

