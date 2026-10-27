package com.mustangos.core

import org.junit.Assert.*
import org.junit.Test

class OemEventDiagnosticsTest {
    @Test fun separatesReceivedFramesFromDecodedReadingsWithoutKeepingPayloads() {
        val stats = OemEventDiagnostics()
        stats.record(514, 1, 0, null, true)
        stats.record(1288, 0, 0, 1, false)
        stats.record(1292, 0, 0, 2, true)
        stats.record(1281, 56, 1, 4, false)
        stats.record(1281, 56, 1, 4, true)
        stats.record(999, 0, 0, 99999, true)
        val report = stats.report()
        assertTrue(report.contains("1288: recibidos=1; interpretados=0"))
        assertTrue(report.contains("1285: recibidos=0; interpretados=0"))
        assertTrue(report.contains("1281: recibidos=2; interpretados=1"))
        assertTrue(report.contains("arg1=56; arg2=1; bytes=4; recibidos=2"))
        assertFalse(report.contains("99999"))
        stats.clear()
        assertFalse(stats.report().contains("arg1=56"))
    }
    @Test fun unknownCanHeadersHaveFixedMemoryAndNeverBecomeMeasurements() {
        val stats = OemEventDiagnostics()
        repeat(100) { stats.record(1281, it, 0, 10, false) }
        stats.record(1281, Int.MAX_VALUE, 0, 10, false)
        val report = stats.report()
        assertEquals(24, report.lineSequence().count { it.startsWith("CAN arg1=") })
        assertTrue(report.contains("Cabeceras adicionales omitidas: 77"))
        assertTrue(report.contains("1281: recibidos=101; interpretados=0"))
        assertFalse(report.contains(Int.MAX_VALUE.toString()))
    }
}
