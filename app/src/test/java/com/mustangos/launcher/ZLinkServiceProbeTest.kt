package com.mustangos.launcher

import android.app.Application
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PermissionInfo
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Looper
import com.mustangos.launcher.carplay.ZLinkServiceProbe
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ZLinkServiceProbeTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private fun permission(level: Int) {
        shadowOf(app.packageManager).addPermissionInfo(PermissionInfo().apply {
            name = ZLinkServiceProbe.PERMISSION; packageName = ZLinkServiceProbe.COMPONENT.packageName; protectionLevel = level
        })
        shadowOf(app).grantPermissions(ZLinkServiceProbe.PERMISSION)
    }
    @Test fun privilegedPermissionDoesNotTriggerBinding() {
        permission(PermissionInfo.PROTECTION_SIGNATURE)
        val probe = ZLinkServiceProbe(app); probe.probe()
        assertTrue(shadowOf(app).boundServiceConnections.isEmpty())
        assertTrue(probe.state.value.contains("no se intenta"))
    }
    @Test fun normalExportedServiceOnlyExposesItsDescriptorAndIsReleased() {
        permission(PermissionInfo.PROTECTION_NORMAL)
        shadowOf(app.packageManager).addOrUpdateService(ServiceInfo().apply {
            packageName = ZLinkServiceProbe.COMPONENT.packageName; name = ZLinkServiceProbe.COMPONENT.className
            permission = ZLinkServiceProbe.PERMISSION; exported = true; enabled = true
            applicationInfo = ApplicationInfo().apply { packageName = ZLinkServiceProbe.COMPONENT.packageName; enabled = true }
        })
        val binder = Binder().apply { attachInterface(null, "test.public.interface") }
        shadowOf(app).setComponentNameAndServiceForBindService(ZLinkServiceProbe.COMPONENT, binder)
        shadowOf(app).setBindServiceCallsOnServiceConnectedDirectly(false)
        var bindFlags: Int? = null
        val context = object : ContextWrapper(app) {
            override fun bindService(intent: Intent, conn: ServiceConnection, flags: Int): Boolean {
                bindFlags = flags; return super.bindService(intent, conn, flags)
            }
            override fun startService(service: Intent): ComponentName? { fail("Probe must not start the service"); return null }
        }
        val probe = ZLinkServiceProbe(context); probe.probe(); shadowOf(Looper.getMainLooper()).idle()
        assertTrue(probe.state.value, probe.state.value.contains("test.public.interface"))
        assertTrue(shadowOf(app).boundServiceConnections.isEmpty())
        assertEquals(0, bindFlags)
        probe.stop()
    }
}
