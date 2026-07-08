package com.apix.app.ui.screens

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apix.app.data.PlayerConfig
import com.apix.app.data.PlayerHeaders
import com.apix.app.ui.theme.Gold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

// ── Local DASH Server ───────────────────────────────────────────────────────

private object YtDashServer {

    private var serverSocket: ServerSocket? = null
    private var port: Int = 0
    private val manifests = ConcurrentHashMap<String, String>()

    @Synchronized
    fun ensureRunning() {
        if (serverSocket != null && !serverSocket!!.isClosed) return
        try {
            serverSocket = ServerSocket(0)
            port = serverSocket!!.localPort
            thread(isDaemon = true) {
                while (serverSocket != null && !serverSocket!!.isClosed) {
                    try { handleClient(serverSocket!!.accept()) } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) { Log.e("YtDash", "server start failed", e) }
    }

    fun register(xml: String): String? {
        if (port == 0) return null
        val id = UUID.randomUUID().toString().replace("-", "")
        manifests[id] = xml
        return "http://127.0.0.1:$port/$id.mpd"
    }

    private fun handleClient(client: Socket) {
        thread(isDaemon = true) {
            try {
                client.use {
                    val reader = BufferedReader(InputStreamReader(it.getInputStream()))
                    val writer = PrintWriter(it.getOutputStream(), true)
                    val line = reader.readLine() ?: return@thread
                    if (!line.startsWith("GET")) return@thread
                    val id = line.split(" ").getOrNull(1)
                        ?.trimStart('/')?.removeSuffix(".mpd") ?: return@thread
                    val xml = manifests[id]
                    if (xml != null) {
                        writer.print("HTTP/1.1 200 OK\r\n")
                        writer.print("Content-Type: application/dash+xml\r\n")
                        writer.print("Access-Control-Allow-Origin: *\r\n")
                        writer.print("Connection: close\r\n\r\n")
                        writer.print(xml)
                    } else {
                        writer.print("HTTP/1.1 404 Not Found\r\n\r\n")
                    }
                    writer.flush()
                }
            } catch (_: Exception) {}
        }
    }
}

// ── YouTube Extractor (بدون NewPipe — يعمل بدون dependency خارجي) ───────────

private const val YT_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
private const val YT_API = "https://www.youtube.com/youtubei/v1/player?key=$YT_KEY"
private val YTUA = "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36"

data class YtStreamInfo(
    val url: String,
    val mimeType: String,
    val height: Int,
    val initRange: String?,
    val indexRange: String?
)

data class YtAudioInfo(
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    val initRange: String?,
    val indexRange: String?,
    val language: String
)

private suspend fun extractYouTube(videoId: String): List<String> = withContext(Dispatchers.IO) {
    val results = mutableListOf<String>()
    try {
        // ── YouTube Internal API (مشابه لـ NewPipe) ──────────────────────
        val body = """
        {
          "videoId": "$videoId",
          "context": {
            "client": {
              "clientName": "ANDROID",
              "clientVersion": "19.09.37",
              "androidSdkVersion": 30,
              "userAgent": "$YTUA",
              "hl": "ar",
              "timeZone": "Asia/Riyadh",
              "utcOffsetMinutes": 180
            }
          },
          "params": "2AMBkaGB"
        }
        """.trimIndent()

        val conn = URL(YT_API).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", YTUA)
        conn.setRequestProperty("X-Youtube-Client-Name", "3")
        conn.setRequestProperty("X-Youtube-Client-Version", "19.09.37")
        conn.setRequestProperty("Origin", "https://www.youtube.com")
        conn.outputStream.write(body.toByteArray())

        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        val root = JSONObject(response)
        val streaming = root
            .optJSONObject("streamingData") ?: return@withContext results

        val duration = root
            .optJSONObject("videoDetails")
            ?.optLong("lengthSeconds") ?: 3600L

        // ── Adaptive Formats (فيديو + صوت منفصل) ────────────────────────
        val adaptive = streaming.optJSONArray("adaptiveFormats")
        val videoStreams = mutableListOf<YtStreamInfo>()
        val audioStreams = mutableListOf<YtAudioInfo>()

        if (adaptive != null) {
            for (i in 0 until adaptive.length()) {
                val fmt = adaptive.getJSONObject(i)
                val url = fmt.optString("url").ifEmpty {
                    decipher(fmt.optString("signatureCipher", fmt.optString("cipher", "")))
                }
                if (url.isEmpty()) continue

                val mime = fmt.optString("mimeType", "")
                val itag = fmt.optInt("itag", 0)

                when {
                    mime.startsWith("video/") && !fmt.has("audioQuality") -> {
                        val height = fmt.optInt("height", 0)
                        val initR = fmt.optJSONObject("initRange")?.let {
                            "${it.optString("start")}-${it.optString("end")}"
                        }
                        val indexR = fmt.optJSONObject("indexRange")?.let {
                            "${it.optString("start")}-${it.optString("end")}"
                        }
                        videoStreams.add(YtStreamInfo(
                            url = url,
                            mimeType = mime.substringBefore(";"),
                            height = height,
                            initRange = initR,
                            indexRange = indexR
                        ))
                    }
                    mime.startsWith("audio/") -> {
                        val br = fmt.optInt("bitrate", 128000)
                        val initR = fmt.optJSONObject("initRange")?.let {
                            "${it.optString("start")}-${it.optString("end")}"
                        }
                        val indexR = fmt.optJSONObject("indexRange")?.let {
                            "${it.optString("start")}-${it.optString("end")}"
                        }
                        val lang = fmt.optJSONObject("audioTrack")
                            ?.optString("id", "ar")
                            ?.substringBefore(".") ?: "ar"
                        audioStreams.add(YtAudioInfo(
                            url = url,
                            mimeType = mime.substringBefore(";"),
                            bitrate = br,
                            initRange = initR,
                            indexRange = indexR,
                            language = lang.uppercase()
                        ))
                    }
                }
            }
        }

        // ── Muxed Formats (احتياطي: فيديو+صوت مدمج) ─────────────────────
        val muxed = streaming.optJSONArray("formats")
        val muxedLinks = mutableListOf<Triple<String, Int, String>>()
        if (muxed != null) {
            for (i in 0 until muxed.length()) {
                val fmt = muxed.getJSONObject(i)
                val url = fmt.optString("url")
                if (url.isEmpty()) continue
                val height = fmt.optInt("height", 0)
                val mime = fmt.optString("mimeType", "video/mp4")
                muxedLinks.add(Triple(url, height, mime.substringBefore(";")))
            }
        }

        // ── بناء DASH Manifest ────────────────────────────────────────────
        YtDashServer.ensureRunning()

        // ترتيب الفيديو من الأعلى للأسفل
        val sortedVideo = videoStreams
            .distinctBy { it.height }
            .sortedByDescending { it.height }

        // تجميع الصوت حسب اللغة
        val audioByLang = audioStreams.groupBy { it.language }

        for (video in sortedVideo) {
            // أفضل صوت متوافق مع مشفّر الفيديو
            val preferWebm = video.mimeType.contains("webm")
            val bestAudios = if (audioByLang.isEmpty()) {
                emptyList()
            } else {
                // أخذ أفضل track لكل لغة
                audioByLang.values.mapNotNull { tracks ->
                    tracks.sortedWith(
                        compareByDescending<YtAudioInfo> {
                            if (preferWebm) it.mimeType.contains("webm") else it.mimeType.contains("mp4")
                        }.thenByDescending { it.bitrate }
                    ).firstOrNull()
                }
            }

            val xml = buildDashXml(video, bestAudios, duration)
            val localUrl = YtDashServer.register(xml)
            if (localUrl != null) results.add(localUrl)
        }

        // إضافة Muxed كـ fallback آخر
        muxedLinks.sortedByDescending { it.second }.forEach { (url, _, _) ->
            results.add(url)
        }

    } catch (e: Exception) {
        Log.e("YtExtractor", "extraction failed", e)
    }
    results
}

// ── Signature Decipher (بسيط — للروابط المشفرة) ────────────────────────────
private fun decipher(cipher: String): String {
    return try {
        val params = cipher.split("&").associate {
            val (k, v) = it.split("=", limit = 2)
            k to URLDecoder.decode(v, "UTF-8")
        }
        val url  = params["url"] ?: return ""
        val sig  = params["s"]   ?: return url
        val sp   = params["sp"]  ?: "signature"
        "$url&$sp=$sig"
    } catch (_: Exception) { "" }
}

// ── DASH XML Builder ────────────────────────────────────────────────────────
private fun buildDashXml(
    video: YtStreamInfo,
    audios: List<YtAudioInfo>,
    durationSec: Long
): String {
    val sb = StringBuilder()
    sb.append("""<?xml version="1.0" encoding="UTF-8"?>""")
    sb.append("""<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" profiles="urn:mpeg:dash:profile:isoff-on-demand:2011" type="static" minBufferTime="PT5S" mediaPresentationDuration="PT${durationSec}S">""")
    sb.append("<Period>")

    // Video
    val vCodec = if (video.mimeType.contains("webm")) "vp9" else "avc1.4d401f"
    val vSeg = if (video.initRange != null && video.indexRange != null)
        """<SegmentBase indexRange="${video.indexRange}"><Initialization range="${video.initRange}"/></SegmentBase>""" else ""
    sb.append("""<AdaptationSet mimeType="${video.mimeType}" subsegmentAlignment="true">""")
    sb.append("""<Representation id="v${video.height}" bandwidth="4000000" height="${video.height}" codecs="$vCodec">""")
    sb.append("""<BaseURL>${esc(video.url)}</BaseURL>$vSeg</Representation></AdaptationSet>""")

    // Audio tracks
    audios.forEachIndexed { i, audio ->
        val aCodec = if (audio.mimeType.contains("webm")) "opus" else "mp4a.40.2"
        val aSeg = if (audio.initRange != null && audio.indexRange != null)
            """<SegmentBase indexRange="${audio.indexRange}"><Initialization range="${audio.initRange}"/></SegmentBase>""" else ""
        sb.append("""<AdaptationSet mimeType="${audio.mimeType}" subsegmentAlignment="true" lang="${audio.language.lowercase()}">""")
        sb.append("""<Representation id="a$i" bandwidth="${audio.bitrate}" codecs="$aCodec">""")
        sb.append("""<BaseURL>${esc(audio.url)}</BaseURL>$aSeg</Representation></AdaptationSet>""")
    }

    sb.append("</Period></MPD>")
    return sb.toString()
}

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

// ── Main Composable ─────────────────────────────────────────────────────────

@Composable
fun YouTubeSnifferScreen(
    youtubeUrl: String,
    config: PlayerConfig,
    onBack: () -> Unit,
    onStreamReady: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableStateOf("loading") }
    var streams by remember { mutableStateOf<List<String>>(emptyList()) }

    // استخراج videoId
    val videoId = remember(youtubeUrl) {
        try {
            val uri = Uri.parse(youtubeUrl)
            uri.getQueryParameter("v")
                ?: uri.pathSegments.lastOrNull()
                ?: youtubeUrl.substringAfter("v=").substringBefore("&")
        } catch (_: Exception) { "" }
    }

    LaunchedEffect(videoId) {
        if (videoId.isEmpty()) { phase = "error"; return@LaunchedEffect }
        phase = "loading"
        val result = extractYouTube(videoId)
        if (result.isEmpty()) {
            phase = "error"
        } else {
            streams = result
            val primaryStream = result.first()
            val playerConfig = config.copy(
                url           = primaryStream,
                drm           = null,
                drmLicenseHeaders = null,
                useLocalProxy = false,
                headers       = PlayerHeaders(
                    userAgent = YTUA,
                    referer   = "https://www.youtube.com/",
                    origin    = "https://www.youtube.com"
                ),
                // السيرفرات المتبقية كـ fallback
                fallbackServers = result.drop(1).mapIndexed { i, url ->
                    com.apix.app.data.FallbackServer(
                        name = "جودة ${i + 2}",
                        url  = url
                    )
                }
            )
            onStreamReady(primaryStream)
            phase = "playing"
            // نمرر الـ config للـ PlayerScreen عبر onStreamReady
            // لكن نحتاج navigateTo — سنعالج هذا عبر ComposeActivity
        }
    }

    when (phase) {
        "playing" -> {
            val playerConfig = config.copy(
                url           = streams.firstOrNull() ?: "",
                drm           = null,
                drmLicenseHeaders = null,
                useLocalProxy = false,
                headers       = PlayerHeaders(
                    userAgent = YTUA,
                    referer   = "https://www.youtube.com/",
                    origin    = "https://www.youtube.com"
                ),
                fallbackServers = streams.drop(1).mapIndexed { i, url ->
                    com.apix.app.data.FallbackServer(name = "جودة ${i + 2}", url = url)
                }
            )
            PlayerScreen(config = playerConfig, onBack = onBack)
        }

        "error" -> {
            Box(
                modifier = modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Button(
                    onClick = onBack,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Gold
                    )
                ) {
                    Text("فشل تحميل الفيديو — رجوع", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        else -> {
            Box(
                modifier = modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = Gold, strokeWidth = 3.dp, modifier = Modifier.size(52.dp))
                    Text("جاري تحميل فيديو يوتيوب...", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(videoId, color = Color(0xFF888888), fontSize = 12.sp)
                }
            }
        }
    }
}