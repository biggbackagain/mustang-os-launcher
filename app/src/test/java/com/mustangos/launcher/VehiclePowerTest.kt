package com.mustangos.launcher

import android.app.Application
import android.content.Intent
import android.os.BatteryManager
import android.os.Looper
import com.mustangos.launcher.vehicle.VehicleTelemetryManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35])
class VehiclePowerTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    @Test fun accessoryStateRequiresNativeEventAndStopUnregistersListener() {
        val vehicle = VehicleTelemetryManager(app)
        assertNull(vehicle.accessoryOn.value)
        val sentBefore = shadowOf(app).broadcastIntents.size
        vehicle.start(); vehicle.start()
        assertEquals(sentBefore, shadowOf(app).broadcastIntents.size)
        assertNull(vehicle.accessoryOn.value)
        app.sendBroadcast(Intent(VehicleTelemetryManager.ACC_ON)); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(true, vehicle.accessoryOn.value)
        app.sendBroadcast(Intent(VehicleTelemetryManager.ACC_OFF)); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(false, vehicle.accessoryOn.value)
        vehicle.stop(); vehicle.stop()
        app.sendBroadcast(Intent(VehicleTelemetryManager.ACC_ON)); shadowOf(Looper.getMainLooper()).idle()
        assertNull(vehicle.accessoryOn.value)
    }
    @Test fun androidBatteryIsEquipmentDataAndNeverBecomesVehicleTelemetry() {
        app.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_VOLTAGE,4200)
            .putExtra(BatteryManager.EXTRA_PRESENT,true).putExtra(BatteryManager.EXTRA_LEVEL,50)
            .putExtra(BatteryManager.EXTRA_SCALE,100).putExtra(BatteryManager.EXTRA_TEMPERATURE,11))
        val vehicle = VehicleTelemetryManager(app)
        assertEquals(4.2f, vehicle.battery.value.voltage!!,0.001f)
        assertEquals(50,vehicle.battery.value.percent)
        assertNull(vehicle.state.value.voltage); assertNull(vehicle.state.value.coolantC); assertNull(vehicle.accessoryOn.value)
        app.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_VOLTAGE,12)
            .putExtra(BatteryManager.EXTRA_LEVEL,200).putExtra(BatteryManager.EXTRA_SCALE,100))
        vehicle.poll()
        assertNull(vehicle.battery.value.voltage); assertNull(vehicle.battery.value.percent)
    }
}
