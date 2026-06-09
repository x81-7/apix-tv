package com.apix.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apix.app.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _sideMenus = MutableStateFlow<Map<String, SideMenu>>(emptyMap())
    val sideMenus: StateFlow<Map<String, SideMenu>> = _sideMenus.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                SupabaseRepository.ensureAnonymousAuth()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Authentication failed: ${e.message}") }
                return@launch
            }

            launch {
                SupabaseRepository.observeCategories().collect { cats ->
                    _uiState.update { state ->
                        val selected = cats.firstOrNull { it.id == state.selectedCategory?.id }
                            ?: cats.firstOrNull()
                        state.copy(
                            categories = cats,
                            selectedCategory = selected,
                            isLoading = false,
                            error = null
                        )
                    }
                    preloadImages(cats.flatMap { c -> c.channels?.values?.map { it.imageUrl } ?: emptyList() })
                }
            }

            launch {
                SupabaseRepository.observeSideMenus().collect { menus ->
                    _sideMenus.value = menus
                    preloadImages(menus.values.flatMap { m -> m.channels?.values?.map { it.imageUrl } ?: emptyList() })
                }
            }

            launch {
                SupabaseRepository.observeAppSettings().collect { settings ->
                    _uiState.update { state ->
                        state.copy(
                            showSettingsSection = settings.showSettingsSection,
                            appMode = settings.appMode,
                            externalSourceUrl = settings.externalSourceUrl
                        )
                    }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val cats = SupabaseRepository.observeCategories().firstOrNull()
                if (cats != null) {
                    _uiState.update { state ->
                        val selected = cats.firstOrNull { it.id == state.selectedCategory?.id }
                            ?: cats.firstOrNull() ?: state.selectedCategory
                        state.copy(categories = cats, selectedCategory = selected, error = null)
                    }
                }
                val menus = SupabaseRepository.observeSideMenus().firstOrNull()
                if (menus != null) {
                    _sideMenus.value = menus
                }
                kotlinx.coroutines.delay(400)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun preloadImages(urls: List<String>) {
        val ctx = getApplication<Application>().applicationContext
        urls.filter { it.isNotEmpty() }.distinct().forEach { url ->
            val request = coil.request.ImageRequest.Builder(ctx)
                .data(url)
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .build()
            coil.Coil.imageLoader(ctx).enqueue(request)
        }
    }

    fun selectCategory(category: Category) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun getVisibleChannels(): List<Channel> {
        val cat = _uiState.value.selectedCategory ?: return emptyList()
        return (cat.channels?.values ?: emptyList())
            .filter { !it.hidden }
            .sortedBy { it.sortOrder }
    }

    fun searchChannels(query: String): List<Channel> {
        if (query.isBlank()) return emptyList()
        val filter = query.lowercase().trim()
        val results = mutableListOf<Channel>()

        for (cat in _uiState.value.categories) {
            cat.channels?.values?.forEach { ch ->
                if (!ch.hidden && ch.name.lowercase().contains(filter)) {
                    results.add(ch)
                }
            }
        }

        for (menu in _sideMenus.value.values) {
            menu.channels?.values?.forEach { sc ->
                if (!sc.hidden && sc.name.lowercase().contains(filter)) {
                    results.add(Channel(
                        id = sc.id,
                        name = sc.name,
                        imageUrl = sc.imageUrl,
                        actionType = "direct_play",
                        stream = sc.stream,
                        androidStream = sc.androidStream,
                        androidActionType = sc.androidActionType,
                        forcedAspectRatio = sc.forcedAspectRatio,
                        lockAspectRatio = sc.lockAspectRatio,
                        useLocalProxy = sc.useLocalProxy
                    ))
                }
            }
        }

        return results
    }

    fun buildPlayerConfig(channel: Channel): PlayerConfig? {
        val config = PlayerConfig(title = channel.name)
        config.forcedAspectRatio = channel.forcedAspectRatio
        config.lockAspectRatio = channel.lockAspectRatio
        config.useLocalProxy = channel.useLocalProxy

        if (channel.androidStream?.url != null) {
            config.url = channel.androidStream!!.url!!
            config.actionType = channel.androidActionType
            config.webViewOrientation = channel.androidStream!!.webViewOrientation
            config.hybridPlayerType = when (channel.androidActionType) {
                "shaka_web" -> "shaka"
                "jw_web" -> "jw"
                else -> null
            }

            channel.androidStream!!.headers?.let { h ->
                val ref = h["referer"] ?: h["referrer"] ?: h["Referer"] ?: h["Referrer"]
                val ua = h["userAgent"] ?: h["useragent"] ?: h["User-Agent"]
                
                config.headers = PlayerHeaders(
                    userAgent = ua,
                    referer = ref,
                    cookie = h["cookie"] ?: h["Cookie"],
                    origin = h["origin"] ?: h["Origin"]
                )
            }

            val as_ = channel.androidStream!!
            val hasDrmConfig = as_.drmScheme != null || as_.drmKeyId != null ||
                    as_.drmKey != null || as_.drmClearKeyCombined != null ||
                    as_.drmLicenseUrl != null
            if (hasDrmConfig) {
                val scheme = as_.drmScheme ?: "clearkey"
                var keyId = as_.drmKeyId
                var key = as_.drmKey

                if (as_.drmClearKeyMode == "combined" && as_.drmClearKeyCombined != null) {
                    val parts = as_.drmClearKeyCombined!!.split(":")
                    if (parts.size == 2) { keyId = parts[0]; key = parts[1] }
                }
                if (key.isNullOrEmpty() && keyId != null && keyId.contains(":") && !keyId.contains("http")) {
                    val parts = keyId.split(":")
                    if (parts.size == 2 && parts[0].length >= 16 && parts[1].length >= 16) {
                        keyId = parts[0]; key = parts[1]
                    }
                }

                config.drm = PlayerDrm(
                    licenseUrl = as_.drmLicenseUrl,
                    scheme = scheme,
                    keyId = keyId,
                    key = key
                )
            }

            channel.androidStream!!.servers?.let { servers ->
                config.servers = servers
            }

            channel.androidStream!!.customHeaders?.let { ch ->
                val map = mutableMapOf<String, String>()
                ch.forEach { h -> if (h.key != null && h.value != null) map[h.key!!] = h.value!! }
                if (map.isNotEmpty()) config.customHeaders = map
            }
            channel.androidStream!!.drmLicenseHeaders?.let { lh ->
                val map = mutableMapOf<String, String>()
                lh.forEach { h -> if (h.key != null && h.value != null) map[h.key!!] = h.value!! }
                if (map.isNotEmpty()) config.drmLicenseHeaders = map
            }
            config.backupUrl = channel.androidStream!!.backupUrl
            config.fallbackServers = channel.androidStream!!.fallbackServers
            config.audioSources = channel.androidStream!!.audioSources
            config.subtitleUrl = channel.androidStream!!.subtitleUrl
            config.dynamicApi = channel.androidStream!!.dynamicApi
            config.forcedAspectRatio = channel.androidStream!!.forcedAspectRatio
            config.lockAspectRatio = channel.androidStream!!.lockAspectRatio
            config.logoOverlay = channel.androidStream!!.logoOverlay
        } else if (channel.stream?.url != null) {
            config.url = channel.stream!!.url!!
            
            val ref = channel.stream!!.referrer
            
            if (channel.stream!!.userAgent != null || ref != null) {
                config.headers = PlayerHeaders(
                    userAgent = channel.stream!!.userAgent,
                    referer = ref,
                    cookie = channel.stream!!.cookies
                )
            }
        }

        return if (config.url.isNotEmpty()) config else null
    }

    suspend fun buildVodPlayerConfig(
        item: MediaItem,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        sourceEngine: com.apix.app.vod.engine.SourceEngine
    ): PlayerConfig? {
        val vodBridge = com.apix.app.vod.engine.VodPlayerBridge(sourceEngine)
        val adaptedConfig = vodBridge.preparePlayerConfig(item, seasonNumber, episodeNumber)

        if (adaptedConfig.url.isEmpty()) return null

        val config = PlayerConfig(title = item.title)
        config.url = adaptedConfig.url

        adaptedConfig.headers?.let { h ->
            config.headers = PlayerHeaders(
                userAgent = h.userAgent,
                referer = h.referer,
                cookie = h.cookie
            )
        }

        config.subtitleUrl = adaptedConfig.subtitleUrl

        return config
    }
}

data class UiState(
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showSettingsSection: Boolean = true,
    val appMode: String = "HYBRID",
    val externalSourceUrl: String = ""
)
