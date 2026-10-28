package com.mustangos.core

import org.junit.Assert.*
import org.junit.Test

class OemPowertrainTest {
    @Test fun onlyTheVerifiedChannelIsDecoded() {
        assertNull(OemEventParser.parsePowertrain(1292, 1200, 1000))
        assertEquals(OemEvent.Powertrain(12f, 1000), OemEventParser.parsePowertrain(1291, 1200, 1000))
    }
    @Test fun unavailableAndImplausibleFieldsNeverBecomeMeasurements() {
        assertEquals(OemEvent.Powertrain(null, null), OemEventParser.parsePowertrain(1291, 65535, 65535))
        assertEquals(OemEvent.Powertrain(null, null), OemEventParser.parsePowertrain(1291, -1, 50000))
        assertEquals(OemEvent.Powertrain(20f, null), OemEventParser.parsePowertrain(1291, 2000, 65535))
    }
    @Test fun receivedZeroIsValidWithoutInferringIgnitionState() {
        assertEquals(OemEvent.Powertrain(0f, 0), OemEventParser.parsePowertrain(1291, 0, 0))
    }
}
