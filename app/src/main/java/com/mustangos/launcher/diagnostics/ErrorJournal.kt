package com.mustangos.launcher.diagnostics

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ErrorJournal {
    @Synchronized fun record(context: Context, code: String) {
        val prefs = context.getSharedPreferences("errors", Context.MODE_PRIVATE)
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
        val safeCode = code.replace(Regex("[^A-Za-z0-9_]"), "").take(80)
        val lines = (prefs.getString("recent", "").orEmpty().lines().filter { it.isNotBlank() } + "$time $safeCode").takeLast(20)
        val edit = prefs.edit().putString("recent", lines.joinToString("\n"))
        if (safeCode.startsWith("CRASH_")) edit.commit() else edit.apply()
    }
    fun recent(context: Context) = context.getSharedPreferences("errors", Context.MODE_PRIVATE).getString("recent", "Sin errores registrados").orEmpty()
}
