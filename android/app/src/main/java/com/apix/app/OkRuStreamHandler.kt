package com.apix.app

import android.util.Log
import com.apix.app.data.PlayerConfig
import com.apix.app.data.PlayerHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object OkRuStreamHandler {

    private const val T = "OkRu"

    // تم وضع الدالة هنا بالداخل لحل مشكلة (Unresolved reference)
    fun isOkRuPayload(json: JSONObject): Boolean {
        return json.optString("type", "") == "okru_extractor"
    }

    suspend fun loadStream(
        json: JSONObject,
        baseConfig: PlayerConfig
    ): PlayerConfig? = withContext(Dispatchers.IO) {

        val type = json.optString("type", "")
        if (type != "okru_extractor") return@withContext null

        val videoId   = json.optString("videoId",   "")
        val cookie    = json.optString("cookie",    "")
        val tkn       = json.optString("tkn",       "")
        val userAgent = json.optString("userAgent", "Mozilla/5.0 (Linux; Android 12)")

        if (videoId.isEmpty()) {
            Log.w(T, "videoId missing")
            return@withContext null
        }

        // استخراج الرابط الحقيقي من OK.ru
        val rawUrl = extractFromOkRu(videoId, cookie, tkn, userAgent)
            ?: return@withContext null

        // تمرير الرابط للبروكسي المحلي
        val proxiedUrl = try {
            val hdrs = HashMap<String, String>()
            hdrs["Cookie"]     = cookie
            hdrs["User-Agent"] = userAgent
            hdrs["Referer"]    = "https://ok.ru/video/$videoId"
            hdrs["Connection"] = "close"
            LocalStreamServer.setHeaders(hdrs)
            LocalStreamServer.ensureStarted()
            LocalStreamServer.wrap(rawUrl)
        } catch (e: Exception) {
            Log.w(T, "proxy wrap failed")
            return@withContext null
        }

        baseConfig.copy(
            url = proxiedUrl,
            headers = PlayerHeaders(
                userAgent = userAgent,
                referer   = "https://ok.ru/video/$videoId",
                cookie    = null,
                origin    = "https://ok.ru"
            ),
            customHeaders = null 
        )
    }

    private fun extractFromOkRu(
        videoId: String,
        cookie: String,
        tkn: String,
        userAgent: String
    ): String? {
        var conn: HttpURLConnection? = null
        return try {
            val endpoint = URL("https://ok.ru/dk?cmd=videoPlayerMetadata")
            conn = endpoint.openConnection() as HttpURLConnection
            conn.requestMethod  = "POST"
            conn.doOutput       = true
            conn.connectTimeout = 12000
            conn.readTimeout    = 15000

            conn.setRequestProperty("Content-Type",  "application/x-www-form-urlencoded")
            conn.setRequestProperty("User-Agent",    userAgent)
            conn.setRequestProperty("Cookie",        cookie)
            conn.setRequestProperty("tkn",           tkn) 
            conn.setRequestProperty("Referer",       "https://ok.ru/video/$videoId")
            conn.setRequestProperty("Origin",        "https://ok.ru")
            conn.setRequestProperty("Accept",        "application/json, */*")
            conn.setRequestProperty("Accept-Language", "ar,en;q=0.9")

            val body = "mid=$videoId&is=on"
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body) }

            if (conn.responseCode != 200) {
                Log.w(T, "HTTP ${conn.responseCode}")
                return null
            }

            val raw = conn.inputStream.bufferedReader().readText()
            parseHighestQuality(raw)

        } catch (e: Exception) {
            Log.w(T, "extract failed: ${e.javaClass.simpleName}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseHighestQuality(responseBody: String): String? {
        return try {
            val root = JSONObject(responseBody)
            
            // 1. فحص رابط البث المباشر HLS
            var hlsUrl = root.optString("hlsMasterPlaylistUrl", "").replace("\\u0026", "&")
            if (hlsUrl.isEmpty()) {
                hlsUrl = root.optString("hlsManifestUrl", "").replace("\\u0026", "&")
            }
            if (hlsUrl.isNotEmpty()) return hlsUrl

            // 2. خطة بديلة (Fallback) للفيديو المسجل
            val videos = root.optJSONArray("videos") ?: return null
            val priority = listOf("1080", "720", "480", "360", "240")
            val map = mutableMapOf<String, String>()

            for (i in 0 until videos.length()) {
                val v   = videos.getJSONObject(i)
                val url = v.optString("url",  "")
                val res = v.optString("name", "")
                if (url.isNotEmpty() && res.isNotEmpty()) {
                    map[res] = url
                }
            }

            for (p in priority) {
                map[p]?.let { return it }
            }

            map.values.firstOrNull()

        } catch (e: Exception) {
            Log.w(T, "parse failed")
            null
        }
    }
}
