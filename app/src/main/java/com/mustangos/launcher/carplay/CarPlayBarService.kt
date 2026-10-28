package com.mustangos.launcher.carplay

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import com.mustangos.core.MediaCommand
import com.mustangos.launcher.R
import com.mustangos.launcher.media.MediaIntegrationManager
import com.mustangos.launcher.settings.LauncherSettings
import com.mustangos.launcher.ui.MainActivity

/** User-started controls over the real ZLink window. Never captures, injects input, or claims to embed its video. */
class CarPlayBarService : Service() {
    private var bar: View? = null
    private var media: MediaIntegrationManager? = null
    private lateinit var windows: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var play: ImageButton? = null
    private var previous: ImageButton? = null
    private var next: ImageButton? = null
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onBind(intent: Intent?) = null
    override fun onCreate() { super.onCreate(); windows = getSystemService(WindowManager::class.java) }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action == CLOSE || intent == null) { stopSelf(); return START_NOT_STICKY }
        if(intent.action == HOME) {
            runCatching { startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)) }
            stopSelf(); return START_NOT_STICKY
        }
        if(intent.action != SHOW) { stopSelf(); return START_NOT_STICKY }
        try {
            showNotification()
            if(!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
            if(bar == null) showBar()
        } catch(_: RuntimeException) {
            Toast.makeText(this,"El equipo no permitió mostrar la barra Mustang",Toast.LENGTH_LONG).show()
            stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun action(action: String,request: Int) = PendingIntent.getService(this,request,Intent(this,CarPlayBarService::class.java).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun showNotification() {
        val notifications=getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,"Barra Mustang",NotificationManager.IMPORTANCE_LOW))
        val notification=Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_mustang_pony)
            .setContentTitle("Barra Mustang en CarPlay").setContentText("Controles activos · toca para volver a Mustang OS")
            .setContentIntent(action(HOME,1)).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null,"Cerrar barra",action(CLOSE,2)).build()).build()
        // https://developer.android.com/develop/background-work/services/fgs/service-types#special-use
        if(Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION,notification)
    }
    private fun showBar() {
        val settings=LauncherSettings(this)
        val projection=settings.component("zlink")?.packageName ?: "com.zjinnova.zlink"
        media=MediaIntegrationManager(applicationContext,settings,projection).also { it.start(); it.poll() }
        val styled=ContextThemeWrapper(this,R.style.Theme_Mustang)
        val row=LinearLayout(styled).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(5),dp(3),dp(5),dp(3))
            background=GradientDrawable().apply { setColor(0xf20a1422.toInt()); cornerRadius=dp(24).toFloat(); setStroke(dp(1),0xff6e8fb4.toInt()) }
            elevation=dp(6).toFloat()
        }
        val handle=ImageView(styled).apply { setImageResource(R.drawable.ic_mustang_pony); contentDescription="Arrastrar barra Mustang"; setPadding(dp(6),dp(8),dp(6),dp(8)) }
        row.addView(handle,LinearLayout.LayoutParams(dp(48),dp(48)))
        fun control(icon: Int,description: String,click: () -> Unit) = ImageButton(styled).apply {
            setImageResource(icon); contentDescription=description; background=GradientDrawable().apply { cornerRadius=dp(20).toFloat(); setColor(0xff14273e.toInt()) }
            setPadding(dp(12),dp(12),dp(12),dp(12)); setOnClickListener { click() }
        }.also { row.addView(it,LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginStart=dp(3) }) }
        control(R.drawable.ic_home,"Volver a Mustang OS") { onStartCommand(Intent().setAction(HOME),0,0) }
        previous=control(R.drawable.ic_previous,"Canción anterior") { send(MediaCommand.PREVIOUS) }
        play=control(R.drawable.ic_play_pause,"Pausar o reproducir CarPlay") { if(media?.togglePlayPause() != true) unavailable() }
        next=control(R.drawable.ic_next,"Canción siguiente") { send(MediaCommand.NEXT) }
        control(R.drawable.ic_close,"Cerrar barra Mustang") { stopSelf() }
        val screen=resources.displayMetrics
        val params=WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT).apply {
            gravity=Gravity.TOP or Gravity.START; x=((screen.widthPixels-dp(320))/2).coerceAtLeast(0); y=dp(8)
        }
        var downX=0f; var downY=0f; var initialX=0; var initialY=0
        handle.setOnTouchListener { _,event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX=event.rawX; downY=event.rawY; initialX=params.x; initialY=params.y; true }
                MotionEvent.ACTION_MOVE -> {
                    params.x=(initialX+event.rawX-downX).toInt().coerceIn(0,(screen.widthPixels-row.width).coerceAtLeast(0))
                    params.y=(initialY+event.rawY-downY).toInt().coerceIn(0,(screen.heightPixels-row.height-dp(24)).coerceAtLeast(0))
                    runCatching { windows.updateViewLayout(row,params) }; true
                }
                MotionEvent.ACTION_UP -> { handle.performClick(); true }
                else -> false
            }
        }
        bar=row
        // The window consumes touches only inside its own narrow bounds.
        // https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY
        windows.addView(row,params)
        handler.post(refresh)
    }
    private val refresh=object : Runnable {
        override fun run() {
            if(bar == null) return
            if(!Settings.canDrawOverlays(this@CarPlayBarService)) { stopSelf(); return }
            media?.poll()
            previous?.isEnabled=media?.canCommand(MediaCommand.PREVIOUS) == true
            next?.isEnabled=media?.canCommand(MediaCommand.NEXT) == true
            play?.isEnabled=media?.let { it.canCommand(MediaCommand.PLAY) || it.canCommand(MediaCommand.PAUSE) } == true
            val state=media?.state?.value
            play?.setImageResource(if(state?.playbackStateKnown == true) { if(state.facts.playing) R.drawable.ic_pause else R.drawable.ic_play } else R.drawable.ic_play_pause)
            handler.postDelayed(this,1000)
        }
    }
    private fun unavailable() { Toast.makeText(this,"ZLink no publica un control multimedia disponible",Toast.LENGTH_SHORT).show() }
    private fun send(command: MediaCommand) { if(media?.command(command) != true) unavailable() }
    override fun onTaskRemoved(rootIntent: Intent?) { stopSelf() }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null); media?.stop(); media=null
        bar?.let { runCatching { windows.removeViewImmediate(it) } }; bar=null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        const val SHOW="com.mustangos.launcher.CARPLAY_BAR_SHOW"
        const val CLOSE="com.mustangos.launcher.CARPLAY_BAR_CLOSE"
        const val HOME="com.mustangos.launcher.CARPLAY_BAR_HOME"
        private const val CHANNEL="mustang_carplay_bar"
        private const val NOTIFICATION=51
    }
}
