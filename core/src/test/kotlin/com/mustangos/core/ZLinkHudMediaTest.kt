package com.mustangos.core

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

/** Synthetic protocol fixtures derived from static analysis, never represented as captured phone data. */
class ZLinkHudMediaTest {
    private fun tlv(id: Int,vararg values: ByteArray): ByteArray {
        val bytes=values.fold(byteArrayOf()) { a,b -> a+b }
        return ByteBuffer.allocate(bytes.size+4).putShort((bytes.size+4).toShort()).putShort(id.toShort()).put(bytes).array()
    }
    private fun u32(n: Int)=ByteBuffer.allocate(4).putInt(n).array()
    @Test fun completeItemAndElapsedAreBigEndianAndOnlyKnownFieldsAreInterpreted() {
        val body=tlv(0,tlv(0,ByteArray(8) { 1 }),tlv(1,"Song".toByteArray()),tlv(12,"Artist".toByteArray()),
            tlv(6,"Album\u0000".toByteArray()),tlv(4,u32(240000)),tlv(26,byteArrayOf(3)),tlv(99,byteArrayOf(1)))+
            tlv(1,tlv(1,u32(15000)),tlv(0,byteArrayOf(9)))
        val decoded=ZLinkHudMediaDecoder.decode(body)
        val music=ZLinkHudMusic().apply(decoded,100)
        assertEquals("Song",music.title); assertEquals("Artist",music.artist); assertEquals("Album",music.album)
        assertEquals(240000L,music.durationMs); assertEquals(15000L,music.elapsedMs)
        assertEquals(9,music.rawStatus); assertEquals(3,music.artworkId)
        assertTrue(decoded.shapes.contains("0/12:6")); assertFalse(decoded.shapes.toString().contains("Song"))
    }
    @Test fun elapsedOnlyTwelveByteMessageDoesNotInventSongOrPlaybackSemantics() {
        val body=tlv(1,tlv(1,u32(45000)))
        assertEquals(12,body.size)
        val music=ZLinkHudMusic().apply(ZLinkHudMediaDecoder.decode(body),100)
        assertNull(music.title); assertNull(music.rawStatus); assertNull(music.durationMs)
        assertEquals(45000L,music.elapsedMs)
    }
    @Test fun trackTransitionClearsPreviousArtistDurationPositionAndArtworkIdentifier() {
        val first=ZLinkHudMusic().apply(ZLinkHudMediaDecoder.decode(tlv(0,tlv(1,"A".toByteArray()),
            tlv(12,"old artist".toByteArray()),tlv(4,u32(200)),tlv(26,byteArrayOf(5)))+tlv(1,tlv(1,u32(100)))),1)
        val next=first.apply(ZLinkHudMediaDecoder.decode(tlv(0,tlv(1,"B".toByteArray()))),2)
        assertEquals("B",next.title); assertNull(next.artist); assertNull(next.durationMs)
        assertNull(next.elapsedMs); assertNull(next.artworkId); assertTrue(next.trackRevision>first.trackRevision)
    }
    @Test fun partialUpdatesKeepTrackAndEmptyFieldClearsValue() {
        val first=ZLinkHudMusic(title="A",artist="artist",durationMs=1000,trackRevision=7)
        val next=first.apply(ZLinkHudMediaDecoder.decode(tlv(1,tlv(1,u32(500)))),2)
        assertEquals("A",next.title); assertEquals(7L,next.trackRevision)
        assertNull(next.apply(ZLinkHudMediaDecoder.decode(tlv(0,tlv(12,byteArrayOf()))),3).artist)
    }
    @Test fun backwardsClockWithoutItemDataDropsThePreviousSongConservatively() {
        val first=ZLinkHudMusic(title="Previous",artist="artist",durationMs=100000,elapsedMs=45000)
        val next=first.apply(ZLinkHudMediaDecoder.decode(tlv(1,tlv(1,u32(1000)))),100)
        assertNull(next.title); assertNull(next.artist); assertNull(next.durationMs); assertEquals(1000L,next.elapsedMs)
    }
    @Test fun malformedTlvDuplicateFieldsBadUtf8AndWrongNumberSizesAreRejectedAtomically() {
        val invalid=listOf(byteArrayOf(0,3,0,0),byteArrayOf(0,10,0,0),tlv(0,tlv(1,byteArrayOf(0xc3.toByte()))),
            tlv(0,tlv(1,byteArrayOf(1)),tlv(1,byteArrayOf(2))),tlv(1,tlv(1,byteArrayOf(1))),
            tlv(0,tlv(4,u32(1))+byteArrayOf(0)),tlv(0,tlv(1,ByteArray(1025) { 65 })))
        invalid.forEach { bytes ->
            try { ZLinkHudMediaDecoder.decode(bytes); fail("Invalid TLV accepted") } catch(_: Exception) { }
        }
    }
    @Test fun readerRetainsOnlyRequestedTypesAndKeepsNextFrameAligned() {
        fun frame(type: Int,body: ByteArray)=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(type).putInt(body.size).array()+body
        val input=ByteArrayInputStream(frame(8,byteArrayOf(4,5,6))+frame(10,tlv(1,tlv(1,u32(5)))))
        assertNull(ZLinkHudFrameReader.readFrame(input,setOf(10)).payload)
        assertEquals(5L,ZLinkHudMediaDecoder.decode(ZLinkHudFrameReader.readFrame(input,setOf(10)).payload!!).elapsedMs)
    }
}
