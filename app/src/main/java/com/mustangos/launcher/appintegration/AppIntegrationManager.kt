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
    fun apps(): List<LaunchableApp> = query(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
    fun homes(): List<LaunchableApp> = query(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
    fun maps(): List<LaunchableApp> = query(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=")))
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
    fun launch(role: String): Boolean = selected(role)?.let(::launchComponent) ?: false
    fun launchApp(app: LaunchableApp) = launchComponent(ComponentName(app.packageName, app.activity))
    fun launchComponent(component: ComponentName): Boolean = try {
        val info = pm.getActivityInfo(component, 0)
        if (!info.exported || !info.enabled || !info.applicationInfo.enabled) false
        else {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent)
            true
        }
    } catch (_: PackageManager.NameNotFoundException) { ErrorJournal.record(context, "APP_REMOVED"); false }
      catch (_: RuntimeException) { ErrorJournal.record(context, "APP_LAUNCH_DENIED"); false }

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
