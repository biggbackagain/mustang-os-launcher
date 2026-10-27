package com.mustangos.launcher

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.location.Location
import android.location.LocationManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Looper
import android.os.SystemClock
import com.mustangos.core.MediaCommand
import com.mustangos.launcher.gps.GpsSpeedManager
import com.mustangos.launcher.media.MediaAccessService
import com.mustangos.launcher.media.MediaIntegrationManager
import com.mustangos.launcher.settings.LauncherSettings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class IntegrationLifecycleTest {
    private lateinit var app: Application
    @Before fun setup() { app = RuntimeEnvironment.getApplication(); app.getSharedPreferences("launcher", 0).edit().clear().commit() }
    private fun controller(): MediaController {
        val token = Shadow.newInstanceOf(MediaSession.Token::class.java)
        val contract = Class.forName("android.media.session.ISessionController")
        val binder = android.os.Binder()
        val proxy = java.lang.reflect.Proxy.newProxyInstance(contract.classLoader, arrayOf(contract)) { _, method, _ ->
            when {
                method.name == "asBinder" -> binder
                method.returnType == Boolean::class.javaPrimitiveType -> false
                method.returnType == Int::class.javaPrimitiveType -> 0
                method.returnType == Long::class.javaPrimitiveType -> 0L
                else -> null
            }
        }
        ReflectionHelpers.setField(token, "mBinder", proxy)
        return MediaController(app, token).also { shadowOf(it).setPackageName("test.projection") }
    }
    private fun authorizedMedia(controller: MediaController): MediaIntegrationManager {
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(ComponentName(app, MediaAccessService::class.java), true)
        shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(controller)
        return MediaIntegrationManager(app, LauncherSettings(app)).also { it.refresh() }
    }
    @Test fun metadataAndCapabilityCallbacksRemoveStaleData() {
        val controller = controller()
        val shadow = shadowOf(controller)
        shadow.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Test title").putString(MediaMetadata.METADATA_KEY_ARTIST, "Test artist").build())
        shadow.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_SKIP_TO_NEXT).setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        val media = authorizedMedia(controller)
        assertEquals("Test title", media.state.value.facts.title)
        assertTrue(media.state.value.facts.supports(MediaCommand.NEXT))
        assertFalse(media.state.value.facts.supports(MediaCommand.PAUSE))
        shadow.executeOnMetadataChanged(MediaMetadata.Builder().build())
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(media.state.value.facts.title)
        assertNull(media.state.value.facts.artist)
        media.stop()
        assertTrue(shadow.callbacks.isEmpty())
    }
    @Test fun revokedMediaAccessClearsSourceAndControls() {
        val media = authorizedMedia(controller())
        assertNotNull(media.state.value.facts.source)
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(ComponentName(app, MediaAccessService::class.java), false)
        media.refresh()
        assertNull(media.state.value.facts.source)
        assertFalse(media.command(MediaCommand.NEXT))
        media.stop()
    }
    @Test fun gpsListenerExpiresAndStopsWithoutBackgroundUpdates() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val locationManager = app.getSystemService(LocationManager::class.java)
        val shadow = shadowOf(locationManager)
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        val gps = GpsSpeedManager(app); gps.start()
        assertEquals(1, shadow.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).size)
        val fix = Location(LocationManager.GPS_PROVIDER).apply {
            speed = 10f; accuracy = 5f; speedAccuracyMetersPerSecond = 1f
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        gps.onLocationChanged(fix)
        assertEquals(36f, gps.state.value.kmh!!, 0.01f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
        assertNull(gps.state.value.kmh)
        gps.stop()
        assertTrue(shadow.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isEmpty())
        gps.onLocationChanged(fix)
        assertNull(gps.state.value.kmh)
    }
}
