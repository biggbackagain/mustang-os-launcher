package com.mustangos.launcher.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mustangos.launcher.R
import com.mustangos.launcher.navigation.NativeMapView

/** Expands the same map instance; no second renderer or competing location subscription. */
class MapScreen(
    private val context: Context,
    private val map: NativeMapView,
    initialStatus: String,
    onFollow: () -> Unit,
    onOpenNavigation: () -> Unit,
    onDismiss: () -> Unit
) {
    private val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    private val originalParent = map.parent as ViewGroup
    private val originalIndex = originalParent.indexOfChild(map)
    private val originalParams = map.layoutParams
    val status = TextView(context).apply { text = initialStatus; setTextAppearance(R.style.Muted) }
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(7, 11, 16))
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun button(context: Context, label: String, action: () -> Unit) = Button(context, null, 0, R.style.ActionButton).apply {
        text = label; minWidth = dp(64)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56)).apply { marginEnd = dp(8) }
        setOnClickListener { action() }
    }
    init {
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(20), dp(8), dp(12), dp(8))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply { text = "Mapa de la pantalla"; setTextAppearance(R.style.Title) })
                addView(status)
            })
            addView(button(context, "Cerrar") { dismiss() })
        }
        content.addView(header)
        originalParent.removeView(map)
        content.addView(map, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        content.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER; setPadding(dp(12), dp(8), dp(12), dp(8))
            addView(button(context, "−") { map.zoomOut() })
            addView(button(context, "+") { map.zoomIn() })
            addView(button(context, "Centrar GPS", onFollow))
            addView(button(context, "Abrir navegación", onOpenNavigation))
        })
        dialog.setContentView(content)
        dialog.setOnDismissListener {
            (map.parent as? ViewGroup)?.removeView(map)
            originalParent.addView(map, originalIndex.coerceAtMost(originalParent.childCount), originalParams)
            onDismiss()
        }
    }
    fun show() {
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
    fun dismiss() = dialog.dismiss()
}
