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
            appendLine("MUSTANG OS 0.5.0 · diagnóstico básico")
            appendLine("SDK real: ${Build.VERSION.SDK_INT}; etiqueta RELEASE: ${Build.VERSION.RELEASE}")
            appendLine("Identificación declarada del equipo: fabricante=${Build.MANUFACTURER}; marca=${Build.BRAND}; modelo=${Build.MODEL}; placa=${Build.BOARD}; hardware=${Build.HARDWARE}")
            appendLine("API 29 corresponde a Android 10; API 32 a Android 12L; API 33 a Android 13. La etiqueta del fabricante puede diferir.")
            appendLine("Ventana/densidad: ${d.widthPixels}x${d.heightPixels}px; ${d.densityDpi}dpi")
            appendLine("RAM total: ${mem.totalMem / 1_048_576} MiB; disponible: ${mem.availMem / 1_048_576} MiB")
            appendLine("GPS: ${model.gps.state.value.status}")
            appendLine("Permiso ubicación precisa: ${context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED}")
            appendLine("Acceso a sesiones multimedia: ${model.media.hasAccess()}")
            appendLine("Multimedia: ${model.media.state.value.status}")
            appendLine("Paquetes de sesiones visibles: ${model.media.sessions.map { it.packageName }.distinct().joinToString().ifBlank { "ninguno" }}")
            appendLine("Acciones de sesión seleccionada: ${model.media.state.value.facts.actions}")
            appendLine("Estado de reproducción publicado: ${model.media.state.value.playbackStateKnown}")
            appendLine("CarPlay: ${model.carplay.connectionStatus}")
            appendLine("Servicio ZLink: ${model.zlinkProbe.state.value}")
            val mediaFacts = model.media.state.value.facts
            appendLine("Campos multimedia principales extraídos: título=${mediaFacts.title != null}; artista=${mediaFacts.artist != null}; álbum=${mediaFacts.album != null}; duración=${mediaFacts.durationMs > 0}; portada=${model.media.state.value.artwork != null}")
            appendLine(model.media.sessionDiagnostics())
            appendLine("Navegación: ${model.navigation.status}")
            appendLine("Mapa Android por Internet habilitado: ${model.settings.mapNetworkEnabled}; caché de imágenes del área vista; sin rutas ni coordenadas en este informe")
            appendLine("Portada seleccionada: ${model.media.state.value.coverStatus}")
            appendLine("Datos del vehículo: ${model.vehicle.status}")
            val oem = model.oem.state.value
            appendLine("Lectura OEM: ${oem.status}; eventos=${oem.frames}; activa por el usuario=${model.settings.oemReadEnabled}")
            appendLine("Suscripción local OEM activa: ${oem.sourceActive}; contiene lecturas anteriores: ${oem.stale}")
            appendLine(oem.ageSummary)
            appendLine(oem.voltageRequestStatus)
            appendLine("Valores OEM recibidos: voltaje=${oem.voltage ?: "sin evento"}; temperatura_C=${oem.temperatureC ?: "sin evento"}; máscara de puertas=${oem.doorMask ?: "sin evento"}")
            appendLine("Perfil MCU recibido: ${oem.protocol?.let { "modelo=${it.model}; tipo=${it.type}" } ?: "sin evento; no se presupone a partir del coche"}")
            appendLine(oem.eventReport)
            appendLine(oem.temperatureRequestStatus)
            appendLine("Eventos de aperturas: máscara no cero=${oem.doorOpenEvents}; máscara cero=${oem.doorZeroEvents}")
            appendLine(model.vehicle.carInfo.report())
            appendLine(model.vehicle.nativeReport())
            val fix = model.gps.state.value
            appendLine("Calidad GPS: precisión=${fix.accuracyMeters ?: "sin lectura"} m; satélites usados=${fix.satellitesUsed ?: "sin lectura"}")
            appendLine("Proveedor de posición: ${fix.provider ?: "sin lectura"}; edad de posición_ms=${fix.fixAgeMs ?: "sin lectura"}; satélites visibles=${fix.satellitesVisible ?: "sin lectura"}")
            appendLine("ACC OEM: ${model.vehicle.accessoryOn.value ?: "sin evento"}; no confirma motor en marcha. Faros de bienvenida: efecto visual.")
            appendLine("Último error al abrir una app: ${model.apps.lastFailure ?: "ninguno"}")
            appendLine("Asignaciones:")
            listOf("zlink", "music", "maps", "phone", "radio", "home").forEach { appendLine("$it: ${model.apps.selected(it)?.flattenToShortString() ?: "sin elegir"}") }
            appendLine("Candidatos ZLink por etiqueta/paquete (sin verificar identidad):")
            model.apps.candidates().forEach { appendLine("${it.label}: ${it.packageName}/${it.activity}") }
            model.carplay.packageName?.let { appendLine("Componentes declarados exportados, sin probar su contrato:"); appendLine(model.apps.exportedComponents(it)) }
            appendLine(OemInspector(context).report())
            appendLine("Errores recientes (códigos, sin contenido privado):")
            appendLine(ErrorJournal.recent(context))
            appendLine("No contiene coordenadas, contenidos de notificaciones, canciones, IMEI ni seriales.")
        }
    }
}
