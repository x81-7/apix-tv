package com.apix.app.data

data class Category(
    var id: String = "",
    var name: String = "",
    var sortOrder: Int = 0,
    var channels: Map<String, Channel>? = null,
    var hidden: Boolean = false
)

data class Channel(
    var id: String = "",
    var name: String = "",
    var imageUrl: String = "",
    var sortOrder: Int = 0,
    var actionType: String = "direct_play",
    var hidden: Boolean = false,
    var stream: StreamConfig? = null,
    var sideMenuId: String? = null,
    var externalUrl: String? = null,
    var preferredPlayer: String? = null,
    var androidStream: AndroidStreamConfig? = null,
    var androidActionType: String? = null,
    var pinCode: String? = null,
    var forcedAspectRatio: String? = null,
    var lockAspectRatio: Boolean = false
)

data class StreamConfig(
    var url: String? = null,
    var userAgent: String? = null,
    var referrer: String? = null,
    var cookies: String? = null,
    var drm: DRMConfig? = null
)

data class CustomHeader(
    var key: String? = null,
    var value: String? = null
)

data class AudioSource(
    var name: String? = null,
    var url: String? = null
)

data class LogoOverlay(
    var url: String? = null,
    var position: String? = null,
    var offsetX: Int = 0,
    var offsetY: Int = 0,
    var width: Int = 80,
    var height: Int = 40,
    var opacity: Float = 1.0f
)

data class DynamicApiConfig(
    var enabled: Boolean = false,
    var endpoint: String? = null,
    var method: String? = "GET",
    var channelIdParam: String? = null,
    var headers: Map<String, String>? = null,
    // When set, the API response's "token" field is appended to the ORIGINAL stream URL
    // as `?{tokenParam}={token}` instead of replacing the whole URL. Useful for streams
    // that need a per-request token (e.g. ?token=xxx).
    var tokenParam: String? = null,
    // Optional: where in the JSON to read the token (default: "token").
    var tokenJsonField: String? = null
)

data class AndroidStreamConfig(
    var url: String? = null,
    var webViewOrientation: String? = null,
    var headers: Map<String, String>? = null,
    var customHeaders: List<CustomHeader>? = null,
    var intentUri: String? = null,
    var drmLicenseUrl: String? = null,
    var drmScheme: String? = null,
    var drmKeyId: String? = null,
    var drmKey: String? = null,
    var drmClearKeyCombined: String? = null,
    var drmClearKeyMode: String? = null,
    var drmLicenseHeaders: List<CustomHeader>? = null,
    var servers: List<Server>? = null,
    var fallbackServers: List<FallbackServer>? = null,
    var backupUrl: String? = null,
    var audioSources: List<AudioSource>? = null,
    var subtitleUrl: String? = null,
    var dynamicApi: DynamicApiConfig? = null,
    var forcedAspectRatio: String? = null,
    var lockAspectRatio: Boolean = false,
    var logoOverlay: LogoOverlay? = null
)

data class DRMConfig(
    var clearKeyId: String? = null,
    var clearKeyKey: String? = null,
    var clearKeyCombined: String? = null,
    var clearKeyUrl: String? = null,
    var clearKeyMode: String? = null
)

data class Server(
    var name: String? = null,
    var url: String? = null
)

/**
 * Full-power fallback server — same fields as the primary stream so the
 * player can switch to it seamlessly on error/stop without losing headers/DRM.
 */
data class FallbackServer(
    var id: String? = null,
    var name: String? = null,
    var url: String? = null,
    var userAgent: String? = null,
    var referer: String? = null,
    var origin: String? = null,
    var cookie: String? = null,
    var customHeaders: List<CustomHeader>? = null,
    var drmScheme: String? = null,
    var drmLicenseUrl: String? = null,
    var drmKeyId: String? = null,
    var drmKey: String? = null,
    var drmClearKeyCombined: String? = null,
    var drmClearKeyMode: String? = null,
    var drmLicenseHeaders: List<CustomHeader>? = null
)

data class SideMenu(
    var id: String = "",
    var name: String = "",
    var channels: Map<String, SubChannel>? = null,
    var pinCode: String? = null
)

data class AppSettings(
    var showSettingsSection: Boolean = true
)

data class SubChannel(
    var id: String = "",
    var name: String = "",
    var imageUrl: String = "",
    var sortOrder: Int = 0,
    var stream: StreamConfig? = null,
    var preferredPlayer: String? = null,
    var hidden: Boolean = false,
    var androidStream: AndroidStreamConfig? = null,
    var androidActionType: String? = null,
    var pinCode: String? = null,
    var forcedAspectRatio: String? = null,
    var lockAspectRatio: Boolean = false
)

/**
 * Player config passed between screens via JSON
 */
data class PlayerConfig(
    var url: String = "",
    var title: String = "",
    var actionType: String? = null,
    var hybridPlayerType: String? = null,
    var webViewOrientation: String? = null,
    var headers: PlayerHeaders? = null,
    var customHeaders: Map<String, String>? = null,
    var drm: PlayerDrm? = null,
    var drmLicenseHeaders: Map<String, String>? = null,
    var servers: List<Server>? = null,
    var fallbackServers: List<FallbackServer>? = null,
    var backupUrl: String? = null,
    var audioSources: List<AudioSource>? = null,
    var subtitleUrl: String? = null,
    var dynamicApi: DynamicApiConfig? = null,
    var forcedAspectRatio: String? = null,
    var lockAspectRatio: Boolean = false,
    var logoOverlay: LogoOverlay? = null
)

data class PlayerHeaders(
    var userAgent: String? = null,
    var referer: String? = null,
    var cookie: String? = null,
    var origin: String? = null
)

data class PlayerDrm(
    var licenseUrl: String? = null,
    var scheme: String? = null,
    var keyId: String? = null,
    var key: String? = null
)
