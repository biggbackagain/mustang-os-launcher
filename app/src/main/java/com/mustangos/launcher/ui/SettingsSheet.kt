package com.mustangos.launcher.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.ImageView
import android.graphics.Bitmap
import com.mustangos.core.MustangProfile
import com.mustangos.launcher.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/** Product settings stay separate from the technical compatibility report. */
class SettingsSheet(private val context: Context, onDismiss: () -> Unit) {
    private val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    private val view = LayoutInflater.from(context).inflate(R.layout.dialog_launcher_settings, null)
    private val rows = view.findViewById<LinearLayout>(R.id.settingsRows)
    init {
        dialog.setContentView(view)
        view.findViewById<Button>(R.id.closeSettings).setOnClickListener { dialog.dismiss() }
        val owner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) dialog.dismiss() }
        owner?.lifecycle?.addObserver(observer)
        dialog.setOnDismissListener { owner?.lifecycle?.removeObserver(observer); onDismiss() }
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun profile(profile: MustangProfile, photo: Bitmap?, caption: String? = null, description: String? = null, digital: Boolean = false): SettingsSheet = apply {
        view.findViewById<TextView>(R.id.settingsCarProfile).text = "${profile.year} · ${profile.subtitle}"
        view.findViewById<TextView>(R.id.settingsImageReference).setText(if (photo != null) R.string.vehicle_custom_image_caption else R.string.vehicle_stock_image_caption)
        caption?.let { view.findViewById<TextView>(R.id.settingsImageReference).text = it }
        view.findViewById<ImageView>(R.id.settingsCarImage).apply {
            contentDescription = context.getString(if (photo != null) R.string.vehicle_custom_image_caption else R.string.vehicle_description)
            description?.let { contentDescription = it }
            if (photo != null) setImageBitmap(photo)
            if (digital && photo == null) setImageDrawable(DigitalMustangDrawable(profile))
        }
    }
    fun section(title: String): SettingsSheet = apply {
        rows.addView(TextView(context).apply {
            text = title; setTextAppearance(R.style.Muted); letterSpacing = .1f
            setPadding(dp(4), dp(20), dp(4), dp(10))
        })
    }
    private fun textColumn(title: String, subtitle: String) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        addView(TextView(context).apply {
            text = title; setTextAppearance(R.style.Body); textSize = 18f
        })
        addView(TextView(context).apply {
            text = subtitle; setTextAppearance(R.style.Muted); textSize = 13f
            setPadding(0, dp(4), 0, 0)
        })
    }
    private fun row() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12)); minimumHeight = dp(72)
        setBackgroundResource(R.drawable.bg_button)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
    }
    fun action(title: String, subtitle: String, callback: () -> Unit): SettingsSheet = apply {
        rows.addView(row().apply {
            addView(textColumn(title, subtitle))
            addView(TextView(context).apply { text = "›"; textSize = 30f; setTextColor(Color.rgb(123, 217, 255)) })
            isClickable = true; isFocusable = true; contentDescription = "$title. $subtitle"
            val attrs = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            foreground = attrs.getDrawable(0); attrs.recycle()
            setOnClickListener { dialog.dismiss(); callback() }
        })
    }
    fun toggle(title: String, subtitle: String, checked: Boolean, callback: (Boolean) -> Unit): SettingsSheet = apply {
        rows.addView(row().apply {
            addView(textColumn(title, subtitle))
            val control = Switch(context).apply {
                isChecked = checked; contentDescription = title
                minimumWidth = dp(56); minimumHeight = dp(48)
                setOnCheckedChangeListener { _, enabled -> callback(enabled) }
            }
            addView(control)
            setOnClickListener { control.isChecked = !control.isChecked }
        })
    }
    fun show() {
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}
