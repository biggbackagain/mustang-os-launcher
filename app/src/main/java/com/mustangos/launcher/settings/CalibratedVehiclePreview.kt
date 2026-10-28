package com.mustangos.launcher.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.LruCache
import com.mustangos.core.*
import com.mustangos.launcher.R
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Runtime paint preview on the bundled, calibrated S197 convertible. Original asset is never changed. */
class CalibratedVehiclePreview(context: Context) {
    private val resources = context.applicationContext.resources
    private val lock = Mutex()
    private var original: Bitmap? = null
    private val cache=object : LruCache<MustangPaint,Bitmap>(4*1024*1024) {
        override fun sizeOf(key: MustangPaint,value: Bitmap)=value.allocationByteCount
    }
    fun supports(profile: MustangProfile) = profile.year in 2010..2014 && profile.body == MustangBody.CONVERTIBLE
    suspend fun load(profile: MustangProfile): Bitmap? = withContext(Dispatchers.IO) {
        if(!supports(profile)) return@withContext null
        lock.withLock {
            val source=original ?: BitmapFactory.decodeResource(resources,R.drawable.mustang_2010,BitmapFactory.Options().apply { inScaled=false }).also { original=it } ?: return@withLock null
            if(profile.paint == MustangPaint.RED) return@withLock source
            cache.get(profile.paint)?.let { return@withLock it }
            val pixels=IntArray(source.width*source.height)
            source.getPixels(pixels,0,source.width,0,0,source.width,source.height)
            val target=FloatArray(3); Color.colorToHSV(profile.paint.argb,target)
            val hsv=FloatArray(3)
            for(y in 0 until source.height) {
                currentCoroutineContext().ensureActive()
                val normalizedY=y*600f/source.height
                if(normalizedY !in 123f..425f) continue
                for(x in 0 until source.width) {
                    val normalizedX=x*900f/source.width
                    if(normalizedX !in 29f..875f) continue
                    if((normalizedX in 44f..70f && normalizedY in 278f..320f) ||
                        (normalizedX in 274f..368f && normalizedY in 282f..332f) ||
                        (normalizedX in 337f..382f && normalizedY in 363f..385f)) continue
                    val index=y*source.width+x; val pixel=pixels[index]
                    val r=Color.red(pixel); val g=Color.green(pixel); val b=Color.blue(pixel)
                    // The calibrated paint is red. Glass, chrome, roof, lamps, wheels and asphalt stay intact.
                    if(r < 30 || r < g*1.3f || r < b*1.25f) continue
                    Color.colorToHSV(pixel,hsv)
                    if(hsv[1] < .3f || (hsv[0] > 24f && hsv[0] < 337f)) continue
                    val value=hsv[2]
                    hsv[0]=target[0]; hsv[1]=(hsv[1]*target[1]).coerceIn(0f,1f)
                    hsv[2]=when(profile.paint) {
                        MustangPaint.BLACK -> value*.29f
                        MustangPaint.WHITE -> (.18f+value*.82f).coerceAtMost(1f)
                        else -> (value*target[2]/.8f).coerceIn(0f,1f)
                    }
                    pixels[index]=Color.HSVToColor(Color.alpha(pixel),hsv)
                }
            }
            currentCoroutineContext().ensureActive()
            Bitmap.createBitmap(pixels,source.width,source.height,Bitmap.Config.ARGB_8888).also { cache.put(profile.paint,it) }
        }
    }
}
