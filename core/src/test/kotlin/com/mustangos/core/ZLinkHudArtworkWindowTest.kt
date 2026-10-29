package com.mustangos.core

import org.junit.Assert.*
import org.junit.Test

class ZLinkHudArtworkWindowTest {
    private fun delta()=ZLinkHudMediaDecoder.decode(byteArrayOf(0,9,0,0,0,5,0,26,7))
    @Test fun imageNeedsARecentItemAnnouncementAndCanOnlyBeConsumedOnce() {
        val window=ZLinkHudArtworkWindow(); val music=ZLinkHudMusic(title="Song",artworkId=7,trackRevision=1)
        assertFalse(window.consume(music,100))
        window.item(ZLinkHudMusic(),music,delta(),100)
        assertTrue(window.consume(music,150)); assertFalse(window.consume(music,200))
    }
    @Test fun lateAndPreviousTrackImagesAreDiscarded() {
        val window=ZLinkHudArtworkWindow(); val music=ZLinkHudMusic(title="Song",artworkId=7,trackRevision=1)
        window.item(ZLinkHudMusic(),music,delta(),100)
        assertFalse(window.consume(music,2200))
        window.item(ZLinkHudMusic(),music,delta(),100)
        assertFalse(window.consume(music.copy(trackRevision=2),200))
    }
}
