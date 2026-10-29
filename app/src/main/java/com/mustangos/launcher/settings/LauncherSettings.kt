package com.mustangos.launcher.settings

import android.content.ComponentName
import android.content.Context
import com.mustangos.core.MustangProfile
import com.mustangos.core.MustangVersion
import com.mustangos.core.MustangBody
import com.mustangos.core.MustangPaint

class LauncherSettings(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    fun component(role: String): ComponentName? = prefs.getString("app_$role", null)?.let(ComponentName::unflattenFromString)
    fun setComponent(role: String, value: ComponentName?) { prefs.edit().putString("app_$role", value?.flattenToString()).apply() }
    var mediaPackage: String?
        get() = prefs.getString("media", null)
        set(value) { prefs.edit().putString("media", value).apply() }
    var oemReadEnabled: Boolean
        get() = prefs.getBoolean("oem_read", false)
        set(value) { prefs.edit().putBoolean("oem_read", value).apply() }
    var oemInterrupted: Boolean
        get() = prefs.getBoolean("oem_interrupted", false)
        set(value) { prefs.edit().putBoolean("oem_interrupted", value).commit() }
    var mapNetworkEnabled: Boolean
        get() = prefs.getBoolean("map_network", false)
        set(value) { prefs.edit().putBoolean("map_network", value).apply() }
    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keep_screen_on", true)
        set(value) { prefs.edit().putBoolean("keep_screen_on", value).apply() }
    var gpsPermissionPrompted: Boolean
        get() = prefs.getBoolean("gps_permission_prompted", false)
        set(value) { prefs.edit().putBoolean("gps_permission_prompted", value).apply() }
    var digitalVehicle: Boolean
        get() = prefs.getBoolean("digital_vehicle", true)
        set(value) { prefs.edit().putBoolean("digital_vehicle", value).apply() }
    var carPlayBar: Boolean
        get() = prefs.getBoolean("carplay_bar", false)
        set(value) { prefs.edit().putBoolean("carplay_bar", value).apply() }
    var zlinkHudEnabled: Boolean
        get() = prefs.getBoolean("zlink_hud", false)
        set(value) { prefs.edit().putBoolean("zlink_hud", value).apply() }
    var mustangProfile: MustangProfile
        get() = MustangProfile(
            prefs.getInt("profile_year", 2010).takeIf { it in 1964..2099 } ?: 2010,
            MustangVersion.entries.firstOrNull { it.name == prefs.getString("profile_version", null) } ?: MustangVersion.V6,
            MustangBody.entries.firstOrNull { it.name == prefs.getString("profile_body", null) } ?: MustangBody.CONVERTIBLE,
            MustangPaint.entries.firstOrNull { it.name == prefs.getString("profile_paint", null) } ?: MustangPaint.RED
        )
        set(value) { prefs.edit().putInt("profile_year", value.year).putString("profile_version", value.version.name)
            .putString("profile_body", value.body.name).putString("profile_paint", value.paint.name).apply() }
}
