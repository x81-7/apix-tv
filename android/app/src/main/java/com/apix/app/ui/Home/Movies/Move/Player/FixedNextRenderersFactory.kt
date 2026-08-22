package com.lagradost.cloudstream3.ui.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory

/**
 * Media3-only renderer factory kept as the compatibility class used by the Movies player.
 * The host APiX application is pinned to Media3 1.2.x, so the optional NextLib renderer
 * stack is deliberately not used here. This avoids pulling a second Media3 ABI.
 */
@OptIn(UnstableApi::class)
class FixedNextRenderersFactory(context: Context) : DefaultRenderersFactory(context)
