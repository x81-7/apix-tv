package com.apix.app.ui.screens

import com.apix.app.data.FallbackServer
import com.apix.app.data.PlayerConfig
import com.apix.app.data.PlayerDrm
import com.apix.app.data.PlayerHeaders

/**
 * Cross-engine helpers for multi-server fallback support.
 *
 * A fallback server may declare which engine should play it:
 *  - "auto"  -> keep the current engine (no switch)
 *  - "exo"   -> native ExoPlayer  (engine = "native")
 *  - "hybrid"-> Hybrid WebView player (JW)     (engine = "hybrid", hybridPlayerType = "jw")
 *  - "shaka" -> Shaka WebView player           (engine = "hybrid", hybridPlayerType = "shaka")
 */

const val ENGINE_NATIVE = "native"
const val ENGINE_HYBRID = "hybrid"

/** Returns the target engine ("native"/"hybrid") for a fallback playerType, or null for "auto". */
fun engineForPlayerType(pt: String?): String? = when (pt?.lowercase()?.trim()) {
    null, "", "auto" -> null
    "exo", "native" -> ENGINE_NATIVE
    "hybrid", "jw", "jw_web" -> ENGINE_HYBRID
    "shaka", "shaka_web" -> ENGINE_HYBRID
    else -> null
}

/** The hybridPlayerType value ("jw"/"shaka") for a fallback playerType. */
fun hybridTypeForPlayerType(pt: String?): String = when (pt?.lowercase()?.trim()) {
    "jw", "jw_web", "hybrid" -> "jw"
    else -> "shaka"
}

/**
 * Builds a merged [PlayerConfig] for a fallback server, inheriting missing values
 * from [base]. Also stamps [PlayerConfig.hybridPlayerType] when the fallback declares
 * a hybrid/shaka engine so the Hybrid screen loads the right HTML.
 */
fun mergeFallbackConfig(base: PlayerConfig, fb: FallbackServer): PlayerConfig {
    val engine = engineForPlayerType(fb.playerType)
    return base.copy(
        url = fb.url ?: base.url,
        headers = PlayerHeaders(
            userAgent = fb.userAgent ?: base.headers?.userAgent,
            referer = fb.referer ?: base.headers?.referer,
            cookie = fb.cookie ?: base.headers?.cookie,
            origin = fb.origin ?: base.headers?.origin
        ),
        customHeaders = fb.customHeaders?.mapNotNull {
            val k = it.key; val v = it.value
            if (k != null && v != null) k to v else null
        }?.toMap() ?: base.customHeaders,
        drm = run {
            val scheme = fb.drmScheme
            if (scheme.isNullOrEmpty()) base.drm
            else {
                var kid = fb.drmKeyId
                var key = fb.drmKey
                if (fb.drmClearKeyMode == "combined" && !fb.drmClearKeyCombined.isNullOrEmpty()) {
                    val parts = fb.drmClearKeyCombined!!.split(":")
                    if (parts.size == 2) { kid = parts[0]; key = parts[1] }
                }
                PlayerDrm(licenseUrl = fb.drmLicenseUrl, scheme = scheme, keyId = kid, key = key)
            }
        },
        drmLicenseHeaders = fb.drmLicenseHeaders?.mapNotNull {
            val k = it.key; val v = it.value
            if (k != null && v != null) k to v else null
        }?.toMap() ?: base.drmLicenseHeaders,
        hybridPlayerType = if (engine == ENGINE_HYBRID) hybridTypeForPlayerType(fb.playerType) else base.hybridPlayerType
    )
}
