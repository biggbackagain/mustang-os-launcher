package com.mustangos.launcher.carplay

import com.mustangos.launcher.appintegration.AppIntegrationManager

// No undocumented intents, service bindings or connection inference from process/media state.
class CarPlayIntegrationManager(private val apps: AppIntegrationManager) {
    val connectionStatus: String get() = "Conexión CarPlay no verificable"
    val packageName: String? get() = apps.selected("zlink")?.packageName
    fun open(): Boolean = apps.launch("zlink")
}
