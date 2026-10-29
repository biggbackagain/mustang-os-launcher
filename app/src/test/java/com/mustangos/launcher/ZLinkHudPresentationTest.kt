package com.mustangos.launcher

import com.mustangos.core.MediaFacts
import com.mustangos.core.ZLinkHudMusic
import com.mustangos.launcher.carplay.ZLinkHudProbeState
import com.mustangos.launcher.media.MediaUiState
import com.mustangos.launcher.media.ZLinkHudPresentation
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class ZLinkHudPresentationTest {
    private val pkg=ZLinkHudPresentation.PACKAGE
    private val hud=ZLinkHudProbeState(running=true,live=true,validMusicFrames=1,
        music=ZLinkHudMusic(title="HUD title",artist="HUD artist",durationMs=200000,elapsedMs=7000,rawStatus=1,updatedAtMs=100))
    private fun compose(base: MediaUiState=MediaUiState(MediaFacts(source=pkg)),preferred: String?=null,
        projection: String?=pkg,sample: ZLinkHudProbeState=hud,now: Long=100) = ZLinkHudPresentation.compose(base,sample,preferred,projection,now)
    @Test fun hudValuesReachCardAndIap2StatusDrivesPlayback() {
        val ui=compose()
        assertEquals("HUD title",ui.displayTitle); assertEquals("HUD artist",ui.displaySubtitle)
        assertEquals(8000L,ui.facts.progress(1100)); assertTrue(ui.reportedPosition)
        assertTrue(ui.playbackStateKnown); assertTrue(ui.facts.playing); assertNull(ui.artwork)
        val paused=compose(sample=hud.copy(music=hud.music.copy(rawStatus=2)))
        assertTrue(paused.playbackStateKnown); assertFalse(paused.facts.playing); assertEquals(7000L,paused.facts.progress(100000))
        val unknown=compose(sample=hud.copy(music=hud.music.copy(rawStatus=77)))
        assertFalse(unknown.playbackStateKnown); assertFalse(unknown.facts.playing)
    }
    @Test fun standardSessionBorrowsTheCoverOnlyForTheSameSong() {
        val cover=android.graphics.Bitmap.createBitmap(4,4,android.graphics.Bitmap.Config.ARGB_8888)
        val withCover=hud.copy(artwork=cover)
        val same=MediaUiState(MediaFacts(source=pkg,title="hud TITLE",artist="session"))
        assertSame(cover,compose(same,sample=withCover).artwork); assertEquals("hud TITLE",compose(same,sample=withCover).displayTitle)
        val other=MediaUiState(MediaFacts(source=pkg,title="standard"))
        assertSame(other,compose(other,sample=withCover))
    }
    @Test fun neverMixBluetoothOtherProjectionStandardTitleOrBoundedProbeWithHud() {
        val bt=MediaUiState(MediaFacts(source="com.tw.bt",artist="BT"))
        assertSame(bt,compose(bt)); assertSame(bt,compose(bt,preferred=pkg))
        val session=MediaUiState(MediaFacts(source=pkg,title="standard",artist="session"))
        assertSame(session,compose(session))
        val empty=MediaUiState()
        assertSame(empty,compose(empty,preferred="com.tw.bt")); assertSame(empty,compose(empty,projection="another.zlink"))
        assertSame(empty,compose(empty,sample=hud.copy(live=false)))
        assertSame(empty,compose(empty,sample=hud.copy(running=false)))
    }
    @Test fun elapsedOnlyDisplaysPublishedTimeButLeavesTitleArtistAndDurationMissing() {
        val ui=compose(sample=hud.copy(music=ZLinkHudMusic(elapsedMs=45000,updatedAtMs=100)))
        assertNull(ui.displayTitle); assertNull(ui.displaySubtitle); assertEquals(0L,ui.facts.durationMs)
        assertTrue(ui.reportedPosition); assertEquals(45000L,ui.facts.positionMs)
        assertTrue(ui.status.contains("canción sin publicar"))
        assertTrue(compose(now=20000).status.contains("últimos"))
    }
    @Test fun durationAloneDoesNotTurnAnUnpublishedClockIntoZeroSeconds() {
        val ui=compose(base=MediaUiState(MediaFacts(source=pkg,playing=true),playbackStateKnown=true),
            sample=hud.copy(music=ZLinkHudMusic(title="Song",durationMs=200000,updatedAtMs=100)))
        assertEquals(200000L,ui.facts.durationMs)
        assertFalse(ui.reportedPosition); assertFalse(ui.playbackStateKnown)
    }
}
