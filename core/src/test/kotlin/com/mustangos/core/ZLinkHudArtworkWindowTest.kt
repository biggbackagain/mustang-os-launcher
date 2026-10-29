package com.mustangos.core

import org.junit.Assert.*
import org.junit.Test

class ZLinkHudArtworkWindowTest {
    private fun delta(vararg fields: Int)=ZLinkHudMediaDelta(fields.toSet(),emptySet(),null,null,null,null,null,null,null,7,emptySet())
    @Test fun imageBelongsToTheAnnouncedTrackWhileTheWindowIsOpen() {
        val window=ZLinkHudArtworkWindow(); val music=ZLinkHudMusic(title="Song",artworkId=7,trackRevision=1)
        assertFalse(window.accepts(music,100))
        window.item(ZLinkHudMusic(),music,delta(1,26),100)
        assertTrue(window.accepts(music,150))
        // A late image of the skipped track may be replaced by the real one: both arrive inside the same window.
        assertTrue(window.accepts(music,9_000)); assertFalse(window.accepts(music,25_000))
    }
    @Test fun previousTrackAndUnidentifiedItemsNeverOwnAnImage() {
        val window=ZLinkHudArtworkWindow(); val music=ZLinkHudMusic(title="Song",artworkId=7,trackRevision=1)
        window.item(ZLinkHudMusic(),music,delta(1,26),100)
        val next=ZLinkHudMusic(trackRevision=2)
        window.item(music,next,delta(26),200)
        assertFalse(window.accepts(music,300)); assertFalse(window.accepts(next,300))
        val named=next.copy(title="Next")
        window.item(next,named,delta(1),400); assertFalse(window.accepts(named,500))
        window.item(named,named.copy(artworkId=8),delta(26),600); assertTrue(window.accepts(named,700))
    }
    @Test fun playbackStatusSurvivesATrackChangeThatDoesNotRepeatIt() {
        val playing=ZLinkHudMusic(title="A",rawStatus=1,elapsedMs=9000,trackRevision=3)
        val next=playing.apply(ZLinkHudMediaDelta(setOf(1),emptySet(),null,"B",null,null,null,null,null,null,emptySet()),10)
        assertEquals("B",next.title); assertEquals(4L,next.trackRevision)
        assertEquals(true,next.playing); assertNull(next.elapsedMs)
    }
    @Test fun iap2PlaybackStatusAndArtworkKey() {
        assertEquals(true,ZLinkHudMusic(rawStatus=1).playing); assertEquals(false,ZLinkHudMusic(rawStatus=2).playing)
        assertNull(ZLinkHudMusic(rawStatus=9).playing); assertNull(ZLinkHudMusic().artworkKey)
        assertEquals(ZLinkHudMusic(title="A",artist="B").artworkKey,ZLinkHudMusic(title="a",artist="b").artworkKey)
    }
}
