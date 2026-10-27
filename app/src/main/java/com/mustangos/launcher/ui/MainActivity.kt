package com.mustangos.launcher.ui

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
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
        }

        // Auto-request location permission on first launch if not granted
        if (!model.gps.hasPermission()) {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
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
        binding.dockApps.setOnClickListener { showAppDrawer() }
        binding.dockEngine.setOnClickListener { showVehicleData() }
        binding.topBattery.setOnClickListener { showVehicleData() }
        binding.topDoorStatus.setOnClickListener { showVehicleData() }
        binding.gpsStatus.setOnClickListener { requestGps() }
        binding.mediaSource.setOnClickListener { mediaSources() }

        // Click on music widget album art or title opens music app
        binding.albumArt.setOnClickListener { openRole("music") }
        binding.artPlaceholder.setOnClickListener { openRole("music") }
        binding.songTitle.setOnClickListener { openRole("music") }

        binding.previous.setOnClickListener { mediaCommand(MediaCommand.PREVIOUS) }
        binding.next.setOnClickListener { mediaCommand(MediaCommand.NEXT) }
        binding.playPause.setOnClickListener {
            mediaCommand(if (model.media.state.value.facts.playing) MediaCommand.PAUSE else MediaCommand.PLAY)
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
                    kotlinx.coroutines.flow.combine(model.oem.state, model.vehicle.battery) { oem, bat ->
                        // Door status indicator in Top Bar
                        val doorOpen = oem.doorMask != null && oem.doorMask != 0
                        if (doorOpen) {
                            binding.topDoorStatus.visibility = View.VISIBLE
                            val doorDesc = model.oem.doorDescription() ?: "PUERTA ABIERTA"
                            binding.topDoorStatus.text = "⚠️ ${doorDesc.uppercase()}"
                        } else {
                            binding.topDoorStatus.visibility = View.GONE
                        }

                        // Battery voltage: Never show 5.0V (internal USB rail) as car battery voltage
                        val volts = oem.voltage ?: bat.voltage?.takeIf { it > 8.0f }
                        val voltsText = volts?.let { "%.1f V".format(it) } ?: "--.- V"
                        binding.topBattery.text = "⚡ $voltsText"

                        // Temperature
                        val temp = oem.temperatureC ?: bat.tempC
                        val tempText = temp?.let { "%.0f °C".format(it) }

                        // Center Card vehicleInfo
                        val doorSummary = model.oem.doorDescription() ?: if (doorOpen) "Puerta abierta" else "Puertas cerradas"
                        val infoParts = listOfNotNull(
                            "⚡ $voltsText",
                            tempText?.let { "🌡️ $it" },
                            doorSummary
                        )
                        binding.vehicleInfo.text = infoParts.joinToString("   ·   ")
                    }.collect { }
                }
                launch {
                    model.zlinkProbe.state.collect { status ->
                        val friendly = when {
                            status.contains("IPhoneContentPresenter") || status.contains("Interfaz Binder") ->
                                "ZLink CarPlay listo · Toca para abrir"
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
        model.zlinkProbe.probe()
    }

    override fun onStop() {
        model.stop()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

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

        listOf(
            binding.previous to MediaCommand.PREVIOUS,
            binding.next to MediaCommand.NEXT,
            binding.playPause to if (m.playing) MediaCommand.PAUSE else MediaCommand.PLAY
        ).forEach { (view, command) ->
            view.isEnabled = m.supports(command)
            view.alpha = if (view.isEnabled) 1f else 0.3f
        }
        updateProgress()
    }

    private fun updateProgress() {
        val m = model.media.state.value.facts
        val position = m.progress(SystemClock.elapsedRealtime())
        if (m.durationMs > 0) {
            binding.progress.progress = ((position.toDouble() / m.durationMs) * 1000).roundToInt()
            binding.songTime.text = "${formatTime(position)} / ${formatTime(m.durationMs)}"
        } else if (m.playing) {
            binding.progress.progress = 500
            val posText = if (position > 0) formatTime(position) else ""
            binding.songTime.text = if (posText.isNotEmpty()) "$posText · Bluetooth Audio" else "▶ En reproducción"
        } else if (m.source != null) {
            binding.progress.progress = 0
            binding.songTime.text = "⏸ Pausado"
        } else {
            binding.progress.progress = 0
            binding.songTime.text = "— / —"
        }
    }

    private fun formatTime(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

    private fun mediaCommand(command: MediaCommand) {
        if (!model.media.command(command)) toast("La fuente no permite este control")
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
        val builder = AlertDialog.Builder(this)
            .setTitle("Telemetría y sensores · Ford Mustang 2010")
            .setView(view)
            .setPositiveButton("Cerrar", null)
            .setNegativeButton(if (model.settings.oemReadEnabled) "Detener lectura OEM" else "Activar lectura OEM") { _, _ ->
                if (model.settings.oemReadEnabled) model.oem.setEnabled(false)
                else AlertDialog.Builder(this).setTitle("Escucha OEM de solo lectura")
                    .setMessage("Se intentará suscribir a los eventos de voltaje, temperatura y puertas usando la interfaz encontrada en tu firmware. La escucha es pasiva. El botón Solicitar temperatura OEM permite pedir una lectura una vez por sesión, como lo hace la barra original; no cambia ajustes del vehículo.\n\nLos valores permanecerán vacíos hasta recibir eventos reales. Después de activarla, abre de nuevo Motor para verlos.")
                    .setPositiveButton("Activar escucha") { _, _ -> model.oem.setEnabled(true); showVehicleData() }
                    .setNegativeButton("Cancelar", null).show()
            }
            .setNeutralButton("Apps OEM") { _, _ ->
                AlertDialog.Builder(this).setItems(arrayOf("OriginalCar", "CarMate")) { _, index ->
                    openKnownPackage(if (index == 0) "com.tw.car" else "com.dofun.carassistant.car")
                }.setNegativeButton("Cerrar", null).show()
            }
        val dialog = builder.create()

        val dashSpeed = view.findViewById<TextView>(R.id.dashSpeed)
        val dashSpeedUnit = view.findViewById<TextView>(R.id.dashSpeedUnit)
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
        val dashCloseBtn = view.findViewById<Button>(R.id.dashCloseBtn)
        val dashOpenCarBtn = view.findViewById<Button>(R.id.dashOpenCarBtn)
        val dashOpenCarMateBtn = view.findViewById<Button>(R.id.dashOpenCarMateBtn)
        val dashOpenFordSettingsBtn = view.findViewById<Button>(R.id.dashOpenFordSettingsBtn)

        dashCloseBtn.setOnClickListener { dialog.dismiss() }
        dashOpenCarBtn.setOnClickListener { openKnownPackage("com.tw.car") }
        dashOpenCarMateBtn.setOnClickListener { openKnownPackage("com.dofun.carassistant.car") }
        dashOpenFordSettingsBtn.setOnClickListener {
            val launched = model.apps.launchDirect(ComponentName("com.tw.car", "com.tw.car.HCFordSettingsActivity"))
                || model.apps.launchDirect(ComponentName("com.tw.car", "com.tw.car.FordEquinoxActivity"))
            if (!launched) openKnownPackage("com.tw.car")
        }
        dashQueryTempBtn.setOnClickListener { model.oem.requestAmbientTemperature() }

        // SensorManager for live G-Force and Gyroscope dynamics
        val sm = getSystemService(SensorManager::class.java)
        val accel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val latG = (event.values[0] / 9.80665f)
                    val longG = (event.values[1] / 9.80665f)
                    dashGForceLat.text = "%+.2f G".format(latG)
                    dashGForceLong.text = "%+.2f G".format(longG)
                } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    val degX = Math.toDegrees(event.values[0].toDouble())
                    val degY = Math.toDegrees(event.values[1].toDouble())
                    val degZ = Math.toDegrees(event.values[2].toDouble())
                    dashGyroDetails.text = "Giroscopio: X: %+.1f°/s · Y: %+.1f°/s · Z: %+.1f°/s".format(degX, degY, degZ)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        accel?.let { sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
        gyro?.let { sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }

        val job = lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(model.oem.state, model.gps.state) { oem, gps ->
                // GPS Speed & Details
                val kmh = gps.kmh
                dashSpeed.text = kmh?.let { "%.0f".format(it) } ?: "0"
                val bearing = gps.bearingDegrees?.let { "Rumbo: %.0f°".format(it) } ?: "Rumbo: --°"
                val alt = gps.altitudeMeters?.let { "Alt: %.0f m".format(it) } ?: "Alt: -- m"
                val acc = gps.accuracyMeters?.let { "Precisión: %.0f m".format(it) } ?: "Precisión: -- m"
                val sats = gps.satellitesUsed?.let { "Satélites: $it" } ?: "Satélites: --"
                dashGpsDetails.text = "$bearing · $alt · $acc · $sats"

                // Voltage
                val volts = oem.voltage ?: model.vehicle.battery.value.voltage?.takeIf { it > 8.0f }
                if (volts != null) {
                    dashVoltage.text = "%.1f".format(volts)
                    when {
                        volts < 11.5f -> {
                            dashVoltageStatus.text = "⚠️ Batería baja (%.1f V)".format(volts)
                            dashVoltageStatus.setTextColor(Color.parseColor("#FF354A"))
                        }
                        volts in 11.5f..12.8f -> {
                            dashVoltageStatus.text = "🔋 Batería en reposo · Motor apagado (%.1f V)".format(volts)
                            dashVoltageStatus.setTextColor(Color.parseColor("#4CAF50"))
                        }
                        volts in 12.9f..14.8f -> {
                            dashVoltageStatus.text = "⚡ Alternador cargando · Motor en marcha (%.1f V)".format(volts)
                            dashVoltageStatus.setTextColor(Color.parseColor("#2196F3"))
                        }
                        else -> {
                            dashVoltageStatus.text = "⚠️ Tensión alta (%.1f V)".format(volts)
                            dashVoltageStatus.setTextColor(Color.parseColor("#FF9800"))
                        }
                    }
                } else {
                    dashVoltage.text = "--.-"
                    dashVoltageStatus.text = "Esperando lectura MCU..."
                    dashVoltageStatus.setTextColor(Color.parseColor("#8E99A8"))
                }

                // Doors
                val doorDesc = model.oem.doorDescription() ?: "Puertas cerradas"
                dashDoorStatus.text = doorDesc
                val doorOpen = oem.doorMask != null && oem.doorMask != 0
                dashDoorStatus.setTextColor(if (doorOpen) Color.parseColor("#FF354A") else Color.parseColor("#4CAF50"))

                // Temperature
                val tempOEM = oem.temperatureC?.let { "%.0f °C".format(it) } ?: "sin lectura"
                val tempBat = model.vehicle.battery.value.tempC?.let { "%.1f °C".format(it) } ?: "--"
                dashTempDetails.text = "Exterior OEM: $tempOEM · Temperatura interna: $tempBat"
                dashQueryTempBtn.isEnabled = model.oem.canRequestAmbientTemperature

                // MCU CAN status
                val proto = oem.protocol?.let { "Modelo: ${it.model}, Tipo: ${it.type}" } ?: "Ford CAN-Bus"
                dashMcuStatus.text = "MCU TWUtil: ${oem.status} · Tramas: ${oem.frames} · $proto"
            }.collect { }
        }

        dialog.setOnDismissListener {
            job.cancel()
            sm?.unregisterListener(sensorListener)
        }
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
