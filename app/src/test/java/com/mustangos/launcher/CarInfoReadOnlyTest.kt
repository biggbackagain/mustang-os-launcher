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
import android.os.Parcel
import com.mustangos.launcher.settings.LauncherSettings
import com.mustangos.launcher.vehicle.CarInfoReadContract
import com.mustangos.launcher.vehicle.CarInfoReadOnlyManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CarInfoReadOnlyTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private lateinit var settings: LauncherSettings
    private var reader: CarInfoReadOnlyManager? = null
    private val flags = mutableListOf<Int>()
    private val context by lazy { object : ContextWrapper(app) {
        override fun bindService(intent: Intent, connection: ServiceConnection, flag: Int): Boolean {
            assertEquals(CarInfoReadOnlyManager.COMPONENT, intent.component)
            assertEquals(CarInfoReadOnlyManager.ACTION, intent.action)
            flags += flag
            return super.bindService(intent, connection, flag)
        }
        override fun startService(intent: Intent): ComponentName? { fail("Read-only adapter cannot start OEM services"); return null }
        override fun startForegroundService(intent: Intent): ComponentName? { fail("Read-only adapter cannot start OEM services"); return null }
    } }
    @Before fun setup() {
        app.getSharedPreferences("launcher", 0).edit().clear().commit()
        settings = LauncherSettings(app)
    }
    @After fun cleanup() { reader?.stop(); shadowOf(Looper.getMainLooper()).idle() }
    private class FixtureBinder(descriptor: String = CarInfoReadContract.DESCRIPTOR) : Binder() {
        val calls = CopyOnWriteArrayList<Int>()
        val threads = CopyOnWriteArrayList<Thread>()
        var volts = 12.6f
        var light = 1
        var mileage = intArrayOf(100_000, 0, 0)
        var temperature = floatArrayOf(68f, 0f, 1f)
        var reject: Int? = null
        var badLength = false
        var entered: CountDownLatch? = null
        var release: CountDownLatch? = null
        init { attachInterface(null, descriptor) }
        override fun onTransact(code: Int, input: Parcel, output: Parcel?, flags: Int): Boolean {
            require(code in setOf(4, 5, 7, 20)) { "Control or unverified transaction attempted: $code" }
            input.enforceInterface(CarInfoReadContract.DESCRIPTOR)
            calls += code; threads += Thread.currentThread()
            entered?.countDown(); release?.await(3, TimeUnit.SECONDS)
            if (code == reject) throw SecurityException("fixture denied getter")
            val reply = requireNotNull(output)
            reply.writeNoException()
            when (code) {
                4 -> reply.writeFloat(volts)
                5 -> reply.writeInt(light)
                7 -> if (badLength) reply.writeInt(Int.MAX_VALUE) else reply.writeIntArray(mileage)
                20 -> reply.writeFloatArray(temperature)
            }
            return true
        }
    }
    private fun install(binder: FixtureBinder = FixtureBinder(), exported: Boolean = true, permission: String? = null) {
        shadowOf(app.packageManager).addOrUpdateService(ServiceInfo().apply {
            packageName = CarInfoReadOnlyManager.COMPONENT.packageName; name = CarInfoReadOnlyManager.COMPONENT.className
            this.permission = permission; this.exported = exported; enabled = true
            applicationInfo = ApplicationInfo().apply { packageName = CarInfoReadOnlyManager.COMPONENT.packageName; enabled = true }
        })
        shadowOf(app).setComponentNameAndServiceForBindService(CarInfoReadOnlyManager.COMPONENT, binder)
        shadowOf(app).setBindServiceCallsOnServiceConnectedDirectly(false)
    }
    private fun manager() = CarInfoReadOnlyManager(context, settings).also { reader = it }
    private fun awaitUi(predicate: () -> Boolean) {
        repeat(300) {
            shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) return
            Thread.sleep(10)
        }
        assertTrue("OEM read should settle on Main", predicate())
    }
    @Test fun contractUsesOnlyFourCacheGettersAndConvertsExplicitUnits() {
        val binder = FixtureBinder().apply { mileage = intArrayOf(100_000, 1, 0) }
        val snapshot = CarInfoReadContract.read(binder)
        assertEquals(listOf(4, 5, 7, 20), binder.calls)
        assertEquals(12.6f, snapshot.voltage!!, 0.001f)
        assertEquals(160_934.4, snapshot.odometerKm!!, 0.001)
        assertEquals(20f, snapshot.temperatureC!!, 0.001f)
        assertEquals(true, snapshot.illuminationSignal)
        assertEquals(0, snapshot.failures)
    }
    @Test fun unexpectedDescriptorIsRejectedBeforeAnyTransaction() {
        val binder = FixtureBinder("different.interface")
        assertThrows(IllegalStateException::class.java) { CarInfoReadContract.read(binder) }
        assertTrue(binder.calls.isEmpty())
    }
    @Test fun sentinelsDefaultZerosMalformedArraysAndUnknownUnitsNeverBecomeMeasurements() {
        val binder = FixtureBinder().apply {
            volts = Float.NaN; light = -1; mileage = intArrayOf(-1, 0, 0); temperature = floatArrayOf(0f, 0f, 0f)
        }
        var snapshot = CarInfoReadContract.read(binder)
        assertNull(snapshot.voltage); assertNull(snapshot.illuminationSignal)
        assertNull(snapshot.odometerKm); assertNull(snapshot.temperatureC)
        binder.mileage = intArrayOf(100_000, 3, 0); binder.temperature = floatArrayOf(22f, 0f, 5f)
        snapshot = CarInfoReadContract.read(binder)
        assertNull(snapshot.odometerKm); assertNull(snapshot.temperatureC)
        binder.badLength = true
        snapshot = CarInfoReadContract.read(binder)
        assertNull(snapshot.odometerKm); assertEquals(1, snapshot.failures)
    }
    @Test fun oneRejectedGetterDoesNotDiscardOtherAvailableReadings() {
        val binder = FixtureBinder().apply { reject = 7 }
        val snapshot = CarInfoReadContract.read(binder)
        assertNull(snapshot.odometerKm); assertEquals(1, snapshot.failures)
        assertEquals(12.6f, snapshot.voltage!!, 0.001f)
        assertEquals(20f, snapshot.temperatureC!!, 0.001f)
    }
    @Test fun disabledPreferenceDoesNotBindOrAutomaticallyEnableOemReading() {
        install()
        val manager = manager(); manager.start()
        assertTrue(flags.isEmpty()); assertFalse(settings.oemReadEnabled)
        assertFalse(manager.state.value.sourceActive)
    }
    @Test fun exportedActiveServiceUsesZeroBindFlagsAndKeepsCacheAgeExplicit() {
        val binder = FixtureBinder(); install(binder); settings.oemReadEnabled = true
        val manager = manager(); manager.start(); manager.start()
        awaitUi { manager.state.value.lastQueryMs != null }
        val state = manager.state.value
        assertEquals(listOf(0), flags)
        assertTrue(state.sourceActive); assertEquals(12.6f, state.voltage!!.value, 0.001f)
        assertTrue(state.voltage.available)
        assertTrue(state.cacheNotice.contains("no publica la fecha"))
        assertTrue(manager.report().contains("edad de medición desconocida"))
        assertTrue(binder.threads.all { it !== Looper.getMainLooper().thread })
        manager.stop()
        assertFalse(manager.state.value.sourceActive)
        assertEquals(12.6f, manager.state.value.voltage!!.value, 0.001f)
        assertTrue(shadowOf(app).boundServiceConnections.isEmpty())
        // Robolectric also queues bound intents as started services; the wrapper above fails on any actual startService call.
    }
    @Test fun hiddenStoppedReaderDoesNotPollAndRestartReadsAgainOnlyWhenEnabled() {
        val binder = FixtureBinder(); install(binder); settings.oemReadEnabled = true
        val manager = manager(); manager.start()
        awaitUi { manager.state.value.lastQueryMs != null }
        manager.stop()
        val completed = binder.calls.size
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16))
        assertEquals(completed, binder.calls.size)
        manager.start(); awaitUi { binder.calls.size >= completed + 4 && manager.state.value.sourceActive }
        manager.stop(); settings.oemReadEnabled = false; manager.start()
        assertEquals(listOf(0, 0), flags)
    }
    @Test fun deniedOrPrivilegedDeclarationNeverAttemptsBinding() {
        install(exported = false); settings.oemReadEnabled = true
        val manager = manager(); manager.start(); assertTrue(flags.isEmpty())
        val permission = CarInfoReadOnlyManager.PERMISSION
        install(permission = permission)
        shadowOf(app.packageManager).addPermissionInfo(PermissionInfo().apply {
            name = permission; packageName = CarInfoReadOnlyManager.COMPONENT.packageName; protectionLevel = PermissionInfo.PROTECTION_SIGNATURE
        })
        shadowOf(app).grantPermissions(permission)
        manager.start(); assertTrue(flags.isEmpty())
    }
    @Test fun descriptorMismatchUnbindsAndNeverReadsVendorMethods() {
        val binder = FixtureBinder("different.interface"); install(binder); settings.oemReadEnabled = true
        val manager = manager(); manager.start()
        awaitUi { manager.state.value.status.contains("difiere") }
        assertTrue(binder.calls.isEmpty())
        assertFalse(manager.state.value.sourceActive)
        assertTrue(shadowOf(app).boundServiceConnections.isEmpty())
    }
    @Test fun unavailableResultRetainsPreviousValueAndItsOriginalQueryTimeWithoutPretendingFreshMeasurement() {
        val binder = FixtureBinder(); install(binder); settings.oemReadEnabled = true
        val manager = manager(); manager.start(); awaitUi { manager.state.value.voltage != null }
        val first = manager.state.value.voltage!!
        binder.volts = -1f
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
        awaitUi { manager.state.value.voltage?.available == false }
        assertEquals(first.value, manager.state.value.voltage!!.value)
        assertEquals(first.queriedAtMs, manager.state.value.voltage!!.queriedAtMs)
        assertTrue(manager.report().contains("disponible=false"))
    }
    @Test fun stopRejectsLateBinderReplyAndReleasesConnection() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val binder = FixtureBinder().apply { this.entered = entered; this.release = release }
        install(binder); settings.oemReadEnabled = true
        val manager = manager(); manager.start(); shadowOf(Looper.getMainLooper()).idle()
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        manager.stop(); release.countDown()
        Thread.sleep(30); shadowOf(Looper.getMainLooper()).idle()
        assertNull(manager.state.value.voltage); assertNull(manager.state.value.lastQueryMs)
        assertFalse(manager.state.value.sourceActive)
        assertTrue(shadowOf(app).boundServiceConnections.isEmpty())
    }
}
