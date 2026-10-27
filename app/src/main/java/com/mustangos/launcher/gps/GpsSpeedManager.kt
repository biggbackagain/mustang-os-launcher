package com.mustangos.launcher.gps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.mustangos.core.SpeedFilter
import com.mustangos.core.SpeedSample
import com.mustangos.launcher.diagnostics.ErrorJournal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GpsState(val kmh: Float? = null, val status: String = "Ubicación precisa sin permiso")
class GpsSpeedManager(private val context: Context) : LocationListener {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val filter = SpeedFilter()
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private val mutable = MutableStateFlow(GpsState())
    val state = mutable.asStateFlow()
    fun hasPermission() = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private val expiry = object : Runnable {
        override fun run() {
            if (!running) return
            if (!hasPermission()) { stop(); mutable.value = GpsState(); return }
            publish()
            handler.postDelayed(this, 1000)
        }
    }
    fun start() {
        stop()
        if (!hasPermission()) { mutable.value = GpsState(); return }
        running = true
        try {
            if (manager.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
            }
            expiry.run()
        } catch (_: SecurityException) { running = false; mutable.value = GpsState(); ErrorJournal.record(context, "GPS_PERMISSION_REVOKED") }
          catch (_: RuntimeException) { running = false; mutable.value = GpsState(status = "GPS no disponible"); ErrorJournal.record(context, "GPS_START_FAILED") }
    }
    fun stop() {
        running = false
        handler.removeCallbacks(expiry)
        runCatching { manager.removeUpdates(this) }
        filter.reset()
        mutable.value = GpsState(status = "GPS detenido")
    }
    private fun publish() {
        val enabled = runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val reading = if (enabled) filter.current(SystemClock.elapsedRealtime()) else filter.reset()
        mutable.value = GpsState(reading.kmh, when { !enabled -> "GPS desactivado"; reading.kmh == null -> "Sin señal GPS válida"; else -> "GPS · lectura real" })
    }
    override fun onLocationChanged(location: Location) {
        if (!running) return
        if (!location.hasSpeed() || !location.hasAccuracy()) { publish(); return }
        filter.update(SpeedSample(location.speed, location.accuracy, if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond else null, location.elapsedRealtimeNanos / 1_000_000), SystemClock.elapsedRealtime())
        publish()
    }
    override fun onProviderEnabled(provider: String) = publish()
    override fun onProviderDisabled(provider: String) = publish()
    @Deprecated("Legacy provider callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
}
