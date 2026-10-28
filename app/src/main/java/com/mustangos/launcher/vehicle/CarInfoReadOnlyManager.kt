package com.mustangos.launcher.vehicle

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import com.mustangos.launcher.settings.LauncherSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** A service-cache observation. queriedAtMs is NOT the time of a physical vehicle measurement. */
data class CarInfoCachedValue<T>(val value: T, val unit: String, val queriedAtMs: Long, val available: Boolean = true)
data class CarInfoReadings(
    val status: String = "Lectura CarInfoService desactivada",
    val sourceActive: Boolean = false,
    val voltage: CarInfoCachedValue<Float>? = null,
    val odometerKm: CarInfoCachedValue<Double>? = null,
    val vehicleTemperatureC: CarInfoCachedValue<Float>? = null,
    val illuminationSignal: CarInfoCachedValue<Boolean>? = null,
    val lastQueryMs: Long? = null
) {
    val cacheNotice: String get() = "Caché del servicio OEM; la fuente no publica la fecha de medición física"
}

internal data class CarInfoSnapshot(val voltage: Float?, val odometerKm: Double?, val temperatureC: Float?,
    val illuminationSignal: Boolean?, val failures: Int)

/** Contract verified from the user's CarInfoService 251014 server DEX, c.b.a.b.onTransact. */
internal object CarInfoReadContract {
    const val DESCRIPTOR = "com.tw.carinfoservice.CarServiceAidl"
    // Deliberately omit callback register/unregister and getters 18/19: some of those write MCU command 1291.
    private val READS = setOf(4, 5, 7, 20)
    fun read(binder: IBinder, cancelled: () -> Boolean = { false }): CarInfoSnapshot {
        check(binder.interfaceDescriptor == DESCRIPTOR) { "Unexpected OEM descriptor" }
        var failures = 0
        fun <T> query(code: Int, decode: (Parcel) -> T): T? {
            check(code in READS)
            if (cancelled()) return null
            val input = Parcel.obtain(); val output = Parcel.obtain()
            return try {
                input.writeInterfaceToken(DESCRIPTOR)
                if (!binder.transact(code, input, output, 0)) { failures++; null }
                else { output.readException(); decode(output) }
            } catch (_: Exception) { failures++; null }
            finally { input.recycle(); output.recycle() }
        }
        val voltage = query(4, Parcel::readFloat)?.takeIf { it.isFinite() && it in 3f..32f }
        val illumination = query(5, Parcel::readInt)?.let { when (it) { 0 -> false; 1 -> true; else -> null } }
        val mileage = query(7) { reply ->
            // Known ABI is three elements. Never allocate an array using an unchecked remote length.
            require(reply.readInt() == 3 && reply.dataAvail() == 12)
            intArrayOf(reply.readInt(), reply.readInt(), reply.readInt())
        }?.let {
            // Zero can be this server's uninitialized default; it is not established as an odometer measurement.
            if (it[0] !in 1..20_000_000 || it[1] !in 0..1) null
            else it[0].toDouble() * if (it[1] == 1) 1.609344 else 1.0
        }
        val temperature = query(20) { reply ->
            require(reply.readInt() == 3 && reply.dataAvail() == 12)
            floatArrayOf(reply.readFloat(), reply.readFloat(), reply.readFloat())
        }?.let {
            val raw = it[0]
            // Zero/-1 are indistinguishable from server defaults/unavailable; no synthetic zero-temperature reading.
            if (!raw.isFinite() || raw == -1f || raw == 0f || it[2] !in listOf(0f, 1f)) null
            else (if (it[2] == 1f) (raw - 32f) / 1.8f else raw).takeIf { value -> value in -40f..150f }
        }
        return CarInfoSnapshot(voltage, mileage, temperature, illumination, failures)
    }
}

/** Binds only an already running, declared exported service and reads four validated cache getters. */
class CarInfoReadOnlyManager(private val context: Context, private val settings: LauncherSettings) {
    private val main = Handler(Looper.getMainLooper())
    private val mutable = MutableStateFlow(CarInfoReadings())
    val state = mutable.asStateFlow()
    @Volatile private var running = false
    @Volatile private var generation = 0L
    private var connection: ServiceConnection? = null
    private var bound = false
    private var remote: IBinder? = null
    private var work: Future<*>? = null
    private val nextRead = Runnable { query() }
    private val timeout = Runnable {
        if (running) {
            release()
            mutable.value = mutable.value.copy(status = "CarInfoService no respondió; últimas consultas conservadas", sourceActive = false)
        }
    }

    fun start() {
        if (running) return
        if (!settings.oemReadEnabled) {
            mutable.value = mutable.value.copy(status = "Lectura CarInfoService desactivada", sourceActive = false)
            return
        }
        if (!declaredPublicService()) return
        running = true
        val stamp = ++generation
        val owned = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (!running || generation != stamp || connection !== this) return
                if (name != COMPONENT) { failConnection("CarInfoService devolvió otro componente"); return }
                remote = service
                query()
            }
            override fun onServiceDisconnected(name: ComponentName) { if (connection === this) failConnection("CarInfoService desconectado; últimas consultas conservadas") }
            override fun onNullBinding(name: ComponentName) { if (connection === this) failConnection("CarInfoService no publicó una interfaz de lectura") }
            override fun onBindingDied(name: ComponentName) { if (connection === this) failConnection("La conexión CarInfoService terminó") }
        }
        connection = owned
        mutable.value = mutable.value.copy(status = "Consultando CarInfoService ya activo…", sourceActive = false)
        try {
            // No BIND_AUTO_CREATE, startService, callback registration, control commands or configuration changes.
            bound = context.bindService(Intent(ACTION).setComponent(COMPONENT), owned, 0)
            if (!bound) { failConnection("CarInfoService no está activo o no permite la conexión"); return }
            main.postDelayed(timeout, 5_000)
        } catch (_: RuntimeException) { failConnection("Android rechazó la lectura CarInfoService") }
    }
    fun stop() {
        release()
        mutable.value = mutable.value.copy(status = if (settings.oemReadEnabled) "CarInfoService detenido · últimas consultas" else "Lectura CarInfoService desactivada", sourceActive = false)
    }
    private fun failConnection(status: String) {
        release(); mutable.value = mutable.value.copy(status = status, sourceActive = false)
    }
    private fun release() {
        running = false; generation++
        main.removeCallbacks(nextRead); main.removeCallbacks(timeout)
        work?.cancel(true); work = null; remote = null
        val owned = connection; connection = null
        if (bound && owned != null) runCatching { context.unbindService(owned) }
        bound = false
        workers.purge()
    }
    private fun declaredPublicService(): Boolean {
        try {
            val info = context.packageManager.getServiceInfo(COMPONENT, 0)
            if (!info.exported || !info.enabled || !info.applicationInfo.enabled) {
                mutable.value = mutable.value.copy(status = "CarInfoService no declara una lectura pública habilitada", sourceActive = false)
                return false
            }
            info.permission?.let { permission ->
                val declaration = context.packageManager.getPermissionInfo(permission, 0)
                if (permission != PERMISSION || declaration.packageName != COMPONENT.packageName ||
                    declaration.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE != PermissionInfo.PROTECTION_NORMAL ||
                    context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    mutable.value = mutable.value.copy(status = "CarInfoService requiere un permiso no disponible; no se intenta acceder", sourceActive = false)
                    return false
                }
            }
            return true
        } catch (_: PackageManager.NameNotFoundException) {
            mutable.value = mutable.value.copy(status = "CarInfoService no está instalado o visible", sourceActive = false)
        } catch (_: RuntimeException) {
            mutable.value = mutable.value.copy(status = "No se permitió inspeccionar CarInfoService", sourceActive = false)
        }
        return false
    }
    private fun query() {
        if (!running) return
        if (!settings.oemReadEnabled) { stop(); return }
        val binder = remote ?: return
        if (work?.isDone == false) return
        main.removeCallbacks(nextRead); main.removeCallbacks(timeout)
        val stamp = generation
        main.postDelayed(timeout, 5_000)
        try {
            work = workers.submit {
                val result = runCatching { CarInfoReadContract.read(binder) { !running || generation != stamp } }
                main.post {
                    if (!running || generation != stamp || remote !== binder) return@post
                    work = null; main.removeCallbacks(timeout)
                    val snapshot = result.getOrNull()
                    if (snapshot == null) { failConnection("La interfaz CarInfoService difiere del contrato verificado"); return@post }
                    val now = SystemClock.elapsedRealtime()
                    fun <T> retain(value: T?, previous: CarInfoCachedValue<T>?, unit: String): CarInfoCachedValue<T>? =
                        value?.let { CarInfoCachedValue(it, unit, now) } ?: previous?.copy(available = false)
                    val previous = mutable.value
                    mutable.value = previous.copy(sourceActive = true, lastQueryMs = now,
                        status = if (snapshot.failures > 0) "CarInfoService · algunas consultas no disponibles" else "CarInfoService · valores de caché OEM",
                        voltage = retain(snapshot.voltage, previous.voltage, "V"),
                        odometerKm = retain(snapshot.odometerKm, previous.odometerKm, "km"),
                        vehicleTemperatureC = retain(snapshot.temperatureC, previous.vehicleTemperatureC, "°C"),
                        illuminationSignal = retain(snapshot.illuminationSignal, previous.illuminationSignal, "señal OEM"))
                    main.postDelayed(nextRead, 5_000)
                }
            }
        } catch (_: RejectedExecutionException) { failConnection("Lectura CarInfoService ocupada; inténtalo al volver a la pantalla") }
    }
    fun report(): String = buildString {
        val s = state.value
        appendLine("CARINFOSERVICE · ${s.status}; conexión local activa=${s.sourceActive}")
        appendLine(s.cacheNotice)
        fun <T> field(label: String, item: CarInfoCachedValue<T>?) {
            if (item == null) appendLine("$label: sin valor verificable")
            else appendLine("$label: ${item.value} ${item.unit}; disponible=${item.available}; consultado_hace_ms=${(SystemClock.elapsedRealtime() - item.queriedAtMs).coerceAtLeast(0)}; edad de medición desconocida")
        }
        field("Voltaje del servicio OEM", s.voltage); field("Odómetro de caché OEM", s.odometerKm)
        field("Temperatura OEM (sensor no identificado)", s.vehicleTemperatureC)
        field("Iluminación OEM (no confirma faros físicos)", s.illuminationSignal)
    }
    companion object {
        const val ACTION = "com.tw.carinfoservice.CarService.Bind"
        const val PERMISSION = "com.tw.carinfoservice.permission.USE_AIDL"
        val COMPONENT = ComponentName("com.tw.carinfoservice", "com.tw.carinfoservice.CarService")
        private val workers by lazy { ThreadPoolExecutor(1, 1, 20, TimeUnit.SECONDS, ArrayBlockingQueue(1), { task ->
            Thread(task, "mustang-carinfo-read").apply { isDaemon = true }
        }).apply { allowCoreThreadTimeOut(true) } }
    }
}
