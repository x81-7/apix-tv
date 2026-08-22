package com.lagradost.cloudstream3.moviecompat

import android.content.res.Configuration
import android.os.Build
import com.lagradost.cloudstream3.CloudStreamApp

/** Minimal layout compatibility layer used by the isolated Movies player. */
object Globals {
    const val PHONE = 1
    const val TV = 2
    const val EMULATOR = 4

    @Volatile private var layoutId: Int = detectLayout()

    val isLayoutTv: Boolean get() = (layoutId and TV) != 0

    fun isLayout(flags: Int): Boolean = (layoutId and flags) != 0

    fun isLandscape(): Boolean {
        val context = CloudStreamApp.context ?: return isLayout(TV or EMULATOR)
        return isLayout(TV or EMULATOR) ||
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    fun updateTv(isTv: Boolean) {
        layoutId = if (isTv) TV else PHONE
    }

    private fun detectLayout(): Int {
        val c = CloudStreamApp.context
        if (c != null) {
            val ui = c.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
            if (ui == Configuration.UI_MODE_TYPE_TELEVISION ||
                c.packageManager.hasSystemFeature("android.software.leanback") ||
                c.packageManager.hasSystemFeature("android.hardware.type.television")) return TV
        }
        val fingerprint = Build.FINGERPRINT.lowercase()
        return if (fingerprint.contains("generic") || fingerprint.contains("emulator")) EMULATOR else PHONE
    }
}
