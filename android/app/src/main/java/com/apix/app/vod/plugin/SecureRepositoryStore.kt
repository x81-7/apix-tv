package com.apix.app.vod.plugin

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class SecureRepositoryStore(context: Context) {

    private val prefs: SharedPreferences
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "secure_repo_store_v1"
        private const val KEY_REPOS = "repositories_list"
    }

    init {
        var sp: SharedPreferences? = null
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            sp = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("SecureRepo", "Keystore failed", e)
            sp = context.getSharedPreferences(PREFS_NAME + "_fallback", Context.MODE_PRIVATE)
        }
        prefs = sp!!
    }

    fun addRepository(name: String, url: String, secretToken: String? = null): PluginRepository {
        val cleanUrl = url.trim().removeSuffix("/")
        
        val manifestUrl = if (cleanUrl.endsWith(".json", ignoreCase = true)) {
            cleanUrl
        } else {
            "$cleanUrl/plugins.json"
        }

        val baseUrl = if (cleanUrl.endsWith(".json", ignoreCase = true)) {
            cleanUrl.substringBeforeLast("/")
        } else {
            cleanUrl
        }

        val repo = PluginRepository(
            id = UUID.randomUUID().toString(),
            name = name,
            baseUrl = baseUrl,
            manifestUrl = manifestUrl,
            secretToken = secretToken
        )

        val current = getRepositories().toMutableList()
        val iterator = current.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().manifestUrl == manifestUrl) {
                iterator.remove()
            }
        }
        
        current.add(repo)
        saveRepositories(current)
        
        return repo
    }

    fun getRepositories(): List<PluginRepository> {
        val json = prefs.getString(KEY_REPOS, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<PluginRepository>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun removeRepository(id: String) {
        val current = getRepositories().toMutableList()
        val iterator = current.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().id == id) {
                iterator.remove()
            }
        }
        saveRepositories(current)
    }

    private fun saveRepositories(list: List<PluginRepository>) {
        prefs.edit().putString(KEY_REPOS, gson.toJson(list)).apply()
    }
}
