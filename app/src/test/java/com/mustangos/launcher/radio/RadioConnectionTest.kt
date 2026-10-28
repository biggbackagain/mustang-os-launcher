package com.mustangos.launcher.radio

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.Looper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = RadioConnectionTest.RecordingApplication::class)
class RadioConnectionTest {
    private val app: RecordingApplication get() = RuntimeEnvironment.getApplication() as RecordingApplication
    @Before fun setup() { FakeFramework.reads.clear(); FakeFramework.opened = null; FakeFramework.last = null }

    @Test fun clientOnlyOpensObservedChannelsAndRequestsTheThreeVerifiedReadSnapshots() {
        val subscription = TwRadioSubscription { FakeFramework::class.java }
        assertTrue(subscription.open(Handler(Looper.getMainLooper())))
        assertArrayEquals(shortArrayOf(769, 1025, 1026, 1028, 1029), FakeFramework.opened!!)
        subscription.requestSnapshot()
        assertEquals(listOf(769 to 255, 1025 to 255, 1028 to 255), FakeFramework.reads.toList())
        val instance = FakeFramework.last!!
        assertNotNull(instance.handler)
        subscription.close()
        assertNull(instance.handler); assertTrue(instance.stopped); assertTrue(instance.closed)
        subscription.close() // Idempotent cleanup never stops another client.
        assertEquals(1, instance.closeCalls)
    }

    @Test fun controlConnectionUsesOnlyTheVerifiedExplicitEndpointAndZeroBindFlags() {
        install(exported = true)
        val controls = TwRadioControls(app)
        try {
            controls.start {}
            await { app.binds.isNotEmpty() }
            val (intent, flags) = app.binds.single()
            assertEquals(0, flags)
            assertEquals(ComponentName("com.tw.service.xt", "com.tw.service.xt.CommandService"), intent.component)
            assertEquals("com.tw.service.xt.CommandService.Bind", intent.action)
            assertNull(shadowOf(app).nextStartedService)
        } finally { controls.stop() }
    }

    @Test fun unexportedEndpointIsNotBoundAndNoFallbackServiceIsStarted() {
        install(exported = false)
        val controls = TwRadioControls(app)
        var reported: Boolean? = null
        try {
            controls.start { reported = it }; await { reported != null }
            assertEquals(false, reported); assertTrue(app.binds.isEmpty())
            assertNull(shadowOf(app).nextStartedService)
        } finally { controls.stop() }
    }

    @Test fun missingEndpointPermissionDoesNotAttemptBinding() {
        install(exported = true, permission = "test.vendor.RADIO_PRIVILEGED")
        val controls = TwRadioControls(app)
        var reported: Boolean? = null
        try {
            controls.start { reported = it }; await { reported != null }
            assertEquals(false, reported); assertTrue(app.binds.isEmpty())
        } finally { controls.stop() }
    }

    private fun install(exported: Boolean, permission: String? = null) {
        shadowOf(app.packageManager).addOrUpdateService(ServiceInfo().apply {
            packageName = "com.tw.service.xt"; name = "com.tw.service.xt.CommandService"
            enabled = true; this.exported = exported; this.permission = permission
            applicationInfo = ApplicationInfo().apply { packageName = "com.tw.service.xt"; enabled = true }
        })
    }
    private fun await(condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!condition() && System.nanoTime() < end) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
        shadowOf(Looper.getMainLooper()).idle(); assertTrue(condition())
    }
    class RecordingApplication : Application() {
        val binds = CopyOnWriteArrayList<Pair<Intent, Int>>()
        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
            binds += Intent(service) to flags
            return false // An absent running service remains absent; no BIND_AUTO_CREATE behavior.
        }
    }
    class FakeFramework {
        var handler: Handler? = null
        var stopped = false
        var closed = false
        var closeCalls = 0
        fun addHandler(key: String, value: Handler) { assertEquals("MustangOS.Radio.ReadOnly", key); handler = value }
        fun removeHandler(key: String) { assertEquals("MustangOS.Radio.ReadOnly", key); handler = null }
        fun stop() { stopped = true }
        fun close() { closed = true; closeCalls++ }
        fun write(code: Int, argument: Int): Int { reads += code to argument; return 0 }
        companion object {
            val reads = CopyOnWriteArrayList<Pair<Int, Int>>()
            var opened: ShortArray? = null
            var last: FakeFramework? = null
            @JvmStatic fun open(client: Int, codes: ShortArray, flags: Int): FakeFramework {
                assertEquals(0, client); assertEquals(0, flags); opened = codes
                return FakeFramework().also { last = it }
            }
        }
    }
}
