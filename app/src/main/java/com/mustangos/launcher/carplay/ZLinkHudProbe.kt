package com.mustangos.launcher.carplay

import android.os.SystemClock
import android.util.LruCache
import com.mustangos.core.ZLinkHudFrameReader
import com.mustangos.core.ZLinkHudHeaderException
import com.mustangos.core.ZLinkHudMusic
import com.mustangos.core.ZLinkHudMediaDecoder
import com.mustangos.core.ZLinkHudArtworkWindow
import com.mustangos.core.ZLinkHudGuidanceDecoder
import com.mustangos.core.ZLinkHudNavigationShape
import com.mustangos.core.ZLinkRouteGuidance
import android.graphics.Bitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket

data class ZLinkHudProbeState(val status: String="Lectura HUD no iniciada",val running: Boolean=false,
    val connected: Boolean=false,val frames: Int=0,val bytes: Int=0,val types: Map<Int,Int> = emptyMap(),
    val music: ZLinkHudMusic=ZLinkHudMusic(),val validMusicFrames: Int=0,val invalidMusicFrames: Int=0,
    val shapes: Set<String> = emptySet(),val imageFrames: Int=0,val live: Boolean=false,
    val artwork: Bitmap?=null,val artworkDecoded: Int=0,val artworkUnassigned: Int=0,val artworkInvalid: Int=0,
    val artworkCached: Int=0,val imageSignatures: Set<String> = emptySet(),
    val navigationShapes: Set<String> = emptySet(),val invalidNavigation: Int=0,
    val guidance: ZLinkRouteGuidance=ZLinkRouteGuidance(),val guidanceFrames: Int=0,val connections: Int=0)

/**
 * Passive reader of the exact endpoint observed in the supplied libraries. It never writes to the socket.
 * Live mode reconnects with a growing pause so a ZLink restart or a long drive does not end the reading for good.
 */
class ZLinkHudProbe(private val retryDelaysMs: List<Long> = listOf(2_000,5_000,15_000,30_000),private val socketFactory: () -> Socket = { Socket() }) {
    private val mutable=MutableStateFlow(ZLinkHudProbeState())
    val state=mutable.asStateFlow()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var job: Job?=null
    private val lock=Any()
    private var revision=0
    private var socket: Socket?=null
    // Memory only: lets a repeated song show its cover even when the iPhone does not send it again.
    private val covers=LruCache<String,Bitmap>(12)
    fun start(live: Boolean=false) {
        if(job?.isActive == true) return
        val stamp=synchronized(lock) { ++revision }
        mutable.value=ZLinkHudProbeState("Esperando datos de CarPlay…",true,live=live)
        job=scope.launch {
            var sample=mutable.value
            var failures=0
            while(isActive) {
                val connectedBefore=sample.connections
                sample=withContext(Dispatchers.IO) { session(stamp,sample,live) }
                if(!live || !isActive || currentRevision()!=stamp) break
                if(sample.connections>connectedBefore && sample.frames>0) failures=0
                val wait=retryDelaysMs.getOrElse(failures) { retryDelaysMs.last() }; failures++
                sample=sample.copy(running=true,connected=false,status="${sample.status} · reintento en ${wait/1000} s")
                publish(stamp,sample)
                delay(wait)
            }
            // Retain structural counters, release private music values when the live reader ends.
            publish(stamp,if(live) sample.copy(running=false,music=ZLinkHudMusic(),artwork=null,guidance=ZLinkRouteGuidance()) else sample.copy(running=false))
        }
    }
    private suspend fun session(stamp: Int,initial: ZLinkHudProbeState,live: Boolean): ZLinkHudProbeState {
        var sample=initial.copy(running=true)
        val artWindow=ZLinkHudArtworkWindow()
        val connection=try { socketFactory() } catch(_: Exception) {
            return sample.copy(status="Android no permitió crear la conexión de prueba",running=false)
        }
        return try {
            synchronized(lock) {
                if(revision!=stamp) throw CancellationException()
                socket=connection
            }
            // Live mode is explicitly enabled by the user; each connection is still bounded in time and volume.
            val deadline=SystemClock.elapsedRealtime()+if(live) 4*60*60*1000L else 8_000L
            val frameLimit=sample.frames+if(live) 200_000 else 64
            val byteLimit=sample.bytes.toLong()+if(live) 256L*1024*1024 else 1024L*1024
            connection.soTimeout=250
            connection.connect(InetSocketAddress("127.0.0.1",1555),800)
            sample=sample.copy(connected=true,connections=sample.connections+1,status="Canal conectado · esperando datos de CarPlay")
            publish(stamp,sample)
            while(currentCoroutineContext().isActive && SystemClock.elapsedRealtime()<deadline && sample.frames<frameLimit && sample.bytes<byteLimit) {
                val frame=ZLinkHudFrameReader.readFrame(connection.getInputStream(),setOf(8,9,10,14)) {
                    currentRevision()==stamp && SystemClock.elapsedRealtime()<deadline
                }
                val header=frame.header
                val now=SystemClock.elapsedRealtime()
                sample=sample.copy(frames=sample.frames+1,bytes=(sample.bytes.toLong()+header.bytes).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    types=sample.types+(header.type to ((sample.types[header.type] ?: 0)+1)))
                when(header.type) {
                    8,9 -> sample=try {
                        val body=requireNotNull(frame.payload)
                        val fields=ZLinkHudNavigationShape.inspect(body).map { "${header.type}/$it" }
                        val guidance=if(header.type==8) sample.guidance.apply(ZLinkHudGuidanceDecoder.route(body),now)
                            else sample.guidance.apply(ZLinkHudGuidanceDecoder.maneuver(body),now)
                        sample.copy(guidance=guidance,guidanceFrames=sample.guidanceFrames+1,
                            navigationShapes=(sample.navigationShapes+fields).take(128).toSet())
                    } catch(_: Exception) { sample.copy(invalidNavigation=sample.invalidNavigation+1) }
                    10 -> sample=try {
                        val delta=ZLinkHudMediaDecoder.decode(requireNotNull(frame.payload))
                        val music=sample.music.apply(delta,now)
                        artWindow.item(sample.music,music,delta,now)
                        val changed=music.trackRevision!=sample.music.trackRevision
                        val cached=if(changed || sample.artwork==null) music.artworkKey?.let { covers.get(it) } else null
                        sample.copy(music=music,artwork=cached ?: sample.artwork.takeUnless { changed },
                            artworkCached=sample.artworkCached+if(cached!=null && cached!==sample.artwork) 1 else 0,
                            validMusicFrames=sample.validMusicFrames+1,shapes=(sample.shapes+delta.shapes).take(128).toSet(),
                            status="Recibiendo datos de CarPlay")
                    } catch(_: Exception) { sample.copy(invalidMusicFrames=sample.invalidMusicFrames+1) }
                    14 -> {
                        val body=frame.payload ?: byteArrayOf()
                        sample=sample.copy(imageFrames=sample.imageFrames+1)
                        sample=if(ZLinkHudArtwork.imageStart(body)==null)
                            sample.copy(artworkInvalid=sample.artworkInvalid+1,imageSignatures=(sample.imageSignatures+ZLinkHudArtwork.signature(body)).take(8).toSet())
                        else if(!artWindow.accepts(sample.music,now)) sample.copy(artworkUnassigned=sample.artworkUnassigned+1)
                        else {
                            val bitmap=ZLinkHudArtwork.decode(body)
                            if(bitmap==null) sample.copy(artworkInvalid=sample.artworkInvalid+1)
                            else {
                                sample.music.artworkKey?.let { covers.put(it,bitmap) }
                                sample.copy(artwork=bitmap,artworkDecoded=sample.artworkDecoded+1)
                            }
                        }
                    }
                }
                publish(stamp,sample)
            }
            sample.copy(status="Sesión de lectura completada",connected=false)
        } catch(cancelled: CancellationException) { throw cancelled }
          catch(_: ConnectException) { sample.copy(status="El canal local no aceptó la conexión",connected=false) }
          catch(_: InterruptedIOException) { sample.copy(status="Finalizó el tiempo de muestra",connected=false) }
          catch(header: ZLinkHudHeaderException) { sample.copy(status="Cabecera fuera del formato o límite: tipo=${header.frameType}; bytes=${header.declaredBytes}",connected=false) }
          catch(_: IllegalArgumentException) { sample.copy(status="Cabecera fuera del formato o límite esperado",connected=false) }
          catch(_: Exception) { sample.copy(status="El canal cerró o no permitió completar la muestra",connected=false) }
        finally {
            runCatching { connection.close() }
            synchronized(lock) { if(socket===connection) socket=null }
        }
    }
    private fun publish(stamp: Int,value: ZLinkHudProbeState) = synchronized(lock) { if(revision==stamp) mutable.value=value }
    private fun currentRevision()=synchronized(lock) { revision }
    fun stop() {
        val previous=synchronized(lock) { revision++; socket.also { socket=null } }
        runCatching { previous?.close() }; job?.cancel(); job=null
        covers.evictAll()
        mutable.value=mutable.value.copy(running=false,status="Lectura HUD detenida",music=ZLinkHudMusic(),artwork=null,guidance=ZLinkRouteGuidance())
    }
    fun close() { stop(); scope.cancel() }
    fun report(): String = state.value.let { sample -> buildString {
        appendLine("CANAL HUD CANDIDATO · identidad del servidor no verificada")
        appendLine("${sample.status}; activa=${sample.running}; conexión=${sample.connected}; continuo=${sample.live}; conexiones=${sample.connections}; tramas=${sample.frames}; bytes recibidos=${sample.bytes}")
        sample.types.toSortedMap().forEach { (type,count) -> appendLine("tipo=$type; recibidas=$count") }
        appendLine("Música TLV válida=${sample.validMusicFrames}; rechazada=${sample.invalidMusicFrames}; imágenes tipo 14=${sample.imageFrames}")
        appendLine("Portadas decodificadas=${sample.artworkDecoded}; desde caché=${sample.artworkCached}; fuera de ventana=${sample.artworkUnassigned}; inválidas=${sample.artworkInvalid}; firmas desconocidas=${sample.imageSignatures.sorted().joinToString(",")}")
        appendLine("Guía de ruta válida=${sample.guidanceFrames}; rechazada=${sample.invalidNavigation}; activa=${sample.guidance.active}; estado=${sample.guidance.state}")
        appendLine("Estructura navegación=${sample.navigationShapes.sorted().joinToString(",")}")
        appendLine("Campos grupo/id:bytes=${sample.shapes.sorted().joinToString(",")}")
        appendLine("Valores presentes: título=${sample.music.title!=null}; artista=${sample.music.artist!=null}; duración=${sample.music.durationMs!=null}; posición=${sample.music.elapsedMs!=null}; estado=${sample.music.rawStatus}")
        append("Identidad del servidor no autenticada. Sin envío ni cambios de configuración. El informe omite canciones, imágenes, rutas y coordenadas.")
    } }
}
