package com.apix.app.security

import android.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.net.HttpURLConnection

/**
 * g5 — HMAC-SHA256 request signer
 * يوقّع كل طلب لـ Supabase بهيدر مخفي
 */
object g5 {

    // توليد التوقيع
    @JvmStatic fun s(body: String): String {
        val ts  = System.currentTimeMillis() / 1000
        val sec = g4.kb()
        val msg = "$ts.$body"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(
            sec.toByteArray(Charsets.UTF_8),
            "HmacSHA256"
        ))
        val raw = mac.doFinal(msg.toByteArray(Charsets.UTF_8))
        val sig = Base64.encodeToString(raw, Base64.NO_WRAP)
        return "t=$ts,v1=$sig"
    }

    // إضافة الهيدر للاتصال
    @JvmStatic fun h(conn: HttpURLConnection, body: String) {
        try {
            val ts = System.currentTimeMillis() / 1000
            conn.setRequestProperty("X-S", s(body))
            conn.setRequestProperty("X-T", ts.toString())
        } catch (ignored: Throwable) {}
    }
}