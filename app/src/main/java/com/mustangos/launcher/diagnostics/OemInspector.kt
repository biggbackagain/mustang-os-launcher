package com.mustangos.launcher.diagnostics

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.content.pm.ResolveInfo
import android.os.Build
import com.mustangos.launcher.settings.LauncherSettings
import org.xmlpull.v1.XmlPullParser

/** Reads declarations only. Never constructs TWUtil, binds a service, queries a provider or writes MCU commands. */
class OemInspector(
    private val context: Context,
    private val homeQuery: () -> List<ResolveInfo> = {
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DISABLED_COMPONENTS)
    }
) {
    data class HomeActivity(val packageName: String, val activityName: String, val systemApp: Boolean, val ownApp: Boolean)
    data class HomeInventory(val activities: List<HomeActivity> = emptyList(), val truncated: Boolean = false,
        val unavailable: Boolean = false, val configuredHomePackage: String? = null)

    fun discoverHomes(): HomeInventory = try {
        val configured = LauncherSettings(context).component("home")
        val homes = homeQuery().mapNotNull { result ->
            val activity = result.activityInfo ?: return@mapNotNull null
            val pkg = activity.packageName?.takeIf { it.length <= 255 && it.matches(PACKAGE_NAME) } ?: return@mapNotNull null
            val name = activity.name?.takeIf { it.length <= 512 && it.matches(ACTIVITY_NAME) } ?: return@mapNotNull null
            val flags = runCatching { context.packageManager.getApplicationInfo(pkg, 0).flags }.getOrElse { activity.applicationInfo?.flags ?: 0 }
            HomeActivity(pkg, name, flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                pkg == context.packageName)
        }.distinctBy { it.packageName to it.activityName }
        val original = configured?.takeIf { choice -> homes.any { it.packageName == choice.packageName && it.activityName == choice.className } }?.packageName
        // Keep actual clients, especially the configured original HOME, ahead of setup/recovery entries at the inventory bound.
        val retained = homes.sortedWith(compareBy<HomeActivity>({ !exportableHome(it) }, { it.packageName != original },
            HomeActivity::packageName, HomeActivity::activityName)).take(MAX_HOME_ACTIVITIES)
            .sortedWith(compareBy(HomeActivity::packageName, HomeActivity::activityName))
        HomeInventory(retained, truncated = homes.size > MAX_HOME_ACTIVITIES, configuredHomePackage = original)
    } catch (_: RuntimeException) { HomeInventory(unavailable = true) }

    fun exportPackages(homes: HomeInventory): List<String> = (PACKAGES + homes.activities.asSequence()
        .filter(::exportableHome).map { it.packageName }.distinct()
        .sortedWith(compareBy<String>({ it != homes.configuredHomePackage }, { it }))
        .take(MAX_HOME_PACKAGES).toList()).distinct()

    private fun auxiliaryHome(home: HomeActivity) = (home.packageName to home.activityName) in AUXILIARY_HOME_COMPONENTS
    private fun exportableHome(home: HomeActivity) = home.systemApp && !home.ownApp && !auxiliaryHome(home)

    fun report(homes: HomeInventory = discoverHomes()): String = buildString {
        appendLine("INSPECCIÓN OEM DE SOLO LECTURA")
        appendLine("SDK real: ${Build.VERSION.SDK_INT}")
        appendLine("La presencia de una clase o componente no demuestra acceso a datos.")
        val apps = com.mustangos.launcher.appintegration.AppIntegrationManager(context, com.mustangos.launcher.settings.LauncherSettings(context))
        appendLine("Incrustación entre apps: ${if (Build.VERSION.SDK_INT < 33) "API estándar entre apps no disponible antes de API 33" else "requiere soporte del fabricante y autorización del manifest de ZLink"}")
        appendLine("Actividades en pantallas secundarias: ${context.packageManager.hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)}")
        val packages = exportPackages(homes)
        appendLine("HOME visibles (solo paquete/actividad):")
        if (homes.unavailable) appendLine("Inventario HOME no permitido; no se deducen clientes del launcher")
        else if (homes.activities.isEmpty()) appendLine("No se encontraron actividades HOME visibles")
        homes.activities.forEach { home ->
            val classification = when { home.ownApp -> "propia; omitida"; auxiliaryHome(home) -> "HOME auxiliar de Android; solo inventario";
                home.systemApp -> "sistema/actualización de sistema"; else -> "app de usuario; solo inventario" }
            appendLine("HOME ${home.packageName}/${home.activityName}: $classification; original configurado=${home.packageName == homes.configuredHomePackage}; seleccionado para exportar=${home.packageName in packages && !home.ownApp && !auxiliaryHome(home)}")
        }
        if (homes.truncated) appendLine("Inventario HOME limitado a $MAX_HOME_ACTIVITIES actividades")
        appendLine("Exportación HOME: máximo $MAX_HOME_PACKAGES paquetes de sistema; APK propia y HOME de usuario excluidas")
        appendLine("HOME auxiliares de arranque, aprovisionamiento y recuperación: solo inventario; no consumen la cuota de clientes HOME")
        for (pkg in packages) {
            try {
                val info = context.packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS)
                appendLine("\n$pkg · ${info.versionName ?: "versión no declarada"}")
                appendLine(apps.exportedComponents(pkg))
                appendLine(libraryDeclarations(pkg))
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
                appendLine("\nClase visible: $name (inspección pasiva de firmas)")
                type.declaredMethods.map { method -> "${method.name}(${method.parameterTypes.joinToString { it.simpleName }}): ${method.returnType.simpleName}" }.sorted().take(80).forEach(::appendLine)
            } catch (_: ReflectiveOperationException) { appendLine("Clase no accesible: $name") }
              catch (_: LinkageError) { appendLine("Clase no cargable: $name") }
              catch (_: SecurityException) { appendLine("Clase restringida: $name") }
        }
    }
    private fun libraryDeclarations(pkg: String): String = try {
        context.packageManager.getResourcesForApplication(pkg).assets.openXmlResourceParser("AndroidManifest.xml").use { xml ->
            "Bibliotecas compartidas declaradas (máx. $MAX_LIBRARY_NAMES nombres; sin copiar ni cargar): " +
                declaredLibraryNames(xml).joinToString().ifEmpty { "ninguna declarada visible" }
        }
    } catch (_: Exception) { "Bibliotecas compartidas declaradas: manifest no legible; no se consultan rutas ni se cargan archivos" }

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
        const val MAX_HOME_PACKAGES = 4
        const val MAX_HOME_ACTIVITIES = 64
        private const val MAX_LIBRARY_NAMES = 32
        private val PACKAGE_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")
        private val ACTIVITY_NAME = Regex("[A-Za-z0-9_.$]+")
        private val LIBRARY_NAME = Regex("[A-Za-z0-9_.-]{1,200}")
        private val LIBRARY_TAGS = setOf("uses-library", "uses-static-library", "uses-sdk-library", "uses-native-library")
        // Exact auxiliary HOME components observed in the user's API29 export, not a blanket vendor/package-name heuristic.
        private val AUXILIARY_HOME_COMPONENTS = setOf(
            "android" to "com.android.internal.app.SystemUserHomeActivity",
            "com.android.managedprovisioning" to "com.android.managedprovisioning.preprovisioning.PostEncryptionActivity",
            "com.android.provision" to "com.android.provision.DefaultActivity",
            "com.android.settings" to "com.android.settings.CryptKeeper",
            "com.android.settings" to "com.android.settings.FallbackHome",
            "com.google.android.gms" to "com.google.android.gms.kids.SyncTailTrapperActivity"
        )
        val PACKAGES = listOf("com.zjinnova.zlink", "com.tw.service", "com.tw.uart", "com.tw.car", "com.tw.ac", "com.tw.bt", "com.tw.music", "com.dofun.carassistant.car", "com.dofun.carsetting", "com.android.systemui", "com.tw.carinfoservice")

        internal fun declaredLibraryNames(xml: XmlPullParser): List<String> {
            val names = sortedSetOf<String>()
            var tag = xml.eventType
            while (tag != XmlPullParser.END_DOCUMENT && names.size < MAX_LIBRARY_NAMES) {
                if (tag == XmlPullParser.START_TAG && xml.name in LIBRARY_TAGS) {
                    xml.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                        ?.takeIf { it.matches(LIBRARY_NAME) }?.let(names::add)
                }
                tag = xml.next()
            }
            return names.toList()
        }
    }
}
