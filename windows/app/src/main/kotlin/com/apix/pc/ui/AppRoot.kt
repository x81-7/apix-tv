package com.apix.pc.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.apix.pc.data.Channel
import com.apix.pc.data.StreamConfig
import com.apix.pc.player.PlayerHost
import com.apix.pc.ui.screens.GateScreen
import com.apix.pc.ui.screens.HomeScreen
import com.apix.pc.ui.screens.PinLockScreen
import com.apix.pc.ui.screens.SplashScreen
import com.apix.pc.ui.screens.SubChannelsScreen
import com.apix.pc.util.DeepLinkArgs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * 4-layer navigation, matching Android:
 * 1) Splash         → 2) Main (categories + grid)
 * → 3) SubChannels (one per open_submenu channel)
 * → 4) Player
 *
 * Deep-link payloads jump straight to the Player screen, mirroring Android.
 */
sealed interface Screen {
    data object Splash : Screen
    data object Gate : Screen
    data object Home : Screen
    data class SubChannels(val menuName: String, val channels: List<Channel>) : Screen
    data class Player(val stream: StreamConfig) : Screen
    data class PinLock(val title: String, val pin: String, val onUnlocked: () -> Unit) : Screen
}

@Composable
fun AppRoot(initialDeepLink: DeepLinkArgs.Parsed? = null) {
    val scope = remember { CoroutineScope(SupervisorJob()) }
    val vm = remember { AppViewModel(scope) }
    LaunchedEffect(Unit) { vm.load() }

    val backStack = remember { mutableStateListOf<Screen>() }
    var screen by remember { mutableStateOf<Screen>(Screen.Splash) }

    fun navigate(s: Screen) { backStack.add(screen); screen = s }
    fun back() {
        if (backStack.isNotEmpty()) screen = backStack.removeAt(backStack.lastIndex)
    }

    // External link → Player
    LaunchedEffect(initialDeepLink) {
        val obj = initialDeepLink?.decoded ?: return@LaunchedEffect
        val url = obj.optString("url").takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val drm = obj.optJSONObject("drm")
        val headers = obj.optJSONObject("headers")
        val custom = obj.optJSONObject("customHeaders")?.let { o ->
            buildMap { o.keys().forEach { k -> put(k, o.optString(k)) } }
        }
        screen = Screen.Player(
            StreamConfig(
                url = url,
                title = obj.optString("name", "External"),
                playerType = if (obj.optString("player") == "webview") "shaka_web" else "native",
                drmScheme = drm?.optString("scheme"),
                drmKeyId = drm?.optString("keyId"),
                drmKey = drm?.optString("key"),
                drmLicenseUrl = drm?.optString("licenseUrl"),
                userAgent = headers?.optString("userAgent"),
                referer = headers?.optString("referer"),
                customHeaders = custom
            )
        )
    }

    val openChannel: (Channel) -> Unit = open@{ ch ->
        // Per-channel PIN — prompt before opening anything (player, sub-menu,
        // external link). Mirrors Android's `ComposeActivity.handleChannelClick`
        // and iOS's `RootView.handleChannelTap`.
        val proceedWithoutPin = {
            when (ch.actionType) {
                "open_submenu" -> {
                    val menu = vm.sideMenuById(ch.sideMenuId)
                        ?: vm.sideMenus.firstOrNull { it.name.trim() == ch.name.trim() }
                        ?: vm.sideMenus.firstOrNull { it.name.contains(ch.name, true) || ch.name.contains(it.name, true) }
                    
                    menu?.let { m ->
                        val openMenu: () -> Unit = { navigate(Screen.SubChannels(m.name, m.channels)) }
                        val mPin = m.pinCode
                        if (!mPin.isNullOrBlank()) {
                            navigate(Screen.PinLock(m.name, mPin) {
                                back()       // pop the lock screen
                                openMenu()
                            })
                        } else openMenu()
                    }
                }
                "external_link" -> {
                    ch.externalUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
                    }
                }
                else -> {
                    vm.openChannel(ch)
                    vm.activeStream.value?.let { navigate(Screen.Player(it)) }
                }
            }
        }
        val chPin = ch.pinCode
        if (!chPin.isNullOrBlank()) {
            navigate(Screen.PinLock(ch.name, chPin) {
                back()                // pop the lock screen
                proceedWithoutPin()
            })
        } else {
            proceedWithoutPin()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val s = screen) {
            is Screen.Splash      -> SplashScreen(onDone = {
                screen = if (vm.gateEnabled.value && !vm.unlocked.value) Screen.Gate else Screen.Home
            })
            is Screen.Gate        -> GateScreen(
                vm = vm,
                onUnlock = { vm.unlocked.value = true; screen = Screen.Home },
                onDirectPlay = { stream -> screen = Screen.Player(stream) }
            )
            is Screen.Home        -> HomeScreen(vm = vm, onChannelClick = openChannel)
            is Screen.SubChannels -> SubChannelsScreen(
                menuName = s.menuName,
                channels = s.channels,
                onChannelClick = openChannel,
                onBack = { back() }
            )
            is Screen.Player      -> PlayerHost(stream = s.stream, onClose = { back() })
            is Screen.PinLock     -> PinLockScreen(
                title = s.title,
                expectedPin = s.pin,
                onUnlocked = s.onUnlocked,
                onCancel = { back() },
            )
        }
    }
}
