package com.mustangos.launcher

import android.graphics.Bitmap
import com.mustangos.launcher.carplay.ZLinkHudArtwork
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ZLinkHudArtworkTest {
    @Test fun boundedPngDecodesToASmallImageAndInvalidContentCannotAllocateLargeBitmaps() {
        val source=Bitmap.createBitmap(800,800,Bitmap.Config.ARGB_8888).apply { eraseColor(0xff1268ab.toInt()) }
        val png=ByteArrayOutputStream().also { source.compress(Bitmap.CompressFormat.PNG,100,it) }.toByteArray()
        val image=ZLinkHudArtwork.decode(png)!!
        assertTrue(image.width<=384 && image.height<=384)
        assertEquals(0xff1268ab.toInt(),image.getPixel(10,10))
        assertNull(ZLinkHudArtwork.decode(ByteArray(300000)))
        assertNull(ZLinkHudArtwork.decode(png.copyOf(20)))
        val attack=png.copyOf().apply { for(i in 16..19) this[i]=0x7f }
        assertNull(ZLinkHudArtwork.decode(attack)); assertNull(ZLinkHudArtwork.decode("not an image".toByteArray()))
        image.recycle(); source.recycle()
    }
}
