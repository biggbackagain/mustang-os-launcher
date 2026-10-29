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
import com.mustangos.launcher.MustangApplication
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
    private var song: TextView? = null
    private val expandable = mutableListOf<View>()
    private var expanded = true
    private val collapse = Runnable { setExpanded(false) }
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
        val handle=ImageView(styled).apply { setImageResource(R.drawable.ic_mustang_pony); contentDescription="Barra Mustang: toca para mostrar u ocultar, arrastra para mover"; setPadding(dp(6),dp(8),dp(6),dp(8)) }
        row.addView(handle,LinearLayout.LayoutParams(dp(48),dp(48)))
        song=TextView(styled).apply {
            setTextColor(0xffe5f3ff.toInt()); textSize=13f; maxLines=2
            ellipsize=android.text.TextUtils.TruncateAt.END
            maxWidth=(resources.displayMetrics.widthPixels-dp(370)).coerceIn(0,dp(200))
            setPadding(dp(6),0,dp(10),0); visibility=View.GONE
        }.also { row.addView(it,LinearLayout.LayoutParams(-2,dp(48))); expandable.add(it) }
        fun control(icon: Int,description: String,click: () -> Unit) = ImageButton(styled).apply {
            setImageResource(icon); contentDescription=description; background=GradientDrawable().apply { cornerRadius=dp(20).toFloat(); setColor(0xff14273e.toInt()) }
            setPadding(dp(12),dp(12),dp(12),dp(12)); setOnClickListener { click(); scheduleCollapse() }
        }.also { row.addView(it,LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginStart=dp(3) }); expandable.add(it) }
        control(R.drawable.ic_home,"Volver a Mustang OS") { onStartCommand(Intent().setAction(HOME),0,0) }
        previous=control(R.drawable.ic_previous,"Canción anterior") { send(MediaCommand.PREVIOUS) }
        play=control(R.drawable.ic_play_pause,"Pausar o reproducir CarPlay") { if(media?.togglePlayPause() != true) unavailable() }
        next=control(R.drawable.ic_next,"Canción siguiente") { send(MediaCommand.NEXT) }
        control(R.drawable.ic_close,"Cerrar barra Mustang") { stopSelf() }
        val screen=resources.displayMetrics
        val prefs=getSharedPreferences("launcher",MODE_PRIVATE)
        // Default: a small pony on the right edge, clear of CarPlay's dock and of the map's guidance banner.
        val params=WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT).apply {
            gravity=Gravity.TOP or Gravity.START
            x=prefs.getInt("bar_x",screen.widthPixels).coerceIn(0,screen.widthPixels); y=prefs.getInt("bar_y",(screen.heightPixels*.42f).toInt()).coerceIn(0,screen.heightPixels)
        }
        var downX=0f; var downY=0f; var initialX=0; var initialY=0; var moved=false
        handle.setOnTouchListener { _,event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX=event.rawX; downY=event.rawY; initialX=params.x; initialY=params.y; moved=false; handler.removeCallbacks(collapse); true }
                MotionEvent.ACTION_MOVE -> {
                    if(kotlin.math.abs(event.rawX-downX)+kotlin.math.abs(event.rawY-downY)>dp(8)) moved=true
                    params.x=(initialX+event.rawX-downX).toInt().coerceIn(0,(screen.widthPixels-row.width).coerceAtLeast(0))
                    params.y=(initialY+event.rawY-downY).toInt().coerceIn(0,(screen.heightPixels-row.height-dp(24)).coerceAtLeast(0))
                    runCatching { windows.updateViewLayout(row,params) }; true
                }
                MotionEvent.ACTION_UP -> {
                    if(moved) prefs.edit().putInt("bar_x",params.x).putInt("bar_y",params.y).apply()
                    else { handle.performClick(); setExpanded(!expanded) }
                    scheduleCollapse(); true
                }
                else -> false
            }
        }
        bar=row
        row.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ ->
            val x=params.x.coerceIn(0,(screen.widthPixels-row.width).coerceAtLeast(0))
            if(x!=params.x) { params.x=x; runCatching { windows.updateViewLayout(row,params) } }
        }
        // The window consumes touches only inside its own narrow bounds.
        // https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY
        windows.addView(row,params)
        (application as MustangApplication).zlinkHud.visible("bar",true)
        handler.post(refresh)
        scheduleCollapse()
    }
    /** The bar folds into the pony after a few seconds so the CarPlay map stays fully visible. */
    private fun scheduleCollapse() { handler.removeCallbacks(collapse); if(expanded) handler.postDelayed(collapse,6000) }
    private fun setExpanded(value: Boolean) {
        expanded=value
        for(view in expandable) view.visibility=if(value && (view!==song || song?.text?.isNotEmpty()==true)) View.VISIBLE else View.GONE
        (bar?.background as? GradientDrawable)?.alpha=if(value) 255 else 190
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
            val projection=LauncherSettings(this@CarPlayBarService).component("zlink")?.packageName ?: "com.zjinnova.zlink"
            val display=com.mustangos.launcher.media.ZLinkHudPresentation.compose(state ?: com.mustangos.launcher.media.MediaUiState(),
                (application as MustangApplication).zlinkHud.probe.state.value,projection,projection,SystemClock.elapsedRealtime())
            song?.apply {
                val title=display.displayTitle
                val position=display.facts.positionMs
                text=title?.let { listOfNotNull(it,display.displaySubtitle).joinToString("\n") }
                    ?: if(display.reportedPosition) "CarPlay\n%d:%02d".format(position/60000,position/1000%60) else ""
                visibility=if(expanded && text.isNotEmpty() && maxWidth>=dp(90)) View.VISIBLE else View.GONE
            }
            play?.setImageResource(if(state?.playbackStateKnown == true) { if(state.facts.playing) R.drawable.ic_pause else R.drawable.ic_play } else R.drawable.ic_play_pause)
            handler.postDelayed(this,1000)
        }
    }
    private fun unavailable() { Toast.makeText(this,"ZLink no publica un control multimedia disponible",Toast.LENGTH_SHORT).show() }
    private fun send(command: MediaCommand) { if(media?.command(command) != true) unavailable() }
    override fun onTaskRemoved(rootIntent: Intent?) { stopSelf() }
    override fun onDestroy() {
        (application as MustangApplication).zlinkHud.visible("bar",false)
        handler.removeCallbacksAndMessages(null); media?.stop(); media=null
        bar?.let { runCatching { windows.removeViewImmediate(it) } }; bar=null; song=null; expandable.clear()
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
