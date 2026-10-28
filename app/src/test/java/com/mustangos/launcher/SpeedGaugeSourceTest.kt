package com.mustangos.launcher

import com.mustangos.launcher.ui.SpeedGaugeView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SpeedGaugeSourceTest {
    @Test fun equalSpeedFromAnotherSourceUpdatesItsDescription() {
        val gauge = SpeedGaugeView(RuntimeEnvironment.getApplication())
        gauge.setSpeed(20f, "OEM"); assertTrue(gauge.contentDescription.contains("OEM"))
        gauge.setSpeed(20f); assertTrue(gauge.contentDescription.contains("GPS"))
        gauge.setSpeed(null); assertTrue(gauge.contentDescription.contains("sin señal"))
    }
}
