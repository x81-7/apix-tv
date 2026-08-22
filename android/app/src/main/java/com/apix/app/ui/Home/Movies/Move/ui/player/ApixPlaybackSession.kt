package com.lagradost.cloudstream3.apix.player

import android.content.Context
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import java.util.Locale

/**
 * Hand-off between the APiX UI and the player.
 *
 * The UI no longer waits for every extension to answer: it stores a *streaming* resolver here
 * and opens the player immediately. The player's generator then drives the resolver and receives
 * links/subtitles progressively (best sources first, the rest in the background).
 */
object ApixPlaybackSession {

    /** Streaming resolver: pushes each server/subtitle the moment it is found. */
    fun interface Resolver {
        suspend fun resolve(
            onLink: (ExtractorLink) -> Unit,
            onSub: (SubtitleData) -> Unit,
        )
    }

    class Request(val id: Int, val title: String, val resolver: Resolver)

    @Volatile
    var pending: Request? = null

    fun consume(): Request? {
        val value = pending
        pending = null
        return value
    }
}

/** Quality ranking, download-link rejection and per-provider quality memory. */
object ApixLinkPolicy {

    private const val PREFS = "apix_link_policy"
    private const val KEY_PREFIX = "score_"

    /** Words that mark a "download only" mirror — the user never wants those in the player. */
    private val DOWNLOAD_WORDS = listOf(
        "download", "تحميل", "تنزيل", "dl.", "/dl/", "?dl=", "&dl=",
        "direct download", "google drive", "drive.google", "mediafire", "mega.nz",
        "zippyshare", "uptobox", "1fichier", "torrent", "magnet", "userscloud",
        "solidfiles", "sendspace", "anonfiles", "gofile", "pixeldrain", "krakenfiles",
    )

    private val FILE_EXTS = listOf(".zip", ".rar", ".7z", ".apk", ".srt.zip")

    /** True when the link is a file-hosting / download mirror rather than a playable stream. */
    fun isDownloadOnly(link: ExtractorLink): Boolean {
        if (link.type == ExtractorLinkType.TORRENT || link.type == ExtractorLinkType.MAGNET) return true
        val haystack = (link.name + " " + link.source + " " + link.url).lowercase(Locale.ROOT)
        if (FILE_EXTS.any { haystack.contains(it) }) return true
        // an m3u8/dash manifest is always streamable, even if the host name looks like a locker
        if (link.type == ExtractorLinkType.M3U8 || link.type == ExtractorLinkType.DASH) return false
        return DOWNLOAD_WORDS.any { haystack.contains(it) }
    }

    /** Higher is better: quality first, streaming manifests slightly preferred over raw files. */
    fun score(link: ExtractorLink): Int =
        link.quality * 10 + if (link.type == ExtractorLinkType.M3U8) 1 else 0

    /** Arabic subtitles come first so the player can auto-select one instantly. */
    fun isArabic(sub: SubtitleData): Boolean {
        val n = (sub.originalName + " " + (sub.languageCode ?: "")).lowercase(Locale.ROOT)
        return n.contains("ar") && (n.contains("arab") || n.contains("عرب") || n.startsWith("ar"))
    }

    // ------------------------------------------------------- provider memory

    /** Remembers how good each provider was, so the next playback asks the best ones first. */
    fun rememberProvider(context: Context, provider: String, bestQuality: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getInt(KEY_PREFIX + provider, 0)
        // simple moving average keeps one lucky hit from dominating forever
        val next = ((old * 3) + bestQuality) / 4
        prefs.edit().putInt(KEY_PREFIX + provider, next).apply()
    }

    fun providerScore(context: Context, provider: String): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_PREFIX + provider, 0)
}
