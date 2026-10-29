package com.mustangos.launcher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import kotlin.math.roundToInt
import com.mustangos.core.MustangProfile

/** A lightweight lamp layer aligned to the bundled 900x600 Mustang image. No vehicle state is inferred here. */
class MustangCarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImageView(context, attrs, defStyleAttr) {
    private var lightLevel = 0f
    private var animator: ValueAnimator? = null
    private var requestedLight: Boolean? = null
    private var digital: DigitalMustangDrawable? = null
    private var scene: MustangSceneDrawable? = null
    var sceneProfile: MustangProfile? = null
        set(value) { field=value; scene=value?.let(::MustangSceneDrawable); invalidate(); scheduleScene() }
    private var sceneActive=false
    private var leftLevel=0f; private var rightLevel=0f
    private var leftTarget=0f; private var rightTarget=0f
    var motionSpeedKmh: Float?=null; private set
    private var lastSceneFrame=0L
    private val sceneFrame=object : Runnable {
        override fun run() {
            if(!sceneActive || !isShown || windowVisibility!=VISIBLE || scene==null) return
            val now=android.os.SystemClock.elapsedRealtime()
            val seconds=if(lastSceneFrame==0L) .033f else ((now-lastSceneFrame)/1000f).coerceIn(0f,.1f)
            lastSceneFrame=now
            fun approach(value: Float,target: Float): Float {
                val delta=target-value; val step=seconds*2.5f
                return if(kotlin.math.abs(delta)<=step) target else value+if(delta>0) step else -step
            }
            leftLevel=approach(leftLevel,leftTarget); rightLevel=approach(rightLevel,rightTarget)
            scene?.let { it.travelMeters=(it.travelMeters+(motionSpeedKmh ?: 0f)/3.6f*seconds)%1000f }
            invalidate(); scheduleScene()
        }
    }
    fun setSceneSignals(leftOpen: Boolean?,rightOpen: Boolean?,speedKmh: Float?) {
        leftTarget=leftOpen?.let { if(it) 1f else 0f } ?: leftLevel
        rightTarget=rightOpen?.let { if(it) 1f else 0f } ?: rightLevel
        motionSpeedKmh=speedKmh?.takeIf { it.isFinite() && it in 0f..350f }
        scheduleScene(); invalidate()
    }
    fun setSceneActive(active: Boolean) { sceneActive=active; lastSceneFrame=0; scheduleScene() }
    private fun scheduleScene() {
        removeCallbacks(sceneFrame)
        if(sceneActive && scene!=null && isShown && windowVisibility==VISIBLE && ((motionSpeedKmh ?: 0f)>.5f || leftLevel!=leftTarget || rightLevel!=rightTarget))
            postDelayed(sceneFrame,33)
    }
    var digitalProfile: MustangProfile? = null
        set(value) { field = value; digital = value?.let(::DigitalMustangDrawable); invalidate() }
    var lampOverlayEnabled: Boolean = true
        set(value) { field = value; if (!value) stopAnimation(); invalidate() }
    private val nearGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(339f, 306f, 42f,
            intArrayOf(Color.argb(220, 245, 251, 255), Color.argb(120, 182, 218, 255), Color.TRANSPARENT),
            floatArrayOf(0f, .25f, 1f), Shader.TileMode.CLAMP)
    }
    private val farGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(58f, 296f, 24f,
            intArrayOf(Color.argb(210, 245, 251, 255), Color.argb(100, 182, 218, 255), Color.TRANSPARENT),
            floatArrayOf(0f, .3f, 1f), Shader.TileMode.CLAMP)
    }
    private val lampCore = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(244, 249, 255) }
    private val nearCore = RectF(326f, 297f, 353f, 315f)
    private val farCore = RectF(53f, 285f, 63f, 307f)
    val isLightAnimationRunning: Boolean get() = animator?.isRunning == true

    fun setHeadlightsOn(on: Boolean, animate: Boolean = false) {
        if (!lampOverlayEnabled) { stopAnimation(); return }
        val target = if (on) 1f else 0f
        if(requestedLight==on && (animator?.isRunning==true || lightLevel==target)) return
        requestedLight=on
        animator?.cancel()
        animator = null
        if (animate && lightLevel != target) {
            animator = ValueAnimator.ofFloat(lightLevel, target).apply {
                duration = 450
                interpolator = DecelerateInterpolator()
                addUpdateListener { setLightLevel(it.animatedValue as Float) }
                start()
            }
        } else setLightLevel(target)
    }

    /** A short welcome effect, distinct from a confirmed ACC or engine reading. */
    fun playWelcomeLights() {
        if (!lampOverlayEnabled) return
        animator?.cancel()
        requestedLight=null
        animator = ValueAnimator.ofFloat(0f, .9f, .9f, 0f).apply {
            duration = 1600
            addUpdateListener { setLightLevel(it.animatedValue as Float) }
            start()
        }
    }

    fun stopAnimation() {
        animator?.cancel()
        animator = null
        requestedLight=null
        setLightLevel(0f)
        setSceneActive(false)
    }

    private fun setLightLevel(level: Float) {
        lightLevel = level.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        scene?.let { model ->
            model.setBounds(paddingLeft,paddingTop,width-paddingRight,height-paddingBottom)
            model.lights=lightLevel; model.leftDoor=leftLevel; model.rightDoor=rightLevel
            model.draw(canvas); return
        }
        digital?.let { illustration ->
            illustration.setBounds(paddingLeft, paddingTop, width-paddingRight, height-paddingBottom)
            illustration.lights = lightLevel
            illustration.draw(canvas)
            return
        }
        super.onDraw(canvas)
        val image = drawable ?: return
        if (!lampOverlayEnabled || lightLevel <= 0f || image.intrinsicWidth <= 0 || image.intrinsicHeight <= 0) return
        val checkpoint = canvas.save()
        canvas.clipRect(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
        canvas.concat(imageMatrix)
        canvas.scale(image.intrinsicWidth / 900f, image.intrinsicHeight / 600f)
        nearGlow.alpha = (255 * lightLevel).roundToInt()
        farGlow.alpha = nearGlow.alpha
        lampCore.alpha = (210 * lightLevel).roundToInt()
        canvas.drawCircle(339f, 306f, 42f, nearGlow)
        canvas.drawCircle(58f, 296f, 24f, farGlow)
        canvas.drawOval(nearCore, lampCore)
        canvas.drawOval(farCore, lampCore)
        canvas.restoreToCount(checkpoint)
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); scheduleScene() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); scheduleScene() }
}
