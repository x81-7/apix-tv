package com.apix.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apix.app.BuildConfig
import com.apix.app.data.Category
import com.apix.app.data.CinemaJson
import com.apix.app.data.HomeData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CinemaViewModel(app: Application) : AndroidViewModel(app) {

    private val _homeState = MutableStateFlow(HomeData())
    val homeState: StateFlow<HomeData> = _homeState.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var cachedMovies = JSONArray()
    private var cachedSeries = JSONArray()

    fun loadCinemaData(appMode: String, externalUrl: String, categories: List<Category>) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                // 1. جلب الأفلام والمسلسلات من TMDB مباشرة وحفظها بالذاكرة المؤقتة (لمنع التكرار)
                if (cachedMovies.length() == 0) {
                    cachedMovies = fetchTmdbTrending("movie", "vod")
                }
                if (cachedSeries.length() == 0) {
                    cachedSeries = fetchTmdbTrending("tv", "series")
                }

                // 2. تجهيز البانر العلوي (Hero)
                val heroArray = JSONArray()
                for (i in 0 until minOf(5, cachedMovies.length())) {
                    heroArray.put(cachedMovies.getJSONObject(i))
                }

                val rowsArray = JSONArray()

                // 3. دمج قنوات البث المباشر تلقائياً من Cloudflare كصفوف
                for (cat in categories) {
                    if (cat.hidden) continue
                    val channels = cat.channels?.values?.filter { !it.hidden }?.sortedBy { it.sortOrder }
                    if (channels.isNullOrEmpty()) continue

                    val catRow = JSONObject()
                    catRow.put("title", "بث مباشر: ${cat.name}")
                    val catItems = JSONArray()
                    for (ch in channels) {
                        val item = JSONObject()
                        item.put("id", ch.id)
                        item.put("title", ch.name)
                        item.put("poster", ch.imageUrl)
                        item.put("backdrop", ch.imageUrl)
                        item.put("section", "live")
                        item.put("url", ch.stream?.url ?: "")
                        item.put("useLocalProxy", ch.useLocalProxy)
                        catItems.put(item)
                    }
                    catRow.put("items", catItems)
                    rowsArray.put(catRow)
                }

                // 4. إضافة أفلام ومسلسلات TMDB
                if (cachedMovies.length() > 0) {
                    val moviesRow = JSONObject()
                    moviesRow.put("title", "أفلام شائعة")
                    moviesRow.put("items", cachedMovies)
                    rowsArray.put(moviesRow)
                }

                if (cachedSeries.length() > 0) {
                    val seriesRow = JSONObject()
                    seriesRow.put("title", "مسلسلات شائعة")
                    seriesRow.put("items", cachedSeries)
                    rowsArray.put(seriesRow)
                }

                // 5. تجميع الـ JSON وتمريره للمحلل الخاص بك (CinemaJson) لتجنب أخطاء الكلاسات المفقودة
                val root = JSONObject()
                root.put("success", true)
                root.put("app_mode", appMode)
                root.put("hero", heroArray)
                root.put("rows", rowsArray)

                val parsedData = CinemaJson.parseHome(root.toString())
                
                withContext(Dispatchers.Main) {
                    _homeState.value = parsedData
                    Log.d("CinemaViewModel", "تم بناء ودمج بيانات السينما بنجاح")
                }
            } catch (e: Exception) {
                Log.e("CinemaViewModel", "خطأ أثناء جلب أو دمج البيانات", e)
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    private suspend fun fetchTmdbTrending(type: String, section: String): JSONArray = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.TMDB_API_KEY
        val url = "https://api.themoviedb.org/3/trending/$type/week?api_key=$apiKey&language=ar"
        val result = JSONArray()
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (conn.responseCode == 200) {
                val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonText)
                val arr = json.optJSONArray("results") ?: return@withContext result
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val item = JSONObject()
                    item.put("id", obj.optString("id"))
                    item.put("tmdb_id", obj.optString("id"))
                    item.put("title", obj.optString("title", obj.optString("name", "")))
                    item.put("poster", "https://image.tmdb.org/t/p/w500" + obj.optString("poster_path", ""))
                    item.put("backdrop", "https://image.tmdb.org/t/p/w780" + obj.optString("backdrop_path", ""))
                    item.put("description", obj.optString("overview", ""))
                    item.put("rating", String.format("%.1f", obj.optDouble("vote_average", 0.0)))
                    item.put("year", obj.optString("release_date", obj.optString("first_air_date", "")).take(4))
                    item.put("section", section)
                    item.put("ext", "mp4")
                    result.put(item)
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
