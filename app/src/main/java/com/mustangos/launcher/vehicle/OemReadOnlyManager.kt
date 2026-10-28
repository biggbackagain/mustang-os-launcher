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
    fun requestMcuProtocol(): Int? = null
    fun requestIllumination(): Int? = null
    fun requestAmbientTemperature(): Int? = null
    fun pollMcu() = Unit
    fun close()
}

/** Uses the factory already verified on this unit and fixed OEM read requests only. */
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
        // The static factory starts its own client; calling start() again is not part of this route.
        return true
    }
    override fun requestMcuProtocol(): Int? {
        val value = instance ?: return null
        // SystemUI StatusBarViewInit and TWCore request the existing MCU version with this fixed read.
        return type?.getMethod("write", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            ?.invoke(value, 266, 255) as? Int
    }
    override fun requestIllumination(): Int? {
        val value = instance ?: return null
        // Fixed read-only query demonstrated in the supplied SystemUI StatusBar; never write brightness.
        return type?.getMethod("write", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)?.invoke(value, 516, 255) as? Int
    }
    override fun requestAmbientTemperature(): Int? {
        val value = instance ?: return null
        // StatusBarViewInit uses precisely this request to obtain the existing ambient-temperature value.
        return type?.getMethod("write", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            ?.invoke(value, 1288, 255) as? Int
    }
    override fun pollMcu() {
        val value = instance ?: return
        type?.getMethod("write", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            ?.invoke(value, 1292, 255)
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
    val doorZeroEvents: Int = 0,
    val sourceActive: Boolean = false,
    val stale: Boolean = false,
    val lastVoltageMs: Long? = null,
    val lastTemperatureMs: Long? = null,
    val lastDoorMs: Long? = null,
    val ageSummary: String = "Sin lecturas OEM recibidas",
    val voltageRequestStatus: String = "Voltaje OEM sin solicitar",
    val temperatureQueryAvailable: Boolean = false,
    val speedKmh: Float? = null, val rpm: Int? = null, val lastPowertrainMs: Long? = null,
    val illuminationOn: Boolean? = null, val lastIlluminationMs: Long? = null
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
    private var lastTemperatureQueryMs: Long? = null
    private var nextVoltageQueryMs = Long.MAX_VALUE
    val canRequestAmbientTemperature: Boolean get() = running &&
        lastTemperatureQueryMs?.let { SystemClock.elapsedRealtime() - it >= TEMPERATURE_QUERY_INTERVAL_MS } != false
    private val diagnostics = OemEventDiagnostics()
    private val mutable = MutableStateFlow(OemReadings())
    val state = mutable.asStateFlow()
    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (!running || msg.what !in OemEventDiagnostics.CODES) return
            val payload = msg.obj as? ByteArray
            val event = OemEventParser.parseProtocol(msg.what, msg.arg1, msg.obj as? String)
                ?: OemEventParser.parsePowertrain(msg.what, msg.arg1, msg.arg2)
                ?: OemEventParser.parse(msg.what, msg.arg1, payload,
                    Settings.System.getInt(context.contentResolver, "TEMP_CONVERT", 0) == 1, mutable.value.protocol)
            diagnostics.record(msg.what, msg.arg1, msg.arg2, payload?.size, event != null)
            val now = SystemClock.elapsedRealtime()
            val current = mutable.value.copy(status = "OEM · recepción real", frames = mutable.value.frames + 1,
                lastEventMs = now, eventReport = diagnostics.report())
            if (event is OemEvent.Temperature && temperatureRequested) temperatureReceivedAfterRequest = true
            publish(when (event) {
                is OemEvent.Voltage -> current.copy(voltage = event.volts, lastVoltageMs = now,
                    voltageRequestStatus = "Voltaje OEM recibido")
                is OemEvent.Temperature -> current.copy(temperatureC = event.celsius,
                    lastTemperatureMs = now,
                    temperatureRequestStatus = if (temperatureRequested) "Temperatura recibida durante la escucha" else current.temperatureRequestStatus)
                is OemEvent.Doors -> current.copy(doorMask = event.mask,
                    lastDoorMs = now,
                    doorOpenEvents = current.doorOpenEvents + if (event.mask != 0) 1 else 0,
                    doorZeroEvents = current.doorZeroEvents + if (event.mask == 0) 1 else 0)
                is OemEvent.Protocol -> current.copy(protocol = event.profile,
                    doorMask = if (current.protocol == event.profile) current.doorMask else null,
                    lastDoorMs = if (current.protocol == event.profile) current.lastDoorMs else null)
                is OemEvent.Powertrain -> current.copy(speedKmh = event.speedKmh, rpm = event.rpm, lastPowertrainMs = now)
                is OemEvent.Illumination -> current.copy(illuminationOn = event.on, lastIlluminationMs = now)
                OemEvent.Heartbeat, null -> current
            })
        }
    }
    private val expiry = object : Runnable {
        override fun run() {
            if (!running) return
            if (SystemClock.elapsedRealtime() >= nextVoltageQueryMs) pollVoltage()
            publish(mutable.value)
            handler.postDelayed(this, 1000)
        }
    }
    fun start() {
        if (running) return
        if (!settings.oemReadEnabled) { publish(mutable.value.copy(status = "Lectura OEM desactivada", sourceActive = false)); return }
        if (settings.oemInterrupted) {
            settings.oemReadEnabled = false; settings.oemInterrupted = false
            publish(mutable.value.copy(status = "Lectura OEM desactivada tras una interrupción; puede reactivarse manualmente", sourceActive = false))
            return
        }
        settings.oemInterrupted = true
        try {
            val candidate = createSubscription()
            subscription = candidate
            temperatureRequested = false
            temperatureReceivedAfterRequest = false
            diagnostics.clear()
            running = true
            publish(mutable.value.copy(status = "OEM suscrito · esperando datos reales", sourceActive = true,
                frames = 0, protocol = null, eventReport = diagnostics.report(), doorOpenEvents = 0, doorZeroEvents = 0,
                illuminationOn = null, lastIlluminationMs = null))
            val ownedHandler = object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) {
                    if (running && subscriptionHandler === this) handler.handleMessage(msg)
                }
            }
            subscriptionHandler = ownedHandler
            if (!candidate.open(OemEventDiagnostics.CODES.map(Int::toShort).toShortArray(), ownedHandler)) {
                stop(); publish(mutable.value.copy(status = "El firmware rechazó la suscripción de lectura OEM")); return
            }
            // Optional initial read only. A rejected version request must leave the other channels listening.
            try { candidate.requestMcuProtocol() }
            catch (_: Exception) { /* Protocol remains unknown until an actual event arrives. */ }
            catch (_: LinkageError) { /* This optional request is unsupported by the vendor adapter. */ }
            try { candidate.requestIllumination() }
            catch (_: Exception) { /* Unknown until TW 516 arrives; never substitute ACC. */ }
            catch (_: LinkageError) { /* Optional fixed read is unavailable on this firmware. */ }
            pollVoltage()
            requestAmbientTemperature()
            // Guard only initialization. A normal power cut after success must not disable the next ignition.
            settings.oemInterrupted = false
            handler.postDelayed(expiry, 1000)
        } catch (failure: Exception) {
            val cause = (failure as? java.lang.reflect.InvocationTargetException)?.cause ?: failure
            stop(); publish(mutable.value.copy(status = "Interfaz OEM no permitida o incompatible (${cause.javaClass.simpleName}); sin elevar permisos"))
        } catch (_: LinkageError) {
            stop(); publish(mutable.value.copy(status = "Biblioteca OEM no disponible para esta APK"))
        }
    }
    fun stop() {
        running = false
        temperatureRequested = false
        temperatureReceivedAfterRequest = false
        nextVoltageQueryMs = Long.MAX_VALUE
        subscriptionHandler?.removeCallbacksAndMessages(null)
        subscriptionHandler = null
        handler.removeCallbacksAndMessages(null)
        val owned = subscription
        subscription = null
        diagnostics.clear()
        owned?.let { runCatching { it.close() }; settings.oemInterrupted = false }
        publish(mutable.value.copy(status = if (settings.oemReadEnabled) "Lectura OEM detenida · últimas lecturas" else "Lectura OEM desactivada · últimas lecturas", sourceActive = false))
    }
    fun setEnabled(enabled: Boolean) {
        stop(); settings.oemReadEnabled = enabled; settings.oemInterrupted = false
        if (enabled) start()
    }
    fun requestAmbientTemperature() {
        if (!canRequestAmbientTemperature) return
        temperatureRequested = true
        temperatureReceivedAfterRequest = false
        lastTemperatureQueryMs = SystemClock.elapsedRealtime()
        try {
            val result = subscription?.requestAmbientTemperature()
            publish(mutable.value.copy(temperatureRequestStatus = when {
                temperatureReceivedAfterRequest -> "Temperatura recibida durante la escucha"
                result == null -> "Consulta de temperatura no disponible en esta interfaz"
                else -> "Temperatura solicitada; esperando evento (retorno OEM $result)"
            }))
            if (result != null) handler.postDelayed({
                if (running && !temperatureReceivedAfterRequest) publish(mutable.value.copy(
                    temperatureRequestStatus = "Consulta de temperatura sin respuesta en 5 s (retorno OEM $result)"))
            }, 5000)
        } catch (failure: Exception) {
            val cause = (failure as? java.lang.reflect.InvocationTargetException)?.cause ?: failure
            publish(mutable.value.copy(temperatureRequestStatus = "Consulta de temperatura rechazada (${cause.javaClass.simpleName})"))
        }
    }
    fun doorDescription(): String? {
        val mask = mutable.value.doorMask ?: return null
        return if (mask == 0) "Sin aperturas publicadas" else "Apertura OEM detectada"
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

    private fun pollVoltage() {
        if (!running) return
        nextVoltageQueryMs = SystemClock.elapsedRealtime() + VOLTAGE_QUERY_INTERVAL_MS
        publish(mutable.value.copy(voltageRequestStatus = "Lectura de voltaje OEM solicitada"))
        try { subscription?.pollMcu() }
        catch (failure: Exception) {
            val cause = (failure as? java.lang.reflect.InvocationTargetException)?.cause ?: failure
            nextVoltageQueryMs = SystemClock.elapsedRealtime() + 30_000
            publish(mutable.value.copy(voltageRequestStatus = "Consulta de voltaje no disponible (${cause.javaClass.simpleName}); escucha conservada"))
        }
    }

    private fun publish(value: OemReadings) {
        val now = SystemClock.elapsedRealtime()
        val ages = listOfNotNull(value.lastVoltageMs?.let { "voltaje" to it },
            value.lastTemperatureMs?.let { "temperatura" to it }, value.lastDoorMs?.let { "aperturas" to it })
        val stale = ages.isNotEmpty() && (!value.sourceActive || ages.any { now - it.second > 15_000 })
        val ageText = ages.joinToString(" · ") { (label, time) -> "$label hace ${((now - time).coerceAtLeast(0) / 1000)} s" }
        val engineFresh = value.sourceActive && value.lastPowertrainMs?.let { now - it <= 5_000 } == true
        mutable.value = value.copy(stale = stale, speedKmh = value.speedKmh.takeIf { engineFresh }, rpm = value.rpm.takeIf { engineFresh },
            status = if (stale && value.sourceActive) "OEM · últimas lecturas conservadas; esperando actualización" else value.status,
            ageSummary = if (ageText.isEmpty()) "Sin lecturas OEM recibidas" else "Últimas lecturas · $ageText",
            temperatureQueryAvailable = canRequestAmbientTemperature)
    }

    companion object {
        private const val VOLTAGE_QUERY_INTERVAL_MS = 5_000L
        private const val TEMPERATURE_QUERY_INTERVAL_MS = 30_000L
    }
}
