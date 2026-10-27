package com.mustangos.launcher.vehicle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.provider.Settings
import com.mustangos.core.OemEvent
import com.mustangos.core.OemEventParser
import com.mustangos.core.OemEventDiagnostics
import com.mustangos.core.OemProtocol
import com.mustangos.launcher.settings.LauncherSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.reflect.Modifier

interface OemSubscription {
    fun open(codes: ShortArray, handler: Handler): Boolean
    fun requestAmbientTemperature(): Int? = null
    fun pollMcu() = Unit
    fun close()
}

/** Public OEM lifecycle; queries the real voltage and ambient temperature used by the installed SystemUI. */
class TwReadOnlySubscription(private val loadClass: () -> Class<*> = { Class.forName("android.tw.john.TWUtil") }) : OemSubscription {
    private var instance: Any? = null
    private var type: Class<*>? = null
    override fun open(codes: ShortArray, handler: Handler): Boolean {
        check(instance == null)
        val found = loadClass()
        val factory = found.getMethod("open", Int::class.javaPrimitiveType, ShortArray::class.java, Int::class.javaPrimitiveType)
        check(Modifier.isStatic(factory.modifiers))
        val add = found.getMethod("addHandler", String::class.java, Handler::class.java)
        found.getMethod("removeHandler", String::class.java)
        found.getMethod("stop"); found.getMethod("close")
        type = found
        instance = factory.invoke(null, 0, codes, 0) ?: return false
        try { add.invoke(instance, HANDLER_KEY, handler) }
        catch (failure: Exception) { close(); throw failure }
        // Crucial: Start the native loop if start() method exists
        runCatching { found.getMethod("start").invoke(instance) }
        // Send initial telemetry requests on real TWUtil hardware
        if (found.name.contains("TWUtil", ignoreCase = true)) {
            runCatching {
                val write = found.getMethod("write", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                write.invoke(instance, 1292, 255) // Query voltage (code 1292)
                write.invoke(instance, 1288, 255) // Query ambient temp (code 1288)
                write.invoke(instance, 258, 255)  // Query MCU status
                write.invoke(instance, 516, 255)  // Query MCU power
            }
        }
        return true
    }
    override fun requestAmbientTemperature(): Int? {
        val value = instance ?: return null
        // StatusBarViewInit uses precisely this request to obtain the existing ambient-temperature value.
        return type?.getMethod("write", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            ?.invoke(value, 1288, 255) as? Int
    }
    override fun pollMcu() {
        val value = instance ?: return
        if (type?.name?.contains("TWUtil", ignoreCase = true) == true) {
            runCatching {
                val write = type?.getMethod("write", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                write?.invoke(value, 1292, 255)
            }
        }
    }
    override fun close() {
        val value = instance ?: return
        instance = null
        type?.let { klass ->
            runCatching { klass.getMethod("removeHandler", String::class.java).invoke(value, HANDLER_KEY) }
            runCatching { klass.getMethod("stop").invoke(value) }
            runCatching { klass.getMethod("close").invoke(value) }
        }
        type = null
    }
    companion object { private const val HANDLER_KEY = "MustangOS.ReadOnly" }
}

data class OemReadings(
    val status: String = "Lectura OEM desactivada",
    val voltage: Float? = null,
    val temperatureC: Float? = null,
    val doorMask: Int? = null,
    val frames: Int = 0,
    val lastEventMs: Long = 0,
    val protocol: OemProtocol? = null,
    val eventReport: String = "",
    val temperatureRequestStatus: String = "Consulta manual de temperatura no solicitada",
    val doorOpenEvents: Int = 0,
    val doorZeroEvents: Int = 0
)

class OemReadOnlyManager(
    private val context: Context,
    private val settings: LauncherSettings,
    private val createSubscription: () -> OemSubscription = { TwReadOnlySubscription() }
) {
    private var subscription: OemSubscription? = null
    private var subscriptionHandler: Handler? = null
    private var running = false
    private var temperatureRequested = false
    private var temperatureReceivedAfterRequest = false
    val canRequestAmbientTemperature: Boolean get() = running && !temperatureRequested
    private val diagnostics = OemEventDiagnostics()
    private val mutable = MutableStateFlow(OemReadings())
    val state = mutable.asStateFlow()
    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (!running || msg.what !in OemEventDiagnostics.CODES) return
            val payload = msg.obj as? ByteArray
            val event = OemEventParser.parseProtocol(msg.what, msg.arg1, msg.obj as? String)
                ?: OemEventParser.parse(msg.what, msg.arg1, payload,
                    Settings.System.getInt(context.contentResolver, "TEMP_CONVERT", 0) == 1, mutable.value.protocol)
            diagnostics.record(msg.what, msg.arg1, msg.arg2, payload?.size, event != null)
            val current = mutable.value.copy(status = "OEM · recepción real", frames = mutable.value.frames + 1,
                lastEventMs = SystemClock.elapsedRealtime(), eventReport = diagnostics.report())
            if (event is OemEvent.Temperature && temperatureRequested) temperatureReceivedAfterRequest = true
            mutable.value = when (event) {
                is OemEvent.Voltage -> current.copy(voltage = event.volts)
                is OemEvent.Temperature -> current.copy(temperatureC = event.celsius,
                    temperatureRequestStatus = if (temperatureRequested) "Temperatura recibida durante la escucha" else current.temperatureRequestStatus)
                is OemEvent.Doors -> current.copy(doorMask = event.mask,
                    doorOpenEvents = current.doorOpenEvents + if (event.mask != 0) 1 else 0,
                    doorZeroEvents = current.doorZeroEvents + if (event.mask == 0) 1 else 0)
                is OemEvent.Protocol -> current.copy(protocol = event.profile,
                    doorMask = if (current.protocol == event.profile) current.doorMask else null)
                OemEvent.Heartbeat, null -> current
            }
        }
    }
    private val expiry = object : Runnable {
        override fun run() {
            if (!running) return
            subscription?.pollMcu()
            val current = mutable.value
            val sysDoor = runCatching { Settings.System.getInt(context.contentResolver, "SYSTEM_DOOR", 0) }.getOrDefault(0)
            if (sysDoor != 0 && current.doorMask == null) {
                mutable.value = current.copy(doorMask = sysDoor)
            }
            if (current.lastEventMs > 0 && SystemClock.elapsedRealtime() - current.lastEventMs > 15_000 &&
                (current.voltage != null || current.temperatureC != null || current.doorMask != null)) {
                mutable.value = current.copy(status = "OEM · datos caducados, esperando eventos", voltage = null, temperatureC = null, doorMask = null)
            }
            handler.postDelayed(this, 1000)
        }
    }
    fun start() {
        if (running) return
        if (!settings.oemReadEnabled) { mutable.value = OemReadings(); return }
        if (settings.oemInterrupted) {
            settings.oemReadEnabled = false; settings.oemInterrupted = false
            mutable.value = OemReadings(status = "Lectura OEM desactivada tras una interrupción; puede reactivarse manualmente")
            return
        }
        settings.oemInterrupted = true
        try {
            val candidate = createSubscription()
            subscription = candidate
            temperatureRequested = false
            temperatureReceivedAfterRequest = false
            diagnostics.clear()
            mutable.value = OemReadings(status = "OEM suscrito · esperando datos reales", eventReport = diagnostics.report())
            running = true
            val ownedHandler = object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) {
                    if (running && subscriptionHandler === this) handler.handleMessage(msg)
                }
            }
            subscriptionHandler = ownedHandler
            if (!candidate.open(OemEventDiagnostics.CODES.map(Int::toShort).toShortArray(), ownedHandler)) {
                stop(); mutable.value = OemReadings(status = "El firmware rechazó la suscripción de lectura OEM"); return
            }
            handler.postDelayed(expiry, 1000)
        } catch (failure: Exception) {
            val cause = (failure as? java.lang.reflect.InvocationTargetException)?.cause ?: failure
            stop(); mutable.value = OemReadings(status = "Interfaz OEM no permitida o incompatible (${cause.javaClass.simpleName}); sin elevar permisos")
        } catch (_: LinkageError) {
            stop(); mutable.value = OemReadings(status = "Biblioteca OEM no disponible para esta APK")
        }
    }
    fun stop() {
        running = false
        temperatureRequested = false
        temperatureReceivedAfterRequest = false
        subscriptionHandler?.removeCallbacksAndMessages(null)
        subscriptionHandler = null
        handler.removeCallbacksAndMessages(null)
        val owned = subscription
        subscription = null
        diagnostics.clear()
        owned?.let { runCatching { it.close() }; settings.oemInterrupted = false }
        mutable.value = OemReadings(status = if (settings.oemReadEnabled) "Lectura OEM detenida" else "Lectura OEM desactivada")
    }
    fun setEnabled(enabled: Boolean) {
        stop(); settings.oemReadEnabled = enabled; settings.oemInterrupted = false
        if (enabled) start()
    }
    fun requestAmbientTemperature() {
        if (!canRequestAmbientTemperature) return
        temperatureRequested = true
        temperatureReceivedAfterRequest = false
        try {
            val result = subscription?.requestAmbientTemperature()
            mutable.value = mutable.value.copy(temperatureRequestStatus = when {
                temperatureReceivedAfterRequest -> "Temperatura recibida durante la escucha"
                result == null -> "Consulta de temperatura no disponible en esta interfaz"
                else -> "Temperatura solicitada; esperando evento (retorno OEM $result)"
            })
            if (result != null) handler.postDelayed({
                if (running && !temperatureReceivedAfterRequest) mutable.value = mutable.value.copy(
                    temperatureRequestStatus = "Consulta de temperatura sin respuesta en 5 s (retorno OEM $result)")
            }, 5000)
        } catch (failure: Exception) {
            val cause = (failure as? java.lang.reflect.InvocationTargetException)?.cause ?: failure
            mutable.value = mutable.value.copy(temperatureRequestStatus = "Consulta de temperatura rechazada (${cause.javaClass.simpleName})")
        }
    }
    fun doorDescription(): String? {
        val mask = mutable.value.doorMask ?: return null
        if (mask == 0) return "Puertas cerradas"
        val open = mutableListOf<String>()
        if (mask and 0x80 != 0 || mask and 0x01 != 0) open.add("Conductor")
        if (mask and 0x40 != 0 || mask and 0x02 != 0) open.add("Copiloto")
        if (mask and 0x20 != 0 || mask and 0x04 != 0) open.add("Trasera Izq")
        if (mask and 0x10 != 0 || mask and 0x08 != 0) open.add("Trasera Der")
        if (mask and 0x08 != 0) open.add("Cajuela")
        return if (open.isEmpty()) "Puerta abierta" else "Abierta: " + open.distinct().joinToString(", ")
    }

    fun summary(): String {
        val s = mutable.value
        val fields = listOfNotNull(
            s.voltage?.let { "%.1f V".format(it) },
            s.temperatureC?.let { "%.1f °C".format(it) },
            doorDescription()
        )
        return fields.joinToString(" · ").ifEmpty { s.status }
    }
}
