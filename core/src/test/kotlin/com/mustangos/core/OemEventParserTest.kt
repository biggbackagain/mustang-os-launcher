package com.mustangos.core

import org.junit.Assert.*
import org.junit.Test

class OemEventParserTest {
    @Test fun voltageMatchesSystemUiHundredthsOfVolt() {
        assertEquals(12.1f, (OemEventParser.parse(1292, 0, byteArrayOf(0x04, 0xBA.toByte())) as OemEvent.Voltage).volts, 0.001f)
    }
    @Test fun voltageRejectsInvalidFlagShortPayloadAndWrongArg() {
        assertNull(OemEventParser.parse(1292, 0, byteArrayOf(0x84.toByte(), 0xBA.toByte())))
        assertNull(OemEventParser.parse(1292, 0, byteArrayOf(4)))
        assertNull(OemEventParser.parse(1292, 1, byteArrayOf(4, 0xBA.toByte())))
    }
    @Test fun negativeTemperatureUsesSignedByteAndConvertsConfiguredFahrenheit() {
        assertEquals(-5f, (OemEventParser.parse(1288, 0, byteArrayOf(0, -5)) as OemEvent.Temperature).celsius, 0f)
        assertEquals(20f, (OemEventParser.parse(1288, 0, byteArrayOf(0, 68), true) as OemEvent.Temperature).celsius, 0.01f)
        assertNull(OemEventParser.parse(1288, 0, byteArrayOf(0, 127)))
    }
    @Test fun doorMaskPreservesUnknownPositionRatherThanInventingSpeedOrRpm() {
        assertEquals(0x40, (OemEventParser.parse(1285, 0, byteArrayOf(0x40)) as OemEvent.Doors).mask)
        assertEquals(0, (OemEventParser.parse(1285, 0, byteArrayOf(0)) as OemEvent.Doors).mask)
        assertNull(OemEventParser.parse(1285, 0, byteArrayOf()))
        assertNull(OemEventParser.parse(999, 0, byteArrayOf(1,2)))
    }
}
