package com.mustangos.launcher

import com.mustangos.core.MediaFacts
import com.mustangos.core.ZLinkHudMusic
import com.mustangos.launcher.carplay.ZLinkHudProbeState
import com.mustangos.launcher.media.MediaUiState
import com.mustangos.launcher.media.ZLinkHudPresentation
import org.junit.Assert.*
import org.junit.Test

class ZLinkHudPresentationTest {
    private val pkg=ZLinkHudPresentation.PACKAGE
    private val hud=ZLinkHudProbeState(running=true,live=true,validMusicFrames=1,
        music=ZLinkHudMusic(title="HUD title",artist="HUD artist",durationMs=200000,elapsedMs=7000,rawStatus=1,updatedAtMs=100))
    private fun compose(base: MediaUiState=MediaUiState(MediaFacts(source=pkg)),preferred: String?=null,
        projection: String?=pkg,sample: ZLinkHudProbeState=hud,now: Long=100) = ZLinkHudPresentation.compose(base,sample,preferred,projection,now)
    @Test fun hudValuesReachCardWithoutInventedPlaybackOrPositionInterpolation() {
        val ui=compose()
        assertEquals("HUD title",ui.displayTitle); assertEquals("HUD artist",ui.displaySubtitle)
        assertEquals(7000L,ui.facts.progress(100000)); assertTrue(ui.reportedPosition)
        assertFalse(ui.playbackStateKnown); assertFalse(ui.facts.playing); assertNull(ui.artwork)
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
