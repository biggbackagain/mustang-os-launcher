package com.mustangos.launcher.navigation

import org.mapsforge.map.reader.MapFile
import java.io.DataInputStream
import java.io.File

data class OfflineMapDescriptor(val file: File, val bytes: Long, val modified: Long, val dataDate: Long)

/** Header/reader validation must run on IO, before a partial file can become the active map. */
object OfflineMapValidator {
    const val MAX_BYTES = 2L * 1024 * 1024 * 1024
    private val magic = "mapsforge binary OSM".toByteArray(Charsets.US_ASCII)

    fun validate(file: File): OfflineMapDescriptor {
        require(file.isFile && file.length() in 65_536..MAX_BYTES) { "Tamaño de mapa no válido" }
        val header = ByteArray(magic.size)
        DataInputStream(file.inputStream()).use { it.readFully(header) }
        require(header.contentEquals(magic)) { "El archivo no es un mapa Mapsforge" }
        val map = MapFile(file, "es")
        try {
            val info = map.mapFileInfo
            require(info.fileSize == file.length()) { "El mapa está incompleto" }
            require(info.projectionName == "Mercator" && info.numberOfSubFiles > 0) { "Formato de mapa no compatible" }
            val bounds = info.boundingBox
            require(bounds.minLatitude <= 15 && bounds.maxLatitude >= 32 &&
                bounds.minLongitude <= -117 && bounds.maxLongitude >= -87) { "Selecciona el mapa de México completo" }
            return OfflineMapDescriptor(file, file.length(), file.lastModified(), info.mapDate)
        } finally { map.close() }
    }
}
