package com.mustangos.launcher.vehicle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.provider.Settings
import com.mustangos.core.OemEvent
import com.mustangos.core.OemEventParser
import com.mustangos.launcher.settings.LauncherSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.reflect.Modifier

interface OemSubscription {
    fun open(codes: ShortArray, handler: Handler): Boolean
    fun close()
}

/** Only the public subscription lifecycle observed in the installed SystemUI. No write method is resolved. */
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
        return true
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
    val lastEventMs: Long = 0
)

class OemReadOnlyManager(
    private val context: Context,
    private val settings: LauncherSettings,
    private val createSubscription: () -> OemSubscription = { TwReadOnlySubscription() }
) {
    private var subscription: OemSubscription? = null
    private var running = false
    private val mutable = MutableStateFlow(OemReadings())
    val state = mutable.asStateFlow()
    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (!running) return
            val event = OemEventParser.parse(msg.what, msg.arg1, msg.obj as? ByteArray,
                Settings.System.getInt(context.contentResolver, "TEMP_CONVERT", 0) == 1) ?: return
            val current = mutable.value.copy(status = "OEM · recepción real", frames = mutable.value.frames + 1, lastEventMs = SystemClock.elapsedRealtime())
            mutable.value = when (event) {
                is OemEvent.Voltage -> current.copy(voltage = event.volts)
                is OemEvent.Temperature -> current.copy(temperatureC = event.celsius)
                is OemEvent.Doors -> current.copy(doorMask = event.mask)
                OemEvent.Heartbeat -> current
            }
        }
    }
    private val expiry = object : Runnable {
        override fun run() {
            if (!running) return
            val current = mutable.value
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
            running = true
            if (!candidate.open(shortArrayOf(514, 1285, 1288, 1292), handler)) {
                stop(); mutable.value = OemReadings(status = "El firmware rechazó la suscripción de lectura OEM"); return
            }
            mutable.value = OemReadings(status = "OEM suscrito · esperando datos reales")
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
        handler.removeCallbacksAndMessages(null)
        val owned = subscription
        subscription = null
        owned?.let { runCatching { it.close() }; settings.oemInterrupted = false }
        mutable.value = OemReadings(status = if (settings.oemReadEnabled) "Lectura OEM detenida" else "Lectura OEM desactivada")
    }
    fun setEnabled(enabled: Boolean) {
        stop(); settings.oemReadEnabled = enabled; settings.oemInterrupted = false
        if (enabled) start()
    }
    fun summary(): String {
        val s = mutable.value
        val fields = listOfNotNull(s.voltage?.let { "%.1f V".format(it) }, s.temperatureC?.let { "%.1f °C".format(it) }, s.doorMask?.let { if (it == 0) "Puertas: sin apertura publicada" else "Apertura OEM detectada" })
        return fields.joinToString(" · ").ifEmpty { s.status }
    }
}
