package com.apix.app.vod.engine

import android.util.Log
import com.apix.app.BuildConfig
import com.apix.app.data.MediaItem
import com.apix.app.ui.screens.TvEpisode
import com.apix.app.ui.screens.TvSeason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object TMDBRepository {

    private const val BASE_URL = "https://api.themoviedb.org/3"
    private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
    private const val IMAGE_BASE_ORIGINAL = "https://image.tmdb.org/t/p/original"

    private fun getApiKey(): String {
        return BuildConfig.TMDB_API_KEY
    }

    suspend fun getDetails(tmdbId: String, isSeries: Boolean): MediaItem? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val type = if (isSeries) "tv" else "movie"
            val url = "$BASE_URL/$type/$tmdbId?api_key=${getApiKey()}&language=ar"
            
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                
                val title = if (isSeries) json.optString("name") else json.optString("title")
                val posterPath = json.optString("poster_path", "")
                val backdropPath = json.optString("backdrop_path", "")
                val releaseDate = if (isSeries) json.optString("first_air_date") else json.optString("release_date")
                
                return@withContext MediaItem(
                    id = tmdbId,
                    tmdbId = tmdbId,
                    title = title,
                    poster = if (posterPath.isNotEmpty() && posterPath != "null") "$IMAGE_BASE$posterPath" else "",
                    backdrop = if (backdropPath.isNotEmpty() && backdropPath != "null") "$IMAGE_BASE_ORIGINAL$backdropPath" else "",
                    description = json.optString("overview", ""),
                    rating = String.format(java.util.Locale.US, "%.1f", json.optDouble("vote_average", 0.0)),
                    year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else "",
                    section = if (isSeries) "series" else "vod"
                )
            }
        } catch (e: Exception) {
            Log.e("TMDB", "Details fetch failed", e)
        } finally {
            conn?.disconnect()
        }
        return@withContext null
    }

    suspend fun getSeasons(tmdbId: String): List<TvSeason> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = "$BASE_URL/tv/$tmdbId?api_key=${getApiKey()}&language=ar"
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val seasonsArray = json.optJSONArray("seasons") ?: return@withContext emptyList()
                
                val result = mutableListOf<TvSeason>()
                for (i in 0 until seasonsArray.length()) {
                    val s = seasonsArray.getJSONObject(i)
                    val sNum = s.optInt("season_number", 0)
                    if (sNum > 0) {
                        result.add(TvSeason(sNum, s.optString("name", "Season $sNum")))
                    }
                }
                return@withContext result
            }
        } catch (e: Exception) {
            Log.e("TMDB", "Seasons fetch failed", e)
        } finally {
            conn?.disconnect()
        }
        return@withContext emptyList()
    }

    suspend fun getEpisodes(tmdbId: String, seasonNumber: Int): List<TvEpisode> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = "$BASE_URL/tv/$tmdbId/season/$seasonNumber?api_key=${getApiKey()}&language=ar"
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val episodesArray = json.optJSONArray("episodes") ?: return@withContext emptyList()
                
                val result = mutableListOf<TvEpisode>()
                for (i in 0 until episodesArray.length()) {
                    val ep = episodesArray.getJSONObject(i)
                    val stillPath = ep.optString("still_path", "")
                    val runTime = ep.optInt("runtime", 0)
                    val durationStr = if (runTime > 0) "$runTime min" else ""
                    
                    result.add(
                        TvEpisode(
                            episodeNumber = ep.optInt("episode_number"),
                            name = ep.optString("name", "Episode ${ep.optInt("episode_number")}"),
                            stillUrl = if (stillPath.isNotEmpty() && stillPath != "null") "$IMAGE_BASE$stillPath" else "",
                            duration = durationStr
                        )
                    )
                }
                return@withContext result
            }
        } catch (e: Exception) {
            Log.e("TMDB", "Episodes fetch failed", e)
        } finally {
            conn?.disconnect()
        }
        return@withContext emptyList()
    }
}
