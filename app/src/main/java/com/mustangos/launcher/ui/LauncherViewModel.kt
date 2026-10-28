package com.mustangos.launcher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.mustangos.launcher.appintegration.AppIntegrationManager
import com.mustangos.launcher.carplay.CarPlayIntegrationManager
import com.mustangos.launcher.carplay.ZLinkServiceProbe
import com.mustangos.launcher.gps.GpsSpeedManager
import com.mustangos.launcher.media.MediaIntegrationManager
import com.mustangos.launcher.navigation.NavigationIntegrationManager
import com.mustangos.launcher.settings.LauncherSettings
import com.mustangos.launcher.vehicle.VehicleTelemetryManager
import com.mustangos.launcher.vehicle.OemReadOnlyManager

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    val settings = LauncherSettings(application)
    val apps = AppIntegrationManager(application, settings)
    val carplay = CarPlayIntegrationManager(apps)
    val zlinkProbe = ZLinkServiceProbe(application)
    val media = MediaIntegrationManager(application, settings)
    val gps = GpsSpeedManager(application)
    val navigation = NavigationIntegrationManager()
    val vehicle = VehicleTelemetryManager(application)
    val oem = OemReadOnlyManager(application, settings)
    var lastDiagnostic = ""
    fun start() { vehicle.start(); media.start(); gps.start(); oem.start(); vehicle.startReadings() }
    fun stop() { vehicle.stopReadings(); media.stop(); gps.stop(); oem.stop(); zlinkProbe.stop() }
    // Keep the passive ACC observer while ZLink is in front; it owns only the Application context.
    override fun onCleared() { stop(); vehicle.stop() }
}
