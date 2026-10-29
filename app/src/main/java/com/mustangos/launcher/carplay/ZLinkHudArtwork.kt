package com.mustangos.launcher.carplay

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** Decode only bounded PNG bytes from the same ordered HUD connection, never a URL or file path. */
internal object ZLinkHudArtwork {
    private val signature=byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,13,10,26,10)
    fun decode(bytes: ByteArray): Bitmap? {
        if(bytes.size !in 24..com.mustangos.core.ZLinkHudFrameReader.MAX_ARTWORK_BYTES || !bytes.copyOfRange(0,8).contentEquals(signature) ||
            !bytes.copyOfRange(12,16).contentEquals("IHDR".toByteArray(Charsets.US_ASCII))) return null
        fun number(i: Int)=(0..3).fold(0L) { n,k -> (n shl 8) or (bytes[i+k].toLong() and 255) }
        val width=number(16); val height=number(20)
        if(width !in 1..2048 || height !in 1..2048 || width*height>1048576) return null
        var sample=1
        while((maxOf(width,height)+sample-1)/sample>384) sample*=2
        return try {
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize=sample; inPreferredConfig=Bitmap.Config.ARGB_8888 })
                ?.takeIf { it.width<=384 && it.height<=384 }
        } catch(_: RuntimeException) { null } catch(_: OutOfMemoryError) { null }
    }
}
