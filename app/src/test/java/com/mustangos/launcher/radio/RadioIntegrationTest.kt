package com.mustangos.launcher.radio

import android.app.Application
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Parcel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [29])
class RadioIntegrationTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private fun event(code: Int, a: Int = 0, b: Int = 0, text: Any? = null) = Message.obtain().apply { what = code; arg1 = a; arg2 = b; obj = text }

    @Test fun frequencyRequiresReportedBandAndFmAmUnitsMatchTheInstalledRadio() {
        var value = RadioState()
        assertEquals("—", value.frequencyText)
        value = RadioEvents.reduce(value, event(1025, 2, 10170), 10)
        assertEquals("—", value.frequencyText)
        value = RadioEvents.reduce(value, event(1025, 1, 0), 20)
        assertEquals("—", value.frequencyText)
        value = RadioEvents.reduce(value, event(1025, 2, 10170), 30)
        assertEquals("101.70 MHz", value.frequencyText)
        value = RadioEvents.reduce(value, event(1025, 1, 2), 40)
        assertNull(value.frequency)
        value = RadioEvents.reduce(value, event(1025, 2, 1470), 50)
        assertEquals("1470 kHz", value.frequencyText)
    }

    @Test fun changedStationAndBandClearThePreviousRdsInsteadOfReusingIt() {
        var value = RadioState(band = 0, frequency = 10170, stationName = "Old PS", radioText = "Old RT")
        value = RadioEvents.reduce(value, event(1025, 2, 10250), 1)
        assertNull(value.stationName); assertNull(value.radioText)
        value = RadioEvents.reduce(value, event(1028, text = "New PS"), 2)
        value = RadioEvents.reduce(value, event(1029, text = "New RT"), 3)
        assertEquals("New PS", value.stationName); assertEquals("New RT", value.radioText)
        value = RadioEvents.reduce(value, event(1025, 2, 10250, ""), 4)
        assertNull(value.stationName)
        value = RadioEvents.reduce(value, event(1025, 1, 2), 5)
        assertNull(value.stationName); assertNull(value.radioText)
    }

    @Test fun presetsAreBoundedUseReportedBandAndDoNotInventMeaningForUnusedBits() {
        var value = RadioState(band = 0)
        repeat(18) { value = RadioEvents.reduce(value, event(1026, it or (250 shl 8), 8750 + it * 10, "Preset $it"), 0) }
        value = RadioEvents.reduce(value, event(1026, 255, 10100), 0)
        assertEquals(18, value.presets.size)
        assertTrue(value.presets.all { it.band == 0 && it.frequencyText.endsWith("MHz") })
        value = RadioEvents.reduce(value, event(1026, 0, 0), 0)
        assertEquals(17, value.presets.size)
        value = RadioEvents.reduce(value, event(1025, 1, 2), 0)
        assertTrue(value.presets.isEmpty())
    }

    @Test fun malformedAndOversizedEventsCannotExpandTheStateOrCreateValues() {
        val initial = RadioState()
        for (message in listOf(event(1025, 1, 99), event(1025, 2, -1), event(1026, 27, 10000), event(7777, 1, 2)))
            assertEquals(initial, RadioEvents.reduce(initial, message, 1))
        val bounded = RadioEvents.reduce(initial, event(1028, text = "A\n".repeat(5000)), 1)
        assertTrue(bounded.stationName!!.length <= 256)
        assertFalse(bounded.stationName!!.contains('\n'))
    }

    @Test fun sourceEventReportsRadioOnlyForTheVerifiedSourceValue() {
        val radio = RadioEvents.reduce(RadioState(), event(769, 1), 10)
        assertEquals(true, radio.radioSelected); assertTrue(radio.sourceFresh)
        val projection = RadioEvents.reduce(radio, event(769, 0), 20)
        assertEquals(false, projection.radioSelected)
        assertFalse(projection.canControl)
    }

    @Test fun confirmedBinderDescriptorAndExactVerifiedVoidMethodsAreUsed() {
        val calls = mutableListOf<Int>()
        val wire = object : Binder() {
            init { attachInterface(null, VerifiedRadioBinder.DESCRIPTOR) }
            override fun onTransact(code: Int, request: Parcel, reply: Parcel?, flags: Int): Boolean {
                request.enforceInterface(VerifiedRadioBinder.DESCRIPTOR)
                assertEquals(0, flags); assertEquals(0, request.dataAvail())
                calls += code; reply!!.writeNoException(); return true
            }
        }
        val controls = VerifiedRadioBinder(wire)
        for (action in RadioAction.entries) assertTrue(controls.submit(action))
        assertEquals(listOf(11, 12, 13, 14, 18), calls)
    }

    @Test fun wrongBinderDescriptorNeverReceivesACommand() {
        var calls = 0
        val wire = object : Binder() {
            init { attachInterface(null, "another.vendor.interface") }
            override fun onTransact(code: Int, request: Parcel, reply: Parcel?, flags: Int): Boolean { calls++; return true }
        }
        assertFalse(VerifiedRadioBinder(wire).submit(RadioAction.NEXT)); assertEquals(0, calls)
    }

    @Test fun openingPanelReadsButNeverSubmitsAControlAndCarplaySourceBlocksCommands() {
        val subscription = FakeSubscription(); val controls = FakeControls()
        val media = RadioIntegrationManager(app, subscription, controls, { 100 })
        try {
            media.start(); await { media.state.value.reading && subscription.reads > 0 }
            assertTrue(controls.actions.isEmpty())
            subscription.emit(event(769, 0)); shadowOf(Looper.getMainLooper()).idle()
            assertFalse(media.submit(RadioAction.NEXT)); assertTrue(controls.actions.isEmpty())
        } finally { media.stop() }
    }

    @Test fun explicitCommandRequiresCurrentRadioSourceAndDoesNotOptimisticallyChangeFrequency() {
        val subscription = FakeSubscription(); val controls = FakeControls()
        val media = RadioIntegrationManager(app, subscription, controls, { 100 })
        try {
            media.start(); await { media.state.value.reading }
            subscription.emit(event(769, 1)); subscription.emit(event(1025, 1, 0)); subscription.emit(event(1025, 2, 10170))
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(media.submit(RadioAction.SEEK_NEXT))
            await { !media.state.value.commandPending }
            assertEquals(listOf(RadioAction.SEEK_NEXT), controls.actions.toList())
            assertEquals("101.70 MHz", media.state.value.frequencyText)
            assertEquals("Solicitud enviada; esperando lectura del equipo", media.state.value.commandStatus)
        } finally { media.stop() }
    }

    @Test fun expiredSourceCannotEnableControlsEvenIfTheLastKnownValueWasRadio() {
        var time = 100L
        val subscription = FakeSubscription(); val controls = FakeControls()
        val media = RadioIntegrationManager(app, subscription, controls, { time })
        try {
            media.start(); await { media.state.value.reading }
            subscription.emit(event(769, 1)); shadowOf(Looper.getMainLooper()).idle()
            assertTrue(media.state.value.canControl)
            time = 20_000
            assertFalse(media.submit(RadioAction.NEXT)); assertTrue(controls.actions.isEmpty())
        } finally { media.stop() }
    }

    @Test fun aQueuedCommandIsCancelledIfCarplayBecomesTheSourceBeforeDispatch() {
        val subscription = FakeSubscription().apply { blockRead = true }
        val controls = FakeControls()
        val media = RadioIntegrationManager(app, subscription, controls, { 100 })
        try {
            media.start(); await { media.state.value.reading }
            assertTrue(subscription.entered.await(2, TimeUnit.SECONDS))
            subscription.emit(event(769, 1)); shadowOf(Looper.getMainLooper()).idle()
            assertTrue(media.submit(RadioAction.NEXT))
            subscription.emit(event(769, 0)); shadowOf(Looper.getMainLooper()).idle()
            subscription.release.countDown()
            await { !media.state.value.commandPending }
            assertTrue(controls.actions.isEmpty())
            assertEquals("Solicitud cancelada: la fuente de audio cambió", media.state.value.commandStatus)
        } finally { subscription.release.countDown(); media.stop() }
    }

    @Test fun stopClosesTheClientAndLateEventsCannotChangeUiOrControlAnotherSource() {
        val subscription = FakeSubscription(); val controls = FakeControls()
        val media = RadioIntegrationManager(app, subscription, controls, { 100 })
        media.start(); await { media.state.value.reading }
        val oldReceiver = requireNotNull(subscription.receiver)
        media.stop(); await { subscription.closed > 0 }
        val stopped = media.state.value
        oldReceiver.sendMessage(event(769, 1)); oldReceiver.sendMessage(event(1025, 2, 9999))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
        assertEquals(stopped, media.state.value)
        assertFalse(media.submit(RadioAction.NEXT)); assertTrue(controls.actions.isEmpty())
    }

    private fun await(condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!condition() && System.nanoTime() < end) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
        shadowOf(Looper.getMainLooper()).idle(); assertTrue(condition())
    }
    private class FakeSubscription : RadioSubscription {
        @Volatile var receiver: Handler? = null
        @Volatile var reads = 0
        @Volatile var closed = 0
        @Volatile var blockRead = false
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        override fun open(handler: Handler): Boolean { receiver = handler; return true }
        override fun requestSnapshot() { reads++; if (blockRead) { entered.countDown(); release.await(3, TimeUnit.SECONDS) } }
        override fun close() { closed++ }
        fun emit(event: Message) { requireNotNull(receiver).sendMessage(event) }
    }
    private class FakeControls : RadioControls {
        val actions = CopyOnWriteArrayList<RadioAction>()
        override fun start(onAvailable: (Boolean) -> Unit) { onAvailable(true) }
        override fun stop() = Unit
        override fun submit(action: RadioAction): Boolean { actions += action; return true }
    }
}
