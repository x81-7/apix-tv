package com.lagradost.cloudstream3.ui.download.button

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageButton
import com.lagradost.cloudstream3.ui.download.DownloadClickEvent

/** No-op download button so legacy data-binding layouts compile while downloads are disabled. */
class PieFetchButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ImageButton(context, attrs, defStyleAttr) {
    fun setDefaultClickListener(data: Any?, mirror: Any?, callback: (DownloadClickEvent) -> Unit) {
        setOnClickListener { }
    }
    fun resetView() {}
    fun setPersistentId(id: Int) {}
    fun setStatus(status: Any?) { visibility = GONE }
}
