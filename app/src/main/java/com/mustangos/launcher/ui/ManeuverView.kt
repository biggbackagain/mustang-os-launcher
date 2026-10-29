package com.mustangos.launcher.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.mustangos.core.ManeuverGlyph
import kotlin.math.min

/** Turn arrow for the CarPlay route card. The iPhone's own instruction text is always shown next to it. */
class ManeuverView @JvmOverloads constructor(context: Context,attrs: AttributeSet?=null) : View(context,attrs) {
    var glyph: ManeuverGlyph=ManeuverGlyph.STRAIGHT
        set(value) { if(field!=value) { field=value; invalidate() } }
    private val stroke=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND; color=Color.WHITE }
    private val fill=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE }
    private val path=Path()
    override fun onDraw(canvas: Canvas) {
        val s=min(width,height).toFloat(); if(s<=0) return
        canvas.save(); canvas.translate((width-s)/2,(height-s)/2); canvas.scale(s/100f,s/100f)
        stroke.strokeWidth=11f
        path.reset()
        fun head(x: Float,y: Float,angle: Float) {
            canvas.save(); canvas.translate(x,y); canvas.rotate(angle)
            val h=Path().apply { moveTo(0f,-17f); lineTo(15f,4f); lineTo(-15f,4f); close() }
            canvas.drawPath(h,fill); canvas.restore()
        }
        when(glyph) {
            ManeuverGlyph.STRAIGHT,ManeuverGlyph.START -> { path.moveTo(50f,92f); path.lineTo(50f,24f); canvas.drawPath(path,stroke); head(50f,20f,0f) }
            ManeuverGlyph.LEFT,ManeuverGlyph.RIGHT -> {
                val d=if(glyph==ManeuverGlyph.LEFT) -1f else 1f
                path.moveTo(50f-d*10f,92f); path.lineTo(50f-d*10f,55f); path.quadTo(50f-d*10f,40f,50f+d*5f,40f); path.lineTo(50f+d*22f,40f)
                canvas.drawPath(path,stroke); head(50f+d*26f,40f,90f*d)
            }
            ManeuverGlyph.SLIGHT_LEFT,ManeuverGlyph.SLIGHT_RIGHT,ManeuverGlyph.KEEP_LEFT,ManeuverGlyph.KEEP_RIGHT,ManeuverGlyph.EXIT_LEFT,ManeuverGlyph.EXIT_RIGHT -> {
                val d=if(glyph==ManeuverGlyph.SLIGHT_LEFT || glyph==ManeuverGlyph.KEEP_LEFT || glyph==ManeuverGlyph.EXIT_LEFT) -1f else 1f
                if(glyph==ManeuverGlyph.KEEP_LEFT || glyph==ManeuverGlyph.KEEP_RIGHT) {
                    stroke.alpha=90; canvas.drawLine(50f-d*14f,92f,50f-d*14f,22f,stroke); stroke.alpha=255
                }
                path.moveTo(50f-d*12f,92f); path.lineTo(50f-d*12f,62f); path.cubicTo(50f-d*12f,46f,50f+d*20f,44f,50f+d*20f,26f)
                canvas.drawPath(path,stroke); head(50f+d*20f,22f,0f)
            }
            ManeuverGlyph.SHARP_LEFT,ManeuverGlyph.SHARP_RIGHT -> {
                val d=if(glyph==ManeuverGlyph.SHARP_LEFT) -1f else 1f
                path.moveTo(50f-d*14f,22f); path.lineTo(50f-d*14f,72f); path.lineTo(50f+d*16f,58f)
                canvas.drawPath(path,stroke); head(50f+d*20f,56f,90f*d+25f*d)
            }
            ManeuverGlyph.UTURN -> {
                path.moveTo(64f,92f); path.lineTo(64f,44f); path.arcTo(RectF(34f,22f,64f,52f),0f,-180f); path.lineTo(34f,62f)
                canvas.drawPath(path,stroke); head(34f,66f,180f)
            }
            ManeuverGlyph.ROUNDABOUT -> {
                stroke.strokeWidth=8f; canvas.drawCircle(50f,50f,17f,stroke); stroke.strokeWidth=11f
                canvas.drawLine(50f,92f,50f,67f,stroke); canvas.drawLine(62f,38f,80f,22f,stroke); head(82f,20f,45f)
            }
            ManeuverGlyph.ARRIVE -> {
                canvas.drawLine(50f,92f,50f,52f,stroke)
                canvas.drawCircle(50f,34f,17f,fill)
                fill.color=Color.rgb(10,36,68); canvas.drawCircle(50f,34f,6f,fill); fill.color=Color.WHITE
            }
            ManeuverGlyph.FERRY -> {
                canvas.drawLine(22f,64f,78f,64f,stroke); canvas.drawLine(30f,64f,38f,42f,stroke); canvas.drawLine(38f,42f,62f,42f,stroke); canvas.drawLine(62f,42f,70f,64f,stroke)
                stroke.strokeWidth=6f; canvas.drawLine(18f,82f,82f,82f,stroke)
            }
        }
        canvas.restore()
    }
}

/** Keeps album art square inside whatever space the media card leaves for it. */
class SquareFrameLayout @JvmOverloads constructor(context: Context,attrs: AttributeSet?=null) : FrameLayout(context,attrs) {
    override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
        val w=MeasureSpec.getSize(widthMeasureSpec); val h=MeasureSpec.getSize(heightMeasureSpec)
        val side=when {
            MeasureSpec.getMode(heightMeasureSpec)==MeasureSpec.UNSPECIFIED -> w
            MeasureSpec.getMode(widthMeasureSpec)==MeasureSpec.UNSPECIFIED -> h
            else -> min(w,h)
        }
        val spec=MeasureSpec.makeMeasureSpec(side,MeasureSpec.EXACTLY)
        super.onMeasure(spec,spec)
    }
}
