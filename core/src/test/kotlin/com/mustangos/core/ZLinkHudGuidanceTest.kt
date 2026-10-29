package com.mustangos.core

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

/** Synthetic iAP2-shaped fixtures; not captured phone data. */
class ZLinkHudGuidanceTest {
    private fun tlv(id: Int,bytes: ByteArray)=ByteBuffer.allocate(bytes.size+4).putShort((bytes.size+4).toShort()).putShort(id.toShort()).put(bytes).array()
    private fun u16(n: Int)=ByteBuffer.allocate(2).putShort(n.toShort()).array()
    private fun u32(n: Int)=ByteBuffer.allocate(4).putInt(n).array()
    private fun u64(n: Long)=ByteBuffer.allocate(8).putLong(n).array()
    @Test fun routeAndManeuverMergeIntoTheCurrentInstruction() {
        val route=tlv(0,u16(1))+tlv(1,byteArrayOf(1))+tlv(4,"Casa".toByteArray())+tlv(6,u64(780))+tlv(7,u32(12400))+
            tlv(8,"12.4".toByteArray())+tlv(9,byteArrayOf(0))+tlv(10,u32(350))+tlv(11,"350".toByteArray())+tlv(12,byteArrayOf(2))+tlv(13,u16(3)+u16(4))
        var g=ZLinkRouteGuidance().apply(ZLinkHudGuidanceDecoder.route(route),10)
        g=g.apply(ZLinkHudGuidanceDecoder.maneuver(tlv(0,u16(1))+tlv(1,u16(3))+tlv(2,"Gira a la derecha".toByteArray())+tlv(3,byteArrayOf(2))+tlv(4,"Av. Vallarta".toByteArray())),11)
        g=g.apply(ZLinkHudGuidanceDecoder.maneuver(tlv(1,u16(4))+tlv(2,"Continúa".toByteArray())+tlv(3,byteArrayOf(3))),12)
        assertTrue(g.active); assertEquals("Gira a la derecha",g.current?.description); assertEquals("Av. Vallarta",g.current?.afterRoad)
        assertEquals("350 m",g.distanceToManeuver); assertEquals("12.4 km",g.distanceRemaining); assertEquals(780L,g.timeRemainingSeconds)
        assertEquals(ManeuverGlyph.RIGHT,ManeuverGlyph.of(g.current?.type)); assertEquals("Continúa",g.next?.description)
        // Partial update keeps the other fields; distance changes only.
        g=g.apply(ZLinkHudGuidanceDecoder.route(tlv(10,u32(120))+tlv(11,"100".toByteArray())),13)
        assertEquals("100 m",g.distanceToManeuver); assertEquals("Casa",g.destination)
        g=g.apply(ZLinkHudGuidanceDecoder.route(tlv(1,byteArrayOf(0))),14)
        assertFalse(g.active); assertNull(g.current); assertNull(g.destination)
    }
    @Test fun malformedOrUnrecognisedFramesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ZLinkHudGuidanceDecoder.route(byteArrayOf(0,9,0,1)) }
        assertThrows(IllegalArgumentException::class.java) { ZLinkHudGuidanceDecoder.route(tlv(3,"x".toByteArray())) }
        assertThrows(IllegalArgumentException::class.java) { ZLinkHudGuidanceDecoder.maneuver(tlv(2,"x".toByteArray())) }
        // Wrong widths are ignored, invalid UTF-8 text is dropped.
        val m=ZLinkHudGuidanceDecoder.maneuver(tlv(1,u16(2))+tlv(3,u32(2))+tlv(2,byteArrayOf(0xff.toByte(),0xfe.toByte())))
        assertNull(m.type); assertNull(m.description)
    }
}
