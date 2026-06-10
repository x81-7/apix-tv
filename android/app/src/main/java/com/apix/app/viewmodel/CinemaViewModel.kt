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
                val franchises = fetchCollections() // 👈 جلب السلاسل الحقيقية كحزم مجمعة

                _homeState.value = HomeData(
                    appMode = appMode,
                    hero = trendingMovies.take(6), // السلايدر العلوي
                    rows = listOf(
                        HomeRow(id = "trending/movie/week", title = "أفلام شائعة", items = trendingMovies),
                        HomeRow(id = "trending/tv/week", title = "مسلسلات شائعة", items = trendingSeries),
                        HomeRow(id = "discover/tv?with_genres=16&with_original_language=ja&sort_by=first_air_date.desc", title = "أحدث الأنميات", items = latestAnime),
                        HomeRow(id = "franchises", title = "سلاسل الأفلام (Collections)", items = franchises)
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

    // 👈 دالة خاصة لجلب بيانات شركة معينة (نتفلكس وغيرها)
    suspend fun getStudioData(companyId: Int, networkId: Int): List<HomeRow> = withContext(Dispatchers.IO) {
        val rows = mutableListOf<HomeRow>()
        if (companyId != -1) {
            rows.add(HomeRow("movies_$companyId", "أفلام", fetchTmdb("discover/movie?with_companies=$companyId", "vod", 1)))
        }
        if (networkId != -1) {
            rows.add(HomeRow("series_$networkId", "مسلسلات", fetchTmdb("discover/tv?with_networks=$networkId", "series", 1)))
        }
        rows
    }

    // 👈 دالة خاصة لجلب أجزاء السلسلة (مثلا أجزاء Fast & Furious بالترتيب)
    suspend fun getCollectionParts(collectionId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.TMDB_API_KEY
        try {
            val url = URL("https://api.themoviedb.org/3/collection/$collectionId?api_key=$apiKey&language=ar")
            val conn = url.openConnection() as HttpURLConnection
            if (conn.responseCode == 200) {
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val parts = json.optJSONArray("parts") ?: return@withContext emptyList()
                val list = mutableListOf<MediaItem>()
                for (i in 0 until parts.length()) {
                    val obj = parts.getJSONObject(i)
                    list.add(MediaItem(
                        id = obj.optString("id"), tmdbId = obj.optString("id"),
                        title = obj.optString("title", ""),
                        poster = "https://image.tmdb.org/t/p/w500" + obj.optString("poster_path", ""),
                        backdrop = "https://image.tmdb.org/t/p/w780" + obj.optString("backdrop_path", ""),
                        year = obj.optString("release_date", "").take(4),
                        section = "vod"
                    ))
                }
                return@withContext list.sortedBy { it.year } // ترتيب الأجزاء من القديم للجديد
            }
        } catch(e:Exception){}
        emptyList()
    }

    private suspend fun fetchCollections(): List<MediaItem> = withContext(Dispatchers.IO) {
        // حزم سلاسل مشهورة: Fast, Harry Potter, Avengers, Transformers, Pirates, X-Men
        val ids = listOf(9485, 1241, 86311, 528, 295, 8707) 
        val apiKey = BuildConfig.TMDB_API_KEY
        val result = mutableListOf<MediaItem>()
        for (id in ids) {
            try {
                val url = URL("https://api.themoviedb.org/3/collection/$id?api_key=$apiKey&language=ar")
                val conn = url.openConnection() as HttpURLConnection
                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    result.add(MediaItem(
                        id = json.optString("id"), tmdbId = json.optString("id"),
                        title = json.optString("name", ""),
                        poster = "https://image.tmdb.org/t/p/w500" + json.optString("poster_path", ""),
                        backdrop = "https://image.tmdb.org/t/p/w780" + json.optString("backdrop_path", ""),
                        section = "collection" // 👈 تمييزها كسلسلة لتفتح الشاشة الجديدة
                    ))
                }
                conn.disconnect()
            } catch (e: Exception) {}
        }
        result
    }

    private suspend fun fetchTmdb(endpoint: String, section: String, page: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.TMDB_API_KEY
        val prefix = if (endpoint.contains("?")) "&" else "?"
        // 👈 تم إضافة منع المحتوى الإباحي &include_adult=false بشكل جذري
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
