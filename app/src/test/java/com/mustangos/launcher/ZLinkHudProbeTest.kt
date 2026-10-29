package com.mustangos.launcher

import android.os.Looper
import com.mustangos.launcher.carplay.ZLinkHudProbe
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.*
import java.net.*
import java.nio.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class ZLinkHudProbeTest {
    private class FixtureSocket(val input: InputStream, val refuse: Boolean=false) : Socket() {
        var endpoint: InetSocketAddress?=null; var attempts=0; var released=false
        override fun setSoTimeout(timeout: Int) { assertEquals(250,timeout) }
        override fun connect(endpoint: SocketAddress,timeout: Int) {
            this.endpoint=endpoint as InetSocketAddress; attempts++; assertEquals(800,timeout)
            if(refuse) throw ConnectException()
        }
        override fun getInputStream()=input
        override fun getOutputStream(): OutputStream { throw AssertionError("The observation must never write to ZLink") }
        override fun close() { released=true; input.close() }
    }
    private fun awaitDone(probe: ZLinkHudProbe) {
        val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3)
        while(probe.state.value.running && System.nanoTime()<until) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }
        assertFalse(probe.state.value.running)
    }
    @Test fun onlyTheKnownLoopbackIsReadAndNoContentAppearsInReport() {
        val bytes=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(10).putInt(7).array()+"PRIVATE".toByteArray()
        val socket=FixtureSocket(ByteArrayInputStream(bytes)); val probe=ZLinkHudProbe { socket }
        assertEquals(0,socket.attempts)
        probe.start(); awaitDone(probe)
        assertEquals("127.0.0.1",socket.endpoint!!.hostString); assertEquals(1555,socket.endpoint!!.port)
        assertEquals(1,probe.state.value.frames); assertEquals(mapOf(10 to 1),probe.state.value.types)
        assertTrue(socket.released); assertFalse(probe.report().contains("PRIVATE"))
        probe.close()
    }
    @Test fun aRefusedConnectionNeverRetriesOrInventsMetadata() {
        val socket=FixtureSocket(ByteArrayInputStream(byteArrayOf()),true); val probe=ZLinkHudProbe { socket }
        probe.start(); awaitDone(probe)
        assertFalse(probe.state.value.connected); assertEquals(0,probe.state.value.frames)
        assertEquals(1,socket.attempts); assertTrue(socket.released)
        probe.close()
    }
    @Test fun stoppingClosesBlockedInputAndRejectsTheLateResult() {
        val entered=CountDownLatch(1); val released=CountDownLatch(1)
        val input=object : InputStream() {
            override fun read(): Int { entered.countDown(); released.await(2,TimeUnit.SECONDS); return -1 }
            override fun close() { released.countDown() }
        }
        val socket=FixtureSocket(input); val probe=ZLinkHudProbe { socket }
        probe.start(); assertTrue(entered.await(2,TimeUnit.SECONDS))
        probe.stop(); assertTrue(socket.released)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Lectura HUD detenida",probe.state.value.status)
        assertEquals(0,probe.state.value.frames)
        probe.close()
    }
    @Test fun liveModePublishesDecodedFieldsInMemoryButNeverExportsTheirContents() {
        fun tlv(id: Int,bytes: ByteArray)=ByteBuffer.allocate(bytes.size+4).putShort((bytes.size+4).toShort()).putShort(id.toShort()).put(bytes).array()
        val body=tlv(0,tlv(1,"PRIVATE SONG".toByteArray())+tlv(12,"PRIVATE ARTIST".toByteArray()))+
            tlv(1,tlv(1,ByteBuffer.allocate(4).putInt(12000).array()))
        val bytes=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(10).putInt(body.size).array()+body
        val atEnd=CountDownLatch(1); val closed=CountDownLatch(1)
        val source=ByteArrayInputStream(bytes)
        val input=object : InputStream() {
            override fun read(): Int {
                if(source.available()>0) return source.read()
                atEnd.countDown(); closed.await(2,TimeUnit.SECONDS); return -1
            }
            override fun read(b: ByteArray,off: Int,len: Int): Int {
                if(source.available()>0) return source.read(b,off,len)
                return read()
            }
            override fun close() { closed.countDown() }
        }
        val socket=FixtureSocket(input); val probe=ZLinkHudProbe { socket }
        probe.start(true); assertTrue(atEnd.await(2,TimeUnit.SECONDS))
        assertTrue(probe.state.value.running); assertEquals("PRIVATE SONG",probe.state.value.music.title)
        assertEquals(12000L,probe.state.value.music.elapsedMs); assertEquals(1,probe.state.value.validMusicFrames)
        assertTrue(probe.report().contains("0/1:12")); assertFalse(probe.report().contains("PRIVATE"))
        probe.start(true); assertEquals(1,socket.attempts)
        probe.stop(); shadowOf(Looper.getMainLooper()).idle()
        assertNull(probe.state.value.music.title); assertTrue(socket.released)
        probe.close()
    }
}
