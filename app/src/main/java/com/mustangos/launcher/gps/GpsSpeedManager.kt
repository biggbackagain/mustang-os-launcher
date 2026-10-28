package com.mustangos.launcher.gps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
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

data class GpsState(
    val kmh: Float? = null,
    val status: String = "Ubicación precisa sin permiso",
    val accuracyMeters: Float? = null,
    val altitudeMeters: Double? = null,
    val bearingDegrees: Float? = null,
    val satellitesUsed: Int? = null,
    val satellitesVisible: Int? = null,
    val provider: String? = null,
    val fixAgeMs: Long? = null,
    // Volatile position for the visible map only; diagnostic exports must omit coordinates.
    val latitude: Double? = null,
    val longitude: Double? = null
)

class GpsSpeedManager(private val context: Context) : LocationListener {
    private data class FixDetails(val provider: String, val timeMs: Long, val accuracy: Float,
        val altitude: Double?, val bearing: Float?, val latitude: Double, val longitude: Double)
    private val manager = context.getSystemService(LocationManager::class.java)
    private val filter = SpeedFilter()
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var gpsFix: FixDetails? = null
    private var networkFix: FixDetails? = null
    private var satellites: Int? = null
    private var visibleSatellites: Int? = null
    private var gpsRegistrationFailed = false
    private var registeredProviders = emptySet<String>()
    private val mutable = MutableStateFlow(GpsState())
    val state = mutable.asStateFlow()
    private val gnss = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            if (!running) return
            satellites = (0 until status.satelliteCount).count(status::usedInFix)
            visibleSatellites = status.satelliteCount
            publish()
        }
    }
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
            val available = manager.allProviders.toSet()
            val registered = mutableSetOf<String>()
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (provider !in available) continue
                try {
                    manager.requestLocationUpdates(provider, 1000L, 0f, this, Looper.getMainLooper())
                    registered += provider
                } catch (_: SecurityException) { throw SecurityException() }
                  catch (_: RuntimeException) {
                    if (provider == LocationManager.GPS_PROVIDER) gpsRegistrationFailed = true
                    ErrorJournal.record(context, if (provider == LocationManager.GPS_PROVIDER) "GPS_REGISTRATION_FAILED" else "NETWORK_LOCATION_REGISTRATION_FAILED")
                }
            }
            registeredProviders = registered
            if (registered.isEmpty()) {
                running = false
                mutable.value = GpsState(status = if (available.isEmpty()) "El equipo no publica proveedores de ubicación" else "No se pudo iniciar ningún proveedor de ubicación")
                return
            }
            if (LocationManager.GPS_PROVIDER in registered) runCatching { manager.registerGnssStatusCallback(gnss, handler) }
            // Only a recent, timestamped cache is useful after resuming; old fixes never become current.
            registered.forEach { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull()?.let(::onLocationChanged) }
            expiry.run()
        } catch (_: SecurityException) {
            stop(); mutable.value = GpsState(); ErrorJournal.record(context, "GPS_PERMISSION_REVOKED")
        } catch (_: RuntimeException) {
            stop(); mutable.value = GpsState(status = "GPS no disponible"); ErrorJournal.record(context, "GPS_START_FAILED")
        }
    }

    fun stop() {
        running = false
        handler.removeCallbacks(expiry)
        runCatching { manager.removeUpdates(this) }
        runCatching { manager.unregisterGnssStatusCallback(gnss) }
        gpsFix = null; networkFix = null; satellites = null; visibleSatellites = null
        registeredProviders = emptySet(); gpsRegistrationFailed = false
        filter.reset()
        mutable.value = GpsState(status = "GPS detenido")
    }

    private fun enabled(provider: String) = runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
    private fun publish() {
        val now = SystemClock.elapsedRealtime()
        val gpsEnabled = enabled(LocationManager.GPS_PROVIDER)
        if (!gpsEnabled) filter.reset()
        val speed = filter.current(now).kmh
        val gpsDetails = gpsFix?.takeIf { gpsEnabled && now - it.timeMs in 0..8_000 }
        val networkDetails = networkFix?.takeIf { enabled(LocationManager.NETWORK_PROVIDER) && now - it.timeMs in 0..8_000 }
        val details = gpsDetails ?: networkDetails
        val status = when {
            gpsRegistrationFailed -> if (networkDetails != null) "Registro GPS no disponible · ubicación por red" else "No se pudo registrar el GPS del equipo"
            LocationManager.GPS_PROVIDER !in registeredProviders -> if (networkDetails != null)
                "El equipo no publica GPS · ubicación por red" else "El equipo no publica un proveedor GPS utilizable"
            !gpsEnabled -> if (networkDetails != null) "GPS desactivado · ubicación por red" else "GPS desactivado en ajustes"
            speed != null -> "GPS · lectura real"
            gpsDetails != null -> "GPS con posición · velocidad no publicada o sin precisión suficiente"
            networkDetails != null -> "Ubicación por red · esperando posición GPS"
            visibleSatellites != null && visibleSatellites!! > 0 -> "GPS: $visibleSatellites satélites visibles, ${satellites ?: 0} usados · esperando posición"
            else -> "Buscando señal GPS…"
        }
        mutable.value = GpsState(speed, status, details?.accuracy, details?.altitude, details?.bearing,
            if (gpsEnabled) satellites else null, if (gpsEnabled) visibleSatellites else null,
            details?.provider, details?.let { now - it.timeMs }, details?.latitude, details?.longitude)
    }

    private fun fixTime(location: Location, now: Long): Long? {
        if (location.elapsedRealtimeNanos > 0) {
            val time = location.elapsedRealtimeNanos / 1_000_000
            return time.takeIf { now - it in 0..5_000 }
        }
        // Some vendor providers omit monotonic time. A recent wall-clock timestamp is still required.
        if (location.time <= 0) return null
        val age = System.currentTimeMillis() - location.time
        return (now - age).takeIf { age in 0..5_000 }
    }

    override fun onLocationChanged(location: Location) {
        if (!running) return
        val now = SystemClock.elapsedRealtime()
        val time = fixTime(location, now) ?: run { publish(); return }
        if (!location.hasAccuracy() || !location.accuracy.isFinite() || location.accuracy !in 0f..100f ||
            !location.latitude.isFinite() || location.latitude !in -90.0..90.0 ||
            !location.longitude.isFinite() || location.longitude !in -180.0..180.0) { publish(); return }
        val details = FixDetails(location.provider ?: "", time, location.accuracy,
            location.altitude.takeIf { location.hasAltitude() && it.isFinite() },
            location.bearing.takeIf { location.hasBearing() && it.isFinite() }, location.latitude, location.longitude)
        when (location.provider) {
            LocationManager.GPS_PROVIDER -> {
                if (!enabled(LocationManager.GPS_PROVIDER)) { publish(); return }
                gpsFix = details
                if (location.hasSpeed()) filter.update(SpeedSample(location.speed, location.accuracy,
                    if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond else null, time), now)
            }
            LocationManager.NETWORK_PROVIDER -> networkFix = details
            else -> { publish(); return }
        }
        publish()
    }
    override fun onProviderEnabled(provider: String) { if (running) publish() }
    override fun onProviderDisabled(provider: String) {
        if (!running) return
        if (provider == LocationManager.GPS_PROVIDER) { filter.reset(); gpsFix = null; satellites = null; visibleSatellites = null }
        if (provider == LocationManager.NETWORK_PROVIDER) networkFix = null
        publish()
    }
    @Deprecated("Legacy provider callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
}
