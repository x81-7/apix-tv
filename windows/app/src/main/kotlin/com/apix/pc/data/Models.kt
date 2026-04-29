package com.apix.pc.data

import org.json.JSONObject

/**
 * Schema mirrors the Supabase `channels` / `sub_channels` rows the Android app
 * reads (see android/app/src/main/java/com/apix/app/RemoteModels.java).
 *
 * Windows channels prefer `windows_stream` / `windows_action_type`, falling
 * back to `web_stream` (which the admin panel ships as the desktop default).
 */
data class StreamSpec(
    val url: String?,
    val userAgent: String?,
    val referer: String?,
    val cookie: String?,
    val origin: String?,
    val customHeaders: Map<String, String>?,
    val drmScheme: String?,
    val drmKeyId: String?,
    val drmKey: String?,
    val drmLicenseUrl: String?
) {
    companion object {
        fun fromJson(o: JSONObject?): StreamSpec? {
            if (o == null || o.length() == 0) return null
            val h = o.optJSONObject("headers")
            val ch = o.optJSONObject("customHeaders")
            val drm = o.optJSONObject("drm")
            val custom = ch?.let { buildMap { it.keys().forEach { k -> put(k, it.optString(k)) } } }
            return StreamSpec(
                url = o.optString("url").takeIf { it.isNotBlank() },
                userAgent = h?.optString("userAgent")?.takeIf { it.isNotBlank() },
                referer = h?.optString("referer")?.takeIf { it.isNotBlank() },
                cookie = h?.optString("cookie")?.takeIf { it.isNotBlank() },
                origin = h?.optString("origin")?.takeIf { it.isNotBlank() },
                customHeaders = custom,
                drmScheme = drm?.optString("scheme")?.takeIf { it.isNotBlank() },
                drmKeyId = drm?.optString("keyId")?.takeIf { it.isNotBlank() },
                drmKey = drm?.optString("key")?.takeIf { it.isNotBlank() },
                drmLicenseUrl = drm?.optString("licenseUrl")?.takeIf { it.isNotBlank() }
            )
        }
    }
}

data class Channel(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val categoryId: String?,
    val sortOrder: Int,
    val hidden: Boolean,
    val actionType: String,
    val externalUrl: String?,
    val sideMenuId: String?,
    val windowsActionType: String?,
    val windowsStream: StreamSpec?,
    val webStream: StreamSpec?,
    val offlineCacheEnabled: Boolean,
    val cacheVersion: Long = 1L,
    val forcedAspectRatio: String? = null,
    val lockAspectRatio: Boolean = false,
    /** Per-channel PIN — when set, prompt the user before opening the player. */
    val pinCode: String? = null
) {
    val effectiveStream: StreamSpec? get() = windowsStream ?: webStream
    val effectivePlayer: String get() = (windowsActionType ?: "native")

    companion object {
        fun fromJson(o: JSONObject): Channel {
            val ws = o.optJSONObject("windows_stream")
            return Channel(
                id = o.optString("id"),
                name = o.optString("name"),
                imageUrl = o.optString("image_url").takeIf { it.isNotBlank() },
                categoryId = o.optString("category_id").takeIf { it.isNotBlank() },
                sortOrder = o.optInt("sort_order", 0),
                hidden = o.optBoolean("hidden", false),
                actionType = o.optString("action_type", "direct_play"),
                externalUrl = o.optString("external_url").takeIf { it.isNotBlank() },
                sideMenuId = o.optString("side_menu_id").takeIf { it.isNotBlank() },
                windowsActionType = o.optString("windows_action_type").takeIf { it.isNotBlank() },
                windowsStream = StreamSpec.fromJson(ws),
                webStream = StreamSpec.fromJson(o.optJSONObject("web_stream")),
                offlineCacheEnabled = o.optBoolean("offline_cache_enabled", false),
                cacheVersion = o.optLong("cache_version", 1L),
                forcedAspectRatio = ws?.optString("forcedAspectRatio")?.takeIf { it.isNotBlank() },
                lockAspectRatio = ws?.optBoolean("lockAspectRatio", false) ?: false,
                pinCode = o.optString("pin_code").takeIf { it.isNotBlank() }
            )
        }
    }
}

data class Category(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val hidden: Boolean,
    val channels: List<Channel>
)

/** Side menu container. Sub-channels behave like Channels with a sideMenuId. */
data class SideMenu(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val pinCode: String?,
    val channels: List<Channel>
) {
    companion object {
        fun fromJson(o: JSONObject): SideMenu = SideMenu(
            id = o.optString("id"),
            name = o.optString("name"),
            sortOrder = o.optInt("sort_order", 0),
            pinCode = o.optString("pin_code").takeIf { it.isNotBlank() },
            channels = emptyList()
        )
    }
}

/** Resolved stream the player consumes. */
data class StreamConfig(
    val url: String,
    val title: String,
    val playerType: String,        // "native" | "shaka_web" | "webview"
    val drmScheme: String?,
    val drmKeyId: String?,
    val drmKey: String?,
    val drmLicenseUrl: String?,
    val userAgent: String?,
    val referer: String?,
    val customHeaders: Map<String, String>?
)
