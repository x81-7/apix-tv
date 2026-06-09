package com.apix.app.vod.extractors

data class WatchRequest(
    val tmdbId: String,
    val imdbId: String?,
    val title: String,
    val originalTitle: String,
    val year: Int,
    val isSeries: Boolean,
    val season: Int? = null,
    val episode: Int? = null
)

data class StreamSource(
    val providerName: String,
    val url: String,
    val quality: Int,
    val headers: Map<String, String>?,
    val isEmbed: Boolean,
    val internalSubtitles: List<SubtitleSource> = emptyList()
)

data class SubtitleSource(
    val language: String,
    val url: String,
    val isEmbedded: Boolean
)

interface ApixProvider {
    val name: String
    val isHealthy: Boolean
    val supportedTypes: List<String> 

    suspend fun getStreams(request: WatchRequest): List<StreamSource>
    suspend fun getSubtitles(request: WatchRequest): List<SubtitleSource>
}
