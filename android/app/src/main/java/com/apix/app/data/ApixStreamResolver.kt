package com.apix.app.data

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * يكشف روابط apix.png ويفك تشفيرها من CloudFlare Worker
 * الرابط يبدو للخارج كصورة عادية لكنه يعيد JSON مشفر
 */
object ApixStreamResolver {

    private const val T = "ApixResolver"

    // الكشف: الرابط ينتهي بـ apix.png أو يحتوي عليها
    fun isApixStream(url: String): Boolean {
        val lower = url.lowercase().trimEnd()
        return lower.endsWith("apix.png")
            || lower.contains("/apix.png?")
            || lower.contains("/apix.png#")
    }

    // الحل: جلب → فك تشفير → تحليل → PlayerConfig
    fun resolve(url: String, base: PlayerConfig): PlayerConfig? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout    = 15000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.setRequestProperty("Accept", "application/json, image/png, */*")
            conn.instanceFollowRedirects = true

            if (conn.responseCode != 200) {
                Log.w(T, "HTTP ${conn.responseCode} for $url")
                return null
            }

            val body = conn.inputStream.bufferedReader().readText().trim()
            Log.d(T, "Got encrypted payload (${body.length} chars)")

            // فك التشفير بالمفتاح الخارجي من NDK
            val plain = com.apix.app.PayloadCipher.decryptExternal(body)
            Log.d(T, "Decrypted OK")

            parseJson(plain, base)
        } catch (e: Exception) {
            Log.e(T, "resolve failed for $url", e)
            null
        }
    }

    private fun parseJson(json: String, base: PlayerConfig): PlayerConfig? {
        return try {
            val obj = JSONObject(json)

            // ── OK.ru type: handled via OkRuExtractor in ComposeActivity ──
            // apix.png okru_extractor type is NOT processed here anymore.
            // OkRuExtractor (WebView-based) intercepts ok.ru URLs before this resolver.
            if (obj.optString("type", "") == "okru_extractor") {
                return kotlinx.coroutines.runBlocking {
                    com.apix.app.OkRuStreamHandler.loadStream(obj, base)
                }
            }

            // رابط ok.ru مباشر داخل JSON — يُحوَّل لـ OkRuExtractor تلقائياً
            val directUrl = obj.optString("url", "")
            if (com.apix.app.OkRuExtractor.isOkRuUrl(directUrl)) {
                val videoId = com.apix.app.OkRuExtractor.extractVideoId(directUrl)
                if (videoId != null) {
                    return base.copy(
                        url           = com.apix.app.OkRuExtractor.buildEmbedUrl(videoId),
                        okruVideoId   = videoId,
                        okruChannel   = obj.optString("name", ""),
                        drm           = null,
                        useLocalProxy = false
                    )
                }
            }

            val config = base.copy()

            // الرابط الأساسي — إلزامي
            val streamUrl = obj.optString("url", "")
            if (streamUrl.isEmpty()) {
                Log.w(T, "JSON has no 'url' field")
                return null
            }
            config.url = streamUrl

            // رابط احتياطي
            obj.optString("backupUrl", "").takeIf { it.isNotEmpty() }?.let {
                config.backupUrl = it
            }

            // الهيدرز بما فيها User-Agent و Referer
            obj.optJSONObject("headers")?.let { h ->
                val headersMap = mutableMapOf<String, String>()
                h.keys().forEach { k ->
                    headersMap[k] = h.optString(k)
                }
                if (headersMap.isNotEmpty()) {
                    val merged = (config.customHeaders ?: emptyMap()) + headersMap
                    config.customHeaders = merged
                }
            }

            // customHeaders إضافية
            obj.optJSONObject("customHeaders")?.let { ch ->
                val extra = mutableMapOf<String, String>()
                ch.keys().forEach { k -> extra[k] = ch.optString(k) }
                if (extra.isNotEmpty()) {
                    val merged = (config.customHeaders ?: emptyMap()) + extra
                    config.customHeaders = merged
                }
            }

            // DRM
            obj.optJSONObject("drm")?.let { d ->
                config.drm = PlayerDrm(
                    scheme     = d.optString("scheme").takeIf { it.isNotEmpty() },
                    licenseUrl = d.optString("licenseUrl").takeIf { it.isNotEmpty() },
                    keyId      = d.optString("keyId").takeIf { it.isNotEmpty() },
                    key        = d.optString("key").takeIf { it.isNotEmpty() }
                )
            }

            // ترجمة
            obj.optString("subtitleUrl", "").takeIf { it.isNotEmpty() }?.let {
                config.subtitleUrl = it
            }

            // الاسم
            obj.optString("name", "").takeIf { it.isNotEmpty() }?.let {
                config.title = it
            }

            Log.d(T, "Resolved: ${config.url}")
            config
        } catch (e: Exception) {
            Log.e(T, "parseJson failed", e)
            null
        }
    }
}
