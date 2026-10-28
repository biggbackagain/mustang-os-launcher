package com.mustangos.launcher

import android.app.Application
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import com.mustangos.launcher.carplay.CarPlayBarService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class CarPlayBarTest {
    private val app: Application get()=RuntimeEnvironment.getApplication()
    @Test fun missingOverlayPermissionStopsWithoutStartingOrChangingZlink() {
        ShadowSettings.setCanDrawOverlays(false)
        val controller=Robolectric.buildService(CarPlayBarService::class.java).create()
        val service=controller.get()
        assertFalse(Settings.canDrawOverlays(service))
        service.onStartCommand(Intent(service,CarPlayBarService::class.java).setAction(CarPlayBarService.SHOW),0,1)
        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)
        assertNull(Shadows.shadowOf(app).nextStartedActivity)
        controller.destroy()
    }
    @Test fun explicitlyStartedBarHasForegroundNotificationAndCloseDoesNotLaunchAnApp() {
        ShadowSettings.setCanDrawOverlays(true)
        val controller=Robolectric.buildService(CarPlayBarService::class.java).create()
        val service=controller.get()
        service.onStartCommand(Intent(service,CarPlayBarService::class.java).setAction(CarPlayBarService.SHOW),0,1)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(Shadows.shadowOf(service).lastForegroundNotification)
        assertFalse(Shadows.shadowOf(service).isStoppedBySelf)
        service.onStartCommand(Intent().setAction(CarPlayBarService.CLOSE),0,2)
        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)
        controller.destroy()
        assertNull(Shadows.shadowOf(app).nextStartedActivity)
    }
    @Test fun permissionRevokedClosesTheBarAndReleasesItsWork() {
        ShadowSettings.setCanDrawOverlays(true)
        val controller=Robolectric.buildService(CarPlayBarService::class.java).create()
        val service=controller.get()
        service.onStartCommand(Intent().setAction(CarPlayBarService.SHOW),0,1)
        ShadowSettings.setCanDrawOverlays(false)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }
    @Test @Config(sdk=[35]) fun modernAndroidUsesADeclaredForegroundServiceType() {
        ShadowSettings.setCanDrawOverlays(true)
        val controller=Robolectric.buildService(CarPlayBarService::class.java).create()
        val service=controller.get()
        service.onStartCommand(Intent().setAction(CarPlayBarService.SHOW),0,1)
        assertFalse(Shadows.shadowOf(service).isStoppedBySelf)
        val component=android.content.ComponentName(app,CarPlayBarService::class.java)
        val info=app.packageManager.getServiceInfo(component,0)
        assertEquals(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,info.foregroundServiceType)
        assertFalse(info.exported)
        controller.destroy()
    }
}
