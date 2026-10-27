package com.mustangos.launcher.vehicle

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VehicleTelemetry(val rpm: Int? = null, val coolantC: Float? = null, val voltage: Float? = null, val odometerKm: Long? = null)
data class BatteryFacts(val voltage: Float? = null, val percent: Int? = null, val tempC: Float? = null)
interface VehicleTelemetrySource { suspend fun read(): VehicleTelemetry }

class VehicleTelemetryManager(private val context: Context) {
    private val mutable = MutableStateFlow(VehicleTelemetry())
    val state = mutable.asStateFlow()
    private val batteryMutable = MutableStateFlow(BatteryFacts())
    val battery = batteryMutable.asStateFlow()
    val status = "Motor (RPM, refrigerante y kilometraje): sin interfaz verificada"

    init {
        poll()
    }

    fun poll() {
        try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val rawVoltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
            // Car batteries operate between 10V and 16V. Discard ~5V USB internal rails.
            val volts = when {
                rawVoltage in 9000..30000 -> rawVoltage / 1000f
                rawVoltage in 9..30 -> rawVoltage.toFloat()
                else -> null
            }
            val percent = if (level >= 0 && scale > 0) (level * 100) / scale else null
            val tempC = if (temp in -400..1200) temp / 10f else null
            batteryMutable.value = BatteryFacts(volts, percent, tempC)
        } catch (_: Exception) {}
    }

    fun nativeReport(): String = buildString {
        appendLine("LECTURAS NATIVAS ANDROID (sin OBD)")
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val rawVoltage = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val present = battery?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false) == true
        appendLine("Batería Android declarada presente: $present")
        appendLine("Voltaje publicado por BatteryManager: ${if (rawVoltage in 9000..30000) "${rawVoltage / 1000f} V" else "no disponible"}")
        appendLine("Origen eléctrico: Android; no se atribuye a la batería del coche ni a la MCU sin comprobarlo")
        val temperature = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        if (present && temperature in -400..1200) appendLine("Temperatura de batería Android: ${temperature / 10f} °C; no es refrigerante ni temperatura exterior")
        val audio = context.getSystemService(AudioManager::class.java)
        appendLine("Volumen multimedia Android: ${audio.getStreamVolume(AudioManager.STREAM_MUSIC)}/${audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}; el amplificador OEM puede usar otro nivel")
        val brightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        appendLine("Brillo Android: ${brightness.takeIf { it >= 0 } ?: "no publicado"}/255")
        val space = StatFs(Environment.getDataDirectory().path)
        appendLine("Almacenamiento libre: ${"%.1f".format(space.availableBytes / 1073741824.0)} GiB")
        val sensors = context.getSystemService(SensorManager::class.java).getSensorList(Sensor.TYPE_ALL).map { it.stringType }.distinct()
        appendLine("Sensores declarados por Android: ${sensors.joinToString().ifEmpty { "ninguno" }}")
        appendLine("No se convierten sensores del equipo en lecturas del automóvil.")
    }
}
