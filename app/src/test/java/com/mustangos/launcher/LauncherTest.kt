package com.mustangos.launcher

import android.app.Application
import android.content.DialogInterface
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import com.mustangos.launcher.appintegration.AppIntegrationManager
import com.mustangos.launcher.gps.GpsSpeedManager
import com.mustangos.launcher.media.MediaIntegrationManager
import com.mustangos.launcher.settings.LauncherSettings
import com.mustangos.launcher.ui.MainActivity
import com.mustangos.launcher.ui.LauncherViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], qualifiers = "w1280dp-h720dp-land-mdpi")
class LauncherTest {
    private lateinit var app: Application
    private lateinit var prefs: LauncherSettings
    @Before fun setup() {
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory::class.java, "_instance", null)
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("launcher", 0).edit().clear().commit()
        prefs = LauncherSettings(app)
    }
    @Test fun startsWithoutPermissionsAndRestoresAfterExternalApp() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        assertFalse(activity.findViewById<ImageButton>(R.id.playPause).isEnabled)
        assertEquals("— / —", activity.findViewById<TextView>(R.id.songTime).text.toString())
        assertEquals("Permitir GPS", activity.findViewById<TextView>(R.id.gpsStatus).text.toString())
        assertFalse(ViewModelProvider(activity)[com.mustangos.launcher.ui.LauncherViewModel::class.java].gps.hasPermission())
        controller.pause().stop().start().resume().visible()
        assertFalse(activity.findViewById<ImageButton>(R.id.next).isEnabled)
        assertNotNull(activity.findViewById<View>(R.id.openCarplay))
        controller.pause().stop().destroy()
    }
    @Test fun motorPrimaryCloseKeepsOemEnabledAndDoesNotRequestTemperature() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        controller.get().findViewById<View>(R.id.dockEngine).performClick()
        ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val model = ViewModelProvider(controller.get())[LauncherViewModel::class.java]
        assertEquals("Cerrar", dialog.getButton(DialogInterface.BUTTON_POSITIVE).text.toString())
        assertEquals("Detener lectura OEM", dialog.getButton(DialogInterface.BUTTON_NEGATIVE).text.toString())
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(model.settings.oemReadEnabled)
        assertTrue(model.oem.state.value.temperatureRequestStatus.contains("no solicitada"))
        controller.pause().stop().destroy()
    }
    @Test fun absentZlinkCannotLaunchOrClaimConnection() {
        val apps = AppIntegrationManager(app, prefs)
        assertFalse(apps.launch("zlink"))
        assertTrue(apps.candidates().isEmpty())
    }
    private fun installLaunchable(pkg: String = "test.vendor.projection", label: String = "ZLink"): ComponentName {
        val component = ComponentName(pkg, "$pkg.Start")
        val info = ActivityInfo().apply {
            packageName = component.packageName; name = component.className; exported = true; enabled = true
            applicationInfo = ApplicationInfo().apply { packageName = component.packageName; enabled = true }
        }
        shadowOf(app.packageManager).addOrUpdateActivity(info)
        val resolve = ResolveInfo().apply { activityInfo = info; nonLocalizedLabel = label }
        shadowOf(app.packageManager).addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), resolve)
        return component
    }
    @Test fun knownFirmwareAppsOpenWithoutInitialPicker() {
        for ((role, pkg) in mapOf("zlink" to "com.zjinnova.zlink", "radio" to "com.tw.radio", "phone" to "com.tw.bt", "music" to "com.tw.music")) {
            val component = installLaunchable(pkg, role)
            assertTrue("Must open installed $role", AppIntegrationManager(app, prefs).launch(role))
            assertEquals(component, shadowOf(app).nextStartedActivity.component)
        }
    }
    @Test fun mapsPickerExcludesUnrelatedAppsAndInvalidSavedAssignment() {
        val unrelated = installLaunchable("com.tw.radio", "Radio")
        prefs.setComponent("maps", unrelated)
        val apps = AppIntegrationManager(app, prefs)
        assertTrue(apps.choices("maps").isEmpty())
        assertFalse(apps.launch("maps"))
        assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun launchFailureExplainsMissingVendorPermission() {
        val component = installLaunchable()
        val info = app.packageManager.getActivityInfo(component, 0)
        info.permission = "vendor.permission.CONTROL_HEADUNIT"
        shadowOf(app.packageManager).addOrUpdateActivity(info)
        prefs.setComponent("zlink", component)
        val apps = AppIntegrationManager(app, prefs)
        assertFalse(apps.launch("zlink"))
        assertTrue(apps.lastFailure.orEmpty().contains("vendor.permission.CONTROL_HEADUNIT"))
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
        // The default Robolectric fingerprint is also its model/brand; use a distinct private fixture.
        ReflectionHelpers.setStaticField(android.os.Build::class.java, "FINGERPRINT", "private-device-build-fingerprint-fixture")
        val model = com.mustangos.launcher.ui.LauncherViewModel(app)
        val text = com.mustangos.launcher.diagnostics.DiagnosticReporter(app, model).create()
        assertTrue(text.contains("SDK real: 32"))
        assertTrue(text.contains("Conexión CarPlay no verificable"))
        assertFalse(text.contains(android.os.Build.FINGERPRINT))
    }
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun dashboardLaysOutAtTargetResolutionWithLargeControls() {
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
    @Test @Config(qualifiers = "w853dp-h480dp-land-mdpi")
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun compactDensityKeepsMediaControlsInsideCard() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val root = controller.get().findViewById<View>(R.id.root)
        root.measure(View.MeasureSpec.makeMeasureSpec(853, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 853, 480)
        val button = root.findViewById<View>(R.id.mediaSource)
        val card = button.parent as View
        assertTrue("Media source must fit inside its card at 240dpi on a 1280x720 display", button.bottom <= card.height)
        val dock = root.findViewById<TextView>(R.id.dockCarplay)
        assertTrue("Icon and label must fit: top=${dock.compoundPaddingTop}, bottom=${dock.compoundPaddingBottom}, text=${dock.layout.height}, height=${dock.height}, lines=${dock.lineCount}", dock.compoundPaddingTop + dock.compoundPaddingBottom + dock.layout.height <= dock.height)
        val navigationStatus = root.findViewById<View>(R.id.carplayStatus)
        assertTrue("Connection state must fit", navigationStatus.bottom <= (navigationStatus.parent as View).height)
        controller.pause().stop().destroy()
    }
    @Test @Config(sdk = [26, 35]) fun startsAtMinimumAndModernApiWithoutPermissions() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        assertFalse(controller.get().findViewById<ImageButton>(R.id.playPause).isEnabled)
        controller.pause().stop().destroy()
    }
}
