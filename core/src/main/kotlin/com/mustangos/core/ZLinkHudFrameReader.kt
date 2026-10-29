package com.mustangos.core

import java.io.EOFException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

data class ZLinkHudFrameHeader(val type: Int, val bytes: Int)
data class ZLinkHudFrame(val header: ZLinkHudFrameHeader, val payload: ByteArray?)
class ZLinkHudHeaderException(val frameType: Int,val declaredBytes: Int) : IllegalArgumentException("type=$frameType; bytes=$declaredBytes")

/** Observed wire framing: two little-endian int32s. Retain only explicitly requested, bounded bodies. */
object ZLinkHudFrameReader {
    const val MAX_PAYLOAD_BYTES = 256 * 1024
    const val MAX_ARTWORK_BYTES = 2 * 1024 * 1024
    fun read(input: InputStream, active: () -> Boolean = { true }): ZLinkHudFrameHeader {
        return readFrame(input, emptySet(), active).header
    }
    fun readFrame(input: InputStream, retainTypes: Set<Int>, active: () -> Boolean = { true }): ZLinkHudFrame {
        fun exact(buffer: ByteArray, count: Int) {
            var offset=0
            while(offset<count) {
                if(!active()) throw InterruptedIOException("Sample finished")
                val n=try { input.read(buffer,offset,count-offset) } catch(_: SocketTimeoutException) { continue }
                if(n<0) throw EOFException("Incomplete frame")
                if(n==0) throw EOFException("No progress")
                offset+=n
            }
        }
        val header=ByteArray(8); exact(header,8)
        fun intAt(index: Int) = (0..3).fold(0) { value,i -> value or ((header[index+i].toInt() and 255) shl (8*i)) }
        val type=intAt(0); val length=intAt(4)
        val limit=if(type==14) MAX_ARTWORK_BYTES else MAX_PAYLOAD_BYTES
        if(type !in 0..65535 || length !in 0..limit) throw ZLinkHudHeaderException(type,length)
        if(type in retainTypes) {
            val body=ByteArray(length); exact(body,length)
            return ZLinkHudFrame(ZLinkHudFrameHeader(type,length),body)
        }
        val discard=ByteArray(minOf(4096,length))
        var remaining=length
        while(remaining>0) { val n=minOf(remaining,discard.size); exact(discard,n); remaining-=n }
        return ZLinkHudFrame(ZLinkHudFrameHeader(type,length),null)
    }
}
