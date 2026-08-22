package com.lagradost.cloudstream3.ui.player

import com.lagradost.cloudstream3.apix.player.ApixLinkPolicy
import com.lagradost.cloudstream3.apix.player.ApixPlaybackSession
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType

/**
 * Opens the player *immediately* and streams servers into it while the APiX engine is still
 * searching the remaining extensions.
 *
 * The player view-model pushes every link it receives through [callback] into its live state, so
 * emitting links one by one from inside [generateLinks] makes them appear in the server list as
 * soon as they are resolved. The first (best) link starts playing right away.
 */
class ApixProgressiveGenerator(
    private val request: ApixPlaybackSession.Request,
) : NoVideoGenerator(request.id) {

    override suspend fun generateLinks(
        clearCache: Boolean,
        sourceTypes: Set<ExtractorLinkType>,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit,
        offset: Int,
        isCasting: Boolean,
    ): Boolean {
        var any = false
        request.resolver.resolve(
            onLink = { link ->
                if (!ApixLinkPolicy.isDownloadOnly(link) && sourceTypes.contains(link.type)) {
                    any = true
                    callback(link to null)
                }
            },
            onSub = { sub ->
                if (sub.isAllowedPlaybackSubtitle()) subtitleCallback(sub)
            },
        )
        return any
    }
}
