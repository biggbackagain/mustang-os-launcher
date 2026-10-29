package com.mustangos.core

import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class ZLinkHudFrameReaderTest {
    private fun header(type: Int,length: Int)=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(type).putInt(length).array()
    @Test fun fragmentedFramesAreReadExactlyWithoutLeakingTheirContent() {
        val bytes=header(10,7)+"private".toByteArray()+header(14,0)
        val input=object : ByteArrayInputStream(bytes) { override fun read(b: ByteArray,off: Int,len: Int)=super.read(b,off,minOf(2,len)) }
        assertEquals(ZLinkHudFrameHeader(10,7),ZLinkHudFrameReader.read(input))
        assertEquals(ZLinkHudFrameHeader(14,0),ZLinkHudFrameReader.read(input))
        assertFalse(ZLinkHudFrameReader.read(ByteArrayInputStream(header(8,0))).toString().contains("private"))
    }
    @Test fun invalidLengthsAndTypesAreRejectedBeforeReadingContent() {
        for(bytes in listOf(header(10,-1),header(10,Int.MAX_VALUE),header(-1,0))) {
            val input=ByteArrayInputStream(bytes)
            try { ZLinkHudFrameReader.read(input); fail("Invalid frame accepted") } catch(_: IllegalArgumentException) {}
            assertEquals(0,input.available())
        }
    }
    @Test fun truncatedInputAndDeadlineCannotBlockOrProduceAFrame() {
        for(bytes in listOf(byteArrayOf(1,0),header(10,5)+byteArrayOf(1,2))) {
            try { ZLinkHudFrameReader.read(ByteArrayInputStream(bytes)); fail("Truncated frame accepted") } catch(_: EOFException) {}
        }
        try { ZLinkHudFrameReader.read(ByteArrayInputStream(header(10,0))) { false }; fail("Deadline ignored") } catch(_: InterruptedIOException) {}
    }
    @Test fun artworkHasItsOwnBoundAndDoesNotDisconnectTheFollowingMusicFrame() {
        val image=ByteArray(300000)
        val input=ByteArrayInputStream(header(14,image.size)+image+header(10,0))
        assertEquals(image.size,ZLinkHudFrameReader.readFrame(input,setOf(14)).payload!!.size)
        assertEquals(ZLinkHudFrameHeader(10,0),ZLinkHudFrameReader.read(input))
        try { ZLinkHudFrameReader.read(ByteArrayInputStream(header(14,ZLinkHudFrameReader.MAX_ARTWORK_BYTES+1))); fail("Oversize image accepted") } catch(_: ZLinkHudHeaderException) { }
    }
}
