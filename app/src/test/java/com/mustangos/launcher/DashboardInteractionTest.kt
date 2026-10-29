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
import com.mustangos.launcher.diagnostics.DiagnosticReporter
import com.mustangos.launcher.ui.LauncherViewModel
import com.mustangos.launcher.ui.MainActivity
import com.mustangos.launcher.settings.LauncherSettings
import com.mustangos.core.MustangProfile
import com.mustangos.core.MustangVersion
import com.mustangos.core.MustangBody
import com.mustangos.core.MustangPaint
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
import org.robolectric.shadows.ShadowDialog
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

    @Test fun mapButtonExpandsAndRestoresTheNativeMapWithoutLaunchingCarPlay() {
        val zlink = install("com.zjinnova.zlink")
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        val model = ViewModelProvider(activity)[LauncherViewModel::class.java]
        assertSame(app, model.getApplication<Application>())
        model.settings.setComponent("zlink", zlink)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(zlink, model.apps.selected("zlink"))
        assertTrue(activity.findViewById<View>(R.id.openMaps).hasOnClickListeners())
        assertEquals("Ampliar mapa", activity.findViewById<TextView>(R.id.openMaps).text.toString())
        val host = activity.findViewById<android.view.ViewGroup>(R.id.mapHost)
        val map = host.getChildAt(0)
        assertTrue(activity.findViewById<View>(R.id.openMaps).performClick())
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue(dialog.isShowing)
        assertEquals(0, host.childCount)
        assertNotNull(map.parent)
        assertNull(shadowOf(app).nextStartedActivity)
        dialog.dismiss()
        shadowOf(Looper.getMainLooper()).idle()
        assertSame(map, host.getChildAt(0))
        activity.findViewById<View>(R.id.openMaps).performClick()
        assertTrue(ShadowDialog.getLatestDialog().isShowing)
        ShadowDialog.getLatestDialog().dismiss()
        shadowOf(Looper.getMainLooper()).idle()
        assertSame(map, host.getChildAt(0))
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
    @Test fun hudTimelineSurvivesThePeriodicAndroidPollWithoutInventingProgress() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity=controller.get()
        val model=ViewModelProvider(activity)[LauncherViewModel::class.java]
        val flow=ReflectionHelpers.getField<kotlinx.coroutines.flow.MutableStateFlow<com.mustangos.launcher.carplay.ZLinkHudProbeState>>(model.zlinkHudProbe,"mutable")
        flow.value=com.mustangos.launcher.carplay.ZLinkHudProbeState(running=true,live=true,validMusicFrames=1,
            music=com.mustangos.core.ZLinkHudMusic(title="HUD fixture",artist="Fixture artist",durationMs=180000,elapsedMs=15000,
                updatedAtMs=android.os.SystemClock.elapsedRealtime()))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
        assertEquals("HUD fixture",activity.findViewById<TextView>(R.id.songTitle).text.toString())
        assertEquals("0:15 / 3:00",activity.findViewById<TextView>(R.id.songTime).text.toString())
        assertEquals(83,activity.findViewById<android.widget.ProgressBar>(R.id.progress).progress)
        val report=DiagnosticReporter(activity,model).create()
        assertFalse(report.contains("HUD fixture")); assertFalse(report.contains("Fixture artist"))
        controller.pause().stop().destroy()
    }

    @Test fun displayOnlyMetadataReachesDashboardAndExportWithoutExportingSongContents() {
        install("com.zjinnova.zlink")
        val mediaController = session("com.zjinnova.zlink")
        shadowOf(mediaController).setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, "Private display song")
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, "Private display subtitle").build())
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        val model = ViewModelProvider(activity)[LauncherViewModel::class.java]
        model.media.refresh(); shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Private display song", activity.findViewById<TextView>(R.id.songTitle).text.toString())
        assertEquals("Private display subtitle", activity.findViewById<TextView>(R.id.songArtist).text.toString())
        assertNull(model.media.state.value.facts.artist)
        val report = DiagnosticReporter(activity, model).create()
        assertTrue(report.contains("DISPLAY_TITLE{clave=true,valor=true}"))
        assertTrue(report.contains("texto_secundario_origen=DISPLAY_SUBTITLE"))
        assertFalse(report.contains("Private display"))
        controller.pause().stop().destroy()
    }

    @Test fun screenPreferenceCanBeChangedFromTheProductSettings() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        val model = ViewModelProvider(activity)[LauncherViewModel::class.java]
        assertTrue(activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
        activity.findViewById<View>(R.id.topSettings).performClick()
        val dialog = ShadowDialog.getLatestDialog()
        val rows = dialog.findViewById<android.view.ViewGroup>(R.id.settingsRows)
        val control = (0 until rows.childCount).mapNotNull { rows.getChildAt(it) as? android.view.ViewGroup }
            .flatMap { row -> (0 until row.childCount).map { row.getChildAt(it) } }
            .filterIsInstance<android.widget.Switch>().first { it.contentDescription == "Mantener pantalla encendida" }
        control.performClick()
        assertFalse(model.settings.keepScreenOn)
        assertEquals(0, activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        dialog.dismiss(); controller.pause().stop().destroy()
    }

    @Test fun chosenVehicleProfileAppearsOnHomeAndSettingsAfterRecreation() {
        val selected = MustangProfile(2020, MustangVersion.SHELBY_GT500, MustangBody.FASTBACK, MustangPaint.BLUE)
        LauncherSettings(app).digitalVehicle = false // Exercise the independent photographic-reference mode.
        LauncherSettings(app).mustangProfile = selected
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(selected.title, activity.findViewById<TextView>(R.id.vehicleProfileTitle).text.toString())
        assertEquals(selected.subtitle, activity.findViewById<TextView>(R.id.vehicleProfileSubtitle).text.toString())
        assertFalse(activity.findViewById<TextView>(R.id.vehicleProfileSubtitle).text.contains("4.0"))
        val until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(4)
        while (!activity.findViewById<View>(R.id.carImage).contentDescription.toString().contains("GT500") && System.nanoTime() < until) {
            shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10)
        }
        val description = activity.findViewById<View>(R.id.carImage).contentDescription.toString()
        assertTrue(description.contains("2020") && description.contains("GT500"))
        assertFalse(description.contains("Azul"))
        controller.pause().stop().start().resume().visible()
        activity.findViewById<View>(R.id.topSettings).performClick()
        val dialog = ShadowDialog.getLatestDialog()
        assertEquals("2020 · ${selected.subtitle}", dialog.findViewById<TextView>(R.id.settingsCarProfile).text.toString())
        assertEquals(description, dialog.findViewById<View>(R.id.settingsCarImage).contentDescription)
        assertTrue(dialog.findViewById<TextView>(R.id.settingsImageReference).text.contains("Referencia aproximada"))
        dialog.dismiss(); controller.pause().stop().destroy()
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
