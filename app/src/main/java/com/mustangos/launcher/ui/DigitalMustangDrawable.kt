package com.mustangos.launcher.ui

import android.graphics.*
import android.graphics.drawable.Drawable
import com.mustangos.core.*
import kotlin.math.*

/** Original lightweight illustration. It deliberately does not claim to be an exact model-year photograph. */
class DigitalMustangDrawable(var profile: MustangProfile) : Drawable() {
    var lights = 0f
    private val brush = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun path(vararg xy: Float) = Path().apply {
        moveTo(xy[0], xy[1]); for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1]); close()
    }
    private fun color(value: Int) { brush.shader = null; brush.color = value; brush.style = Paint.Style.FILL }
    private fun line(canvas: Canvas, shape: Path, value: Int, width: Float = 2f) {
        color(value); brush.style = Paint.Style.STROKE; brush.strokeWidth = width; canvas.drawPath(shape, brush); brush.style = Paint.Style.FILL
    }
    override fun draw(canvas: Canvas) {
        val checkpoint = canvas.save()
        val scale = min(bounds.width() / 900f, bounds.height() / 510f)
        canvas.translate(bounds.left + (bounds.width() - 900f * scale) / 2, bounds.top + (bounds.height() - 510f * scale) / 2)
        canvas.scale(scale, scale)
        val generation = MustangGeneration.forYear(profile.year)
        val classic = generation == MustangGeneration.CLASSIC || generation == MustangGeneration.II
        val square = generation == MustangGeneration.FOX || generation == MustangGeneration.II
        val modern = generation.ordinal >= MustangGeneration.S550.ordinal
        val convertible = profile.body == MustangBody.CONVERTIBLE
        val base = profile.paint.argb
        fun shade(factor: Float) = Color.rgb((Color.red(base) * factor).toInt().coerceIn(0,255),
            (Color.green(base) * factor).toInt().coerceIn(0,255), (Color.blue(base) * factor).toInt().coerceIn(0,255))
        color(0x48000000); canvas.drawOval(35f, 391f, 870f, 467f, brush)
        fun wheel(x: Float, y: Float, rx: Float, ry: Float) {
            color(0xff080b10.toInt()); canvas.drawOval(x-rx,y-ry,x+rx,y+ry,brush)
            color(0xff242b35.toInt()); canvas.drawOval(x-rx*.78f,y-ry*.78f,x+rx*.78f,y+ry*.78f,brush)
            color(0xffa6b2c1.toInt()); canvas.drawOval(x-rx*.64f,y-ry*.64f,x+rx*.64f,y+ry*.64f,brush)
            color(0xff101822.toInt()); canvas.drawOval(x-rx*.52f,y-ry*.52f,x+rx*.52f,y+ry*.52f,brush)
            for (i in 0 until 5) {
                val angle = i * PI * 2 / 5
                val a = angle + .15; val b = angle + .36
                color(0xffc9d3e0.toInt())
                canvas.drawPath(path(x,y,(x+cos(a)*rx*.6).toFloat(),(y+sin(a)*ry*.6).toFloat(),
                    (x+cos(b)*rx*.6).toFloat(),(y+sin(b)*ry*.6).toFloat()),brush)
            }
            color(0xff61738a.toInt()); canvas.drawCircle(x,y,8f,brush)
        }
        wheel(447f,371f,68f,85f); wheel(786f,341f,48f,70f)
        val roofStart = if (classic) 361f else if (square) 379f else 389f
        val roofTop = if (square) 116f else 105f
        color(0xff10151d.toInt())
        canvas.drawPath(path(274f,224f,roofStart,roofTop,585f,roofTop-8,671f,130f,741f,217f,550f,241f),brush)
        color(if (convertible) 0xff1b2028.toInt() else shade(.83f))
        canvas.drawPath(path(roofStart,roofTop,488f,90f,598f,94f,668f,124f,743f,214f,721f,220f,647f,139f,582f,114f,400f,125f,298f,224f,274f,224f),brush)
        brush.shader = LinearGradient(420f,125f,420f,226f,0xff496777.toInt(),0xff101a25.toInt(),Shader.TileMode.CLAMP)
        canvas.drawPath(path(398f,126f,575f,118f,559f,223f,300f,213f),brush)
        canvas.drawPath(path(597f,120f,642f,143f,703f,215f,577f,224f),brush)
        if (convertible) { color(0xff171b22.toInt()); canvas.drawPath(path(495f,91f,600f,94f,670f,126f,743f,214f,716f,217f,641f,145f,587f,117f),brush) }
        val body = Path().apply {
            moveTo(37f,300f); cubicTo(42f,258f,146f,232f,288f,211f)
            cubicTo(382f,200f,480f,215f,556f,232f); lineTo(731f,208f)
            lineTo(825f,207f); quadTo(860f,211f,863f,256f); lineTo(860f,354f)
            lineTo(836f,384f); lineTo(517f,425f); lineTo(365f,438f)
            lineTo(62f,421f); quadTo(25f,402f,28f,350f); close()
        }
        val openings = Path().apply { addOval(370f,278f,523f,463f,Path.Direction.CW); addOval(730f,266f,843f,425f,Path.Direction.CW) }
        body.op(openings,Path.Op.DIFFERENCE)
        brush.shader = LinearGradient(0f,213f,0f,431f,intArrayOf(shade(1.45f),shade(1.05f),shade(.6f),shade(.92f)),floatArrayOf(0f,.30f,.72f,1f),Shader.TileMode.CLAMP)
        canvas.drawPath(body,brush)
        line(canvas,body,0x70485870,1.5f)
        val hood = path(55f,278f,282f,216f,426f,218f,555f,234f,370f,296f)
        brush.shader = LinearGradient(0f,220f,0f,308f,shade(1.5f),shade(.8f),Shader.TileMode.CLAMP)
        canvas.drawPath(hood,brush)
        line(canvas,path(98f,267f,288f,223f,448f,227f),0x77ffffff,2f)
        line(canvas,path(365f,297f,554f,246f,831f,229f),0x99ffffff.toInt(),2.5f)
        line(canvas,path(536f,269f,702f,252f,703f,371f,534f,393f),shade(.43f),2f)
        line(canvas,path(540f,396f,728f,373f),0x66e5f4ff,3f)
        color(0xff0b1119.toInt())
        canvas.drawPath(path(69f,291f,256f,295f,270f,339f,58f,332f),brush)
        canvas.drawPath(path(62f,365f,248f,373f,230f,406f,70f,398f),brush)
        for (i in 0 until 8) line(canvas,path(75f+i*22,300f,80f+i*22,326f),0xff2a3544.toInt(),2f)
        color(0xffbfcddd.toInt()); canvas.drawRoundRect(666f,265f,696f,270f,3f,3f,brush)
        color(0xff121923.toInt()); canvas.drawOval(587f,209f,645f,237f,brush)
        if (profile.version.name.startsWith("SHELBY") || profile.version == MustangVersion.BOSS_302) {
            color(if(profile.paint == MustangPaint.WHITE) 0xcc172538.toInt() else 0xccdae4ef.toInt())
            canvas.drawPath(path(201f,242f,220f,237f,411f,278f,392f,285f),brush)
            canvas.drawPath(path(228f,236f,246f,232f,437f,271f,419f,278f),brush)
        }
        color(0xffccd7e4.toInt()); canvas.drawPath(path(148f,307f,166f,309f,179f,305f,176f,315f,168f,315f,163f,322f,159f,315f,148f,315f),brush)
        fun lamp(x: Float, y: Float, near: Boolean) {
            val rx = if(near) 33f else 13f
            color(0xff121b26.toInt()); canvas.drawRoundRect(x-rx-5,y-17,x+rx+5,y+17,5f,5f,brush)
            color(if(lights > .01f) 0xffeff8ff.toInt() else 0xff788c9f.toInt())
            if (classic || generation == MustangGeneration.S197) {
                canvas.drawOval(x-rx*.6f,y-12,x+rx*.6f,y+12,brush)
            } else if (modern) {
                for(i in 0..2) canvas.drawRoundRect(x-rx+i*rx*.65f,y-9,x-rx+i*rx*.65f+6,y+8,2f,2f,brush)
            } else canvas.drawRoundRect(x-rx,y-10,x+rx,y+10,2f,2f,brush)
            if(lights > .01f) {
                brush.shader = RadialGradient(x,y,if(near) 58f else 30f,Color.argb((125*lights).toInt(),180,223,255),Color.TRANSPARENT,Shader.TileMode.CLAMP)
                canvas.drawCircle(x,y,if(near) 58f else 30f,brush)
            }
        }
        lamp(49f,311f,false); lamp(315f,318f,true)
        canvas.restoreToCount(checkpoint)
    }
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Drawable opacity") override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth() = 900
    override fun getIntrinsicHeight() = 510
}
