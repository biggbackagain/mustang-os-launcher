package com.mustangos.launcher.gps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.GnssStatus
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.mustangos.core.SpeedFilter
import com.mustangos.core.SpeedSample
import com.mustangos.launcher.diagnostics.ErrorJournal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GpsState(val kmh: Float? = null, val status: String = "Ubicación precisa sin permiso", val accuracyMeters: Float? = null, val altitudeMeters: Double? = null, val bearingDegrees: Float? = null, val satellitesUsed: Int? = null)
class GpsSpeedManager(private val context: Context) : LocationListener {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val filter = SpeedFilter()
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var detailTime = 0L
    private var accuracy: Float? = null
    private var altitude: Double? = null
    private var bearing: Float? = null
    private var satellites: Int? = null
    private val gnss = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            if (!running) return
            satellites = (0 until status.satelliteCount).count(status::usedInFix)
            publish()
        }
    }
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
    private var lastLocation: Location? = null
    private var lastLocationTime: Long = 0L

    fun start() {
        stop()
        if (!hasPermission()) { mutable.value = GpsState(); return }
        running = true
        try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            for (provider in providers) {
                if (manager.allProviders.contains(provider)) {
                    runCatching { manager.requestLocationUpdates(provider, 1000L, 0f, this, Looper.getMainLooper()) }
                }
            }
            if (manager.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                runCatching { manager.registerGnssStatusCallback(gnss, handler) }
            }
            expiry.run()
        } catch (_: SecurityException) { running = false; mutable.value = GpsState(); ErrorJournal.record(context, "GPS_PERMISSION_REVOKED") }
          catch (_: RuntimeException) { running = false; mutable.value = GpsState(status = "GPS no disponible"); ErrorJournal.record(context, "GPS_START_FAILED") }
    }
    fun stop() {
        running = false
        handler.removeCallbacks(expiry)
        runCatching { manager.removeUpdates(this) }
        runCatching { manager.unregisterGnssStatusCallback(gnss) }
        accuracy = null; altitude = null; bearing = null; satellites = null; detailTime = 0
        lastLocation = null; lastLocationTime = 0L
        filter.reset()
        mutable.value = GpsState(status = "GPS detenido")
    }
    private fun publish() {
        val enabled = runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val reading = filter.current(SystemClock.elapsedRealtime())
        val fresh = accuracy != null && SystemClock.elapsedRealtime() - detailTime <= 8000
        val kmh = reading.kmh
        val statusText = when {
            !enabled -> "GPS desactivado en ajustes"
            kmh != null -> "GPS · ${kmh.toInt()} km/h"
            satellites != null && satellites!! > 0 -> "Satélites: $satellites · esperando señal"
            else -> "Buscando señal GPS…"
        }
        mutable.value = GpsState(kmh, statusText, if (fresh) accuracy else null, if (fresh) altitude else null, if (fresh) bearing else null, satellites)
    }
    override fun onLocationChanged(location: Location) {
        if (!running) return
        val now = SystemClock.elapsedRealtime()
        val fixTimeMs = if (location.elapsedRealtimeNanos > 0) location.elapsedRealtimeNanos / 1_000_000 else now
        val age = now - fixTimeMs

        if (location.hasAccuracy() && location.accuracy in 0f..100f && (age in 0..10000 || location.elapsedRealtimeNanos <= 0)) {
            accuracy = location.accuracy
            detailTime = now
            if (location.hasAltitude() && location.altitude.isFinite()) altitude = location.altitude
            if (location.hasBearing() && location.bearing.isFinite()) bearing = location.bearing
        }

        var speedMps: Float? = if (location.hasSpeed() && location.speed >= 0f) location.speed else null
        if (speedMps == null && lastLocation != null && lastLocationTime > 0) {
            val dt = (now - lastLocationTime) / 1000f
            if (dt in 0.5f..5f) {
                val dist = lastLocation!!.distanceTo(location)
                speedMps = (dist / dt).coerceIn(0f, 80f)
            }
        }
        lastLocation = location
        lastLocationTime = now

        if (speedMps != null && (accuracy ?: 99f) <= 60f) {
            val speedAcc = if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond else null
            filter.update(SpeedSample(speedMps, accuracy ?: 15f, speedAcc, now), now)
        }
        publish()
    }
    override fun onProviderEnabled(provider: String) = publish()
    override fun onProviderDisabled(provider: String) = publish()
    @Deprecated("Legacy provider callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
}
