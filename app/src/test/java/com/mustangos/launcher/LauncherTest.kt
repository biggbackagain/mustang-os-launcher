package com.mustangos.launcher

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.mustangos.launcher.appintegration.AppIntegrationManager
import com.mustangos.launcher.gps.GpsSpeedManager
import com.mustangos.launcher.media.MediaIntegrationManager
import com.mustangos.launcher.settings.LauncherSettings
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], qualifiers = "w1280dp-h720dp-land-mdpi")
class LauncherTest {
    private lateinit var app: Application
    private lateinit var prefs: LauncherSettings
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("launcher", 0).edit().clear().commit()
        prefs = LauncherSettings(app)
    }
    @Test fun startsWithoutPermissionsAndRestoresAfterExternalApp() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        assertFalse(activity.findViewById<ImageButton>(R.id.playPause).isEnabled)
        assertEquals("— / —", activity.findViewById<TextView>(R.id.songTime).text.toString())
        assertTrue(activity.findViewById<TextView>(R.id.gpsStatus).text.contains("permiso"))
        controller.pause().stop().start().resume().visible()
        assertFalse(activity.findViewById<ImageButton>(R.id.next).isEnabled)
        assertNotNull(activity.findViewById<View>(R.id.openCarplay))
        controller.pause().stop().destroy()
    }
    @Test fun absentZlinkCannotLaunchOrClaimConnection() {
        val apps = AppIntegrationManager(app, prefs)
        assertFalse(apps.launch("zlink"))
        assertTrue(apps.candidates().isEmpty())
    }
    private fun installLaunchable(): ComponentName {
        val component = ComponentName("test.vendor.projection", "test.vendor.projection.Start")
        val info = ActivityInfo().apply {
            packageName = component.packageName; name = component.className; exported = true; enabled = true
            applicationInfo = ApplicationInfo().apply { packageName = component.packageName; enabled = true }
        }
        shadowOf(app.packageManager).addOrUpdateActivity(info)
        val resolve = ResolveInfo().apply { activityInfo = info; nonLocalizedLabel = "ZLink" }
        shadowOf(app.packageManager).addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), resolve)
        return component
    }
    @Test fun detectsInstalledActivityAndLaunchesOnlySelectedComponent() {
        val component = installLaunchable()
        val apps = AppIntegrationManager(app, prefs)
        assertEquals(component.packageName, apps.candidates().single().packageName)
        prefs.setComponent("zlink", component)
        assertTrue(apps.launch("zlink"))
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(component, intent.component)
        assertEquals(0, intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    @Test fun removedPackageReturnsFailure() {
        prefs.setComponent("zlink", ComponentName("absent.app", "absent.app.Main"))
        assertFalse(AppIntegrationManager(app, prefs).launch("zlink"))
    }
    @Test fun gpsWithoutPermissionKeepsSpeedUnavailable() {
        val gps = GpsSpeedManager(app); gps.start()
        assertNull(gps.state.value.kmh)
        assertTrue(gps.state.value.status.contains("permiso")); gps.stop()
    }
    @Test fun mediaWithoutAccessHasNoDataOrControls() {
        val media = MediaIntegrationManager(app, prefs)
        media.refresh()
        assertNull(media.state.value.facts.source)
        assertFalse(media.command(com.mustangos.core.MediaCommand.PLAY))
        media.stop()
    }
    @Test fun diagnosticsExcludeContentAndIdentifiers() {
        val model = com.mustangos.launcher.ui.LauncherViewModel(app)
        val text = com.mustangos.launcher.diagnostics.DiagnosticReporter(app, model).create()
        assertTrue(text.contains("SDK real: 32"))
        assertTrue(text.contains("Conexión CarPlay no verificable"))
        assertFalse(text.contains(android.os.Build.FINGERPRINT))
    }
    @Test fun dashboardLaysOutAtTargetResolutionWithLargeControls() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val root = controller.get().findViewById<View>(R.id.root)
        root.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1280, 720)
        for (id in listOf(R.id.openCarplay, R.id.openMaps, R.id.dockCarplay, R.id.playPause, R.id.gpsStatus)) {
            val view = root.findViewById<View>(id)
            assertTrue("Control $id has a large touch target", view.height >= 48)
            assertTrue(view.width >= 48)
        }
        controller.pause().stop().destroy()
    }
}
