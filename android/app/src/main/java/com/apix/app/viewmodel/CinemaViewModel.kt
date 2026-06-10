package com.apix.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apix.app.BuildConfig
import com.apix.app.data.HomeData
import com.apix.app.data.MediaItem
import com.apix.app.data.HomeRow
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

    private val _moviesRows = MutableStateFlow<List<HomeRow>>(emptyList())
    val moviesRows: StateFlow<List<HomeRow>> = _moviesRows.asStateFlow()

    private val _seriesRows = MutableStateFlow<List<HomeRow>>(emptyList())
    val seriesRows: StateFlow<List<HomeRow>> = _seriesRows.asStateFlow()

    private val _animeRows = MutableStateFlow<List<HomeRow>>(emptyList())
    val animeRows: StateFlow<List<HomeRow>> = _animeRows.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadCinemaData(appMode: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val trendingMovies = fetchTmdb("trending/movie/week", "vod", 1)
                val trendingSeries = fetchTmdb("trending/tv/week", "series", 1)
                val latestAnime = fetchTmdb("discover/tv?with_genres=16&with_original_language=ja&sort_by=first_air_date.desc", "anime", 1)
                val franchises = fetchTmdb("discover/movie?with_genres=28,878&sort_by=revenue.desc", "vod", 1)

                _homeState.value = HomeData(
                    appMode = appMode,
                    hero = trendingMovies.take(6), // السلايدر العلوي
                    rows = listOf(
                        HomeRow(id = "trending/movie/week", title = "أفلام شائعة", items = trendingMovies),
                        HomeRow(id = "trending/tv/week", title = "مسلسلات شائعة", items = trendingSeries),
                        HomeRow(id = "discover/tv?with_genres=16&with_original_language=ja&sort_by=first_air_date.desc", title = "أحدث الأنميات", items = latestAnime),
                        HomeRow(id = "discover/movie?with_genres=28,878&sort_by=revenue.desc", title = "سلاسل الأفلام", items = franchises)
                    )
                )

                _moviesRows.value = listOf(
                    HomeRow(id = "discover/movie?with_original_language=en", title = "أفلام أجنبية", items = fetchTmdb("discover/movie?with_original_language=en", "vod", 1)),
                    HomeRow(id = "discover/movie?with_original_language=ko", title = "أفلام كورية", items = fetchTmdb("discover/movie?with_original_language=ko", "vod", 1)),
                    HomeRow(id = "discover/movie?with_genres=16", title = "أفلام أنميشن", items = fetchTmdb("discover/movie?with_genres=16", "vod", 1))
                )

                _seriesRows.value = listOf(
                    HomeRow(id = "discover/tv?with_original_language=en", title = "مسلسلات أجنبية", items = fetchTmdb("discover/tv?with_original_language=en", "series", 1)),
                    HomeRow(id = "discover/tv?with_original_language=ko", title = "مسلسلات كورية", items = fetchTmdb("discover/tv?with_original_language=ko", "series", 1)),
                    HomeRow(id = "discover/tv?with_original_language=ar", title = "مسلسلات عربية", items = fetchTmdb("discover/tv?with_original_language=ar", "series", 1))
                )

                _animeRows.value = listOf(
                    HomeRow(id = "discover/tv?with_genres=16&with_original_language=en", title = "أنميشن عالمي", items = fetchTmdb("discover/tv?with_genres=16&with_original_language=en", "anime", 1)),
                    HomeRow(id = "discover/tv?with_genres=16&with_original_language=ja", title = "أنمي ياباني", items = fetchTmdb("discover/tv?with_genres=16&with_original_language=ja", "anime", 1)),
                    HomeRow(id = "discover/tv?with_genres=16&with_original_language=ko", title = "أنمي كوري", items = fetchTmdb("discover/tv?with_genres=16&with_original_language=ko", "anime", 1))
                )
            } catch (e: Exception) {
                Log.e("CinemaViewModel", "خطأ", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun fetchMore(endpoint: String, section: String, page: Int): List<MediaItem> {
        return fetchTmdb(endpoint, section, page)
    }

    private suspend fun fetchTmdb(endpoint: String, section: String, page: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.TMDB_API_KEY
        val prefix = if (endpoint.contains("?")) "&" else "?"
        // 👈 تم إضافة &include_adult=false لمنع أي محتوى إباحي نهائياً
        val url = "https://api.themoviedb.org/3/$endpoint${prefix}api_key=$apiKey&language=ar&page=$page&include_adult=false"
        
        val result = mutableListOf<MediaItem>()
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (conn.responseCode == 200) {
                val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONObject(jsonText).optJSONArray("results") ?: return@withContext emptyList()
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
            Log.e("CinemaViewModel", "TMDB error", e)
        } finally {
            conn?.disconnect()
        }
        return@withContext result
    }
}
