package com.apix.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.apix.app.BuildConfig
import com.apix.app.RemoteModels
import com.apix.app.SupabaseDataManager
import com.apix.app.security.g5
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// p6 = OfflineDataRepository
class p6(private val ctx: Context) {

    private val db = p1.get(ctx)
    private val sp: SharedPreferences =
        ctx.getSharedPreferences("m", Context.MODE_PRIVATE)

    companion object {
        private const val T  = "p6"
        private const val KV = "v"
        private const val KS = "s"
        private const val D30 = 30L * 24 * 60 * 60 * 1000
    }

    fun ok(): Boolean = db.r1().n1() > 0

    fun fresh(): Boolean =
        (System.currentTimeMillis() - sp.getLong(KS, 0L)) < D30

    fun local(): SupabaseDataManager.DataBundle? {
        return try {
            val cats  = db.r2().q1()
            val chans = db.r1().q1()
            if (cats.isEmpty()) null
            else toBundle(cats, chans)
        } catch (e: Exception) {
            Log.e(T, "local read failed", e)
            null
        }
    }

    fun remoteVer(): Int {
        return try {
            val url  = URL(BuildConfig.CLOUD_URL + "/functions/v1/data-version")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("apikey", BuildConfig.CLOUD_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.CLOUD_ANON_KEY}")
            g5.h(conn, "{}")
            conn.connectTimeout = 5000
            conn.readTimeout    = 5000
            if (conn.responseCode != 200) return -1
            val r = conn.inputStream.bufferedReader().readText()
            JSONObject(r).optInt("version", -1)
        } catch (e: Exception) {
            Log.w(T, "ver check: ${e.message}")
            -1
        }
    }

    fun localVer(): Int = sp.getInt(KV, -1)
    fun saveVer(v: Int) = sp.edit().putInt(KV, v).apply()

    fun sync(bundle: SupabaseDataManager.DataBundle) {
        try {
            val cats = bundle.categories.map { c ->
                p3(
                    a = c.id ?: "",
                    b = c.name ?: "",
                    c = c.icon ?: "",
                    d = c.sortOrder,
                    e = c.hidden // تم التعديل من isHidden إلى hidden لتطابق الجافا
                )
            }
            val chans = bundle.allChannels.map { c ->
                p2(
                    a = c.id ?: "",
                    b = c.name ?: "",
                    c = c.categoryId ?: "",
                    d = c.imageUrl ?: "", // تم التعديل من logo إلى imageUrl لتطابق الجافا
                    e = c.androidStreamJson ?: "{}",
                    f = c.sortOrder,
                    g = c.hidden, // تم التعديل لتطابق الجافا
                    h = System.currentTimeMillis()
                )
            }
            db.r2().d1(); db.r2().i1(cats)
            db.r1().d1(); db.r1().i1(chans)
            sp.edit().putLong(KS, System.currentTimeMillis()).apply()
            Log.d(T, "sync: ${cats.size}c ${chans.size}ch")
        } catch (e: Exception) {
            Log.e(T, "sync failed", e)
        }
    }

    private fun toBundle(
        cats: List<p3>,
        chans: List<p2>
    ): SupabaseDataManager.DataBundle {
        val b = SupabaseDataManager.DataBundle()
        b.categories = cats.map { e ->
            RemoteModels.Category().also {
                it.id        = e.a
                it.name      = e.b
                it.icon      = e.c
                it.sortOrder = e.d
                it.hidden    = e.e // مطابقة للجافا
            }
        }
        b.allChannels = chans.map { e ->
            RemoteModels.Channel().also {
                it.id               = e.a
                it.name             = e.b
                it.categoryId       = e.c
                it.imageUrl         = e.d // مطابقة للجافا
                it.androidStreamJson = e.e
                it.sortOrder        = e.f
                it.hidden           = e.g // مطابقة للجافا
            }
        }
        return b
    }
}
