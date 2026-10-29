package com.mustangos.core

/**
 * The native image callback omits its transfer ID. iAP2 announces the transfer with the item attributes and the
 * bytes follow, so an image belongs to the identified track whose change or announcement opened the window.
 * Several images may arrive for one track (a late image of the skipped track, then its own); the last one wins.
 */
class ZLinkHudArtworkWindow(private val windowMs: Long = 20_000) {
    private var owner: Long? = null
    private var openedAt = 0L
    fun item(previous: ZLinkHudMusic,current: ZLinkHudMusic,delta: ZLinkHudMediaDelta,now: Long) {
        val newTrack=previous.trackRevision!=current.trackRevision
        if(newTrack) owner=null
        if((current.title!=null || current.itemId!=null) && (newTrack || 26 in delta.itemFields)) { owner=current.trackRevision; openedAt=now }
    }
    fun accepts(current: ZLinkHudMusic,now: Long) = owner==current.trackRevision && now-openedAt in 0..windowMs
}
