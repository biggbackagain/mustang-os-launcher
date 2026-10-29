package com.mustangos.core

/** Structural diagnosis only. Never decodes or exports a street, destination, location or route. */
object ZLinkHudNavigationShape {
    fun inspect(body: ByteArray): Set<String> {
        require(body.size<=ZLinkHudFrameReader.MAX_PAYLOAD_BYTES)
        val fields=mutableSetOf<String>(); var offset=0; var count=0
        fun u16(i: Int)=((body[i].toInt() and 255) shl 8) or (body[i+1].toInt() and 255)
        while(offset<body.size) {
            require(++count<=128 && body.size-offset>=4)
            val size=u16(offset); val id=u16(offset+2)
            require(size>=4 && size<=body.size-offset)
            fields.add("$id:${size-4}"); offset+=size
        }
        return fields
    }
}
