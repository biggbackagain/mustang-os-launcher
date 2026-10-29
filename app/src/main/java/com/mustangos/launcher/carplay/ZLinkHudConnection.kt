package com.mustangos.launcher.carplay

import android.os.Handler
import android.os.Looper
import com.mustangos.launcher.settings.LauncherSettings

/** Application-owned single connection: home and overlay share the vendor's single-client channel. */
class ZLinkHudConnection(private val settings: LauncherSettings,val probe: ZLinkHudProbe=ZLinkHudProbe()) {
    private val owners=mutableSetOf<String>()
    private val handler=Handler(Looper.getMainLooper())
    private val release=Runnable { if(owners.isEmpty() && probe.state.value.live) probe.stop() }
    fun visible(owner: String,visible: Boolean) {
        handler.removeCallbacks(release)
        if(visible) owners.add(owner) else owners.remove(owner)
        if(owners.isEmpty()) handler.postDelayed(release,500) else if(settings.zlinkHudEnabled && compatible() && !probe.state.value.running) probe.start(true)
    }
    fun setEnabled(enabled: Boolean) {
        settings.zlinkHudEnabled=enabled
        probe.stop()
        if(enabled && compatible() && owners.isNotEmpty()) probe.start(true)
    }
    fun retryOrSample() {
        probe.stop()
        if(compatible()) probe.start(settings.zlinkHudEnabled && owners.isNotEmpty())
    }
    private fun compatible() = (settings.component("zlink")?.packageName ?: "com.zjinnova.zlink")=="com.zjinnova.zlink"
}
