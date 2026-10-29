package com.mustangos.launcher.carplay

import android.os.SystemClock
import com.mustangos.core.ZLinkHudFrameReader
import com.mustangos.core.ZLinkHudHeaderException
import com.mustangos.core.ZLinkHudMusic
import com.mustangos.core.ZLinkHudMediaDecoder
import com.mustangos.core.ZLinkHudArtworkWindow
import com.mustangos.core.ZLinkHudNavigationShape
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
    val shapes: Set<String> = emptySet(),val pngFrames: Int=0,val live: Boolean=false,
    val artwork: Bitmap?=null,val artworkDecoded: Int=0,val artworkUnassigned: Int=0,val artworkInvalid: Int=0,
    val navigationShapes: Set<String> = emptySet(),val invalidNavigation: Int=0)

/** One passive connection to the exact endpoint observed in the supplied libraries. No writes or retries. */
class ZLinkHudProbe(private val socketFactory: () -> Socket = { Socket() }) {
    private val mutable=MutableStateFlow(ZLinkHudProbeState())
    val state=mutable.asStateFlow()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var job: Job?=null
    private val lock=Any()
    private var revision=0
    private var socket: Socket?=null
    fun start(live: Boolean=false) {
        if(job?.isActive == true) return
        val stamp=synchronized(lock) { ++revision }
        mutable.value=ZLinkHudProbeState("Esperando datos de CarPlay…",true,live=live)
        job=scope.launch {
            val result=withContext(Dispatchers.IO) {
                var sample=ZLinkHudProbeState("Sin muestra",true,live=live)
                val artWindow=ZLinkHudArtworkWindow()
                val connection=try { socketFactory() } catch(_: Exception) {
                    return@withContext sample.copy(status="Android no permitió crear la conexión de prueba",running=false)
                }
                try {
                    synchronized(lock) {
                        if(revision!=stamp) throw CancellationException()
                        socket=connection
                    }
                    // Live mode is explicitly enabled and owned by a visible home/bar. Session limits bound abuse.
                    val deadline=SystemClock.elapsedRealtime()+if(live) 4*60*60*1000L else 8_000L
                    connection.soTimeout=250
                    connection.connect(InetSocketAddress("127.0.0.1",1555),800)
                    sample=sample.copy(connected=true)
                    sample=sample.copy(status="Canal conectado · esperando campos musicales")
                    publish(stamp,sample)
                    while(currentCoroutineContext().isActive && SystemClock.elapsedRealtime()<deadline &&
                        sample.frames<(if(live) 100_000 else 64) && sample.bytes<(if(live) 64*1024*1024 else 1024*1024)) {
                        val frame=ZLinkHudFrameReader.readFrame(connection.getInputStream(),setOf(8,9,10,14)) {
                            currentRevision()==stamp && SystemClock.elapsedRealtime()<deadline
                        }
                        val header=frame.header
                        sample=sample.copy(frames=sample.frames+1,bytes=sample.bytes+header.bytes,
                            types=sample.types+(header.type to ((sample.types[header.type] ?: 0)+1)))
                        if(header.type==8 || header.type==9) {
                            sample=try {
                                val fields=ZLinkHudNavigationShape.inspect(requireNotNull(frame.payload)).map { "${header.type}/$it" }
                                sample.copy(navigationShapes=(sample.navigationShapes+fields).take(128).toSet())
                            } catch(_: Exception) { sample.copy(invalidNavigation=sample.invalidNavigation+1) }
                        }
                        if(header.type==10) {
                            sample=try {
                                val delta=ZLinkHudMediaDecoder.decode(requireNotNull(frame.payload))
                                val now=SystemClock.elapsedRealtime(); val music=sample.music.apply(delta,now)
                                artWindow.item(sample.music,music,delta,now)
                                val changed=music.trackRevision!=sample.music.trackRevision || music.artworkId!=sample.music.artworkId
                                sample.copy(music=music,artwork=sample.artwork.takeUnless { changed },
                                    validMusicFrames=sample.validMusicFrames+1,shapes=(sample.shapes+delta.shapes).take(128).toSet(),
                                    status="Recibiendo campos musicales de CarPlay")
                            } catch(_: Exception) { sample.copy(invalidMusicFrames=sample.invalidMusicFrames+1) }
                        }
                        if(header.type==14 && frame.payload?.take(8)?.toByteArray()?.contentEquals(PNG_HEADER)==true) {
                            sample=sample.copy(pngFrames=sample.pngFrames+1)
                            sample=if(!artWindow.consume(sample.music,SystemClock.elapsedRealtime())) sample.copy(artworkUnassigned=sample.artworkUnassigned+1)
                            else {
                                val bitmap=ZLinkHudArtwork.decode(requireNotNull(frame.payload))
                                if(bitmap==null) sample.copy(artworkInvalid=sample.artworkInvalid+1)
                                else sample.copy(artwork=bitmap,artworkDecoded=sample.artworkDecoded+1)
                            }
                        }
                        publish(stamp,sample)
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
            // Retain structural counters, release private music values when the live socket ends.
            publish(stamp,if(live) result.copy(music=ZLinkHudMusic(),artwork=null) else result)
        }
    }
    private fun publish(stamp: Int,value: ZLinkHudProbeState) = synchronized(lock) { if(revision==stamp) mutable.value=value }
    private fun currentRevision()=synchronized(lock) { revision }
    fun stop() {
        val previous=synchronized(lock) { revision++; socket.also { socket=null } }
        runCatching { previous?.close() }; job?.cancel(); job=null
        mutable.value=mutable.value.copy(running=false,status="Prueba HUD detenida",music=ZLinkHudMusic(),artwork=null)
    }
    fun close() { stop(); scope.cancel() }
    fun report(): String = state.value.let { sample -> buildString {
        appendLine("CANAL HUD CANDIDATO · identidad del servidor no verificada")
        appendLine("${sample.status}; activa=${sample.running}; conexión=${sample.connected}; continuo=${sample.live}; tramas=${sample.frames}; bytes recibidos=${sample.bytes}")
        sample.types.toSortedMap().forEach { (type,count) -> appendLine("tipo=$type; recibidas=$count") }
        appendLine("Música TLV válida=${sample.validMusicFrames}; rechazada=${sample.invalidMusicFrames}; cabecera PNG=${sample.pngFrames}")
        appendLine("PNG decodificados=${sample.artworkDecoded}; sin anuncio reciente=${sample.artworkUnassigned}; inválidos=${sample.artworkInvalid}")
        appendLine("Estructura navegación=${sample.navigationShapes.sorted().joinToString(",")}; rechazada=${sample.invalidNavigation}")
        appendLine("Campos grupo/id:bytes=${sample.shapes.sorted().joinToString(",")}")
        appendLine("Valores presentes: título=${sample.music.title!=null}; artista=${sample.music.artist!=null}; duración=${sample.music.durationMs!=null}; posición=${sample.music.elapsedMs!=null}")
        append("Identidad del servidor no autenticada. Sin envío, reintentos ni cambios de configuración. El informe omite canciones, imágenes, rutas y coordenadas.")
    } }
    companion object { private val PNG_HEADER=byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,13,10,26,10) }
}
