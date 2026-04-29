package com.apix.pc.security

import com.apix.pc.data.SupabaseClient
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Windows equivalent of `android/.../HandshakeClient.java` and
 * `ios/.../HandshakeService.swift`. Sends the hardware-bound device id and
 * platform="windows" to the Supabase device-handshake edge function so the
 * admin panel can ban abusers across all 3 platforms with the same logic.
 *
 * The edge function captures the IP from `x-forwarded-for` automatically.
 */
data class BanVerdict(
    val status: String = "ACTIVE",
    val banUntil: String? = null,
    val reason: String? = null,
    val telegramUrl: String? = null,
    val message: String? = null,
)

object HandshakeClient {

    fun handshake(appVersion: String = "1.0.0"): BanVerdict {
        return try {
            val deviceId = HardwareId.get()
            val body = JSONObject().apply {
                put("device_id", deviceId)
                put("platform", "windows")
                put("app_version", appVersion)
                put("is_fresh_install", false)
            }

            val url = URL("${SupabaseClient.baseUrl}/functions/v1/device-handshake")
            val c = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${SupabaseClient.anonKey}")
                setRequestProperty("apikey", SupabaseClient.anonKey)
                doOutput = true
                connectTimeout = 8_000
                readTimeout = 8_000
            }
            c.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = c.responseCode
            val raw = (if (code in 200..299) c.inputStream else c.errorStream)
                .bufferedReader().readText()
            val o = JSONObject(raw)
            BanVerdict(
                status = o.optString("status", "ACTIVE"),
                banUntil = o.optString("ban_until").takeIf { it.isNotBlank() && it != "null" },
                reason = o.optString("ban_reason").takeIf { it.isNotBlank() && it != "null" },
                telegramUrl = o.optString("telegram_url").takeIf { it.isNotBlank() && it != "null" },
                message = o.optString("message").takeIf { it.isNotBlank() && it != "null" },
            )
        } catch (t: Throwable) {
            // Fail-open on network errors — same behaviour as Android client.
            BanVerdict(status = "ERROR", message = t.message)
        }
    }
}
