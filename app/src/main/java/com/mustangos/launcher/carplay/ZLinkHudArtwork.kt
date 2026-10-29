package com.mustangos.launcher.carplay

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** Decode only bounded image bytes from the same ordered HUD connection, never a URL or file path. */
internal object ZLinkHudArtwork {
    private const val MAX_SIDE=384
    private val signatures=listOf(
        byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,13,10,26,10),      // PNG
        byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte()),      // JPEG (iPhone artwork is usually JPEG)
        "RIFF".toByteArray(Charsets.US_ASCII),                       // WebP container
        "GIF8".toByteArray(Charsets.US_ASCII))
    /** Offset of a known image signature within the first bytes; ZLink may prefix its own small header. */
    fun imageStart(bytes: ByteArray): Int? = (0..minOf(64,bytes.size-3).coerceAtLeast(-1)).firstOrNull { offset ->
        signatures.any { s -> offset+s.size<=bytes.size && s.indices.all { bytes[offset+it]==s[it] } }
    }
    fun signature(bytes: ByteArray): String = bytes.take(4).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun decode(bytes: ByteArray): Bitmap? {
        if(bytes.size !in 24..com.mustangos.core.ZLinkHudFrameReader.MAX_ARTWORK_BYTES) return null
        val start=imageStart(bytes) ?: return null
        val length=bytes.size-start
        return try {
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeByteArray(bytes,start,length,bounds)
            val width=bounds.outWidth.toLong(); val height=bounds.outHeight.toLong()
            if(width !in 1..4096 || height !in 1..4096 || width*height>16_777_216) return null
            var sample=1
            while((maxOf(width,height)+sample-1)/sample>MAX_SIDE*2) sample*=2
            val decoded=BitmapFactory.decodeByteArray(bytes,start,length,BitmapFactory.Options().apply { inSampleSize=sample; inPreferredConfig=Bitmap.Config.ARGB_8888 })
                ?: return null
            if(decoded.width<=MAX_SIDE && decoded.height<=MAX_SIDE) decoded
            else {
                val scale=MAX_SIDE.toFloat()/maxOf(decoded.width,decoded.height)
                Bitmap.createScaledBitmap(decoded,(decoded.width*scale).toInt().coerceAtLeast(1),(decoded.height*scale).toInt().coerceAtLeast(1),true)
                    .also { if(it!==decoded) decoded.recycle() }
            }
        } catch(_: RuntimeException) { null } catch(_: OutOfMemoryError) { null }
    }
}
