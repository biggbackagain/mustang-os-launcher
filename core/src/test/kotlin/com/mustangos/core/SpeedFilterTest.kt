package com.mustangos.core

import org.junit.Assert.*
import org.junit.Test

class SpeedFilterTest {
    private val filter = SpeedFilter()
    @Test fun noFixDiffersFromStationary() {
        assertNull(filter.current(0).kmh)
        assertEquals(0f, filter.update(SpeedSample(0f, 5f, 1f, 1000), 1000).kmh!!, 0f)
    }
    @Test fun staleFixIsClearedAndReconnectIsFresh() {
        filter.update(SpeedSample(10f, 5f, 1f, 1000), 1000)
        assertNull(filter.current(6001).kmh)
        assertEquals(72f, filter.update(SpeedSample(20f, 5f, 1f, 7000), 7000).kmh!!, 0.01f)
    }
    @Test fun rejectsInvalidPoorAndOutOfOrderReadings() {
        assertNull(filter.update(SpeedSample(Float.NaN, 5f, 1f, 1), 1).kmh)
        assertNull(filter.update(SpeedSample(10f, 100f, 1f, 2), 2).kmh)
        assertNull(filter.update(SpeedSample(10f, 5f, 9f, 3), 3).kmh)
        assertNull(filter.update(SpeedSample(10f, 5f, 1f, 0), 6000).kmh)
        filter.update(SpeedSample(10f, 5f, 1f, 1000), 1000)
        assertEquals(36f, filter.update(SpeedSample(20f, 5f, 1f, 999), 1000).kmh!!, 0.01f)
    }
    @Test fun smoothsAndResetClearsSpeed() {
        filter.update(SpeedSample(10f, 5f, 1f, 1000), 1000)
        assertEquals(50.4f, filter.update(SpeedSample(20f, 5f, 1f, 2000), 2000).kmh!!, 0.01f)
        assertNull(filter.reset().kmh)
    }
}
