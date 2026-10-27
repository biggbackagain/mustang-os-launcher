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
import com.mustangos.launcher.diagnostics.CompatibilityExporter
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
    private val exportCompatibility = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) lifecycleScope.launch {
            toast("Preparando informe y archivos estáticos OEM…")
            val saved = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openOutputStream(uri)?.use { CompatibilityExporter(this@MainActivity).write(it, model.lastDiagnostic) } ?: error("No stream") }.isSuccess
            }
            toast(if (saved) "ZIP guardado. Abre el enlace de descarga en Chrome y pulsa Enviar diagnóstico" else "No se pudo completar el ZIP")
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply { hide(WindowInsetsCompat.Type.systemBars()); systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (resources.getBoolean(R.bool.compact_dashboard)) {
            binding.navigationDescription.visibility = View.GONE
            binding.artHint.visibility = View.GONE
        }
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
        binding.dockEngine.setOnClickListener { showVehicleData() }
        binding.gpsStatus.setOnClickListener { requestGps() }
        binding.mediaSource.setOnClickListener { mediaSources() }
        binding.previous.setOnClickListener { mediaCommand(MediaCommand.PREVIOUS) }
        binding.next.setOnClickListener { mediaCommand(MediaCommand.NEXT) }
        binding.playPause.setOnClickListener { mediaCommand(if (model.media.state.value.facts.playing) MediaCommand.PAUSE else MediaCommand.PLAY) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.gps.state.collect { state -> binding.gauge.setSpeed(state.kmh); binding.gpsStatus.text = state.status } }
                launch { model.media.state.collect { renderMedia(it) } }
                launch { model.oem.state.collect { value -> binding.vehicleInfo.text = if (model.settings.oemReadEnabled || value.frames > 0) model.oem.summary() else getString(R.string.gps_disclaimer) } }
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
        if (!launched) {
            val failure = model.apps.lastFailure
            if (failure != null && (failure.startsWith("Permiso OEM") || failure.contains("denegó"))) {
                AlertDialog.Builder(this).setTitle("Acceso restringido por el firmware").setMessage(failure).setPositiveButton("Cerrar", null).show()
            } else chooseApp(role)
        }
    }
    private fun chooseApp(role: String?, launchAfter: Boolean = true) {
        lifecycleScope.launch {
            val available = withContext(Dispatchers.IO) { model.apps.choices(role) }
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
            if (available.isEmpty()) {
                if (role == "maps") AlertDialog.Builder(this@MainActivity).setTitle("No hay mapas Android instalados")
                    .setMessage("Apple Maps del iPhone se ejecuta dentro de ZLink. Esta versión todavía no puede mostrar su superficie dentro del panel. No se ofrecerán radio, clima u otras apps como si fueran mapas.")
                    .setPositiveButton("Abrir ZLink") { _, _ -> openRole("zlink") }.setNegativeButton("Cerrar", null).show()
                else toast("No hay una aplicación compatible instalada para ${roleName(role.orEmpty())}")
                return@launch
            }
            showAppPicker(role, available, launchAfter)
        }
    }
    private fun showAppPicker(role: String?, apps: List<LaunchableApp>, launchAfter: Boolean) {
        AlertDialog.Builder(this).setTitle(if (role == null) "Aplicaciones" else "Elegir ${roleName(role)} · estacionado")
            .setItems(apps.map { "${it.label}\n${it.packageName}" }.toTypedArray()) { _, index ->
                val app = apps[index]
                if (role != null) model.apps.assign(role, app)
                if (launchAfter && !model.apps.launchApp(app)) toast(model.apps.lastFailure ?: "Android no permite abrir esta actividad")
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
        val entries = roles.map { "Elegir ${roleName(it)}" } + listOf("Abrir launcher original", "Elegir launcher predeterminado", "Permisos GPS", "Fuente y permiso multimedia", "Diagnóstico y exportación", "Inspeccionar servicio ZLink", "Ajustes Android")
        AlertDialog.Builder(this).setTitle("Ajustes · configurar estacionado").setItems(entries.toTypedArray()) { _, index ->
            when {
                index < roles.size -> chooseApp(roles[index], false)
                index == 6 -> openRole("home")
                index == 7 -> AlertDialog.Builder(this).setTitle("Launcher predeterminado").setMessage("Primero elige y prueba el launcher original en estos ajustes. Android mostrará las opciones Home disponibles; el firmware puede imponer su propia pantalla de arranque.").setPositiveButton("Abrir ajustes Home") { _, _ -> safeSettings(Intent(Settings.ACTION_HOME_SETTINGS)) }.setNegativeButton("Cancelar", null).show()
                index == 8 -> requestGps()
                index == 9 -> mediaSources()
                index == 10 -> showDiagnostics()
                index == 11 -> showZLinkProbe()
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
            AlertDialog.Builder(this@MainActivity).setTitle("Diagnóstico local").setView(scroll).setPositiveButton("Exportar .txt") { _, _ -> export.launch("MustangOS-diagnostico.txt") }
                .setNeutralButton("Compatibilidad OEM") { _, _ ->
                    AlertDialog.Builder(this@MainActivity).setTitle("Investigar MCU y el panel CarPlay")
                        .setMessage("Se guardará un ZIP con este diagnóstico, los manifiestos y el código instalado de ZLink y apps OEM visibles. No lee sus datos privados ni modifica su instalación. Los archivos permitirán analizar interfaces reales sin ADB.\n\nGuárdalo en Descargas; después puedes enviarlo desde la misma página de descarga de Mustang OS.")
                        .setPositiveButton("Guardar ZIP") { _, _ -> exportCompatibility.launch("MustangOS-compatibilidad.zip") }.setNegativeButton("Cancelar", null).show()
                }.setNegativeButton("Cerrar", null).show()
        }
    }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    private fun showVehicleData() {
        val native = model.vehicle.nativeReport()
        val text = TextView(this).apply { textSize = 16f; setPadding(24, 16, 24, 16) }
        val scroll = ScrollView(this).apply { addView(text) }
        val dialog = AlertDialog.Builder(this).setTitle("Vehículo y equipo · sin OBD").setView(scroll)
            .setPositiveButton(if (model.settings.oemReadEnabled) "Detener lectura OEM" else "Activar lectura OEM") { _, _ ->
                if (model.settings.oemReadEnabled) model.oem.setEnabled(false)
                else AlertDialog.Builder(this).setTitle("Escucha OEM de solo lectura")
                    .setMessage("Se intentará suscribir a los eventos de voltaje, temperatura y puertas usando la interfaz encontrada en tu firmware. No envía consultas ni órdenes a la MCU. Si el sistema rechaza la suscripción, se mostrará el motivo disponible.\n\nLos valores permanecerán vacíos hasta recibir eventos reales. Después de activarla, abre de nuevo Motor para verlos.")
                    .setPositiveButton("Activar escucha") { _, _ -> model.oem.setEnabled(true); showVehicleData() }.setNegativeButton("Cancelar", null).show()
            }
            .setNeutralButton("Apps OEM") { _, _ -> AlertDialog.Builder(this).setItems(arrayOf("OriginalCar", "CarMate")) { _, index -> openKnownPackage(if (index == 0) "com.tw.car" else "com.dofun.carassistant.car") }.setNegativeButton("Cerrar", null).show() }
            .setNegativeButton("Cerrar", null).show()
        val job = lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(model.oem.state, model.gps.state) { oem, gps ->
                "${oem.status}\n${model.oem.summary()}\nEventos recibidos: ${oem.frames}\n" +
                    "Puertas: ${oem.doorMask?.let { "máscara OEM 0x%02X (posición pendiente de comprobar)".format(it) } ?: "sin evento"}\n\n" +
                    "GPS: ${gps.kmh?.let { "%.0f km/h".format(it) } ?: "sin velocidad"}; precisión ${gps.accuracyMeters ?: "—"} m; satélites ${gps.satellitesUsed ?: "—"}\n" +
                    "Altitud GPS: ${gps.altitudeMeters?.let { "%.0f m".format(it) } ?: "—"}; rumbo GPS: ${gps.bearingDegrees?.let { "%.0f°".format(it) } ?: "—"}\n\n" + native +
                    "\nRPM, refrigerante, kilometraje y velocidad CAN: sin interfaz verificada."
            }.collect { text.text = it }
        }
        dialog.setOnDismissListener { job.cancel() }
    }
    private fun openKnownPackage(pkg: String) {
        val candidate = model.apps.apps().firstOrNull { it.packageName == pkg }
        if (candidate == null) toast("Esta aplicación OEM no está visible")
        else if (!model.apps.launchApp(candidate)) toast(model.apps.lastFailure ?: "No se pudo abrir la aplicación OEM")
    }
    private fun showZLinkProbe() {
        val text = TextView(this).apply { textSize = 17f; setPadding(24, 24, 24, 24) }
        val dialog = AlertDialog.Builder(this).setTitle("Interfaz pública de ZLink").setView(text).setPositiveButton("Cerrar", null).show()
        val job = lifecycleScope.launch { model.zlinkProbe.state.collect { text.text = it } }
        dialog.setOnDismissListener { job.cancel(); model.zlinkProbe.stop() }
        model.zlinkProbe.probe()
    }
}
