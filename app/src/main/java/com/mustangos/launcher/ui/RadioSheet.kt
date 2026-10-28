package com.mustangos.launcher.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.mustangos.launcher.R
import com.mustangos.launcher.radio.RadioAction
import com.mustangos.launcher.radio.RadioIntegrationManager
import com.mustangos.launcher.radio.RadioState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** The panel observes radio data. Only a button can open OEM radio or submit a verified control. */
class RadioSheet(
    private val context: Context,
    private val manager: RadioIntegrationManager,
    private val onOpenOem: () -> Unit,
    private val onDismiss: () -> Unit
) {
    private val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val actions = mutableListOf<Button>()
    private val status = text("", R.style.Muted)
    private val source = text("", R.style.Body)
    private val frequency = text("—", R.style.Title).apply { textSize = 42f; gravity = Gravity.CENTER }
    private val station = text("", R.style.Body).apply { gravity = Gravity.CENTER }
    private val radioText = text("", R.style.Muted).apply { gravity = Gravity.CENTER }
    private val command = text("", R.style.Muted)
    private val presets = text("", R.style.Body)
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun text(value: String, style: Int) = TextView(context).apply {
        text = value; setTextAppearance(style); setPadding(0, dp(5), 0, dp(5))
    }
    private fun button(label: String, action: () -> Unit) = Button(context, null, 0, R.style.ActionButton).apply {
        text = label; minHeight = dp(52); setOnClickListener { action() }
    }
    init {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(10), dp(20), dp(16)); setBackgroundResource(R.drawable.bg_mustang_theme)
        }
        content.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text("Radio del equipo", R.style.Title), LinearLayout.LayoutParams(0, -2, 1f))
            addView(button("Cerrar") { dismiss() })
        })
        content.addView(status); content.addView(source); content.addView(frequency); content.addView(station); content.addView(radioText)
        content.addView(button("Escuchar radio") { onOpenOem() }, LinearLayout.LayoutParams(-1, dp(56)))
        content.addView(text("Para cambiar desde CarPlay, toca Escuchar radio. Los controles se habilitan cuando el equipo confirma que la radio es la fuente activa.", R.style.Muted))
        fun row(vararg entries: Pair<String, RadioAction>) {
            content.addView(LinearLayout(context).apply {
                entries.forEach { (label, action) ->
                    val control = button(label) { manager.submit(action) }.also(actions::add)
                    addView(control, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(6) })
                }
            })
        }
        row("Anterior" to RadioAction.PREVIOUS, "Siguiente" to RadioAction.NEXT)
        row("Buscar −" to RadioAction.SEEK_PREVIOUS, "Buscar +" to RadioAction.SEEK_NEXT, "Cambiar banda" to RadioAction.NEXT_BAND)
        content.addView(command)
        content.addView(text("Presintonías recibidas", R.style.Body)); content.addView(presets)
        dialog.setContentView(ScrollView(context).apply { addView(content) })
        val owner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (dialog.isShowing) manager.start()
                Lifecycle.Event.ON_STOP -> manager.stop()
                Lifecycle.Event.ON_DESTROY -> dismiss()
                else -> Unit
            }
        }
        owner?.lifecycle?.addObserver(observer)
        dialog.setOnDismissListener {
            owner?.lifecycle?.removeObserver(observer); manager.stop(); scope.cancel(); onDismiss()
        }
    }
    fun show() {
        dialog.show(); dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        manager.start()
        scope.launch { manager.state.collect(::render) }
    }
    fun dismiss() = dialog.dismiss()
    private fun render(state: RadioState) {
        status.text = state.status; source.text = state.sourceLabel
        frequency.text = state.frequencyText
        station.text = state.stationName ?: "Sin nombre RDS recibido"
        radioText.text = state.radioText ?: ""
        radioText.visibility = if (state.radioText == null) View.GONE else View.VISIBLE
        command.text = state.commandStatus ?: if (state.controlsAvailable) "" else "Controles directos no disponibles; puedes usar la radio original."
        actions.forEach { it.isEnabled = state.canControl }
        presets.text = if (state.presets.isEmpty()) "Sin presintonías recibidas" else state.presets.joinToString("\n") {
            "${it.slot + 1}. ${it.frequencyText}${it.name?.let { name -> " · $name" }.orEmpty()}"
        }
    }
}
