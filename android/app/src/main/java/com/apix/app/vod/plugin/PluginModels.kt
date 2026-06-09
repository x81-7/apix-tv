package com.apix.app.vod.plugin

data class PluginRepository(
    val id: String,
    val name: String,
    val baseUrl: String,
    val manifestUrl: String,
    val secretToken: String? = null
)

data class PluginManifest(
    val id: String,
    val name: String,
    val version: Int,
    val minAppVersion: Int,
    val author: String,
    val plugins: List<PluginEntry>
)

data class PluginEntry(
    val id: String,
    val name: String,
    val type: String,
    val downloadUrl: String,
    val version: Int,
    val className: String
)
