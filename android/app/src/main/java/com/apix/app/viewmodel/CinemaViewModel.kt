package com.apix.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apix.app.BuildConfig
import com.apix.app.data.HomeData
import com.apix.app.data.MediaItem
import com.apix.app.data.MediaRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CinemaViewModel(app: Application) : AndroidViewModel(app) {

    private val _homeState = MutableStateFlow(HomeData(appMode = "HYBRID"))
    val homeState: StateFlow<HomeData> = _homeState.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadCinemaData(appMode: String, externalUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1. جلب الأفلام مباشرة من TMDB 
                val movies = fetchTmdbTrending("movie", "vod")
                // 2. جلب المسلسلات مباشرة من TMDB
                val series = fetchTmdbTrending("tv", "series")
                
                val rows = mutableListOf<MediaRow>()
                if (movies.isNotEmpty()) {
                    rows.add(MediaRow(title = "أفلام شائعة", items = movies))
                }
                if (series.isNotEmpty()) {
                    rows.add(MediaRow(title = "مسلسلات شائعة", items = series))
                }

                _homeState.value = HomeData(
                    success = true,
                    appMode = appMode,
                    hero = movies.take(5), // وضع أول 5 أفلام في البانر العلوي
                    rows = rows
                )
            } catch (e: Exception) {
                Log.e("CinemaViewModel", "خطأ في جلب بيانات TMDB", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchTmdbTrending(type: String, section: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.TMDB_API_KEY
        val url = "https://api.themoviedb.org/3/trending/$type/week?api_key=$apiKey&language=ar"
        val result = mutableListOf<MediaItem>()
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (conn.responseCode == 200) {
                val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonText)
                val arr = json.optJSONArray("results") ?: return@withContext emptyList()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    result.add(
                        MediaItem(
                            id = obj.optString("id"),
                            tmdbId = obj.optString("id"),
                            title = obj.optString("title", obj.optString("name", "")),
                            poster = "https://image.tmdb.org/t/p/w500" + obj.optString("poster_path", ""),
                            backdrop = "https://image.tmdb.org/t/p/w780" + obj.optString("backdrop_path", ""),
                            description = obj.optString("overview", ""),
                            rating = String.format("%.1f", obj.optDouble("vote_average", 0.0)),
                            year = obj.optString("release_date", obj.optString("first_air_date", "")).take(4),
                            section = section,
                            extension = "mp4"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("CinemaViewModel", "TMDB error: $type", e)
        } finally {
            conn?.disconnect()
        }
        return@withContext result
    }
}
