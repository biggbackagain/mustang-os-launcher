package com.mustangos.launcher.carplay

import android.content.ComponentName
import com.mustangos.launcher.appintegration.AppIntegrationManager

class CarPlayIntegrationManager(private val apps: AppIntegrationManager) {
    val connectionStatus: String get() = "Conexión CarPlay no verificable"
    val packageName: String? get() = apps.selected("zlink")?.packageName

    fun open(): Boolean {
        if (apps.launchPackage("com.zjinnova.zlink")) return true
        val carPlayActivity = ComponentName(
            "com.zjinnova.zlink",
            "com.zjinnova.android.zlink.features.launcher.CarPlayActivity"
        )
        if (apps.launchDirect(carPlayActivity)) return true
        return apps.launch("zlink")
    }
}
