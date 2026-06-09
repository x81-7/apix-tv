package com.apix.app.vod.plugin

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class RepositoryManager(private val context: Context) {
    private val gson = Gson()
    private val pluginDir = context.getDir("plugins", Context.MODE_PRIVATE)

    suspend fun fetchManifest(repo: PluginRepository): PluginManifest? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(repo.manifestUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            
            repo.secretToken?.let {
                conn.setRequestProperty("Authorization", "Bearer $it")
            }
            
            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                return@withContext gson.fromJson(json, PluginManifest::class.java)
            }
        } catch (e: Exception) {
            Log.e("RepositoryManager", "Manifest fetch failed", e)
        } finally {
            conn?.disconnect()
        }
        return@withContext null
    }

    suspend fun downloadPlugin(repo: PluginRepository, entry: PluginEntry): File? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val downloadUrl = if (entry.downloadUrl.startsWith("http")) {
                entry.downloadUrl
            } else {
                "${repo.baseUrl}/${entry.downloadUrl}"
            }
            
            conn = URL(downloadUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            
            repo.secretToken?.let {
                conn.setRequestProperty("Authorization", "Bearer $it")
            }
            
            if (conn.responseCode == 200) {
                val file = File(pluginDir, "${entry.id}_${entry.version}.apk")
                conn.inputStream.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                return@withContext file
            }
        } catch (e: Exception) {
            Log.e("RepositoryManager", "Plugin download failed", e)
        } finally {
            conn?.disconnect()
        }
        return@withContext null
    }
}
