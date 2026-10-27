package com.mustangos.launcher.carplay

import com.mustangos.launcher.appintegration.AppIntegrationManager

class CarPlayIntegrationManager(private val apps: AppIntegrationManager) {
    val connectionStatus: String get() = "Conexión CarPlay no verificable"
    val packageName: String? get() = apps.selected("zlink")?.packageName

    fun open(): Boolean {
        val configured = apps.selected("zlink")
        if (configured != null) return apps.launchComponent(configured)
        if (apps.launch("zlink")) return true
        return apps.launchPackage("com.zjinnova.zlink")
    }
}
