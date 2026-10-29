package com.mustangos.launcher.carplay

import com.mustangos.launcher.settings.LauncherSettings

/**
 * Application-owned single connection: home and overlay share the vendor's single-client channel.
 * Once the user enables it, reading continues while ZLink is in front: covers and route steps are only sent
 * when they change, so stopping when the launcher hides would miss them.
 */
class ZLinkHudConnection(private val settings: LauncherSettings,val probe: ZLinkHudProbe=ZLinkHudProbe()) {
    private val owners=mutableSetOf<String>()
    fun visible(owner: String,visible: Boolean) {
        if(visible) owners.add(owner) else owners.remove(owner)
        if(visible) ensureRunning()
    }
    fun ensureRunning() {
        if(settings.zlinkHudEnabled && compatible() && !probe.state.value.running) probe.start(true)
    }
    fun setEnabled(enabled: Boolean) {
        settings.zlinkHudEnabled=enabled
        probe.stop()
        if(enabled) ensureRunning()
    }
    fun retryOrSample() {
        probe.stop()
        if(compatible()) probe.start(settings.zlinkHudEnabled)
    }
    private fun compatible() = (settings.component("zlink")?.packageName ?: "com.zjinnova.zlink")=="com.zjinnova.zlink"
}
