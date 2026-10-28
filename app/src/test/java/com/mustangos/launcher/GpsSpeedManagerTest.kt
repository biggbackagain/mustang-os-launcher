package com.mustangos.launcher

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import com.mustangos.launcher.gps.GpsSpeedManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class GpsSpeedManagerTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val manager get() = app.getSystemService(LocationManager::class.java)
    @Before fun setup() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
    }
    private fun fix(provider: String = LocationManager.GPS_PROVIDER, speed: Float? = 10f, ageMs: Long = 0) = Location(provider).apply {
        accuracy = 5f; latitude = 19.0; longitude = -99.0
        if (speed != null) this.speed = speed
        time = System.currentTimeMillis() - ageMs
        elapsedRealtimeNanos = (SystemClock.elapsedRealtime() - ageMs).coerceAtLeast(0) * 1_000_000
    }
    @Test fun recentGpsCacheProducesRealReadingWithoutWaitingForNextCallback() {
        shadowOf(manager).setLastKnownLocation(LocationManager.GPS_PROVIDER, fix(speed = 8f))
        val gps = GpsSpeedManager(app); gps.start()
        assertEquals(28.8f, gps.state.value.kmh!!, 0.01f)
        assertEquals(LocationManager.GPS_PROVIDER, gps.state.value.provider)
        gps.stop()
    }
    @Test fun realCoarseNetworkPositionCanCenterMapWithoutCreatingGpsSpeed() {
        val gps = GpsSpeedManager(app); gps.start()
        gps.onLocationChanged(fix(LocationManager.NETWORK_PROVIDER,30f).apply { accuracy = 1500f })
        assertEquals(19.0,gps.state.value.latitude!!,0.0001)
        assertEquals(1500f,gps.state.value.accuracyMeters!!,0f); assertNull(gps.state.value.kmh)
        assertEquals(1,gps.state.value.callbacks); assertEquals(0,gps.state.value.rejectedCallbacks)
        gps.onLocationChanged(fix(ageMs=20_000)); assertEquals(1,gps.state.value.rejectedCallbacks)
        gps.stop()
    }
    @Test fun oldOrInaccurateCallbacksCannotBorrowPreviousAccuracyOrRefreshSpeed() {
        val gps = GpsSpeedManager(app); gps.start(); gps.onLocationChanged(fix())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9))
        gps.onLocationChanged(fix(speed = 30f, ageMs = 10_000))
        assertNull(gps.state.value.kmh)
        val missingAccuracy = fix(speed = 20f).apply { removeAccuracy() }
        gps.onLocationChanged(missingAccuracy)
        assertNull(gps.state.value.kmh); assertNull(gps.state.value.accuracyMeters)
        gps.stop()
    }
    @Test fun networkPositionNeverDrivesSpeedometerAndGpsDetailsDoNotInheritNetworkFields() {
        val gps = GpsSpeedManager(app); gps.start()
        gps.onLocationChanged(fix(LocationManager.NETWORK_PROVIDER, 30f).apply { altitude = 800.0; bearing = 33f })
        assertNull(gps.state.value.kmh)
        assertEquals(LocationManager.NETWORK_PROVIDER, gps.state.value.provider)
        assertTrue(gps.state.value.status.contains("red"))
        gps.onLocationChanged(fix())
        assertEquals(36f, gps.state.value.kmh!!, 0.01f)
        assertNull(gps.state.value.altitudeMeters); assertNull(gps.state.value.bearingDegrees)
        gps.stop()
    }
    @Test fun twoPositionsWithoutPublishedSpeedDoNotCreateSpeedFromJitter() {
        val gps = GpsSpeedManager(app); gps.start()
        gps.onLocationChanged(fix(speed = null))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        gps.onLocationChanged(fix(speed = null).apply { latitude += 0.001 })
        assertNull(gps.state.value.kmh)
        assertNotNull(gps.state.value.accuracyMeters)
        assertTrue(gps.state.value.status.contains("posición"))
        gps.stop()
    }
    @Test fun vendorFixWithoutMonotonicTimeStillRequiresRecentWallClockTime() {
        val gps = GpsSpeedManager(app); gps.start()
        gps.onLocationChanged(fix().apply { elapsedRealtimeNanos = 0 })
        assertEquals(36f, gps.state.value.kmh!!, 0.01f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
        gps.onLocationChanged(fix(speed = 20f).apply { elapsedRealtimeNanos = 0; time = 0 })
        assertNull(gps.state.value.kmh)
        gps.stop()
    }
    @Test fun disablingGpsRemovesSpeedWhileLabelledNetworkPositionCanRemain() {
        val gps = GpsSpeedManager(app); gps.start()
        gps.onLocationChanged(fix())
        gps.onLocationChanged(fix(LocationManager.NETWORK_PROVIDER, 30f))
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        gps.onProviderDisabled(LocationManager.GPS_PROVIDER)
        assertNull(gps.state.value.kmh)
        assertEquals(LocationManager.NETWORK_PROVIDER, gps.state.value.provider)
        assertTrue(gps.state.value.status.contains("desactivado"))
        gps.stop()
    }
    @Test fun stopReleasesBothProvidersAndRejectsFurtherCallbacks() {
        val gps = GpsSpeedManager(app); gps.start(); gps.stop()
        assertTrue(shadowOf(manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isEmpty())
        assertTrue(shadowOf(manager).getLocationUpdateListeners(LocationManager.NETWORK_PROVIDER).isEmpty())
        assertTrue(shadowOf(manager).getLocationUpdateListeners(LocationManager.PASSIVE_PROVIDER).isEmpty())
        gps.onLocationChanged(fix()); assertNull(gps.state.value.kmh)
    }
    @Test fun mapCoordinatesAreVolatileFreshMeasurementsAndDisappearWithFix() {
        val gps = GpsSpeedManager(app); gps.start()
        assertNull(gps.state.value.latitude); assertNull(gps.state.value.longitude)
        gps.onLocationChanged(fix(speed = null))
        assertEquals(19.0, gps.state.value.latitude!!, 0.0001)
        assertEquals(-99.0, gps.state.value.longitude!!, 0.0001)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9))
        assertNull(gps.state.value.latitude); assertNull(gps.state.value.longitude)
        gps.onLocationChanged(fix().apply { latitude = Double.NaN })
        assertNull(gps.state.value.latitude)
        gps.onLocationChanged(fix()); gps.stop()
        assertNull(gps.state.value.latitude); assertNull(gps.state.value.longitude)
    }
}
