package com.mustangos.core

/** The native PNG callback omits its transfer ID. Require one recent item/artwork announcement; never guess an owner before it. */
class ZLinkHudArtworkWindow {
    private data class Pending(val revision: Long,val transfer: Int,val at: Long)
    private var pending: Pending?=null
    fun item(previous: ZLinkHudMusic,current: ZLinkHudMusic,delta: ZLinkHudMediaDelta,now: Long) {
        if(previous.trackRevision!=current.trackRevision || previous.artworkId!=current.artworkId) pending=null
        if(26 in delta.itemFields && current.artworkId!=null && (current.title!=null || current.itemId!=null))
            pending=Pending(current.trackRevision,current.artworkId,now)
    }
    fun consume(current: ZLinkHudMusic,now: Long): Boolean {
        val owner=pending; pending=null
        return owner!=null && owner.revision==current.trackRevision && owner.transfer==current.artworkId && now-owner.at in 0..2000
    }
}
