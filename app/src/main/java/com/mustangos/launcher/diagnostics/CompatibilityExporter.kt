package com.mustangos.launcher.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import java.io.OutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** User-triggered export of static installed code and declarations, never private application data. */
class CompatibilityExporter(private val context: Context) {
    fun write(output: OutputStream, diagnostic: String) {
        val notes = mutableListOf<String>()
        var total = 0L
        val inspector = OemInspector(context)
        val homes = inspector.discoverHomes()
        val packages = inspector.exportPackages(homes)
        ZipOutputStream(output.buffered()).use { zip ->
            fun text(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            text("diagnostic.txt", diagnostic)
            text("oem-declarations.txt", inspector.report(homes))
            notes += "Solo manifiestos y DEX estáticos; no recursos, licencias, datos privados, bibliotecas nativas ni bibliotecas compartidas"
            notes += "HOME: hasta ${OemInspector.MAX_HOME_PACKAGES} paquetes de sistema; máximo $MAX_CODE_ENTRIES_PER_APK entradas estáticas por APK"
            if (homes.unavailable) notes += "HOME: inventario no permitido; solo se exportan paquetes OEM conocidos"
            for (pkg in packages) {
                try {
                    val info = context.packageManager.getApplicationInfo(pkg, PackageManager.GET_SHARED_LIBRARY_FILES)
                    val sources = listOfNotNull(info.sourceDir).take(1)
                    var exportedEntries = 0
                    for (source in sources) {
                        if (File(source).length() !in 1..MAX_APK_SIZE) { notes += "$pkg: APK no legible o fuera del límite de tamaño"; continue }
                        ZipFile(source).use { apk ->
                            if (apk.size() > MAX_ARCHIVE_ENTRIES) { notes += "$pkg: APK omitida por número de entradas"; return@use }
                            val entries = apk.entries()
                            var consideredEntries = 0
                            while (entries.hasMoreElements()) {
                                val entry = entries.nextElement()
                                if (!entry.name.matches(Regex("AndroidManifest\\.xml|classes[0-9]*\\.dex"))) continue
                                if (consideredEntries >= MAX_CODE_ENTRIES_PER_APK) { notes += "$pkg: entradas restantes omitidas por límite de número"; break }
                                consideredEntries++
                                if (entry.size !in 0..32L * 1024 * 1024 || total + entry.size > 120L * 1024 * 1024) {
                                    notes += "$pkg/${entry.name}: omitido por límite de tamaño"; continue
                                }
                                zip.putNextEntry(ZipEntry("packages/$pkg/${entry.name}"))
                                apk.getInputStream(entry).use { input ->
                                    val buffer = ByteArray(16 * 1024)
                                    var copied = 0L
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        copied += count; total += count
                                        check(copied <= 32L * 1024 * 1024 && total <= 120L * 1024 * 1024) { "Export size limit" }
                                        zip.write(buffer, 0, count)
                                    }
                                }
                                zip.closeEntry()
                                exportedEntries++
                            }
                        }
                    }
                    notes += "$pkg: $exportedEntries entradas estáticas exportadas; recursos, datos y licencias del usuario no se leen"
                } catch (_: PackageManager.NameNotFoundException) { notes += "$pkg: ausente o no visible" }
                  catch (_: Exception) { notes += "$pkg: archivo no legible o exportación incompleta; sin elevar permisos" }
            }
            text("export-notes.txt", notes.joinToString("\n"))
        }
    }
    companion object {
        private const val MAX_CODE_ENTRIES_PER_APK = 64
        private const val MAX_ARCHIVE_ENTRIES = 100_000
        private const val MAX_APK_SIZE = 512L * 1024 * 1024
    }
}
