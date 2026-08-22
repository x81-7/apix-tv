package com.lagradost.cloudstream3.utils

/**
 * External sync lookups are disabled in this build.
 * Keep the API to avoid compile/runtime crashes.
 */
object SyncUtil {
    @Suppress("UNUSED_PARAMETER")
    suspend fun getIdsFromUrl(url: String?): Pair<String?, String?>? {
        return null
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun getUrlsFromId(id: String, type: String = "anilist"): List<String> {
        return emptyList()
    }
}
