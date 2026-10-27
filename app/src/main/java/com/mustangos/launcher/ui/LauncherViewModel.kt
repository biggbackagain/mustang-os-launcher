package com.mustangos.launcher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.mustangos.launcher.appintegration.AppIntegrationManager
import com.mustangos.launcher.carplay.CarPlayIntegrationManager
import com.mustangos.launcher.gps.GpsSpeedManager
import com.mustangos.launcher.media.MediaIntegrationManager
import com.mustangos.launcher.navigation.NavigationIntegrationManager
import com.mustangos.launcher.settings.LauncherSettings
import com.mustangos.launcher.vehicle.VehicleTelemetryManager

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    val settings = LauncherSettings(application)
    val apps = AppIntegrationManager(application, settings)
    val carplay = CarPlayIntegrationManager(apps)
    val media = MediaIntegrationManager(application, settings)
    val gps = GpsSpeedManager(application)
    val navigation = NavigationIntegrationManager()
    val vehicle = VehicleTelemetryManager()
    var lastDiagnostic = ""
    fun start() { media.start(); gps.start() }
    fun stop() { media.stop(); gps.stop() }
    override fun onCleared() { stop() }
}
