package com.mustangos.launcher.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** User-triggered export of static installed code and declarations, never private application data. */
class CompatibilityExporter(private val context: Context) {
    fun write(output: OutputStream, diagnostic: String) {
        val notes = mutableListOf<String>()
        var total = 0L
        ZipOutputStream(output.buffered()).use { zip ->
            fun text(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            text("diagnostic.txt", diagnostic)
            text("oem-declarations.txt", OemInspector(context).report())
            for (pkg in OemInspector.PACKAGES) {
                try {
                    val info = context.packageManager.getApplicationInfo(pkg, PackageManager.GET_SHARED_LIBRARY_FILES)
                    val sources = listOfNotNull(info.sourceDir).take(1)
                    for (source in sources) ZipFile(source).use { apk ->
                        val entries = apk.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (!entry.name.matches(Regex("AndroidManifest\\.xml|classes[0-9]*\\.dex"))) continue
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
                        }
                    }
                    notes += "$pkg: código estático legible exportado; recursos, datos y licencias del usuario no se leen"
                } catch (_: PackageManager.NameNotFoundException) { notes += "$pkg: ausente o no visible" }
                  catch (_: Exception) { notes += "$pkg: archivo no legible o exportación incompleta; sin elevar permisos" }
            }
            text("export-notes.txt", notes.joinToString("\n"))
        }
    }
}
