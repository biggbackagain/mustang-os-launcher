package com.mustangos.launcher

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import com.mustangos.launcher.media.MediaAccessService
import com.mustangos.launcher.ui.LauncherViewModel
import com.mustangos.launcher.ui.MainActivity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "w1280dp-h720dp-land-mdpi")
class DashboardInteractionTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    @Before fun reset() {
        // AndroidX caches the first process Application; Robolectric creates a fresh one per method.
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory::class.java, "_instance", null)
        app.getSharedPreferences("launcher", 0).edit().clear().commit()
        // Routing fixtures model an already-authorized installation, not the first-run permission activity.
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun install(pkg: String): ComponentName {
        val component = ComponentName(pkg, "$pkg.Main")
        val info = ActivityInfo().apply {
            packageName = pkg; name = component.className; exported = true; enabled = true
            applicationInfo = ApplicationInfo().apply { packageName = pkg; enabled = true }
        }
        shadowOf(app.packageManager).addOrUpdateActivity(info)
        shadowOf(app.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            ResolveInfo().apply { activityInfo = info; nonLocalizedLabel = pkg }
        )
        return component
    }

    private fun session(pkg: String): MediaController {
        val token = Shadow.newInstanceOf(MediaSession.Token::class.java)
        val contract = Class.forName("android.media.session.ISessionController")
        val binder = android.os.Binder()
        ReflectionHelpers.setField(token, "mBinder", java.lang.reflect.Proxy.newProxyInstance(contract.classLoader, arrayOf(contract)) { _, method, _ ->
            when {
                method.name == "asBinder" -> binder
                method.returnType == Boolean::class.javaPrimitiveType -> false
                method.returnType == Int::class.javaPrimitiveType -> 0
                method.returnType == Long::class.javaPrimitiveType -> 0L
                else -> null
            }
        })
        return MediaController(app, token).also { controller ->
            shadowOf(controller).setPackageName(pkg)
            shadowOf(controller).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_NONE, 0, 0f).build())
            shadowOf(controller).setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Fixture")
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)).build())
            shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(ComponentName(app, MediaAccessService::class.java), true)
            shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(controller)
        }
    }

    @Test fun musicDockAndArtworkOpenCarPlayForThePhoneBridge() {
        val zlink = install("com.zjinnova.zlink")
        val music = install("com.tw.music")
        session("com.tw.bt")
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        val model = ViewModelProvider(activity)[LauncherViewModel::class.java]
        model.settings.setComponent("zlink", zlink)
        model.settings.setComponent("music", music)
        model.media.refresh(); shadowOf(Looper.getMainLooper()).idle()
        assertEquals("com.tw.bt", model.media.state.value.facts.source)
        for (id in listOf(R.id.dockMusic, R.id.albumArt, R.id.songTitle)) {
            activity.findViewById<View>(id).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(zlink, shadowOf(app).nextStartedActivity?.component)
        }
        controller.pause().stop().destroy()
    }

    @Test fun carPlayRouteButtonDoesNotRequireAnAndroidMapsApp() {
        val zlink = install("com.zjinnova.zlink")
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        val model = ViewModelProvider(activity)[LauncherViewModel::class.java]
        assertSame(app, model.getApplication<Application>())
        model.settings.setComponent("zlink", zlink)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(zlink, model.apps.selected("zlink"))
        assertTrue(activity.findViewById<View>(R.id.openMaps).hasOnClickListeners())
        assertEquals("Ver mapa en CarPlay", activity.findViewById<TextView>(R.id.openMaps).text.toString())
        assertTrue(activity.findViewById<View>(R.id.openMaps).performClick())
        shadowOf(Looper.getMainLooper()).idle()
        val opened = shadowOf(app).nextStartedActivity
        assertNotNull("tap did not open CarPlay; failure=${model.apps.lastFailure}", opened)
        assertEquals("failure=${model.apps.lastFailure}; opened=$opened", zlink, opened?.component)
        controller.pause().stop().destroy()
    }

    @Test fun unknownPlaybackDoesNotClaimPausedOrFabricateProgress() {
        install("com.zjinnova.zlink")
        session("com.zjinnova.zlink")
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        ViewModelProvider(activity)[LauncherViewModel::class.java].media.refresh()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("— / —", activity.findViewById<TextView>(R.id.songTime).text.toString())
        assertEquals("Reproducir o pausar", activity.findViewById<View>(R.id.playPause).contentDescription.toString())
        controller.pause().stop().destroy()
    }

    @Test fun androidBatteryAndAbsentGpsAreNotPresentedAsVehicleMeasurements() {
        app.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_PRESENT, true)
            .putExtra(BatteryManager.EXTRA_VOLTAGE, 12500).putExtra(BatteryManager.EXTRA_TEMPERATURE, 11))
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        val model = ViewModelProvider(activity)[LauncherViewModel::class.java]
        model.vehicle.poll(); shadowOf(Looper.getMainLooper()).idle()
        assertFalse(activity.findViewById<TextView>(R.id.topBattery).text.contains("12.5"))
        assertFalse(activity.findViewById<TextView>(R.id.vehicleInfo).text.contains("cerradas"))
        activity.findViewById<View>(R.id.dockEngine).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals("—", dialog.findViewById<TextView>(R.id.dashSpeed).text.toString())
        assertEquals("—", dialog.findViewById<TextView>(R.id.dashVoltage).text.toString())
        assertFalse(dialog.findViewById<TextView>(R.id.dashDoorStatus).text.contains("cerradas"))
        dialog.dismiss(); controller.pause().stop().destroy()
    }
}
