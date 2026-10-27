package com.mustangos.launcher.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mustangos.core.LaunchableApp
import com.mustangos.core.MediaCommand
import com.mustangos.launcher.R
import com.mustangos.launcher.databinding.ActivityMainBinding
import com.mustangos.launcher.diagnostics.DiagnosticReporter
import com.mustangos.launcher.diagnostics.ErrorJournal
import com.mustangos.launcher.media.MediaUiState
import kotlinx.coroutines.*
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private val model: LauncherViewModel by viewModels()
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { model.gps.start() }
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching { contentResolver.openOutputStream(uri)?.use { it.write(model.lastDiagnostic.toByteArray(Charsets.UTF_8)) } ?: error("No stream") }.isSuccess }
            toast(if (saved) "Diagnóstico exportado" else "No se pudo exportar el diagnóstico")
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply { hide(WindowInsetsCompat.Type.systemBars()); systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.topSettings.setOnClickListener { showSettings() }
        binding.dockSettings.setOnClickListener { showSettings() }
        binding.openCarplay.setOnClickListener { openRole("zlink") }
        binding.dockCarplay.setOnClickListener { openRole("zlink") }
        binding.dockMusic.setOnClickListener { openRole("music") }
        binding.openMaps.setOnClickListener { openRole("maps") }
        binding.dockNavigation.setOnClickListener { navigationChooser() }
        binding.dockPhone.setOnClickListener { openRole("phone") }
        binding.dockRadio.setOnClickListener { openRole("radio") }
        binding.dockApps.setOnClickListener { chooseApp(null) }
        binding.dockEngine.setOnClickListener { AlertDialog.Builder(this).setTitle("Motor · V6 4.0L").setMessage("${model.vehicle.status}\n\nRPM, refrigerante, voltaje y kilometraje: no disponibles.\n\nEl módulo está preparado para una futura fuente OBD2 verificada. No se accede al CANBUS ni a la MCU.").setPositiveButton("Cerrar", null).show() }
        binding.gpsStatus.setOnClickListener { requestGps() }
        binding.mediaSource.setOnClickListener { mediaSources() }
        binding.previous.setOnClickListener { mediaCommand(MediaCommand.PREVIOUS) }
        binding.next.setOnClickListener { mediaCommand(MediaCommand.NEXT) }
        binding.playPause.setOnClickListener { mediaCommand(if (model.media.state.value.facts.playing) MediaCommand.PAUSE else MediaCommand.PLAY) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.gps.state.collect { state -> binding.gauge.setSpeed(state.kmh); binding.gpsStatus.text = state.status } }
                launch { model.media.state.collect { renderMedia(it) } }
                launch { while (isActive) { updateProgress(); updateConnectivity(); delay(1000) } }
            }
        }
    }
    override fun onStart() { super.onStart(); model.start() }
    override fun onStop() { model.stop(); super.onStop() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent) }
    private fun renderMedia(ui: MediaUiState) {
        val m = ui.facts
        binding.songTitle.text = m.title ?: if (m.source == null) "Sin datos de reproducción" else "Título no disponible"
        binding.songArtist.text = m.artist ?: "Artista no disponible"
        binding.songAlbum.text = m.album
        binding.songAlbum.visibility = if (m.album == null) View.GONE else View.VISIBLE
        binding.albumArt.setImageBitmap(ui.artwork)
        binding.albumArt.visibility = if (ui.artwork == null) View.GONE else View.VISIBLE
        binding.artPlaceholder.visibility = if (ui.artwork == null) View.VISIBLE else View.GONE
        binding.artHint.text = if (m.source == null) "Apple Music vía CarPlay" else "Sin portada disponible"
        binding.mediaStatus.text = ui.status
        binding.playPause.setImageResource(if (m.playing) R.drawable.ic_pause else R.drawable.ic_play)
        binding.playPause.contentDescription = if (m.playing) "Pausar" else "Reproducir"
        listOf(binding.previous to MediaCommand.PREVIOUS, binding.next to MediaCommand.NEXT, binding.playPause to if (m.playing) MediaCommand.PAUSE else MediaCommand.PLAY).forEach { (view, command) ->
            view.isEnabled = m.supports(command); view.alpha = if (view.isEnabled) 1f else 0.3f
        }
        updateProgress()
    }
    private fun updateProgress() {
        val m = model.media.state.value.facts
        val position = m.progress(SystemClock.elapsedRealtime())
        binding.progress.progress = if (m.durationMs > 0) ((position.toDouble() / m.durationMs) * 1000).roundToInt() else 0
        binding.songTime.text = if (m.durationMs > 0) "${formatTime(position)} / ${formatTime(m.durationMs)}" else "— / —"
    }
    private fun formatTime(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)
    private fun mediaCommand(command: MediaCommand) { if (!model.media.command(command)) toast("La fuente no permite este control") }
    private fun updateConnectivity() {
        val manager = getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        binding.connectivity.text = when {
            capabilities == null -> "Sin red activa"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi activo"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet activo"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Red móvil activa"
            else -> "Red activa"
        }
    }
    private fun openRole(role: String) {
        val launched = if (role == "zlink") model.carplay.open() else model.apps.launch(role)
        if (!launched) chooseApp(role)
    }
    private fun chooseApp(role: String?, launchAfter: Boolean = true) {
        lifecycleScope.launch {
            val available = withContext(Dispatchers.IO) { if (role == "home") model.apps.homes() else model.apps.apps() }
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
            if (available.isEmpty()) { toast("No hay aplicaciones visibles compatibles"); return@launch }
            val sorted = if (role == "zlink") available.sortedByDescending { com.mustangos.core.IntegrationPolicy.isZlinkCandidate(it) } else if (role == "maps") {
                val maps = withContext(Dispatchers.IO) { model.apps.maps().map { it.packageName }.toSet() }
                available.sortedByDescending { it.packageName in maps }
            } else available
            showAppPicker(role, sorted, launchAfter)
        }
    }
    private fun showAppPicker(role: String?, apps: List<LaunchableApp>, launchAfter: Boolean) {
        AlertDialog.Builder(this).setTitle(if (role == null) "Aplicaciones" else "Elegir ${roleName(role)} · estacionado")
            .setItems(apps.map { "${it.label}\n${it.packageName}" }.toTypedArray()) { _, index ->
                val app = apps[index]
                if (role != null) model.apps.assign(role, app)
                if (launchAfter && !model.apps.launchApp(app)) toast("Android no permite abrir esta actividad")
                else if (!launchAfter) { toast("Aplicación guardada"); model.media.refresh() }
            }.setNegativeButton("Cancelar", null).show()
    }
    private fun navigationChooser() {
        AlertDialog.Builder(this).setTitle("Navegación").setItems(arrayOf("CarPlay / ZLink · iPhone", "Mapas Android")) { _, which -> openRole(if (which == 0) "zlink" else "maps") }.setNegativeButton("Cerrar", null).show()
    }
    private fun mediaSources() {
        if (!model.media.hasAccess()) {
            AlertDialog.Builder(this).setTitle("Acceso multimedia opcional")
                .setMessage("Android exige acceso a notificaciones para consultar sesiones multimedia de otras apps. Mustang OS no lee sus mensajes ni su contenido. Puedes revocarlo en Ajustes del sistema.\n\nEste permiso no garantiza que ZLink publique Apple Music.")
                .setPositiveButton("Abrir permisos") { _, _ -> safeSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                .setNegativeButton("Cancelar", null).show(); return
        }
        model.media.refresh()
        val sessions = model.media.sessions.map { it.packageName }.distinct()
        if (sessions.isEmpty()) { AlertDialog.Builder(this).setTitle("Sin sesiones compatibles").setMessage("Reproduce música y vuelve a Mustang OS. Si ZLink no publica una MediaSession Android, este panel no puede leer ni controlar Apple Music del iPhone.").setPositiveButton("Abrir ZLink") { _, _ -> openRole("zlink") }.setNegativeButton("Cerrar", null).show(); return }
        AlertDialog.Builder(this).setTitle("Fuente multimedia real").setItems((listOf("Selección automática") + sessions).toTypedArray()) { _, which -> model.settings.mediaPackage = if (which == 0) null else sessions[which - 1]; model.media.refresh() }.setNegativeButton("Cerrar", null).show()
    }
    private fun requestGps() {
        if (!model.gps.hasPermission()) {
            AlertDialog.Builder(this).setTitle("Velocidad GPS").setMessage("La ubicación precisa se usa solamente mientras Mustang OS está visible. No se guardan ni se envían coordenadas. Elige ubicación precisa para medir velocidad. No sustituye los instrumentos originales.")
                .setPositiveButton("Permitir ubicación") { _, _ -> locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
                .setNeutralButton("Permisos de la app") { _, _ -> safeSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))) }
                .setNegativeButton("Cancelar", null).show()
        } else safeSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }
    private fun showSettings() {
        val roles = listOf("zlink", "music", "maps", "phone", "radio", "home")
        val entries = roles.map { "Elegir ${roleName(it)}" } + listOf("Abrir launcher original", "Elegir launcher predeterminado", "Permisos GPS", "Fuente y permiso multimedia", "Diagnóstico y exportación", "Ajustes Android")
        AlertDialog.Builder(this).setTitle("Ajustes · configurar estacionado").setItems(entries.toTypedArray()) { _, index ->
            when {
                index < roles.size -> chooseApp(roles[index], false)
                index == 6 -> openRole("home")
                index == 7 -> AlertDialog.Builder(this).setTitle("Launcher predeterminado").setMessage("Primero elige y prueba el launcher original en estos ajustes. Android mostrará las opciones Home disponibles; el firmware puede imponer su propia pantalla de arranque.").setPositiveButton("Abrir ajustes Home") { _, _ -> safeSettings(Intent(Settings.ACTION_HOME_SETTINGS)) }.setNegativeButton("Cancelar", null).show()
                index == 8 -> requestGps()
                index == 9 -> mediaSources()
                index == 10 -> showDiagnostics()
                else -> safeSettings(Intent(Settings.ACTION_SETTINGS))
            }
        }.setNegativeButton("Cerrar", null).show()
    }
    private fun roleName(role: String) = when (role) { "zlink" -> "CarPlay / ZLink"; "music" -> "música Android"; "maps" -> "mapas Android"; "phone" -> "teléfono"; "radio" -> "radio"; "home" -> "launcher original"; else -> role }
    private fun safeSettings(intent: Intent) { try { startActivity(intent) } catch (_: RuntimeException) { ErrorJournal.record(this, "SETTINGS_UNAVAILABLE"); toast("Esta pantalla de ajustes no está disponible en el firmware") } }
    private fun showDiagnostics() {
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) { DiagnosticReporter(this@MainActivity, model).create() }
            model.lastDiagnostic = report
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
            val text = TextView(this@MainActivity).apply { this.text = report; textSize = 14f; setTextIsSelectable(true); setPadding(24, 16, 24, 16) }
            val scroll = ScrollView(this@MainActivity).apply { addView(text) }
            AlertDialog.Builder(this@MainActivity).setTitle("Diagnóstico local").setView(scroll).setPositiveButton("Exportar .txt") { _, _ -> export.launch("MustangOS-diagnostico.txt") }.setNegativeButton("Cerrar", null).show()
        }
    }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
}
