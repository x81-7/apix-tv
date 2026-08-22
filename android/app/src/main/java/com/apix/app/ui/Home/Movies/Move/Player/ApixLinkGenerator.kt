package com.lagradost.cloudstream3.ui.player

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType

/**
 * Plays links that were already resolved by the APiX engine (every extension is searched
 * up-front), so the player's server list contains *all* mirrors instead of a single URL.
 */
class ApixLinkGenerator(
    private val links: List<ExtractorLink>,
    private val subtitles: List<SubtitleData> = emptyList(),
    id: Int? = null,
) : NoVideoGenerator(id) {

    override suspend fun generateLinks(
        clearCache: Boolean,
        sourceTypes: Set<ExtractorLinkType>,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit,
        offset: Int,
        isCasting: Boolean,
    ): Boolean {
        var any = false
        links.forEach { link ->
            if (sourceTypes.contains(link.type)) {
                callback(link to null)
                any = true
            }
        }
        subtitles.forEach { sub ->
            if (sub.isAllowedPlaybackSubtitle()) subtitleCallback(sub)
        }
        return any
    }
}
