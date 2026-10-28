package com.mustangos.launcher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class SpeedGaugeView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = RectF()
    private val numberMetrics = Paint.FontMetrics()
    private val unitMetrics = Paint.FontMetrics()
    private var speed: Float? = null
    private var sourceLabel = "GPS"
    private var displayed = 0f
    private var animator: ValueAnimator? = null
    fun setSpeed(value: Float?, source: String = "GPS") {
        val label = if (source == "OEM") "OEM" else "GPS"
        if (value == speed && sourceLabel == label) return
        sourceLabel = label
        speed = value; animator?.cancel()
        contentDescription = value?.let { "Velocidad $sourceLabel ${it.roundToInt()} kilómetros por hora" } ?: "Velocidad $sourceLabel sin señal"
        if (value == null || !ValueAnimator.areAnimatorsEnabled()) { displayed = value ?: 0f; invalidate(); return }
        animator = ValueAnimator.ofFloat(displayed, value).apply { duration = 280; addUpdateListener { displayed = it.animatedValue as Float; invalidate() }; start() }
    }
    override fun onDetachedFromWindow() { animator?.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width.toFloat(), height * 1.65f)
        val cx = width / 2f; val cy = height * 0.70f; val radius = size * 0.43f
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeWidth = size * 0.021f; paint.color = Color.rgb(44, 50, 62)
        arc.set(cx-radius, cy-radius, cx+radius, cy+radius)
        canvas.drawArc(arc, 150f, 240f, false, paint)
        paint.color = Color.rgb(123, 217, 255)
        if (speed != null && displayed > 0) canvas.drawArc(arc, 150f, (displayed / 240f).coerceIn(0f, 1f) * 240f, false, paint)
        paint.strokeWidth = size * 0.004f
        for (i in 0..24) {
            val rad = Math.toRadians((150 + i * 10).toDouble())
            val inner = radius * if (i % 6 == 0) 0.87f else 0.93f
            paint.color = Color.rgb(100, 108, 126)
            canvas.drawLine(cx + cos(rad).toFloat()*inner, cy + sin(rad).toFloat()*inner, cx + cos(rad).toFloat()*radius*0.97f, cy + sin(rad).toFloat()*radius*0.97f, paint)
        }
        val unitSize = max(height * 0.10f, 12f * resources.displayMetrics.scaledDensity)
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); paint.textSize = unitSize
        paint.getFontMetrics(unitMetrics)
        val unitBaseline = min(cy + height * 0.18f, height - unitMetrics.descent - 2f * resources.displayMetrics.density)
        paint.color = Color.WHITE; paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        paint.textSize = min(height * 0.43f, width * 0.23f); paint.getFontMetrics(numberMetrics)
        val numberBaseline = min(cy + height * 0.02f, unitBaseline + unitMetrics.ascent - numberMetrics.descent - 4f * resources.displayMetrics.density)
        canvas.drawText(if (speed == null) "—" else displayed.roundToInt().toString(), cx, numberBaseline, paint)
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); paint.textSize = unitSize; paint.color = Color.rgb(176,181,192)
        canvas.drawText("km/h", cx, unitBaseline, paint)
    }
}
