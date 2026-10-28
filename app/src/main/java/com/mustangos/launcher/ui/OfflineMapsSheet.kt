package com.mustangos.launcher.ui

import android.app.AlertDialog
import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.mustangos.launcher.R
import com.mustangos.launcher.navigation.OfflineMapStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Download/import controls; no network work begins until the user presses Download. */
class OfflineMapsSheet(
    private val context: Context,
    private val onImport: () -> Unit,
    private val onReady: () -> Unit,
    private val onDismiss: () -> Unit,
    private val onRegion: (Double, Double, Byte) -> Unit = { _, _, _ -> }
) {
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun show() {
        val store = OfflineMapStore.get(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(16)) }
        lateinit var dialog: AlertDialog
        fun body(text: String) = TextView(context).apply { this.text = text; setTextAppearance(R.style.Muted); setPadding(0, dp(4), 0, dp(12)) }.also { content.addView(it) }
        body("México completo sin conexión. Permite explorar, ampliar y centrar tu ubicación GPS. No calcula rutas ni muestra la ruta del iPhone.")
        val status = body("Revisando archivo local…")
        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000 }
        content.addView(progress, LinearLayout.LayoutParams(-1, dp(8)))
        val enabled = Switch(context).apply { text = "Usar México sin conexión"; minHeight = dp(56); setTextAppearance(R.style.Body) }
        content.addView(enabled, LinearLayout.LayoutParams(-1, dp(56)))
        fun button(label: String) = Button(context, null, 0, R.style.ActionButton).apply { text = label }.also {
            content.addView(it, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        }
        val download = button("Descargar México · solo Wi-Fi")
        val importing = button("Importar mexico.map desde Descargas")
        val cancel = button("Cancelar descarga o importación")
        val regions = listOf("Ver México" to Triple(23.6, -102.5, 4.toByte()),
            "Ver Jalisco" to Triple(20.6, -103.7, 7.toByte()), "Ver Colima" to Triple(19.1, -104.0, 8.toByte()),
            "Ver Michoacán" to Triple(19.2, -101.8, 7.toByte()))
        val regionButtons = regions.map { (name, center) -> button(name).also { control ->
            control.setOnClickListener { onRegion(center.first, center.second, center.third); onReady(); dialog.dismiss() }
        } }
        body("Descarga aproximada: 686 MiB. Se guarda aparte de la APK en el almacenamiento de Mustang OS. Puedes recibir mexico.map desde la PC e importarlo aquí. El mapa por Internet sigue siendo una opción separada.")
        val licenses = button("Avisos y licencias del mapa")
        dialog = AlertDialog.Builder(context).setTitle("Mapa México sin conexión")
            .setView(ScrollView(context).apply { addView(content) }).setPositiveButton("Cerrar", null).create()
        download.setOnClickListener { store.downloadMexico() }
        importing.setOnClickListener { dialog.dismiss(); onImport() }
        cancel.setOnClickListener { store.cancelDownload() }
        enabled.setOnCheckedChangeListener { _, value -> store.setEnabled(value) }
        licenses.setOnClickListener {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    listOf("mapsforge-NOTICE.txt", "LGPL-3.0.txt", "GPL-3.0.txt", "Apache-2.0.txt", "ODbL-1.0.txt").joinToString("\n\n") { file ->
                        runCatching { context.assets.open("licenses/$file").bufferedReader().use { it.readText() } }.getOrDefault(file)
                    }
                }
                if (isActive) AlertDialog.Builder(context).setTitle("Licencias del mapa").setView(ScrollView(context).apply {
                    addView(TextView(context).apply { this.text = text; setTextAppearance(R.style.Muted); setPadding(dp(16),dp(12),dp(16),dp(12)) })
                }).setPositiveButton("Cerrar", null).show()
            }
        }
        val owner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) dialog.dismiss() }
        owner?.lifecycle?.addObserver(observer)
        dialog.setOnDismissListener { scope.cancel(); owner?.lifecycle?.removeObserver(observer); onDismiss() }
        dialog.show()
        var last = store.state.value.map?.let { it.bytes to it.modified }
        scope.launch {
            store.state.collect { value ->
                val size = if (value.total > 0) "\n%.1f / %.1f MiB".format(Locale.getDefault(), value.downloaded / 1048576.0, value.total / 1048576.0) else ""
                val ready = value.map?.let { "\nDisponible: %.1f MiB · datos %s".format(Locale.getDefault(), it.bytes / 1048576.0, DateFormat.getDateInstance().format(Date(it.dataDate))) }.orEmpty()
                status.text = value.message + (if (value.busy) size else "") + ready
                progress.isIndeterminate = value.busy && value.total <= 0
                progress.progress = if (value.total > 0) (1000L * value.downloaded / value.total).coerceIn(0,1000).toInt() else 0
                download.isEnabled = !value.busy
                importing.isEnabled = !value.busy
                cancel.isEnabled = value.busy
                enabled.isEnabled = value.map != null
                regionButtons.forEach { it.isEnabled = value.map != null && value.enabled }
                if (enabled.isChecked != value.enabled) enabled.isChecked = value.enabled
                val current = value.map?.let { it.bytes to it.modified }
                if (current != null && current != last) onReady()
                last = current
            }
        }
        scope.launch { while (isActive) { store.refresh(); delay(1000) } }
    }
}
