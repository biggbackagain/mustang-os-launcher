package com.mustangos.launcher.media

import com.mustangos.launcher.carplay.ZLinkHudProbeState

object ZLinkHudPresentation {
    const val PACKAGE="com.zjinnova.zlink"
    fun compose(base: MediaUiState,hud: ZLinkHudProbeState,preferred: String?,projection: String?,now: Long): MediaUiState {
        if(!hud.live || !hud.running || hud.validMusicFrames==0 || projection!=PACKAGE ||
            (preferred!=null && preferred!=PACKAGE) || (base.facts.source!=null && base.facts.source!=PACKAGE)) return base
        // Do not merge fields from different sessions/tracks. Standard metadata wins when it has a title.
        if(base.displayTitle!=null) return base
        val music=hud.music
        if(music.title==null && music.elapsedMs==null && music.durationMs==null) return base
        val old=now-music.updatedAtMs>10_000
        return base.copy(facts=base.facts.copy(source=PACKAGE,title=music.title,artist=music.artist,album=music.album,
            durationMs=music.durationMs ?: 0,positionMs=music.elapsedMs ?: 0,updatedAtMs=music.updatedAtMs,speed=0f),
            artwork=hud.artwork,coverStatus=if(hud.artwork!=null) "Imagen recibida de CarPlay" else "CarPlay no ha publicado una portada asociable",
            playbackStateKnown=false,
            status=if(old) "CarPlay · últimos datos recibidos" else if(music.title==null) "CarPlay · tiempo recibido; canción sin publicar" else "CarPlay · datos de la canción",
            fallbackDisplayTitle=null,fallbackDisplaySubtitle=null,reportedPosition=music.elapsedMs!=null)
    }
}
