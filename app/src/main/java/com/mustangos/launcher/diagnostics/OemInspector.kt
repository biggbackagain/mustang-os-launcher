package com.mustangos.launcher.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import org.xmlpull.v1.XmlPullParser

/** Reads declarations only. Never constructs TWUtil, binds a service, queries a provider or writes MCU commands. */
class OemInspector(private val context: Context) {
    fun report(): String = buildString {
        appendLine("INSPECCIÓN OEM DE SOLO LECTURA")
        appendLine("SDK real: ${Build.VERSION.SDK_INT}")
        appendLine("La presencia de una clase o componente no demuestra acceso a datos.")
        val apps = com.mustangos.launcher.appintegration.AppIntegrationManager(context, com.mustangos.launcher.settings.LauncherSettings(context))
        appendLine("Incrustación entre apps: ${if (Build.VERSION.SDK_INT < 33) "API estándar entre apps no disponible antes de API 33" else "requiere soporte del fabricante y autorización del manifest de ZLink"}")
        appendLine("Actividades en pantallas secundarias: ${context.packageManager.hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)}")
        for (pkg in PACKAGES) {
            try {
                val info = context.packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS)
                appendLine("\n$pkg · ${info.versionName ?: "versión no declarada"}")
                appendLine(apps.exportedComponents(pkg))
                if (pkg == "com.zjinnova.zlink") appendLine(embeddingDeclarations(pkg))
                val permissions = info.services.orEmpty().mapNotNull { it.permission }.distinct()
                permissions.forEach { permission ->
                    val level = runCatching { context.packageManager.getPermissionInfo(permission, 0).protectionLevel and PermissionInfo.PROTECTION_MASK_BASE }.getOrNull()
                    appendLine("Permiso $permission: nivel=$level; concedido=${context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED}")
                }
            } catch (_: PackageManager.NameNotFoundException) { appendLine("$pkg: no visible o no instalado") }
              catch (_: RuntimeException) { appendLine("$pkg: inspección no permitida") }
        }
        for (name in listOf("android.tw.john.TWUtil", "android.tw.john.TWClient", "androidx.window.extensions.WindowExtensionsProvider")) {
            try {
                val type = Class.forName(name, false, context.classLoader)
                appendLine("\nClase visible: $name (sin inicializar ni invocar)")
                type.declaredMethods.map { method -> "${method.name}(${method.parameterTypes.joinToString { it.simpleName }}): ${method.returnType.simpleName}" }.sorted().take(80).forEach(::appendLine)
            } catch (_: ReflectiveOperationException) { appendLine("Clase no accesible: $name") }
              catch (_: LinkageError) { appendLine("Clase no cargable: $name") }
              catch (_: SecurityException) { appendLine("Clase restringida: $name") }
        }
    }
    private fun embeddingDeclarations(pkg: String): String = try {
        val assets = context.packageManager.getResourcesForApplication(pkg).assets
        assets.openXmlResourceParser("AndroidManifest.xml").use { xml ->
            buildString {
                appendLine("Declaraciones de presentación de ZLink:")
                var tag = xml.eventType
                while (tag != XmlPullParser.END_DOCUMENT) {
                    if (tag == XmlPullParser.START_TAG && xml.name in listOf("application", "activity", "activity-alias")) {
                        val ns = "http://schemas.android.com/apk/res/android"
                        val relevant = listOf("allowUntrustedActivityEmbedding", "knownActivityEmbeddingCerts", "resizeableActivity", "allowEmbedded")
                        val attrs = relevant.mapNotNull { key -> xml.getAttributeValue(ns, key)?.let { "$key=$it" } }
                        appendLine("${xml.name} ${xml.getAttributeValue(ns, "name").orEmpty()}: ${attrs.joinToString().ifEmpty { "sin autorización explícita de incrustación declarada" }}")
                    }
                    tag = xml.next()
                }
            }
        }
    } catch (_: Exception) { "Manifest de ZLink no legible desde esta APK; no se asume autorización" }

    companion object {
        val PACKAGES = listOf("com.zjinnova.zlink", "com.tw.service", "com.tw.uart", "com.tw.car", "com.tw.ac", "com.dofun.carassistant.car", "com.dofun.carsetting", "com.android.systemui")
    }
}
