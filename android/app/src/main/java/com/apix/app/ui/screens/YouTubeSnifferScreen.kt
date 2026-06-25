package com.apix.app.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.apix.app.data.PlayerConfig
import com.apix.app.data.PlayerHeaders
import com.apix.app.ui.theme.Gold
import com.apix.app.ui.theme.MediumRed
import kotlinx.coroutines.delay

// ── الشاشة المستقلة: قناص يوتيوب (متصفح الظل فقط) ────────────────
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeSnifferScreen(
    youtubeUrl: String,
    config: PlayerConfig,
    onBack: () -> Unit,
    onStreamReady: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // المراحل: sniffing (بحث) -> playing (تشغيل) -> error (خطأ)
    var phase by remember { mutableStateOf("sniffing") }
    var statusMsg by remember { mutableStateOf("يجري التحميل انتظر قليلا...") }
    var finalStreamUrl by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var sniffFound by remember { mutableStateOf(false) }

    // مؤقت الأمان (Timeout) في حال كان الرابط معطوباً أو النت ضعيف
    LaunchedEffect(phase) {
        if (phase != "sniffing") return@LaunchedEffect
        delay(20000) // ننتظر 20 ثانية كحد أقصى
        if (!sniffFound) {
            phase = "error"
            statusMsg = "تعذر سحب الفيديو — تأكد من الرابط أو اتصالك"
        }
    }

    when (phase) {
        "playing" -> {
            // ── السحر هنا: تسليم الرابط للمشغل الأصلي ──
            // تم توحيد الـ User-Agent ليتطابق مع المتصفح الخفي
            val playerConfig = config.copy(
                url = finalStreamUrl,
                headers = PlayerHeaders(
                    userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36"
                )
            )
            PlayerScreen(config = playerConfig, onBack = onBack)
        }

        "sniffing" -> {
            Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
                
                // تحضير الرابط ليكون نسخة الهاتف (أسرع في التحميل والسحب)
                val fullUrl = remember(youtubeUrl) {
                    if (youtubeUrl.contains("youtu.be/")) {
                        "https://m.youtube.com/watch?v=${Uri.parse(youtubeUrl).lastPathSegment}"
                    } else {
                        youtubeUrl.replace("www.youtube.com", "m.youtube.com")
                    }
                }

                // المتصفح الخفي (حجمه بكسل واحد)
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(1, 1)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mediaPlaybackRequiresUserGesture = false // مهم جداً: السماح للتشغيل بدون تدخل المستخدم
                                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36"
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                    val reqUrl = request?.url?.toString() ?: return null
                                    
                                    // شروط الاصطياد (تجنب روابط الصوت فقط)
                                    val isMuxedVideo = reqUrl.contains("videoplayback") && !reqUrl.contains("mime=audio")
                                    val isHls = reqUrl.contains("manifest.googlevideo.com") && reqUrl.contains("m3u8")

                                    if ((isMuxedVideo || isHls) && !sniffFound) {
                                        sniffFound = true
                                        
                                        // ── [تنظيف الرابط] من أوامر التقطيع لكي يقبله الإكسو بلاير ──
                                        var cleanUrl = reqUrl
                                        if (cleanUrl.contains("videoplayback")) {
                                            cleanUrl = cleanUrl.replace(Regex("&range=[^&]*"), "")
                                                             .replace(Regex("&rn=[^&]*"), "")
                                                             .replace(Regex("&rbuf=[^&]*"), "")
                                                             .replace(Regex("&ump=[^&]*"), "")
                                        }
                                        
                                        view?.post {
                                            finalStreamUrl = cleanUrl
                                            onStreamReady(cleanUrl)
                                            phase = "playing" // الانتقال للمشغل فوراً
                                            
                                            // تنظيف الذاكرة وإغلاق المتصفح بعد الاصطياد
                                            view.stopLoading()
                                            view.destroy()
                                            webViewRef = null
                                        }
                                    }
                                    return null
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    // حقن سكربت يضغط على زر التشغيل ويكتم الصوت في الخلفية
                                    val js = "(function(){ var a=0; function p(){ a++; var v=document.querySelector('video'); if(v){v.muted=true; v.play().catch(e=>console.log(e));} var b=document.querySelector('.ytp-large-play-button, .ytp-play-button'); if(b) b.click(); if(a<5) setTimeout(p,1000);} setTimeout(p,500); })();"
                                    view?.evaluateJavascript(js, null)
                                }
                            }
                            webChromeClient = WebChromeClient()
                            webViewRef = this
                            loadUrl(fullUrl)
                        }
                    },
                    modifier = Modifier.size(1.dp)
                )

                // واجهة التحميل أثناء عمل المتصفح المخفي
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = MediumRed, strokeWidth = 3.dp, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(20.dp))
                    Text(statusMsg, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // تنظيف قوي عند الخروج المفاجئ
            DisposableEffect(Unit) {
                onDispose { 
                    webViewRef?.stopLoading()
                    webViewRef?.destroy()
                    webViewRef = null 
                }
            }
        }

        "error" -> {
            Box(modifier = modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Error, null, tint = MediumRed, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(statusMsg, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Gold)) {
                        Text("رجوع", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
