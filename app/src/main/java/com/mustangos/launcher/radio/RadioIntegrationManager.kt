package com.mustangos.launcher.radio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class RadioIntegrationManager(
    context: Context,
    private val subscription: RadioSubscription = TwRadioSubscription(),
    private val controls: RadioControls = TwRadioControls(context),
    private val now: () -> Long = SystemClock::elapsedRealtime
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val worker = Dispatchers.IO.limitedParallelism(1)
    private val mutable = MutableStateFlow(RadioState())
    val state = mutable.asStateFlow()
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0L
    private var active = false
    private var handler: Handler? = null
    private var pollJob: Job? = null
    private val poll = object : Runnable {
        override fun run() {
            if (!active) return
            main.removeCallbacks(this)
            val stamp = generation
            mutable.update { it.copy(sourceFresh = it.sourceAtMs?.let { time -> now() - time in 0..15_000 } == true) }
            if (pollJob?.isActive != true) pollJob = scope.launch {
                val ok = withContext(worker) { runCatching { subscription.requestSnapshot() }.isSuccess }
                if (active && stamp == generation && !ok) mutable.update { it.copy(status = "Sin respuesta de radio", sourceFresh = false) }
            }
            main.postDelayed(this, 5_000)
        }
    }
    fun start() {
        if (active) return
        active = true
        val stamp = ++generation
        mutable.update { it.copy(status = "Consultando la radio del equipo", reading = false, sourceFresh = false, controlsAvailable = false, commandPending = false, commandStatus = null) }
        val receiver = Handler(Looper.getMainLooper()) { event ->
            if (active && stamp == generation) mutable.update { RadioEvents.reduce(it, event, now()) }
            true
        }
        handler = receiver
        controls.start { ready -> if (active && stamp == generation) mutable.update { it.copy(controlsAvailable = ready) } }
        scope.launch {
            val opened = withContext(worker) { runCatching { subscription.open(receiver) }.getOrDefault(false) }
            if (!active || stamp != generation) return@launch
            mutable.update { it.copy(reading = opened, status = if (opened) "Lecturas de radio del equipo" else "La radio no publica una lectura compatible") }
            if (opened) poll.run()
        }
    }
    fun stop() {
        if (!active) return
        active = false; generation++
        main.removeCallbacks(poll); handler?.removeCallbacksAndMessages(null); handler = null
        controls.stop()
        mutable.update { it.copy(reading = false, status = "Lectura de radio detenida", sourceFresh = false, controlsAvailable = false, commandPending = false, commandStatus = null) }
        // Serialize close after any in-progress open/read, before a subsequent start.
        scope.launch { withContext(worker) { runCatching { subscription.close() } } }
    }
    fun submit(action: RadioAction): Boolean {
        if (!active || !mutable.value.canControl || mutable.value.sourceAtMs?.let { now() - it in 0..15_000 } != true) return false
        val stamp = generation
        mutable.update { it.copy(commandPending = true, commandStatus = "Enviando solicitud a la radio") }
        scope.launch {
            val sent = withContext(worker) {
                val current = mutable.value
                if (!active || stamp != generation || !current.reading || !current.sourceFresh || current.radioSelected != true ||
                    !current.controlsAvailable || current.sourceAtMs?.let { now() - it in 0..15_000 } != true) null
                else runCatching { controls.submit(action) }.getOrDefault(false)
            }
            if (!active || stamp != generation) return@launch
            mutable.update { it.copy(commandPending = false, commandStatus = when (sent) {
                true -> "Solicitud enviada; esperando lectura del equipo"
                false -> "El equipo no aceptó la solicitud"
                null -> "Solicitud cancelada: la fuente de audio cambió"
            }) }
            if (sent == true) poll.run()
        }
        return true
    }
}
