package com.mustangos.launcher.settings

import android.content.ComponentName
import android.content.Context

class LauncherSettings(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    fun component(role: String): ComponentName? = prefs.getString("app_$role", null)?.let(ComponentName::unflattenFromString)
    fun setComponent(role: String, value: ComponentName?) { prefs.edit().putString("app_$role", value?.flattenToString()).apply() }
    var mediaPackage: String?
        get() = prefs.getString("media", null)
        set(value) { prefs.edit().putString("media", value).apply() }
}
