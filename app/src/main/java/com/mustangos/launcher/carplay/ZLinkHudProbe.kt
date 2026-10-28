package com.mustangos.launcher.carplay

import android.os.SystemClock
import com.mustangos.core.ZLinkHudFrameReader
import com.mustangos.core.ZLinkHudHeaderException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket

data class ZLinkHudProbeState(val status: String="Prueba HUD no realizada",val running: Boolean=false,
    val connected: Boolean=false,val frames: Int=0,val bytes: Int=0,val types: Map<Int,Int> = emptyMap())

/** One explicit, bounded observation of the exact endpoint in the supplied ZLink libraries. No writes or retries. */
class ZLinkHudProbe(private val socketFactory: () -> Socket = { Socket() }) {
    private val mutable=MutableStateFlow(ZLinkHudProbeState())
    val state=mutable.asStateFlow()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var job: Job?=null
    private val lock=Any()
    private var revision=0
    private var socket: Socket?=null
    fun start() {
        if(job?.isActive == true) return
        val stamp=synchronized(lock) { ++revision }
        mutable.value=ZLinkHudProbeState("Comprobando el canal local conocido…",true)
        job=scope.launch {
            val result=withContext(Dispatchers.IO) {
                var sample=ZLinkHudProbeState("Sin muestra",true)
                val connection=try { socketFactory() } catch(_: Exception) {
                    return@withContext sample.copy(status="Android no permitió crear la conexión de prueba",running=false)
                }
                try {
                    synchronized(lock) {
                        if(revision!=stamp) throw CancellationException()
                        socket=connection
                    }
                    val deadline=SystemClock.elapsedRealtime()+8_000
                    connection.soTimeout=250
                    connection.connect(InetSocketAddress("127.0.0.1",1555),800)
                    sample=sample.copy(connected=true)
                    while(currentCoroutineContext().isActive && SystemClock.elapsedRealtime()<deadline && sample.frames<64 && sample.bytes<1024*1024) {
                        val frame=ZLinkHudFrameReader.read(connection.getInputStream()) {
                            currentRevision()==stamp && SystemClock.elapsedRealtime()<deadline
                        }
                        sample=sample.copy(frames=sample.frames+1,bytes=sample.bytes+frame.bytes,types=sample.types+(frame.type to ((sample.types[frame.type] ?: 0)+1)))
                    }
                    sample.copy(status="Muestra finalizada",running=false)
                } catch(cancelled: CancellationException) { throw cancelled }
                  catch(_: ConnectException) { sample.copy(status="El canal local no aceptó la conexión",running=false) }
                  catch(_: InterruptedIOException) { sample.copy(status="Finalizó el tiempo de muestra",running=false) }
                  catch(header: ZLinkHudHeaderException) { sample.copy(status="Cabecera fuera del formato o límite: tipo=${header.frameType}; bytes=${header.declaredBytes}",running=false) }
                  catch(_: IllegalArgumentException) { sample.copy(status="Cabecera fuera del formato o límite esperado",running=false) }
                  catch(_: Exception) { sample.copy(status="El canal cerró o no permitió completar la muestra",running=false) }
                finally {
                    runCatching { connection.close() }
                    synchronized(lock) { if(socket===connection) socket=null }
                }
            }
            if(currentRevision()==stamp) mutable.value=result
        }
    }
    private fun currentRevision()=synchronized(lock) { revision }
    fun stop() {
        val previous=synchronized(lock) { revision++; socket.also { socket=null } }
        runCatching { previous?.close() }; job?.cancel(); job=null
        mutable.value=mutable.value.copy(running=false,status="Prueba HUD detenida")
    }
    fun close() { stop(); scope.cancel() }
    fun report(): String = state.value.let { sample -> buildString {
        appendLine("CANAL HUD CANDIDATO · identidad del servidor no verificada")
        appendLine("${sample.status}; activa=${sample.running}; conexión=${sample.connected}; tramas=${sample.frames}; bytes descartados=${sample.bytes}")
        sample.types.toSortedMap().forEach { (type,count) -> appendLine("tipo=$type; recibidas=$count") }
        append("La conexión sola no acredita metadatos ni acceso al vídeo. No se envió contenido ni se guardaron canciones, imágenes o rutas.")
    } }
}
