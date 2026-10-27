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
import com.mustangos.core.IntegrationPolicy
import com.mustangos.core.MediaCommand
import com.mustangos.core.MediaFacts
import com.mustangos.launcher.diagnostics.ErrorJournal
import com.mustangos.launcher.settings.LauncherSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MediaUiState(val facts: MediaFacts = MediaFacts(), val artwork: Bitmap? = null, val status: String = "Acceso multimedia sin conceder")
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
        select(null); sessions = emptyList(); mutable.value = MediaUiState(status = "Multimedia detenida")
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
            val pkg = IntegrationPolicy.selectMedia(sessions.map { it.packageName }, settings.mediaPackage, settings.component("zlink")?.packageName)
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
            mutable.value = MediaUiState(MediaFacts(controller.packageName,
                m?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() },
                m?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() },
                m?.getString(MediaMetadata.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() },
                m?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0,
                p?.position?.coerceAtLeast(0) ?: 0, p?.lastPositionUpdateTime ?: 0,
                p?.state == PlaybackState.STATE_PLAYING, p?.playbackSpeed ?: 0f, p?.actions ?: 0), art,
                if (p?.state == PlaybackState.STATE_ERROR) "La fuente reportó un error" else "Sesión Android · ${controller.packageName}")
        } catch (_: RuntimeException) { mutable.value = MediaUiState(status = "Fuente multimedia no disponible"); ErrorJournal.record(context, "MEDIA_READ_FAILED") }
    }
    fun command(command: MediaCommand): Boolean {
        val controller = selected ?: return false
        // Re-read capabilities just before dispatch; do not send global media key events.
        publish()
        if (!mutable.value.facts.supports(command)) return false
        return try {
            if ((command == MediaCommand.PLAY || command == MediaCommand.PAUSE) && mutable.value.facts.actions and command.action == 0L) {
                val down = controller.dispatchMediaButtonEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                val up = controller.dispatchMediaButtonEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                return down && up
            }
            when (command) {
                MediaCommand.PLAY -> controller.transportControls.play()
                MediaCommand.PAUSE -> controller.transportControls.pause()
                MediaCommand.NEXT -> controller.transportControls.skipToNext()
                MediaCommand.PREVIOUS -> controller.transportControls.skipToPrevious()
            }; true
        } catch (_: RuntimeException) { ErrorJournal.record(context, "MEDIA_CONTROL_DENIED"); refresh(); false }
    }
}
