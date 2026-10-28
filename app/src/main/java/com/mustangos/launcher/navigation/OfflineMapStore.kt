package com.mustangos.launcher.navigation

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

data class OfflineMapState(
    val map: OfflineMapDescriptor? = null,
    val enabled: Boolean = true,
    val checking: Boolean = true,
    val busy: Boolean = false,
    val downloaded: Long = 0,
    val total: Long = 0,
    val message: String = "Revisando mapa offline…"
)

/** DownloadManager owns network work; only fully validated files are atomically activated. */
class OfflineMapStore internal constructor(context: Context,
    private val directory: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "offline-maps"),
    private val validate: (File) -> OfflineMapDescriptor = OfflineMapValidator::validate
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("offline-maps", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val revision = AtomicLong()
    private val destination = File(directory, "mexico.map")
    private val partial = File(directory, "mexico.map.part")
    private val manager: DownloadManager? get() = app.getSystemService(DownloadManager::class.java)
    private val mutable = MutableStateFlow(OfflineMapState(enabled = prefs.getBoolean("enabled", true)))
    val state: StateFlow<OfflineMapState> = mutable
    private var validatedStamp: Pair<Long, Long>? = null
    private var importing = false

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE &&
                    intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -2) == prefs.getLong("download_id", -1)) refresh()
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        refresh()
    }

    fun refresh() { scope.launch { lock.withLock { refreshLocked() } } }

    private fun refreshLocked() {
        if (importing) return
        directory.mkdirs()
        val stamp = destination.takeIf(File::isFile)?.let { it.length() to it.lastModified() }
        if (stamp != null && stamp != validatedStamp) {
            val descriptor = runCatching { validate(destination) }.getOrNull()
            validatedStamp = if (descriptor != null) stamp else null
            mutable.value = mutable.value.copy(map = descriptor)
        } else if (stamp == null) { validatedStamp = null; mutable.value = mutable.value.copy(map = null) }
        val id = prefs.getLong("download_id", -1)
        if (id < 0) {
            mutable.value = mutable.value.copy(checking = false, busy = false,
                message = if (mutable.value.map != null) "México completo disponible sin conexión" else "México completo · descarga aproximada 686 MiB")
            return
        }
        val cursor = runCatching { manager?.query(DownloadManager.Query().setFilterById(id)) }.getOrNull()
        cursor?.use {
            if (!it.moveToFirst()) { finishDownload("No se encontró la descarga. Puedes reintentar."); return }
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val received = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)).coerceAtLeast(0)
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).coerceAtLeast(0)
            if (total > OfflineMapValidator.MAX_BYTES || received > OfflineMapValidator.MAX_BYTES) {
                manager?.remove(id); partial.delete(); finishDownload("El archivo excede el límite de 2 GiB"); return
            }
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    mutable.value = mutable.value.copy(checking = true, busy = true, message = "Validando México descargado…")
                    try {
                        require(total == 0L || partial.length() == total)
                        val descriptor = commit(partial)
                        prefs.edit().remove("download_id").putBoolean("enabled", true).apply()
                        mutable.value = OfflineMapState(descriptor, enabled = true, checking = false,
                            message = "México completo listo sin conexión")
                    } catch (_: Exception) { partial.delete(); finishDownload("El mapa descargado no es válido. Reintenta la descarga.") }
                }
                DownloadManager.STATUS_FAILED -> { partial.delete(); finishDownload("La descarga no pudo completarse. Puedes reintentar por Wi-Fi.") }
                else -> mutable.value = mutable.value.copy(checking = false, busy = true, downloaded = received, total = total,
                    message = if (status == DownloadManager.STATUS_RUNNING) "Descargando México por Wi-Fi…" else "Esperando Wi-Fi sin medición o disponibilidad de la descarga…")
            }
        } ?: finishDownload("El gestor de descargas no está disponible")
    }

    private fun finishDownload(message: String) {
        prefs.edit().remove("download_id").apply()
        mutable.value = mutable.value.copy(checking = false, busy = false, message = message)
    }

    fun downloadMexico() { scope.launch { lock.withLock {
        if (importing || prefs.getLong("download_id", -1) >= 0) return@withLock
        directory.mkdirs()
        if (directory.usableSpace < EXPECTED_BYTES + RESERVE_BYTES) {
            mutable.value = mutable.value.copy(checking = false, message = "Libera al menos 800 MiB para descargar México"); return@withLock
        }
        partial.delete()
        try {
            val request = DownloadManager.Request(Uri.parse(MEXICO_URL))
                .setTitle("Mustang OS · México sin conexión")
                .setDescription("Mapa vectorial México completo · solo Wi-Fi")
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
                .setAllowedOverMetered(false).setAllowedOverRoaming(false)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(partial))
            val id = requireNotNull(manager).enqueue(request)
            prefs.edit().putLong("download_id", id).apply()
            mutable.value = mutable.value.copy(checking = false, busy = true, downloaded = 0, total = EXPECTED_BYTES, message = "Descarga iniciada · esperando Wi-Fi")
        } catch (_: Exception) { mutable.value = mutable.value.copy(checking = false, busy = false, message = "No se pudo iniciar. También puedes importar mexico.map desde Descargas.") }
    } } }

    fun cancelDownload() {
        revision.incrementAndGet()
        scope.launch { lock.withLock {
            val id = prefs.getLong("download_id", -1)
            if (id >= 0) runCatching { manager?.remove(id) }
            partial.delete(); finishDownload("Descarga cancelada; el mapa anterior se conserva")
        } }
    }

    suspend fun import(uri: Uri): Boolean {
        val request = revision.incrementAndGet()
        return withContext(Dispatchers.IO) {
            if (uri.scheme != "content") return@withContext false
            val pending = File(directory, "import-${UUID.randomUUID()}.part")
            var ownsImport = false
            try {
                lock.withLock {
                    if (importing) return@withContext false
                    val id = prefs.getLong("download_id", -1)
                    if (id >= 0) manager?.remove(id)
                    prefs.edit().remove("download_id").apply(); partial.delete()
                    directory.mkdirs(); importing = true; ownsImport = true
                }
                app.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                    val size = descriptor.declaredLength
                    require(size <= OfflineMapValidator.MAX_BYTES)
                    require(directory.usableSpace >= (if (size > 0) size else EXPECTED_BYTES) + RESERVE_BYTES)
                    mutable.value = mutable.value.copy(busy = true, checking = false, downloaded = 0, total = size.coerceAtLeast(0), message = "Copiando mapa a Mustang OS…")
                    descriptor.createInputStream().use { input -> pending.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        var lastNotice = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            if (revision.get() != request) return@withContext false
                            val count = input.read(buffer)
                            if (count < 0) break
                            copied += count
                            require(copied <= OfflineMapValidator.MAX_BYTES)
                            output.write(buffer, 0, count)
                            if (copied - lastNotice >= 8 * 1024 * 1024) {
                                require(directory.usableSpace > RESERVE_BYTES)
                                mutable.value = mutable.value.copy(downloaded = copied); lastNotice = copied
                            }
                        }
                        require(size < 0 || copied == size)
                    } }
                } ?: return@withContext false
                currentCoroutineContext().ensureActive()
                lock.withLock {
                    if (revision.get() != request) return@withContext false
                    mutable.value = mutable.value.copy(checking = true, message = "Validando mapa de México…")
                    val descriptor = commit(pending)
                    prefs.edit().putBoolean("enabled", true).apply()
                    mutable.value = OfflineMapState(descriptor, enabled = true, checking = false, message = "México completo listo sin conexión")
                }
                true
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (failure: Exception) {
                mutable.value = mutable.value.copy(checking = false, busy = false, message = "No se pudo importar (${failure.javaClass.simpleName}). Usa mexico.map completo (máximo 2 GiB) y comprueba el espacio libre.")
                false
            } finally {
                pending.delete()
                if (ownsImport) lock.withLock { importing = false; mutable.value = mutable.value.copy(checking = false, busy = false) }
            }
        }
    }

    private fun commit(pending: File): OfflineMapDescriptor {
        val validated = validate(pending)
        try { Files.move(pending.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(pending.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        validatedStamp = destination.length() to destination.lastModified()
        return validated.copy(file = destination, modified = destination.lastModified())
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("enabled", enabled).apply()
        mutable.value = mutable.value.copy(enabled = enabled)
    }

    companion object {
        const val MEXICO_URL = "https://download.mapsforge.org/maps/v5/north-america/mexico.map"
        const val EXPECTED_BYTES = 719_418_229L
        private const val RESERVE_BYTES = 64L * 1024 * 1024
        @Volatile private var instance: OfflineMapStore? = null
        fun get(context: Context): OfflineMapStore = instance ?: synchronized(this) {
            instance ?: OfflineMapStore(context).also { instance = it }
        }
    }
}
