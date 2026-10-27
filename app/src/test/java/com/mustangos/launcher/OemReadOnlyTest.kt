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
        var temperatureQueries = 0
        override fun requestAmbientTemperature(): Int { temperatureQueries++; return 0 }
        override fun open(codes: ShortArray, handler: Handler): Boolean {
            calls++; this.handler = handler
            assertArrayEquals(shortArrayOf(266,514,1281,1285,1288,1292), codes)
            return allowed
        }
        override fun close() { closed = true }
        fun voltage() { handler!!.sendMessage(Message.obtain().apply { what = 1292; arg1 = 0; obj = byteArrayOf(4,0xBA.toByte()) }); shadowOf(Looper.getMainLooper()).idle() }
        fun send(code: Int, arg: Int = 0, data: Any? = null) {
            handler!!.sendMessage(Message.obtain().apply { what = code; arg1 = arg; obj = data })
            shadowOf(Looper.getMainLooper()).idle()
        }
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
    @Test fun canHeadersAreCountedButDoorsRequireAnObservedMatchingProtocol() {
        settings.oemReadEnabled = true
        val feed = Feed(); val reader = OemReadOnlyManager(app, settings) { feed }
        reader.start()
        feed.send(1281, 56, byteArrayOf(0x80.toByte()))
        assertNull(reader.state.value.doorMask)
        assertTrue(reader.state.value.eventReport.contains("1281: recibidos=1; interpretados=0"))
        feed.send(266, data = "T13.1.1-100-412-DO_NOT_EXPORT_THIS_SUFFIX")
        feed.send(1281, 56, byteArrayOf(0x80.toByte()))
        assertEquals(0x80, reader.state.value.doorMask)
        assertFalse(reader.state.value.toString().contains("DO_NOT_EXPORT_THIS_SUFFIX"))
        feed.send(1281, 56, byteArrayOf(0))
        assertEquals(0, reader.state.value.doorMask)
        assertEquals(1, reader.state.value.doorOpenEvents)
        assertEquals(1, reader.state.value.doorZeroEvents)
        reader.stop(); reader.start()
        feed.send(1281, 56, byteArrayOf(0x80.toByte()))
        assertNull(reader.state.value.doorMask)
        reader.stop()
    }
    @Test fun shortAndInvalidTemperatureFramesRemainVisibleInDiagnostics() {
        settings.oemReadEnabled = true
        val feed = Feed(); val reader = OemReadOnlyManager(app, settings) { feed }
        reader.start()
        feed.send(1288, data = byteArrayOf(0))
        feed.send(1288, data = byteArrayOf(0,127))
        assertNull(reader.state.value.temperatureC)
        assertTrue(reader.state.value.eventReport.contains("1288: recibidos=2; interpretados=0"))
        feed.send(1288, data = byteArrayOf(0,22))
        assertEquals(22f, reader.state.value.temperatureC!!, 0.001f)
        reader.stop()
    }
    @Test fun eventsDeliveredDuringSubscriptionAreNotOverwritten() {
        settings.oemReadEnabled = true
        val reader = OemReadOnlyManager(app, settings) { object : OemSubscription {
            override fun open(codes: ShortArray, handler: Handler): Boolean {
                handler.dispatchMessage(Message.obtain().apply { what = 1292; obj = byteArrayOf(4, 0xBA.toByte()) })
                return true
            }
            override fun close() = Unit
        } }
        reader.start()
        assertEquals(12.1f, reader.state.value.voltage!!, 0.001f)
        assertEquals(1, reader.state.value.frames)
        reader.stop()
    }
    @Test fun callbackFromClosedSubscriptionCannotSelectProtocolForNewSession() {
        settings.oemReadEnabled = true
        val feed = Feed(); val reader = OemReadOnlyManager(app, settings) { feed }
        reader.start()
        val previousHandler = feed.handler!!
        reader.stop(); reader.start()
        previousHandler.dispatchMessage(Message.obtain().apply { what = 266; obj = "T13.1.1-100-412-EXAMPLE" })
        feed.send(1281, 56, byteArrayOf(0x80.toByte()))
        assertNull(reader.state.value.protocol)
        assertNull(reader.state.value.doorMask)
        reader.stop()
    }
    @Test fun temperatureQueryIsManualOncePerSubscriptionAndCannotInventReading() {
        val feed = Feed(); val reader = OemReadOnlyManager(app, settings) { feed }
        reader.requestAmbientTemperature(); assertEquals(0, feed.temperatureQueries)
        reader.setEnabled(true)
        assertEquals(0, feed.temperatureQueries)
        reader.requestAmbientTemperature(); reader.requestAmbientTemperature()
        assertEquals(1, feed.temperatureQueries)
        assertNull(reader.state.value.temperatureC)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
        assertTrue(reader.state.value.temperatureRequestStatus.contains("sin respuesta"))
        feed.send(1288, data = byteArrayOf(0,24))
        assertEquals(24f, reader.state.value.temperatureC!!, 0.001f)
        assertTrue(reader.state.value.temperatureRequestStatus.contains("recibida"))
        reader.stop(); reader.start()
        reader.requestAmbientTemperature(); assertEquals(2, feed.temperatureQueries)
        reader.stop()
    }
    @Test fun immediateTemperatureReplyCannotBeReplacedWithWaitingStatus() {
        settings.oemReadEnabled = true
        val reader = OemReadOnlyManager(app, settings) { object : OemSubscription {
            lateinit var callback: Handler
            override fun open(codes: ShortArray, handler: Handler): Boolean { callback = handler; return true }
            override fun requestAmbientTemperature(): Int {
                callback.dispatchMessage(Message.obtain().apply { what = 1288; obj = byteArrayOf(0,23) })
                return 0
            }
            override fun close() = Unit
        } }
        reader.start(); reader.requestAmbientTemperature()
        assertEquals(23f, reader.state.value.temperatureC!!, 0.001f)
        assertTrue(reader.state.value.temperatureRequestStatus.contains("recibida"))
        reader.stop()
    }
    @Test fun rejectedTemperatureQueryLeavesPassiveVoltageListeningOperational() {
        settings.oemReadEnabled = true
        lateinit var callback: Handler
        val reader = OemReadOnlyManager(app, settings) { object : OemSubscription {
            override fun open(codes: ShortArray, handler: Handler): Boolean { callback = handler; return true }
            override fun requestAmbientTemperature(): Int = throw SecurityException("test")
            override fun close() = Unit
        } }
        reader.start(); reader.requestAmbientTemperature()
        assertNull(reader.state.value.temperatureC)
        assertTrue(reader.state.value.temperatureRequestStatus.contains("rechazada"))
        assertFalse(reader.canRequestAmbientTemperature)
        assertTrue(settings.oemReadEnabled)
        callback.dispatchMessage(Message.obtain().apply { what = 1292; obj = byteArrayOf(4,0xBA.toByte()) })
        assertEquals(12.1f, reader.state.value.voltage!!, 0.001f)
        reader.stop()
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
        fun write(code: Int, argument: Int): Int {
            assertEquals(1288, code); assertEquals(255, argument)
            calls.add("temperature-query"); return 0
        }
    }
    @Test fun reflectiveAdapterUsesOnlySubscriptionLifecycle() {
        PublicVendorApi.calls.clear()
        val subscription = TwReadOnlySubscription { PublicVendorApi::class.java }
        assertTrue(subscription.open(shortArrayOf(1292), Handler(Looper.getMainLooper())))
        subscription.close()
        assertEquals(listOf("open","add","remove","stop","close"), PublicVendorApi.calls)
    }
    @Test fun reflectiveTemperatureQueryUsesOnlyVerifiedTwoArgumentRequest() {
        PublicVendorApi.calls.clear()
        val subscription = TwReadOnlySubscription { PublicVendorApi::class.java }
        assertNull(subscription.requestAmbientTemperature())
        assertTrue(subscription.open(shortArrayOf(1288), Handler(Looper.getMainLooper())))
        assertEquals(0, subscription.requestAmbientTemperature())
        subscription.close()
        assertNull(subscription.requestAmbientTemperature())
        assertEquals(listOf("open","add","temperature-query","remove","stop","close"), PublicVendorApi.calls)
    }
}
