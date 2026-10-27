package com.mustangos.launcher.diagnostics

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.mustangos.launcher.ui.LauncherViewModel

class DiagnosticReporter(private val context: Context, private val model: LauncherViewModel) {
    fun create(): String {
        val mem = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val d = context.resources.displayMetrics
        return buildString {
            appendLine("MUSTANG OS 0.1.0 · diagnóstico básico")
            appendLine("SDK real: ${Build.VERSION.SDK_INT}; etiqueta RELEASE: ${Build.VERSION.RELEASE}")
            appendLine("API 32 corresponde a Android 12L; API 33 a Android 13. La etiqueta del fabricante puede diferir.")
            appendLine("Ventana/densidad: ${d.widthPixels}x${d.heightPixels}px; ${d.densityDpi}dpi")
            appendLine("RAM total: ${mem.totalMem / 1_048_576} MiB; disponible: ${mem.availMem / 1_048_576} MiB")
            appendLine("GPS: ${model.gps.state.value.status}")
            appendLine("Permiso ubicación precisa: ${context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED}")
            appendLine("Acceso a sesiones multimedia: ${model.media.hasAccess()}")
            appendLine("Multimedia: ${model.media.state.value.status}")
            appendLine("Paquetes de sesiones visibles: ${model.media.sessions.map { it.packageName }.distinct().joinToString().ifBlank { "ninguno" }}")
            appendLine("Acciones de sesión seleccionada: ${model.media.state.value.facts.actions}")
            appendLine("CarPlay: ${model.carplay.connectionStatus}")
            appendLine("Navegación: ${model.navigation.status}")
            appendLine("OBD2: ${model.vehicle.status}")
            appendLine("Asignaciones:")
            listOf("zlink", "music", "maps", "phone", "radio", "home").forEach { appendLine("$it: ${model.apps.selected(it)?.flattenToShortString() ?: "sin elegir"}") }
            appendLine("Candidatos ZLink por etiqueta/paquete (sin verificar identidad):")
            model.apps.candidates().forEach { appendLine("${it.label}: ${it.packageName}/${it.activity}") }
            model.carplay.packageName?.let { appendLine("Componentes declarados exportados, sin probar su contrato:"); appendLine(model.apps.exportedComponents(it)) }
            appendLine("Errores recientes (códigos, sin contenido privado):")
            appendLine(ErrorJournal.recent(context))
            appendLine("No contiene coordenadas, contenidos de notificaciones, canciones, IMEI ni seriales.")
        }
    }
}
