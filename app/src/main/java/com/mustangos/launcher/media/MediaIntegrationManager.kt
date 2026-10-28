package com.mustangos.launcher.media

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
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
    val fallbackDisplaySubtitle: String? = null,
    val coverStatus: String = "La fuente no publicó portada"
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
    private val artworkLoader = ContentArtworkLoader(context.contentResolver)
    private var artworkScope: CoroutineScope? = null
    private var artworkJob: Job? = null
    private var artworkOwner: MediaSession.Token? = null
    private var artworkKey: ArtworkKey? = null
    private var artworkRevision = 0L
    private var currentArtwork: Bitmap? = null
    private var currentCoverStatus = ArtworkStatus.NONE
    private var selectionReason = "sin_consulta"
    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { refresh() }
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) { invalidateArtwork(); publish() }
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
        artworkScope?.cancel(); artworkScope = null
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
            // A package can own several independent sessions. Only identical tokens are duplicates.
            sessions = manager.getActiveSessions(component).distinctBy { it.sessionToken }
            val candidates = sessions.map { SessionCandidate(it) }
            val preferred = settings.mediaPackage
            val projection = settings.component("zlink")?.packageName ?: VERIFIED_ZLINK_PACKAGE
            var pkg = IntegrationPolicy.selectMedia(candidates.map { it.pkg }, preferred, projection)
            selectionReason = when {
                preferred != null -> if (pkg == null) "preferida_ausente" else "preferida"
                pkg == projection -> "proyeccion"
                pkg != null -> "paquete_unico"
                else -> "sin_fuente_unica"
            }
            if (pkg == null && preferred == null && sessions.isNotEmpty()) {
                val playing = candidates.filter { it.playbackRank == 4 }
                pkg = playing.firstOrNull { it.controller.sessionToken == selected?.sessionToken }?.pkg ?: playing.firstOrNull()?.pkg
                if (pkg != null) selectionReason = "reproduciendo"
                else if (candidates.any { it.pkg == "com.tw.bt" }) { pkg = "com.tw.bt"; selectionReason = "bluetooth" }
                else candidates.firstOrNull { it.metadataPublished }?.let { pkg = it.pkg; selectionReason = "metadata_publicada" }
            }
            val eligible = candidates.filter { it.pkg == pkg }
            val quality = eligible.maxWithOrNull(compareBy<SessionCandidate> { it.playbackRank }.thenBy { it.metadataRank })
            val best = quality?.let { target -> eligible.filter { it.playbackRank == target.playbackRank && it.metadataRank == target.metadataRank } }
            val chosen = best?.firstOrNull { it.controller.sessionToken == selected?.sessionToken }
                ?: best?.maxByOrNull { it.updatedAtMs }
            select(chosen?.controller)
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
        if (selected === controller) return
        // Fresh wrappers can contain new cached metadata even when the Binder token stays the same.
        if (selected?.sessionToken != controller?.sessionToken) invalidateArtwork()
        selected?.let { runCatching { it.unregisterCallback(callback) } }
        selected = controller
        controller?.registerCallback(callback, handler)
    }
    private data class SessionCandidate(val controller: MediaController) {
        val pkg = controller.packageName
        private val playback = runCatching { controller.playbackState }.getOrNull()
        private val metadata = runCatching { controller.metadata }.getOrNull()
        val metadataPublished = metadata != null
        val playbackRank = when (playback?.state) {
            PlaybackState.STATE_PLAYING -> 4
            PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING, PlaybackState.STATE_SKIPPING_TO_NEXT,
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS, PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> 3
            PlaybackState.STATE_PAUSED -> 2
            PlaybackState.STATE_STOPPED -> 1
            else -> 0
        }
        private fun text(key: String) = runCatching { metadata?.getText(key)?.isNotBlank() == true }.getOrDefault(false)
        val metadataRank = (if (text(MediaMetadata.METADATA_KEY_TITLE) || text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)) 4 else 0) +
            (if (text(MediaMetadata.METADATA_KEY_ARTIST) || text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)) 2 else 0) +
            (if (text(MediaMetadata.METADATA_KEY_ALBUM)) 1 else 0) +
            (if (runCatching { (metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L) > 0L }.getOrDefault(false)) 1 else 0)
        val updatedAtMs = playback?.lastPositionUpdateTime ?: 0L
    }
    private fun publish() {
        val controller = selected ?: run {
            mutable.value = MediaUiState(status = when {
                selectionReason == "preferida_ausente" -> "Esperando la fuente multimedia elegida"
                sessions.map { it.packageName }.distinct().size > 1 -> "Selecciona una fuente multimedia"
                else -> "Sin sesión multimedia compatible"
            }); return
        }
        try {
            val m = controller.metadata
            val p = controller.playbackState
            val image = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            updateArtwork(controller, m, image)
            val rawActions = p?.actions ?: 0L
            mutable.value = MediaUiState(MediaFacts(controller.packageName,
                m?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() },
                m?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() },
                m?.getString(MediaMetadata.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() },
                m?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0,
                p?.position?.coerceAtLeast(0) ?: 0, p?.lastPositionUpdateTime ?: 0,
                p?.state == PlaybackState.STATE_PLAYING, p?.playbackSpeed ?: 0f, rawActions), currentArtwork,
                if (p?.state == PlaybackState.STATE_ERROR) "La fuente reportó un error" else "Sesión Android · ${controller.packageName}",
                p?.state in listOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_PAUSED, PlaybackState.STATE_STOPPED),
                m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { it.isNotBlank() },
                m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)?.takeIf { it.isNotBlank() }, currentCoverStatus.label)
        } catch (_: RuntimeException) { invalidateArtwork(); mutable.value = MediaUiState(status = "Fuente multimedia no disponible"); ErrorJournal.record(context, "MEDIA_READ_FAILED") }
    }
    private fun invalidateArtwork() {
        artworkRevision++
        artworkJob?.cancel(); artworkJob = null
        artworkOwner = null; artworkKey = null
        // Published bitmaps may still be displayed by a view; only abandoned loader results are recycled.
        currentArtwork = null; currentCoverStatus = ArtworkStatus.NONE
    }
    private fun updateArtwork(controller: MediaController, metadata: MediaMetadata?, bitmap: Bitmap?) {
        val owner = controller.sessionToken
        val key = ArtworkKey(metadata, bitmap)
        if (artworkOwner == owner && artworkKey == key) return
        invalidateArtwork()
        artworkOwner = owner; artworkKey = key
        if (bitmap != null) {
            currentArtwork = if (maxOf(bitmap.width, bitmap.height) > 384) {
                val scale = 384f / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
            } else bitmap
            currentCoverStatus = ArtworkStatus.BITMAP
            return
        }
        val published = listOf(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI).mapNotNull { key ->
            metadata?.getString(key)?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
        }
        val uri = published.firstOrNull { it.scheme == "content" && !it.authority.isNullOrBlank() }
        if (uri == null) {
            currentCoverStatus = if (published.isEmpty()) ArtworkStatus.NONE else ArtworkStatus.REJECTED
            return
        }
        currentCoverStatus = ArtworkStatus.LOADING
        val revision = artworkRevision
        val currentScope = artworkScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { artworkScope = it }
        artworkJob = currentScope.launch {
            val result = artworkLoader.load(uri)
            if (revision == artworkRevision && selected?.sessionToken == owner && artworkOwner == owner &&
                artworkKey == key) {
                currentArtwork = result.bitmap; currentCoverStatus = result.status
                mutable.update { it.copy(artwork = result.bitmap, coverStatus = result.status.label) }
            } else result.bitmap?.recycle()
        }
    }
    /** Private structural identity survives Binder parcel copies. Values never enter diagnostics. */
    private data class ArtworkKey(val text: List<String?>, val duration: Long, val bitmapWidth: Int, val bitmapHeight: Int) {
        constructor(metadata: MediaMetadata?, bitmap: Bitmap?) : this(
            listOf(MediaMetadata.METADATA_KEY_MEDIA_ID, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_ARTIST,
                MediaMetadata.METADATA_KEY_ALBUM, MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
                MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
                .map { metadata?.getString(it) },
            metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L, bitmap?.width ?: 0, bitmap?.height ?: 0)
    }
    /** Standard-field presence only. Never prints song contents, custom keys, URI values or notification data. */
    fun sessionDiagnostics(): String = buildString {
        appendLine("SESIONES MULTIMEDIA · campos crudos sin contenido privado")
        appendLine("Selección: preferencia=${settings.mediaPackage ?: "automática"}; motivo=$selectionReason; paquetes=${sessions.map { it.packageName }.distinct().size}; tokens=${sessions.size}")
        if (!runCatching { hasAccess() }.getOrDefault(false)) { append("Acceso multimedia no disponible"); return@buildString }
        val currentSessions = sessions.take(12)
        if (currentSessions.isEmpty()) { append("Sin sesiones visibles"); return@buildString }
        for ((index, controller) in currentSessions.withIndex()) {
            val metadata = runCatching { controller.metadata }
            val m = metadata.getOrNull()
            val playback = runCatching { controller.playbackState }.getOrNull()
            val chosen = selected?.sessionToken == controller.sessionToken
            val pkg = runCatching { controller.packageName }.getOrDefault("paquete no legible")
            val known = playback?.state in listOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_PAUSED, PlaybackState.STATE_STOPPED)
            appendLine("$pkg; seleccionada=$chosen; metadata=${m != null}; metadata_legible=${metadata.isSuccess}; estado=${playback?.state ?: "no publicado"}; estado_conocido=$known; acciones=${playback?.actions ?: 0L}")
            appendLine("Sesión visible=${index + 1}; sesiones de este paquete=${sessions.count { it.packageName == pkg }}")
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
            if (chosen) appendLine("Presentación seleccionada: título_origen=${mutable.value.titleOrigin ?: "ninguno"}; texto_secundario_origen=${mutable.value.subtitleOrigin ?: "ninguno"}; portada=${mutable.value.coverStatus}")
        }
        if (sessions.size > currentSessions.size) appendLine("Sesiones adicionales omitidas: ${sessions.size - currentSessions.size}")
        append("Los datos de una sesión no se combinan con los de otra. Solo se leen URI content de portada de la sesión seleccionada, con permiso normal y límites de tamaño/tiempo; sus valores no se exportan.")
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
