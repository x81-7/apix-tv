package com.apix.app.vod.engine

import android.content.Context
import android.util.Log
import com.apix.app.data.MediaItem
import com.apix.app.vod.extractors.WatchRequest
import com.apix.app.vod.extractors.StreamSource
import com.apix.app.vod.extractors.SubtitleSource

data class PlayerHeaders(
var userAgent: String? = null,
var referer: String? = null,
var cookie: String? = null
)

data class PlayerServerItem(
val name: String,
val url: String,
val quality: String,
val headers: Map<String, String>? = null
)

data class PlayerSubtitleItem(
val label: String,
val url: String,
val mimeType: String = "text/vtt"
)

data class AdaptedPlayerConfig(
var url: String = "",
var headers: PlayerHeaders? = null,
var fallbackServers: List<PlayerServerItem> = emptyList(),
var subtitleUrl: String = "",
var extraSubtitles: List<PlayerSubtitleItem> = emptyList()
)

class VodPlayerBridge(private val sourceEngine: SourceEngine) {

async fun preparePlayerConfig(  
    item: MediaItem,  
    seasonNumber: Int? = null,  
    episodeNumber: Int? = null  
): AdaptedPlayerConfig {  
    val config = AdaptedPlayerConfig()  
      
    val request = WatchRequest(  
        tmdbId = item.tmdbId.ifBlank { item.id },  
        imdbId = null,  
        title = item.title,  
        originalTitle = item.title,  
        year = item.year.toIntOrNull() ?: 2026,  
        isSeries = item.section == "series" || item.section == "anime",  
        season = seasonNumber,  
        episode = episodeNumber  
    )  

    try {  
        val groupedStreams = sourceEngine.fetchStreams(request)  
        val flattenedServers = mutableListOf<PlayerServerItem>()  

        for ((providerName, sources) in groupedStreams) {  
            for (source in sources) {  
                flattenedServers.add(  
                    PlayerServerItem(  
                        name = "$providerName - ${source.quality}p",  
                        url = source.url,  
                        quality = "${source.quality}p",  
                        headers = source.headers  
                    )  
                )  
            }  
        }  

        val primarySource = flattenedServers.firstOrNull()  
        if (primarySource != null) {  
            config.url = primarySource.url  
            if (primarySource.headers != null) {  
                config.headers = PlayerHeaders(  
                    userAgent = primarySource.headers["User-Agent"],  
                    referer = primarySource.headers["Referer"],  
                    cookie = primarySource.headers["Cookie"]  
                )  
            }  
            config.fallbackServers = flattenedServers  
        }  

    } catch (e: Exception) {  
        Log.e("VodPlayerBridge", "Failed to fetch and adapt video streams", e)  
    }  

    try {  
        val fetchedSubs = sourceEngine.fetchSubtitles(request)  
        val adaptedSubs = mutableListOf<PlayerSubtitleItem>()  

        var indexAr = 1  
        var indexEn = 1  
        var indexOther = 1  

        for (sub in fetchedSubs) {  
            val label = when {  
                sub.language.contains("ar", true) -> {  
                    val lbl = "عربي $indexAr"  
                    indexAr++  
                    lbl  
                }  
                sub.language.contains("en", true) -> {  
                    val lbl = "إنجليزي $indexEn"  
                    indexEn++  
                    lbl  
                }  
                else -> {  
                    val lbl = "${sub.language} $indexOther"  
                    indexOther++  
                    lbl  
                }  
            }  

            adaptedSubs.add(  
                PlayerSubtitleItem(  
                    label = label,  
                    url = sub.url  
                )  
            )  
        }  

        val primarySub = adaptedSubs.firstOrNull()  
        if (primarySub != null) {  
            config.subtitleUrl = primarySub.url  
            config.extraSubtitles = adaptedSubs  
        }  

    } catch (e: Exception) {  
        Log.e("VodPlayerBridge", "Failed to fetch and adapt subtitles", e)  
    }  

    return config  
}

}