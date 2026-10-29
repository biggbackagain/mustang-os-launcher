package com.mustangos.core

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Layout observed in the user's 6.1.06 native client. Values are transient; diagnostics use field IDs only. */
data class ZLinkHudMediaDelta(
    val itemFields: Set<Int>, val playbackFields: Set<Int>,
    val itemId: String?, val title: String?, val artist: String?, val album: String?,
    val durationMs: Long?, val elapsedMs: Long?, val rawStatus: Int?, val artworkId: Int?,
    val shapes: Set<String>
)

object ZLinkHudMediaDecoder {
    private data class Field(val id: Int, val bytes: ByteArray)
    private fun fields(body: ByteArray): List<Field> {
        val result=ArrayList<Field>(); var offset=0
        fun u16(i: Int)=((body[i].toInt() and 255) shl 8) or (body[i+1].toInt() and 255)
        while(offset<body.size) {
            require(result.size<128 && body.size-offset>=4) { "Invalid TLV boundary" }
            val length=u16(offset); val id=u16(offset+2)
            require(length>=4 && length<=body.size-offset) { "Invalid TLV length" }
            require(result.none { it.id==id }) { "Duplicate TLV field" }
            result.add(Field(id,body.copyOfRange(offset+4,offset+length))); offset+=length
        }
        return result
    }
    fun decode(body: ByteArray): ZLinkHudMediaDelta {
        require(body.size<=ZLinkHudFrameReader.MAX_PAYLOAD_BYTES)
        val outer=fields(body); val item=fields(outer.firstOrNull { it.id==0 }?.bytes ?: byteArrayOf())
        val playback=fields(outer.firstOrNull { it.id==1 }?.bytes ?: byteArrayOf())
        require(item.isNotEmpty() || playback.isNotEmpty()) { "No music groups" }
        fun value(group: List<Field>,id: Int)=group.firstOrNull { it.id==id }?.bytes
        fun number(group: List<Field>,id: Int,length: Int): Long? = value(group,id)?.let { bytes ->
            require(bytes.size==length) { "Invalid numeric field" }
            bytes.fold(0L) { n,b -> (n shl 8) or (b.toLong() and 255) }
        }
        fun text(id: Int): String? = value(item,id)?.let { bytes ->
            require(bytes.size<=1024) { "Oversize text" }
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString().trimEnd('\u0000').filter { !it.isISOControl() }.trim().takeIf { it.isNotEmpty() }
        }
        val id=value(item,0)?.let { require(it.size==8); it.joinToString("") { b -> "%02x".format(b.toInt() and 255) } }
        val shapes=outer.map { "${it.id}:${it.bytes.size}" }.toMutableSet()
        for((group,entries) in listOf(0 to item,1 to playback)) entries.forEach { shapes.add("$group/${it.id}:${it.bytes.size}") }
        return ZLinkHudMediaDelta(item.map { it.id }.toSet(),playback.map { it.id }.toSet(),id,text(1),text(12),text(6),
            number(item,4,4),number(playback,1,4),number(playback,0,1)?.toInt(),number(item,26,1)?.toInt(),shapes)
    }
}

data class ZLinkHudMusic(
    val itemId: String?=null,val title: String?=null,val artist: String?=null,val album: String?=null,
    val durationMs: Long?=null,val elapsedMs: Long?=null,val rawStatus: Int?=null,val artworkId: Int?=null,
    val trackRevision: Long=0,val updatedAtMs: Long=0
) {
    fun apply(delta: ZLinkHudMediaDelta,now: Long): ZLinkHudMusic {
        // A backwards clock without item attributes could be a skip or seek. Drop the old song conservatively.
        val backwards=delta.itemFields.isEmpty() && delta.elapsedMs!=null && elapsedMs!=null && delta.elapsedMs+2000<elapsedMs
        val changed=backwards || (0 in delta.itemFields && delta.itemId!=itemId) || (1 in delta.itemFields && delta.title!=title)
        val base=if(changed) ZLinkHudMusic(trackRevision=trackRevision+1) else this
        return base.copy(
            itemId=if(0 in delta.itemFields) delta.itemId else base.itemId,
            title=if(1 in delta.itemFields) delta.title else base.title,
            artist=if(12 in delta.itemFields) delta.artist else base.artist,
            album=if(6 in delta.itemFields) delta.album else base.album,
            durationMs=if(4 in delta.itemFields) delta.durationMs else base.durationMs,
            elapsedMs=if(1 in delta.playbackFields) delta.elapsedMs else base.elapsedMs,
            rawStatus=if(0 in delta.playbackFields) delta.rawStatus else base.rawStatus,
            artworkId=if(26 in delta.itemFields) delta.artworkId else base.artworkId,updatedAtMs=now)
    }
}
