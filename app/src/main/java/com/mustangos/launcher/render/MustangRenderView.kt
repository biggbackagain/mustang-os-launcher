package com.mustangos.launcher.render

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.TextureView
import com.mustangos.core.MustangProfile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.min

/**
 * Studio render of the selected Mustang. OpenGL ES 2.0 on its own thread, drawn only when something changes:
 * doors and lamps follow OEM signals, wheels turn only with a valid speed, the camera follows the opened door.
 * Drag to orbit, double tap to return to the default three-quarter view.
 */
class MustangRenderView @JvmOverloads constructor(context: Context,attrs: AttributeSet?=null) : TextureView(context,attrs),TextureView.SurfaceTextureListener {
    var onFailure: ((String) -> Unit)?=null
    /** Called on the main thread after the first frame of a scene reaches the screen. */
    var onRendered: (() -> Unit)?=null
    private var announced=false
    private val main=Handler(Looper.getMainLooper())
    private var thread: HandlerThread?=null
    private var gl: Handler?=null
    private var egl: Egl?=null
    private var renderer: MustangRenderer?=null
    private var builtFor: MustangProfile?=null
    // Written on the main thread, read on the GL thread.
    @Volatile private var profile: MustangProfile?=null
    @Volatile private var leftTarget=0f
    @Volatile private var rightTarget=0f
    @Volatile private var lightTarget=0f
    @Volatile private var speedKmh=0f
    @Volatile private var active=false
    @Volatile private var userYaw: Float?=null
    @Volatile private var userYawAt=0L
    @Volatile private var dragging=false
    // GL-thread animation state.
    private var state=SceneState()
    private var lastFrame=0L
    private var scheduled=false

    init { isOpaque=false; surfaceTextureListener=this }

    fun setProfile(value: MustangProfile) { if(profile!=value) { profile=value; request() } }
    fun setSignals(leftOpen: Boolean?,rightOpen: Boolean?,speed: Float?,lights: Float) {
        leftOpen?.let { leftTarget=if(it) 1f else 0f }
        rightOpen?.let { rightTarget=if(it) 1f else 0f }
        speedKmh=speed?.takeIf { it.isFinite() && it in 0f..350f } ?: 0f
        lightTarget=lights.coerceIn(0f,1f)
        request()
    }
    fun setActive(value: Boolean) { active=value; if(value) request() }

    private val gestures=GestureDetector(context,object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent)=true
        override fun onDoubleTap(e: MotionEvent): Boolean { userYaw=null; request(); return true }
        override fun onScroll(e1: MotionEvent?,e2: MotionEvent,dx: Float,dy: Float): Boolean {
            if(abs(dx)>abs(dy)) parent?.requestDisallowInterceptTouchEvent(true)
            userYaw=(userYaw ?: state.yaw)+dx/width.coerceAtLeast(1)*3.2f
            userYawAt=SystemClock.elapsedRealtime(); request(); return true
        }
    })
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        dragging=event.actionMasked==MotionEvent.ACTION_DOWN || event.actionMasked==MotionEvent.ACTION_MOVE
        if(event.actionMasked==MotionEvent.ACTION_UP) performClick()
        return gestures.onTouchEvent(event) || super.onTouchEvent(event)
    }
    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture,w: Int,h: Int) {
        val t=HandlerThread("mustang-render").also { it.start() }
        thread=t; gl=Handler(t.looper)
        gl?.post {
            try {
                egl=Egl(surface)
                builtFor=null; ensureScene(); renderer?.resize(w,h)
                lastFrame=0L; scheduled=false; frame()
            } catch(error: Throwable) {
                releaseGl()
                main.post { onFailure?.invoke(error.message ?: error.javaClass.simpleName) }
            }
        }
    }
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture,w: Int,h: Int) { gl?.post { renderer?.resize(w,h); frame() } }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture)=Unit
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        val done=CountDownLatch(1)
        val handler=gl
        if(handler==null || !handler.post { releaseGl(); done.countDown() }) done.countDown()
        done.await(1,TimeUnit.SECONDS)
        thread?.quitSafely(); thread=null; gl=null
        return true
    }

    private fun request() { gl?.post { if(!scheduled) frame() } }
    private fun ensureScene() {
        val wanted=profile ?: return
        if(builtFor==wanted && renderer!=null) return
        renderer?.release()
        renderer=MustangRenderer(MustangScene(wanted)).also { it.init(); egl?.let { e -> it.resize(e.width(),e.height()) } }
        builtFor=wanted; announced=false
    }
    /** One frame on the GL thread; schedules the next one only while something is still moving. */
    private fun frame() {
        scheduled=false
        val surface=egl ?: return
        try { ensureScene() } catch(error: Throwable) { main.post { onFailure?.invoke(error.message ?: "scene") }; return }
        val r=renderer ?: return
        val now=SystemClock.elapsedRealtime()
        val dt=if(lastFrame==0L) 0f else min((now-lastFrame)/1000f,.1f)
        lastFrame=now
        fun approach(value: Float,target: Float,rate: Float): Float { val d=target-value; val step=rate*dt; return if(abs(d)<=step) target else value+if(d>0) step else -step }
        val left=approach(state.leftDoor,leftTarget,1.6f); val right=approach(state.rightDoor,rightTarget,1.6f)
        // Camera: the user's angle for 20 s, otherwise follow an open door, otherwise the default view.
        val manual=userYaw?.takeIf { SystemClock.elapsedRealtime()-userYawAt<20_000 || dragging }
        if(manual==null && userYaw!=null && !dragging) userYaw=null
        val yawTarget=manual ?: when { leftTarget>.5f && rightTarget<.5f -> 1.15f; rightTarget>.5f && leftTarget<.5f -> -1.15f; else -> SceneState.DEFAULT_YAW }
        val yaw=if(manual!=null) manual else state.yaw+(yawTarget-state.yaw)*min(1f,dt*3.2f).let { k -> if(abs(yawTarget-state.yaw)<.002f) 1f else k }
        state=state.copy(leftDoor=left,rightDoor=right,lights=approach(state.lights,lightTarget,3f),
            travelMeters=(state.travelMeters+speedKmh/3.6f*dt)%10_000f,yaw=yaw)
        r.draw(state)
        surface.swap()
        if(!announced) { announced=true; main.post { onRendered?.invoke() } }
        val moving=state.leftDoor!=leftTarget || state.rightDoor!=rightTarget || state.lights!=lightTarget ||
            speedKmh>.5f || (manual==null && abs(yaw-yawTarget)>.002f)
        if(active && moving && isAttachedToWindow) { scheduled=true; gl?.postDelayed({ frame() },33) }
        // After a manual orbit, return to the automatic view once the user has left it alone for 20 s.
        else if(manual!=null && !dragging) gl?.postDelayed({ if(!scheduled) frame() },(20_000-(SystemClock.elapsedRealtime()-userYawAt)).coerceIn(50,20_000))
    }
    private fun releaseGl() {
        try { renderer?.release() } catch(_: Throwable) { }
        renderer=null; builtFor=null
        egl?.release(); egl=null
    }

    private class Egl(texture: SurfaceTexture) {
        val display: EGLDisplay=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val context: EGLContext
        val surface: EGLSurface
        init {
            val version=IntArray(2)
            check(EGL14.eglInitialize(display,version,0,version,1)) { "EGL no disponible" }
            fun choose(samples: Int): EGLConfig? {
                val attributes=intArrayOf(EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
                    EGL14.EGL_DEPTH_SIZE,16,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT)+
                    (if(samples>0) intArrayOf(EGL14.EGL_SAMPLE_BUFFERS,1,EGL14.EGL_SAMPLES,samples) else intArrayOf())+intArrayOf(EGL14.EGL_NONE)
                val configs=arrayOfNulls<EGLConfig>(1); val count=IntArray(1)
                return if(EGL14.eglChooseConfig(display,attributes,0,configs,0,1,count,0) && count[0]>0) configs[0] else null
            }
            val config=choose(4) ?: choose(0) ?: error("Sin configuración EGL compatible")
            context=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
            check(context!=EGL14.EGL_NO_CONTEXT) { "No se pudo crear el contexto OpenGL" }
            surface=EGL14.eglCreateWindowSurface(display,config,texture,intArrayOf(EGL14.EGL_NONE),0)
            check(surface!=EGL14.EGL_NO_SURFACE) { "No se pudo crear la superficie OpenGL" }
            check(EGL14.eglMakeCurrent(display,surface,surface,context)) { "No se pudo activar OpenGL" }
        }
        private fun query(what: Int)=IntArray(1).also { EGL14.eglQuerySurface(display,surface,what,it,0) }[0]
        fun width()=query(EGL14.EGL_WIDTH)
        fun height()=query(EGL14.EGL_HEIGHT)
        fun swap() { EGL14.eglSwapBuffers(display,surface) }
        fun release() {
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display,surface); EGL14.eglDestroyContext(display,context)
            EGL14.eglReleaseThread(); EGL14.eglTerminate(display)
        }
    }
}
