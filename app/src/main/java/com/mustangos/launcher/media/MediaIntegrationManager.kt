package com.mustangos.launcher.media

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
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
    val playbackStateKnown: Boolean = false,
    val fallbackDisplayTitle: String? = null,
    val fallbackDisplaySubtitle: String? = null
) {
    // Presentation fields do not turn a display subtitle or generic description into artist metadata.
    val displayTitle: String? get() = facts.title ?: fallbackDisplayTitle
    val displaySubtitle: String? get() = facts.artist ?: fallbackDisplaySubtitle
    val titleOrigin: String? get() = when { facts.title != null -> "TITLE"; fallbackDisplayTitle != null -> "DISPLAY_TITLE"; else -> null }
    val subtitleOrigin: String? get() = when { facts.artist != null -> "ARTIST"; fallbackDisplaySubtitle != null -> "DISPLAY_SUBTITLE"; else -> null }
}
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
                p?.state in listOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_PAUSED, PlaybackState.STATE_STOPPED),
                m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { it.isNotBlank() },
                m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)?.takeIf { it.isNotBlank() })
        } catch (_: RuntimeException) { mutable.value = MediaUiState(status = "Fuente multimedia no disponible"); ErrorJournal.record(context, "MEDIA_READ_FAILED") }
    }
    /** Standard-field presence only. Never prints song contents, custom keys, URI values or notification data. */
    fun sessionDiagnostics(): String = buildString {
        appendLine("SESIONES MULTIMEDIA · campos crudos sin contenido privado")
        if (!runCatching { hasAccess() }.getOrDefault(false)) { append("Acceso multimedia no disponible"); return@buildString }
        val currentSessions = sessions.take(12)
        if (currentSessions.isEmpty()) { append("Sin sesiones visibles"); return@buildString }
        for (controller in currentSessions) {
            val metadata = runCatching { controller.metadata }
            val m = metadata.getOrNull()
            val playback = runCatching { controller.playbackState }.getOrNull()
            val chosen = selected?.sessionToken == controller.sessionToken
            val pkg = runCatching { controller.packageName }.getOrDefault("paquete no legible")
            val known = playback?.state in listOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_PAUSED, PlaybackState.STATE_STOPPED)
            appendLine("$pkg; seleccionada=$chosen; metadata=${m != null}; metadata_legible=${metadata.isSuccess}; estado=${playback?.state ?: "no publicado"}; estado_conocido=$known; acciones=${playback?.actions ?: 0L}")
            fun text(short: String, key: String) = "$short{clave=${m?.containsKey(key) == true},valor=${runCatching { m?.getText(key)?.isNotBlank() == true }.getOrDefault(false)}}"
            fun bitmap(short: String, key: String) = "$short{clave=${m?.containsKey(key) == true},bitmap=${runCatching { m?.getBitmap(key) != null }.getOrDefault(false)}}"
            fun uri(short: String, key: String): String {
                val value = runCatching { m?.getString(key)?.takeIf { it.isNotBlank() } }.getOrNull()
                val content = runCatching { value?.let(Uri::parse)?.scheme == "content" }.getOrDefault(false)
                return "$short{clave=${m?.containsKey(key) == true},valor=${value != null},content=$content}"
            }
            appendLine(listOf(text("TITLE", MediaMetadata.METADATA_KEY_TITLE), text("ARTIST", MediaMetadata.METADATA_KEY_ARTIST),
                text("ALBUM", MediaMetadata.METADATA_KEY_ALBUM), "DURATION{clave=${m?.containsKey(MediaMetadata.METADATA_KEY_DURATION) == true},positiva=${runCatching { (m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L) > 0L }.getOrDefault(false)}}").joinToString("; "))
            appendLine(listOf(text("DISPLAY_TITLE", MediaMetadata.METADATA_KEY_DISPLAY_TITLE), text("DISPLAY_SUBTITLE", MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
                text("DISPLAY_DESCRIPTION", MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)).joinToString("; "))
            appendLine(listOf(bitmap("ALBUM_ART", MediaMetadata.METADATA_KEY_ALBUM_ART), bitmap("ART", MediaMetadata.METADATA_KEY_ART),
                bitmap("DISPLAY_ICON", MediaMetadata.METADATA_KEY_DISPLAY_ICON)).joinToString("; "))
            appendLine(listOf(uri("ALBUM_ART_URI", MediaMetadata.METADATA_KEY_ALBUM_ART_URI), uri("ART_URI", MediaMetadata.METADATA_KEY_ART_URI),
                uri("DISPLAY_ICON_URI", MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)).joinToString("; "))
            val description = runCatching { m?.description }.getOrNull()
            appendLine("description.title=${description?.title?.isNotBlank() == true}; description.subtitle=${description?.subtitle?.isNotBlank() == true}; description.description=${description?.description?.isNotBlank() == true}; description.bitmap=${description?.iconBitmap != null}; description.uri=${description?.iconUri != null}; description.content=${description?.iconUri?.scheme == "content"}")
            if (chosen) appendLine("Presentación seleccionada: título_origen=${mutable.value.titleOrigin ?: "ninguno"}; texto_secundario_origen=${mutable.value.subtitleOrigin ?: "ninguno"}")
        }
        if (sessions.size > currentSessions.size) appendLine("Sesiones adicionales omitidas: ${sessions.size - currentSessions.size}")
        append("Los datos de una sesión no se combinan con los de otra; las URI no se abren ni se exportan.")
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
