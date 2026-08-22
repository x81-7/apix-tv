package com.lagradost.cloudstream3.ui.download

/** Download UI compatibility layer. Actual episode downloading is disabled for Movies for now. */
const val DOWNLOAD_ACTION_DOWNLOAD = 1
const val DOWNLOAD_ACTION_LONG_CLICK = 2
const val DOWNLOAD_ACTION_DELETE_FILE = 3
const val DOWNLOAD_ACTION_PAUSE_DOWNLOAD = 4
const val DOWNLOAD_ACTION_RESUME_DOWNLOAD = 5

data class DownloadClickData(
    val id: Int = 0,
    val name: String? = null,
    val episode: Int? = null,
    val season: Int? = null,
    val poster: String? = null,
    val parentId: Int? = null,
    val score: Int? = null,
    val description: String? = null,
)

data class DownloadClickEvent(
    val action: Int,
    val data: DownloadClickData = DownloadClickData(),
)

object DownloadObjects {
    data class DownloadEpisodeCached(
        val name: String? = null,
        val poster: String? = null,
        val episode: Int? = null,
        val season: Int? = null,
        val id: Int = 0,
        val parentId: Int? = null,
        val score: Int? = null,
        val description: String? = null,
        val cacheTime: Long = 0L,
    )
}

object VideoDownloadManager {
    enum class DownloadType { IsPaused, Other }
    val downloadStatus: MutableMap<Int, DownloadType> = mutableMapOf()
}
