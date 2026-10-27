package com.mustangos.launcher.media

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.mustangos.core.IntegrationPolicy
import com.mustangos.core.MediaCommand
import com.mustangos.core.MediaFacts
import com.mustangos.launcher.appintegration.AppIntegrationManager
import com.mustangos.launcher.carplay.CarPlayIntegrationManager
import com.mustangos.launcher.diagnostics.ErrorJournal
import com.mustangos.launcher.settings.LauncherSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MediaUiState(
    val facts: MediaFacts = MediaFacts(),
    val artwork: Bitmap? = null,
    val status: String = "Acceso multimedia sin conceder",
    val playbackStateKnown: Boolean = false
)
class MediaIntegrationManager(private val context: Context, private val settings: LauncherSettings) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, MediaAccessService::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val mutable = MutableStateFlow(MediaUiState())
    val state = mutable.asStateFlow()
    var sessions: List<MediaController> = emptyList(); private set
    private var selected: MediaController? = null
    private var listening = false
    private var scope: CoroutineScope? = null
    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { refresh() }
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() { select(null); mutable.value = MediaUiState(status = "Fuente desconectada") ; handler.post { refresh() } }
    }
    fun hasAccess(): Boolean = if (android.os.Build.VERSION.SDK_INT >= 27) {
        context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
    } else android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        ?.split(':')?.any { ComponentName.unflattenFromString(it) == component } == true
    fun start() {
        stop()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { current -> current.launch { MediaAccessService.revision.collect { refresh() } } }
    }
    fun stop() {
        scope?.cancel(); scope = null
        if (listening) runCatching { manager.removeOnActiveSessionsChangedListener(sessionListener) }
        listening = false
        select(null); handler.removeCallbacksAndMessages(null)
        sessions = emptyList(); mutable.value = MediaUiState(status = "Multimedia detenida")
    }
    fun refresh() {
        if (!hasAccess()) {
            if (listening) runCatching { manager.removeOnActiveSessionsChangedListener(sessionListener) }
            listening = false; select(null); sessions = emptyList()
            mutable.value = MediaUiState(); return
        }
        try {
            if (!listening) { manager.addOnActiveSessionsChangedListener(sessionListener, component, handler); listening = true }
            sessions = manager.getActiveSessions(component)
            val preferred = settings.mediaPackage
            var pkg = IntegrationPolicy.selectMedia(sessions.map { it.packageName }, preferred,
                settings.component("zlink")?.packageName ?: VERIFIED_ZLINK_PACKAGE)
            if (pkg == null && preferred == null && sessions.isNotEmpty()) {
                pkg = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }?.packageName
                    ?: (if (sessions.any { it.packageName == "com.tw.bt" }) "com.tw.bt" else null)
                    ?: sessions.firstOrNull { it.metadata != null }?.packageName
            }
            select(sessions.firstOrNull { it.packageName == pkg })
            publish()
        } catch (_: SecurityException) {
            select(null); sessions = emptyList(); mutable.value = MediaUiState(status = "Esperando autorización del sistema")
            ErrorJournal.record(context, "MEDIA_ACCESS_DENIED")
        } catch (_: RuntimeException) {
            select(null); sessions = emptyList(); mutable.value = MediaUiState(status = "Error al consultar multimedia")
            ErrorJournal.record(context, "MEDIA_QUERY_FAILED")
        }
    }
    private fun select(controller: MediaController?) {
        if (selected?.sessionToken == controller?.sessionToken) return
        selected?.let { runCatching { it.unregisterCallback(callback) } }
        selected = controller
        controller?.registerCallback(callback, handler)
    }
    private fun publish() {
        val controller = selected ?: run { mutable.value = MediaUiState(status = if (sessions.size > 1) "Selecciona una fuente multimedia" else "Sin sesión multimedia compatible"); return }
        try {
            val m = controller.metadata
            val p = controller.playbackState
            val image = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            val art = image?.let { if (maxOf(it.width, it.height) > 384) { val scale = 384f / maxOf(it.width, it.height); Bitmap.createScaledBitmap(it, (it.width * scale).toInt().coerceAtLeast(1), (it.height * scale).toInt().coerceAtLeast(1), true) } else it }
            val rawActions = p?.actions ?: 0L
            mutable.value = MediaUiState(MediaFacts(controller.packageName,
                m?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() },
                m?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() },
                m?.getString(MediaMetadata.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() },
                m?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0,
                p?.position?.coerceAtLeast(0) ?: 0, p?.lastPositionUpdateTime ?: 0,
                p?.state == PlaybackState.STATE_PLAYING, p?.playbackSpeed ?: 0f, rawActions), art,
                if (p?.state == PlaybackState.STATE_ERROR) "La fuente reportó un error" else "Sesión Android · ${controller.packageName}",
                p?.state in listOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_PAUSED, PlaybackState.STATE_STOPPED))
        } catch (_: RuntimeException) { mutable.value = MediaUiState(status = "Fuente multimedia no disponible"); ErrorJournal.record(context, "MEDIA_READ_FAILED") }
    }
    fun poll() {
        // Some OEM sessions omit callbacks; refresh still respects an explicit source selection.
        refresh()
    }
    fun canCommand(command: MediaCommand): Boolean {
        val facts = mutable.value.facts
        return selected != null && hasAccess() && (facts.supports(command) || isLegacyOemSource(facts.source) && facts.actions == 0L)
    }
    /** The bridge may omit playback state. A single toggle then avoids repeatedly sending PLAY. */
    fun togglePlayPause(): Boolean {
        if (!hasAccess()) { refresh(); return false }
        val controller = selected ?: return false
        publish()
        return when (controller.playbackState?.state) {
            PlaybackState.STATE_PLAYING -> command(MediaCommand.PAUSE)
            PlaybackState.STATE_PAUSED, PlaybackState.STATE_STOPPED -> command(MediaCommand.PLAY)
            else -> {
                if (!canCommand(MediaCommand.PLAY) && !canCommand(MediaCommand.PAUSE)) false
                else dispatchToggle(controller).also { if (it) schedulePoll() }
            }
        }
    }
    fun command(command: MediaCommand): Boolean {
        if (!hasAccess()) { refresh(); return false }
        val controller = selected ?: return false
        publish()
        if (!canCommand(command)) return false
        // Exactly one delivery path per tap. A submitted command is not proof of a playback change.
        val submitted = try {
            val facts = mutable.value.facts
            if ((command == MediaCommand.PLAY || command == MediaCommand.PAUSE) && facts.actions and command.action == 0L) {
                dispatchToggle(controller)
            } else {
                when (command) {
                    MediaCommand.PLAY -> controller.transportControls.play()
                    MediaCommand.PAUSE -> controller.transportControls.pause()
                    MediaCommand.NEXT -> controller.transportControls.skipToNext()
                    MediaCommand.PREVIOUS -> controller.transportControls.skipToPrevious()
                }
                true
            }
        } catch (_: RuntimeException) {
            ErrorJournal.record(context, "MEDIA_CONTROL_DENIED")
            false
        }
        if (submitted) schedulePoll()
        return submitted
    }
    private fun dispatchToggle(controller: MediaController): Boolean = try {
        val down = controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        val up = controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        down && up
    } catch (_: RuntimeException) { ErrorJournal.record(context, "MEDIA_CONTROL_DENIED"); false }
    private fun schedulePoll() {
        handler.postDelayed({ poll() }, 300)
        handler.postDelayed({ poll() }, 800)
    }
    /** Explicit Android selection wins; the default and the OEM Bluetooth bridge return to CarPlay. */
    fun openSource(apps: AppIntegrationManager, carplay: CarPlayIntegrationManager): Boolean {
        val preferred = settings.mediaPackage
        val projectionPackage = settings.component("zlink")?.packageName ?: VERIFIED_ZLINK_PACKAGE
        return if (preferred != null && preferred != projectionPackage && !isLegacyOemSource(preferred))
            apps.launchPackage(preferred) else carplay.open()
    }
    private fun isLegacyOemSource(pkg: String?) = pkg == "com.tw.bt" || pkg == VERIFIED_ZLINK_PACKAGE
    companion object { private const val VERIFIED_ZLINK_PACKAGE = "com.zjinnova.zlink" }
}
