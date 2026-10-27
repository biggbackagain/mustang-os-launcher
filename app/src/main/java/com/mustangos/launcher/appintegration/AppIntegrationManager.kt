package com.mustangos.launcher.appintegration

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.mustangos.core.IntegrationPolicy
import com.mustangos.core.LaunchableApp
import com.mustangos.launcher.diagnostics.ErrorJournal
import com.mustangos.launcher.settings.LauncherSettings

class AppIntegrationManager(private val context: Context, private val settings: LauncherSettings) {
    private val pm get() = context.packageManager
    var lastFailure: String? = null; private set
    // Package names confirmed in the user's installed-app screenshots. Activities are still resolved at runtime.
    private val firmwarePackages = mapOf("zlink" to "com.zjinnova.zlink", "radio" to "com.tw.radio", "phone" to "com.tw.bt", "music" to "com.tw.music")
    fun apps(): List<LaunchableApp> = query(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
    fun homes(): List<LaunchableApp> = query(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
    fun maps(): List<LaunchableApp> {
        val geoApps = query(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=")))
        val naviKeywords = listOf("map", "maps", "navi", "gps", "waze", "navigation", "amap", "sygic", "here", "petal", "igo")
        val keywordApps = apps().filter { app ->
            val pkg = app.packageName.lowercase()
            val label = app.label.lowercase()
            naviKeywords.any { pkg.contains(it) || label.contains(it) }
        }
        return (geoApps + keywordApps).distinctBy { "${it.packageName}/${it.activity}" }
    }
    private fun query(intent: Intent): List<LaunchableApp> = try {
        pm.queryIntentActivities(intent, 0).mapNotNull { result ->
            val a = result.activityInfo
            if (a.packageName == context.packageName || !a.exported || !a.enabled) null
            else LaunchableApp(a.packageName, a.name, result.loadLabel(pm).toString())
        }.distinctBy { "${it.packageName}/${it.activity}" }.sortedBy { it.label.lowercase() }
    } catch (_: RuntimeException) { ErrorJournal.record(context, "PACKAGE_QUERY_FAILED"); emptyList() }
    fun candidates() = apps().filter(IntegrationPolicy::isZlinkCandidate)
    fun selected(role: String) = settings.component(role)
    fun assign(role: String, app: LaunchableApp) = settings.setComponent(role, ComponentName(app.packageName, app.activity))
    fun choices(role: String?): List<LaunchableApp> = when (role) {
        null -> apps()
        "maps" -> maps()
        "home" -> homes()
        "zlink" -> candidates()
        "radio", "phone", "music" -> apps().filter { app ->
            app.packageName == firmwarePackages[role] || when (role) {
                "radio" -> app.label.contains("radio", true)
                "phone" -> Regex("teléfono|telefono|bluetooth|phone", RegexOption.IGNORE_CASE).containsMatchIn(app.label)
                else -> Regex("música|musica|music|spotify", RegexOption.IGNORE_CASE).containsMatchIn(app.label)
            }
        }
        else -> apps()
    }
    fun launch(role: String): Boolean {
        lastFailure = null
        val chosen = selected(role)
        if (role == "maps" && chosen != null && maps().none { it.packageName == chosen.packageName && it.activity == chosen.className }) {
            settings.setComponent(role, null)
            lastFailure = "La asignación anterior no es una aplicación de mapas Android"
            return false
        }
        if (chosen != null && launchComponent(chosen)) return true
        // Do not replace a valid selection merely because the OEM denied permission.
        if (chosen != null && lastFailure?.startsWith("Permiso OEM") == true) return false
        val available = choices(role)
        // Auto-assign when there is exactly one candidate — avoids forcing the user through the picker.
        val detected = firmwarePackages[role]?.let { pkg -> available.firstOrNull { it.packageName == pkg } }
            ?: available.singleOrNull()
        if (detected != null) {
            assign(role, detected)
            return launchApp(detected)
        }
        return false
    }

    /**
     * Launch a specific [ComponentName] directly without going through the role/picker flow.
     * Used to open ZLink's CarPlayActivity, skipping the mode-selection menu.
     * Returns false silently if the activity is not found, not exported, or the launch fails.
     */
    fun launchDirect(component: ComponentName): Boolean {
        return try {
            val info = pm.getActivityInfo(component, 0)
            if (!info.exported || !info.enabled || !info.applicationInfo.enabled) return false
            val intent = android.content.Intent().setComponent(component)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent)
            lastFailure = null
            true
        } catch (_: PackageManager.NameNotFoundException) { false }
          catch (_: SecurityException) { false }
          catch (_: RuntimeException) { false }
    }
    fun launchPackage(packageName: String): Boolean = try {
        val intent = pm.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent)
            lastFailure = null
            true
        } else false
    } catch (_: Exception) { false }
    fun launchApp(app: LaunchableApp) = launchComponent(ComponentName(app.packageName, app.activity))
    fun launchComponent(component: ComponentName): Boolean = try {
        val info = pm.getActivityInfo(component, 0)
        if (!info.exported || !info.enabled || !info.applicationInfo.enabled) {
            lastFailure = "La actividad está deshabilitada o no permite acceso externo"; false
        } else if (info.permission?.let { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED } == true) {
            lastFailure = "Permiso OEM no concedido: ${info.permission}"; false
        }
        else {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent)
            lastFailure = null
            true
        }
    } catch (_: PackageManager.NameNotFoundException) { lastFailure = "La actividad elegida ya no está instalada"; ErrorJournal.record(context, "APP_REMOVED"); false }
      catch (_: SecurityException) { lastFailure = "Android denegó el acceso a esta actividad OEM"; ErrorJournal.record(context, "APP_PERMISSION_DENIED"); false }
      catch (_: RuntimeException) { lastFailure = "Android no pudo iniciar la actividad elegida"; ErrorJournal.record(context, "APP_LAUNCH_DENIED"); false }

    fun exportedComponents(packageName: String): String = try {
        val info = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS)
        buildString {
            appendLine("Paquete: $packageName; versión: ${info.versionName ?: "desconocida"}")
            info.activities?.filter { it.exported }?.forEach { appendLine("activity ${it.name}; permiso=${it.permission ?: "ninguno declarado"}") }
            info.services?.filter { it.exported }?.forEach { appendLine("service ${it.name}; permiso=${it.permission ?: "ninguno declarado"}") }
            info.receivers?.filter { it.exported }?.forEach { appendLine("receiver ${it.name}; permiso=${it.permission ?: "ninguno declarado"}") }
            info.providers?.filter { it.exported }?.forEach { appendLine("provider ${it.name}; lectura=${it.readPermission ?: "ninguno"}; escritura=${it.writePermission ?: "ninguno"}") }
        }
    } catch (_: RuntimeException) { "No se pudo inspeccionar el paquete visible" }
      catch (_: PackageManager.NameNotFoundException) { "Paquete ausente" }
}
