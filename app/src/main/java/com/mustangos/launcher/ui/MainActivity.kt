package com.mustangos.launcher.ui

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
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
    private var welcomeLightsPlayed = false
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        model.gps.start()
    }
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use {
                        it.write(model.lastDiagnostic.toByteArray(Charsets.UTF_8))
                    } ?: error("No stream")
                }.isSuccess
            }
            toast(if (saved) "Diagnóstico exportado" else "No se pudo exportar el diagnóstico")
        }
    }
    private val exportCompatibility = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) lifecycleScope.launch {
            toast("Preparando informe y archivos estáticos OEM…")
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use {
                        CompatibilityExporter(this@MainActivity).write(it, model.lastDiagnostic)
                    } ?: error("No stream")
                }.isSuccess
            }
            toast(if (saved) "ZIP guardado. Abre el enlace de descarga en Chrome y pulsa Enviar diagnóstico" else "No se pudo completar el ZIP")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (resources.getBoolean(R.bool.compact_dashboard)) {
            binding.navigationDescription.visibility = View.GONE
            binding.artHint.visibility = View.GONE
            binding.carImage.scaleType = ImageView.ScaleType.FIT_CENTER
        }

        // Auto-request location permission on first launch if not granted
        if (!model.gps.hasPermission()) {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        binding.topSettings.setOnClickListener { showSettings() }
        binding.dockSettings.setOnClickListener { showSettings() }
        binding.openCarplay.setOnClickListener { openRole("zlink") }
        binding.dockCarplay.setOnClickListener { openRole("zlink") }
        binding.dockMusic.setOnClickListener { openCurrentMedia() }
        binding.openMaps.setOnClickListener { openRole("zlink") }
        binding.dockNavigation.setOnClickListener { navigationChooser() }
        binding.dockPhone.setOnClickListener { openRole("phone") }
        binding.dockRadio.setOnClickListener { openRole("radio") }
        binding.dockApps.setOnClickListener { showAppDrawer() }
        binding.dockEngine.setOnClickListener { showVehicleData() }
        binding.topBattery.setOnClickListener { showVehicleData() }
        binding.topDoorStatus.setOnClickListener { showVehicleData() }
        binding.gpsStatus.setOnClickListener { requestGps() }
        binding.mediaSource.setOnClickListener { mediaSources() }

        binding.albumArt.setOnClickListener { openCurrentMedia() }
        binding.artPlaceholder.setOnClickListener { openCurrentMedia() }
        binding.songTitle.setOnClickListener { openCurrentMedia() }

        binding.previous.setOnClickListener { mediaCommand(MediaCommand.PREVIOUS) }
        binding.next.setOnClickListener { mediaCommand(MediaCommand.NEXT) }
        binding.playPause.setOnClickListener {
            if (!model.media.togglePlayPause()) toast(getString(R.string.media_command_unavailable))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    model.gps.state.collect { state ->
                        binding.gauge.setSpeed(state.kmh)
                        binding.gpsStatus.text = state.status
                    }
                }
                launch { model.media.state.collect { renderMedia(it) } }
                launch {
                    model.oem.state.collect { oem ->
                        binding.topDoorStatus.visibility = if (oem.doorMask?.let { it != 0 } == true) View.VISIBLE else View.GONE
                        val now = SystemClock.elapsedRealtime()
                        val previousDoor = !oem.sourceActive || oem.lastDoorMs?.let { now - it > 15_000 } == true
                        binding.topDoorStatus.setText(if (previousDoor) R.string.oem_previous_opening_badge else R.string.oem_opening_badge)
                        binding.topBattery.text = oem.voltage?.let {
                            val previousVoltage = !oem.sourceActive || oem.lastVoltageMs?.let { time -> now - time > 15_000 } == true
                            if (previousVoltage) getString(R.string.oem_previous_voltage, it) else "%.1f V".format(it)
                        } ?: "— V"
                        binding.vehicleInfo.text = "${model.oem.summary()}\n${oem.ageSummary}"
                    }
                }
                launch {
                    model.vehicle.accessoryOn.collect { on ->
                        if (on != null) binding.carImage.setHeadlightsOn(on, animate = true)
                    }
                }
                launch {
                    model.zlinkProbe.state.collect { status ->
                        val friendly = when {
                            status.contains("IPhoneContentPresenter") || status.contains("Interfaz Binder") ->
                                getString(R.string.carplay_service_available)
                            status.contains("Consultando") -> "Verificando ZLink…"
                            status.contains("no activo") -> "Toca para abrir ZLink CarPlay"
                            else -> status
                        }
                        binding.carplayStatus.text = friendly
                    }
                }
                launch {
                    while (isActive) {
                        updateProgress()
                        updateConnectivity()
                        model.media.poll()
                        model.vehicle.poll()
                        delay(1000)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        model.start()
        val accessoryOn = model.vehicle.accessoryOn.value
        if (accessoryOn != null) binding.carImage.setHeadlightsOn(accessoryOn)
        else if (!welcomeLightsPlayed) binding.carImage.playWelcomeLights()
        welcomeLightsPlayed = true
        model.zlinkProbe.probe()
    }

    override fun onStop() {
        binding.carImage.stopAnimation()
        model.stop()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun renderMedia(ui: MediaUiState) {
        val m = ui.facts
        binding.songTitle.text = ui.displayTitle ?: if (m.source == null) "Sin datos de reproducción" else "Título no disponible"
        binding.songArtist.text = ui.displaySubtitle ?: "Artista no disponible"
        binding.songAlbum.text = m.album
        binding.songAlbum.visibility = if (m.album == null) View.GONE else View.VISIBLE
        binding.albumArt.setImageBitmap(ui.artwork)
        binding.albumArt.visibility = if (ui.artwork == null) View.GONE else View.VISIBLE
        binding.artPlaceholder.visibility = if (ui.artwork == null) View.VISIBLE else View.GONE
        binding.artHint.text = if (m.source == null) "Multimedia de CarPlay" else "Sin portada disponible"
        binding.mediaStatus.text = ui.status
        binding.playPause.setImageResource(when {
            !ui.playbackStateKnown -> R.drawable.ic_play_pause
            m.playing -> R.drawable.ic_pause
            else -> R.drawable.ic_play
        })
        binding.playPause.contentDescription = if (!ui.playbackStateKnown) getString(R.string.media_toggle)
            else if (m.playing) "Pausar" else "Reproducir"

        listOf(
            binding.previous to MediaCommand.PREVIOUS,
            binding.next to MediaCommand.NEXT,
            binding.playPause to if (m.playing) MediaCommand.PAUSE else MediaCommand.PLAY
        ).forEach { (view, command) ->
            view.isEnabled = if (view === binding.playPause)
                model.media.canCommand(MediaCommand.PLAY) || model.media.canCommand(MediaCommand.PAUSE)
                else model.media.canCommand(command)
            view.alpha = if (view.isEnabled) 1f else 0.3f
        }
        updateProgress()
    }

    private fun updateProgress() {
        val ui = model.media.state.value
        val m = ui.facts
        if (!ui.playbackStateKnown) {
            binding.progress.progress = 0
            binding.songTime.text = if (m.durationMs > 0) "— / ${formatTime(m.durationMs)}" else getString(R.string.media_unknown_time)
            return
        }
        val position = m.progress(SystemClock.elapsedRealtime())
        if (m.durationMs > 0) {
            binding.progress.progress = ((position.toDouble() / m.durationMs) * 1000).roundToInt()
            binding.songTime.text = "${formatTime(position)} / ${formatTime(m.durationMs)}"
        } else if (m.playing) {
            binding.progress.progress = 0
            binding.songTime.text = if (m.positionMs > 0) "${formatTime(m.positionMs)} / —" else getString(R.string.media_playing_without_duration)
        } else if (m.source != null) {
            binding.progress.progress = 0
            binding.songTime.setText(R.string.media_not_playing)
        } else {
            binding.progress.progress = 0
            binding.songTime.text = "— / —"
        }
    }

    private fun formatTime(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

    private fun mediaCommand(command: MediaCommand) {
        if (!model.media.command(command)) toast("La fuente no permite este control")
    }

    private fun openCurrentMedia() {
        if (!model.media.openSource(model.apps, model.carplay)) {
            toast(model.apps.lastFailure ?: getString(R.string.media_source_unavailable))
        }
    }

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

    private fun showAppDrawer() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { model.apps.apps() }
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch

            val view = layoutInflater.inflate(R.layout.dialog_app_drawer, null)
            val dialog = AlertDialog.Builder(this@MainActivity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                .setView(view)
                .create()

            val grid = view.findViewById<GridView>(R.id.appsGrid)
            val countText = view.findViewById<TextView>(R.id.appCount)
            val closeBtn = view.findViewById<View>(R.id.closeDrawer)

            countText.text = "${apps.size} aplicaciones instaladas"
            closeBtn.setOnClickListener { dialog.dismiss() }

            val pm = packageManager
            grid.adapter = object : BaseAdapter() {
                override fun getCount(): Int = apps.size
                override fun getItem(position: Int): Any = apps[position]
                override fun getItemId(position: Int): Long = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    val item = convertView ?: layoutInflater.inflate(R.layout.item_app_drawer, parent, false)
                    val app = apps[position]
                    val icon = item.findViewById<ImageView>(R.id.appIcon)
                    val label = item.findViewById<TextView>(R.id.appLabel)
                    label.text = app.label
                    try {
                        icon.setImageDrawable(pm.getActivityIcon(ComponentName(app.packageName, app.activity)))
                    } catch (_: Exception) {
                        try {
                            icon.setImageDrawable(pm.getApplicationIcon(app.packageName))
                        } catch (_: Exception) {
                            icon.setImageResource(R.drawable.ic_apps)
                        }
                    }
                    item.setOnClickListener {
                        dialog.dismiss()
                        if (!model.apps.launchApp(app)) toast(model.apps.lastFailure ?: "No se pudo abrir ${app.label}")
                    }
                    return item
                }
            }
            dialog.show()
            dialog.window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
        }
    }

    private fun chooseApp(role: String?, launchAfter: Boolean = true) {
        lifecycleScope.launch {
            val available = withContext(Dispatchers.IO) { model.apps.choices(role) }
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
            if (available.isEmpty()) {
                toast("No hay aplicaciones disponibles")
                return@launch
            }
            showAppPicker(role, available, launchAfter)
        }
    }

    private fun showAppPicker(role: String?, apps: List<LaunchableApp>, launchAfter: Boolean) {
        AlertDialog.Builder(this)
            .setTitle(if (role == null) "Aplicaciones" else "Elegir ${roleName(role)} · estacionado")
            .setItems(apps.map { "${it.label}\n${it.packageName}" }.toTypedArray()) { _, index ->
                val app = apps[index]
                if (role != null) model.apps.assign(role, app)
                if (launchAfter && !model.apps.launchApp(app)) {
                    toast(model.apps.lastFailure ?: "Android no permite abrir esta actividad")
                } else if (!launchAfter) {
                    toast("Aplicación guardada")
                    model.media.refresh()
                }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun navigationChooser() {
        val chosen = model.apps.selected("maps")
        val currentName = chosen?.let { c -> model.apps.apps().firstOrNull { it.packageName == c.packageName }?.label } ?: "Mapas Android"
        AlertDialog.Builder(this).setTitle("Navegación").setItems(arrayOf(
            "CarPlay / ZLink · iPhone",
            "Abrir $currentName",
            "Elegir app de navegación…"
        )) { _, which ->
            when (which) {
                0 -> openRole("zlink")
                1 -> openRole("maps")
                2 -> chooseApp("maps", false)
            }
        }.setNegativeButton("Cerrar", null).show()
    }

    private fun mediaSources() {
        if (!model.media.hasAccess()) {
            AlertDialog.Builder(this).setTitle("Acceso multimedia opcional")
                .setMessage("Android exige acceso a notificaciones para consultar sesiones multimedia de otras apps. Mustang OS no lee sus mensajes ni su contenido. Puedes revocarlo en Ajustes del sistema.\n\nEste permiso no garantiza que ZLink publique Apple Music.")
                .setPositiveButton("Abrir permisos") { _, _ -> safeSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                .setNegativeButton("Cancelar", null).show()
            return
        }
        model.media.refresh()
        val sessions = model.media.sessions.map { it.packageName }.distinct()
        if (sessions.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Sin sesiones compatibles")
                .setMessage("Reproduce música y vuelve a Mustang OS. Si ZLink no publica una MediaSession Android, este panel no puede leer ni controlar Apple Music del iPhone.")
                .setPositiveButton("Abrir ZLink") { _, _ -> openRole("zlink") }
                .setNegativeButton("Cerrar", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Fuente multimedia real")
            .setItems((listOf("Selección automática") + sessions).toTypedArray()) { _, which ->
                model.settings.mediaPackage = if (which == 0) null else sessions[which - 1]
                model.media.refresh()
            }.setNegativeButton("Cerrar", null).show()
    }

    private fun requestGps() {
        if (!model.gps.hasPermission()) {
            AlertDialog.Builder(this).setTitle("Velocidad GPS")
                .setMessage("La ubicación precisa se usa solamente mientras Mustang OS está visible. No se guardan ni se envían coordenadas. Elige ubicación precisa para medir velocidad. No sustituye los instrumentos originales.")
                .setPositiveButton("Permitir ubicación") { _, _ ->
                    locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                .setNeutralButton("Permisos de la app") { _, _ ->
                    safeSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
                }
                .setNegativeButton("Cancelar", null).show()
        } else {
            safeSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    private fun showSettings() {
        val roles = listOf("zlink", "music", "maps", "phone", "radio", "home")
        val entries = roles.map { "Elegir ${roleName(it)}" } + listOf(
            "Abrir launcher original",
            "Elegir launcher predeterminado",
            "Permisos GPS",
            "Fuente y permiso multimedia",
            "Diagnóstico y exportación",
            "Inspeccionar servicio ZLink",
            "Ajustes Android"
        )
        AlertDialog.Builder(this).setTitle("Ajustes · configurar estacionado").setItems(entries.toTypedArray()) { _, index ->
            when {
                index < roles.size -> chooseApp(roles[index], false)
                index == 6 -> openRole("home")
                index == 7 -> AlertDialog.Builder(this).setTitle("Launcher predeterminado")
                    .setMessage("Primero elige y prueba el launcher original en estos ajustes. Android mostrará las opciones Home disponibles; el firmware puede imponer su propia pantalla de arranque.")
                    .setPositiveButton("Abrir ajustes Home") { _, _ -> safeSettings(Intent(Settings.ACTION_HOME_SETTINGS)) }
                    .setNegativeButton("Cancelar", null).show()
                index == 8 -> requestGps()
                index == 9 -> mediaSources()
                index == 10 -> showDiagnostics()
                index == 11 -> showZLinkProbe()
                else -> safeSettings(Intent(Settings.ACTION_SETTINGS))
            }
        }.setNegativeButton("Cerrar", null).show()
    }

    private fun roleName(role: String) = when (role) {
        "zlink" -> "CarPlay / ZLink"
        "music" -> "música Android"
        "maps" -> "mapas Android"
        "phone" -> "teléfono"
        "radio" -> "radio"
        "home" -> "launcher original"
        else -> role
    }

    private fun safeSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: RuntimeException) {
            ErrorJournal.record(this, "SETTINGS_UNAVAILABLE")
            toast("Esta pantalla de ajustes no está disponible en el firmware")
        }
    }

    private fun showDiagnostics() {
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) { DiagnosticReporter(this@MainActivity, model).create() }
            model.lastDiagnostic = report
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
            val text = TextView(this@MainActivity).apply {
                this.text = report
                textSize = 14f
                setTextIsSelectable(true)
                setPadding(24, 16, 24, 16)
            }
            val scroll = ScrollView(this@MainActivity).apply { addView(text) }
            AlertDialog.Builder(this@MainActivity).setTitle("Diagnóstico local").setView(scroll)
                .setPositiveButton("Exportar .txt") { _, _ -> export.launch("MustangOS-diagnostico.txt") }
                .setNeutralButton("Compatibilidad OEM") { _, _ ->
                    AlertDialog.Builder(this@MainActivity).setTitle("Investigar MCU y el panel CarPlay")
                        .setMessage("Se guardará un ZIP con este diagnóstico, los manifiestos y el código instalado de ZLink y apps OEM visibles. No lee sus datos privados ni modifica su instalación. Los archivos permitirán analizar interfaces reales sin ADB.\n\nGuárdalo en Descargas; después puedes enviarlo desde la misma página de descarga de Mustang OS.")
                        .setPositiveButton("Guardar ZIP") { _, _ -> exportCompatibility.launch("MustangOS-compatibilidad.zip") }
                        .setNegativeButton("Cancelar", null).show()
                }.setNegativeButton("Cerrar", null).show()
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showVehicleData() {
        val view = layoutInflater.inflate(R.layout.dialog_vehicle_dashboard, null)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Telemetría y sensores · Ford Mustang 2010")
            .setView(view)
            .setPositiveButton("Cerrar", null)
            .setNegativeButton(if (model.settings.oemReadEnabled) "Detener lectura OEM" else "Activar lectura OEM") { _, _ ->
                if (model.settings.oemReadEnabled) model.oem.setEnabled(false)
                else AlertDialog.Builder(this).setTitle("Lectura OEM")
                    .setMessage(getString(R.string.oem_read_explanation))
                    .setPositiveButton("Activar escucha") { _, _ -> model.oem.setEnabled(true); showVehicleData() }
                    .setNegativeButton("Cancelar", null).show()
            }
            .setNeutralButton("Apps OEM") { _, _ ->
                AlertDialog.Builder(this).setItems(arrayOf("OriginalCar", "CarMate")) { _, index ->
                    openKnownPackage(if (index == 0) "com.tw.car" else "com.dofun.carassistant.car")
                }.setNegativeButton("Cerrar", null).show()
            }.create()

        val dashSpeed = view.findViewById<TextView>(R.id.dashSpeed)
        val dashGpsDetails = view.findViewById<TextView>(R.id.dashGpsDetails)
        val dashVoltage = view.findViewById<TextView>(R.id.dashVoltage)
        val dashVoltageStatus = view.findViewById<TextView>(R.id.dashVoltageStatus)
        val dashGForceLat = view.findViewById<TextView>(R.id.dashGForceLat)
        val dashGForceLong = view.findViewById<TextView>(R.id.dashGForceLong)
        val dashGyroDetails = view.findViewById<TextView>(R.id.dashGyroDetails)
        val dashDoorStatus = view.findViewById<TextView>(R.id.dashDoorStatus)
        val dashTempDetails = view.findViewById<TextView>(R.id.dashTempDetails)
        val dashQueryTempBtn = view.findViewById<Button>(R.id.dashQueryTempBtn)
        val dashMcuStatus = view.findViewById<TextView>(R.id.dashMcuStatus)
        view.findViewById<Button>(R.id.dashCloseBtn).setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.dashOpenCarBtn).setOnClickListener { openKnownPackage("com.tw.car") }
        view.findViewById<Button>(R.id.dashOpenCarMateBtn).setOnClickListener { openKnownPackage("com.dofun.carassistant.car") }
        view.findViewById<Button>(R.id.dashOpenFordSettingsBtn).setOnClickListener {
            val launched = model.apps.launchDirect(ComponentName("com.tw.car", "com.tw.car.HCFordSettingsActivity"))
                || model.apps.launchDirect(ComponentName("com.tw.car", "com.tw.car.FordEquinoxActivity"))
            if (!launched) openKnownPackage("com.tw.car")
        }
        dashQueryTempBtn.setOnClickListener { model.oem.requestAmbientTemperature() }

        val sm = getSystemService(SensorManager::class.java)
        val linear = sm?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val accel = linear ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val accelerationLabel = when {
            linear != null -> getString(R.string.acceleration_linear_source)
            accel != null -> getString(R.string.acceleration_gravity_source)
            else -> getString(R.string.acceleration_unavailable)
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.size < 3 || event.values.take(3).any { !it.isFinite() }) return
                when (event.sensor.type) {
                    Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER -> {
                        dashGForceLat.text = "%+.2f G".format(event.values[0] / 9.80665f)
                        dashGForceLong.text = "%+.2f G".format(event.values[1] / 9.80665f)
                    }
                    Sensor.TYPE_GYROSCOPE -> dashGyroDetails.text = accelerationLabel + "\n" +
                        "Giroscopio del equipo: X %+.1f°/s · Y %+.1f°/s · Z %+.1f°/s".format(
                            Math.toDegrees(event.values[0].toDouble()), Math.toDegrees(event.values[1].toDouble()),
                            Math.toDegrees(event.values[2].toDouble()))
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val job = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                dashGForceLat.text = "—"
                dashGForceLong.text = "—"
                dashGyroDetails.text = accelerationLabel + "\n" + getString(
                    if (gyro == null) R.string.gyroscope_unavailable else R.string.device_gyro_unknown)
                accel?.let { runCatching { sm?.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) } }
                gyro?.let { runCatching { sm?.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) } }
                try {
                    kotlinx.coroutines.flow.combine(model.oem.state, model.gps.state, model.vehicle.battery,
                        model.vehicle.accessoryOn) { oem, gps, battery, accessory ->
                        dashSpeed.text = gps.kmh?.let { "%.0f".format(it) } ?: "—"
                        val bearing = gps.bearingDegrees?.let { "Rumbo %.0f°".format(it) } ?: "Rumbo —"
                        val altitude = gps.altitudeMeters?.let { "Altitud %.0f m".format(it) } ?: "Altitud —"
                        val accuracy = gps.accuracyMeters?.let { "Precisión %.0f m".format(it) } ?: "Precisión —"
                        dashGpsDetails.text = "${gps.status}\n$bearing · $altitude · $accuracy\n" +
                            "Satélites usados: ${gps.satellitesUsed ?: "—"} / visibles: ${gps.satellitesVisible ?: "—"}"

                        dashVoltage.text = oem.voltage?.let { "%.1f".format(it) } ?: "—"
                        dashVoltageStatus.text = if (oem.voltage == null) getString(R.string.oem_voltage_unknown)
                            else oem.ageSummary
                        dashVoltageStatus.setTextColor(Color.parseColor("#8E99A8"))
                        dashDoorStatus.text = model.oem.doorDescription() ?: getString(R.string.oem_doors_unknown)
                        dashDoorStatus.setTextColor(Color.parseColor(if (oem.doorMask?.let { it != 0 } == true) "#FF354A" else "#8E99A8"))
                        val temperature = oem.temperatureC?.let { "%.1f °C".format(it) } ?: "sin lectura"
                        val androidTemperature = battery.tempC?.let { "%.1f °C".format(it) } ?: "sin lectura"
                        dashTempDetails.text = "Exterior OEM: $temperature\nBatería Android: $androidTemperature"
                        dashQueryTempBtn.isEnabled = oem.temperatureQueryAvailable
                        val profile = oem.protocol?.let { "Perfil recibido: modelo ${it.model}, tipo ${it.type}" } ?: "Perfil OEM sin evento"
                        val accessoryText = when (accessory) { true -> "ACC OEM activo"; false -> "ACC OEM inactivo"; null -> "ACC OEM sin evento" }
                        dashMcuStatus.text = "${oem.status} · Eventos: ${oem.frames}\n${oem.ageSummary}\n" +
                            "${oem.voltageRequestStatus}\n${oem.temperatureRequestStatus}\n$profile · $accessoryText\n" +
                            getString(R.string.engine_data_unknown)
                    }.collect { }
                } finally { sm?.unregisterListener(listener) }
            }
        }
        dialog.setOnDismissListener { job.cancel() }
        dialog.show()
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
