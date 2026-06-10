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

    // حالة الصفحة الرئيسية
    private val _homeState = MutableStateFlow(HomeData(appMode = "HYBRID"))
    val homeState: StateFlow<HomeData> = _homeState.asStateFlow()

    // حالات الصفحات الفرعية
    private val _moviesRows = MutableStateFlow<List<HomeRow>>(emptyList())
    val moviesRows: StateFlow<List<HomeRow>> = _moviesRows.asStateFlow()

    private val _seriesRows = MutableStateFlow<List<HomeRow>>(emptyList())
    val seriesRows: StateFlow<List<HomeRow>> = _seriesRows.asStateFlow()

    private val _animeRows = MutableStateFlow<List<HomeRow>>(emptyList())
    val animeRows: StateFlow<List<HomeRow>> = _animeRows.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadCinemaData(appMode: String, externalUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // --- بيانات الصفحة الرئيسية ---
                val trendingMovies = fetchTmdb("trending/movie/week", "vod")
                val trendingSeries = fetchTmdb("trending/tv/week", "series")
                val latestAnime = fetchTmdb("discover/tv?with_genres=16&with_original_language=ja&sort_by=first_air_date.desc", "anime")
                val movieFranchises = fetchTmdb("discover/movie?sort_by=revenue.desc&with_genres=28,878", "vod") // اكشن وخيال علمي كسلاسل

                _homeState.value = HomeData(
                    appMode = appMode,
                    hero = trendingMovies.take(5),
                    rows = listOf(
                        HomeRow("أفلام شائعة", trendingMovies),
                        HomeRow("مسلسلات شائعة", trendingSeries),
                        HomeRow("أحدث الأنميات", latestAnime),
                        HomeRow("سلاسل الأفلام والأكشن", movieFranchises)
                    )
                )

                // --- بيانات صفحة الأفلام (مقسمة) ---
                _moviesRows.value = listOf(
                    HomeRow("أفلام أجنبية", fetchTmdb("discover/movie?with_original_language=en", "vod")),
                    HomeRow("أفلام كورية", fetchTmdb("discover/movie?with_original_language=ko", "vod")),
                    HomeRow("أفلام أنميشن", fetchTmdb("discover/movie?with_genres=16", "vod"))
                )

                // --- بيانات صفحة المسلسلات (مقسمة) ---
                _seriesRows.value = listOf(
                    HomeRow("مسلسلات أجنبية", fetchTmdb("discover/tv?with_original_language=en", "series")),
                    HomeRow("مسلسلات كورية", fetchTmdb("discover/tv?with_original_language=ko", "series")),
                    HomeRow("مسلسلات عربية", fetchTmdb("discover/tv?with_original_language=ar", "series"))
                )

                // --- بيانات صفحة الأنمي (مقسمة) ---
                _animeRows.value = listOf(
                    HomeRow("أنميشن عالمي", fetchTmdb("discover/tv?with_genres=16&with_original_language=en", "anime")),
                    HomeRow("أنمي ياباني", fetchTmdb("discover/tv?with_genres=16&with_original_language=ja", "anime")),
                    HomeRow("أنمي كوري", fetchTmdb("discover/tv?with_genres=16&with_original_language=ko", "anime"))
                )

            } catch (e: Exception) {
                Log.e("CinemaViewModel", "خطأ في جلب بيانات TMDB", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchTmdb(endpoint: String, section: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.TMDB_API_KEY
        val prefix = if (endpoint.contains("?")) "&" else "?"
        val url = "https://api.themoviedb.org/3/$endpoint${prefix}api_key=$apiKey&language=ar"
        
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
            Log.e("CinemaViewModel", "TMDB error: $endpoint", e)
        } finally {
            conn?.disconnect()
        }
        return@withContext result
    }
}
