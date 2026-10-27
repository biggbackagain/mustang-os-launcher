package com.mustangos.launcher

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.Message
import com.mustangos.launcher.settings.LauncherSettings
import com.mustangos.launcher.vehicle.OemReadOnlyManager
import com.mustangos.launcher.vehicle.OemSubscription
import com.mustangos.launcher.vehicle.TwReadOnlySubscription
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
class OemReadOnlyTest {
    private lateinit var settings: LauncherSettings
    private val app: Application get() = RuntimeEnvironment.getApplication()
    @Before fun setup() { app.getSharedPreferences("launcher", 0).edit().clear().commit(); settings = LauncherSettings(app) }
    private class Feed(val allowed: Boolean = true) : OemSubscription {
        var calls = 0; var closed = false; var handler: Handler? = null
        override fun open(codes: ShortArray, handler: Handler): Boolean {
            calls++; this.handler = handler
            assertArrayEquals(shortArrayOf(514,1285,1288,1292), codes)
            return allowed
        }
        override fun close() { closed = true }
        fun voltage() { handler!!.sendMessage(Message.obtain().apply { what = 1292; arg1 = 0; obj = byteArrayOf(4,0xBA.toByte()) }); shadowOf(Looper.getMainLooper()).idle() }
    }
    @Test fun disabledDoesNotOpenAnything() {
        val feed = Feed(); val reader = OemReadOnlyManager(app, settings) { feed }
        reader.start(); assertEquals(0, feed.calls); assertNull(reader.state.value.voltage)
    }
    @Test fun rejectedSubscriptionDoesNotInventMeasurements() {
        settings.oemReadEnabled = true
        val feed = Feed(false); val reader = OemReadOnlyManager(app, settings) { feed }
        reader.start(); assertNull(reader.state.value.voltage); assertTrue(feed.closed); assertFalse(settings.oemInterrupted)
    }
    @Test fun onlyReceivedValuesAreDisplayedAndStopReleasesClient() {
        settings.oemReadEnabled = true
        val feed = Feed(); val reader = OemReadOnlyManager(app, settings) { feed }
        reader.start(); assertNull(reader.state.value.voltage)
        feed.voltage(); assertEquals(12.1f, reader.state.value.voltage!!, 0.001f)
        reader.stop(); assertTrue(feed.closed); assertNull(reader.state.value.voltage); assertFalse(settings.oemInterrupted)
    }
    @Test fun feedTimeoutClearsOldValues() {
        settings.oemReadEnabled = true
        val feed = Feed(); val reader = OemReadOnlyManager(app, settings) { feed }
        reader.start(); feed.voltage()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(17))
        assertNull(reader.state.value.voltage); reader.stop()
    }
    @Test fun interruptedNativeAttemptCannotCauseStartupLoop() {
        settings.oemReadEnabled = true; settings.oemInterrupted = true
        val feed = Feed(); val reader = OemReadOnlyManager(app, settings) { feed }
        reader.start(); assertEquals(0, feed.calls); assertFalse(settings.oemReadEnabled)
    }
    class PublicVendorApi {
        companion object {
            var calls = mutableListOf<String>()
            @JvmStatic fun open(channel: Int, codes: ShortArray, flags: Int): PublicVendorApi {
                assertEquals(0, channel); assertEquals(0, flags); assertEquals(1, codes.size)
                calls.add("open"); return PublicVendorApi()
            }
        }
        fun addHandler(key: String, handler: Handler) { calls.add("add") }
        fun removeHandler(key: String) { calls.add("remove") }
        fun stop() { calls.add("stop") }
        fun close() { calls.add("close") }
        fun write(code: Int): Int { fail("Write must never be used"); return 0 }
    }
    @Test fun reflectiveAdapterUsesOnlySubscriptionLifecycle() {
        PublicVendorApi.calls.clear()
        val subscription = TwReadOnlySubscription { PublicVendorApi::class.java }
        assertTrue(subscription.open(shortArrayOf(1292), Handler(Looper.getMainLooper())))
        subscription.close()
        assertEquals(listOf("open","add","remove","stop","close"), PublicVendorApi.calls)
    }
}
