package com.mustangos.launcher.media

import com.mustangos.launcher.carplay.ZLinkHudProbeState

object ZLinkHudPresentation {
    const val PACKAGE="com.zjinnova.zlink"
    fun compose(base: MediaUiState,hud: ZLinkHudProbeState,preferred: String?,projection: String?,now: Long): MediaUiState {
        if(!hud.live || !hud.running || hud.validMusicFrames==0 || projection!=PACKAGE ||
            (preferred!=null && preferred!=PACKAGE) || (base.facts.source!=null && base.facts.source!=PACKAGE)) return base
        val music=hud.music
        // Standard metadata wins when it has a title; the cover is borrowed only for the very same song.
        if(base.displayTitle!=null) {
            val sameSong=music.title!=null && music.title.equals(base.displayTitle,ignoreCase=true)
            return if(base.artwork==null && hud.artwork!=null && sameSong) base.copy(artwork=hud.artwork,coverStatus="Imagen recibida de CarPlay") else base
        }
        if(music.title==null && music.elapsedMs==null && music.durationMs==null) return base
        val old=now-music.updatedAtMs>10_000
        val playing=music.playing?.let { it && !old }
        return base.copy(facts=base.facts.copy(source=PACKAGE,title=music.title,artist=music.artist,album=music.album,
            durationMs=music.durationMs ?: 0,positionMs=music.elapsedMs ?: 0,updatedAtMs=music.updatedAtMs,
            playing=playing==true,speed=if(playing==true) 1f else 0f),
            artwork=hud.artwork,coverStatus=if(hud.artwork!=null) "Imagen recibida de CarPlay" else "Esperando portada de CarPlay",
            playbackStateKnown=playing!=null,
            status=if(old) "CarPlay · últimos datos recibidos" else if(music.title==null) "CarPlay · tiempo recibido; canción sin publicar" else "CarPlay · datos de la canción",
            fallbackDisplayTitle=null,fallbackDisplaySubtitle=null,reportedPosition=music.elapsedMs!=null)
    }
}
