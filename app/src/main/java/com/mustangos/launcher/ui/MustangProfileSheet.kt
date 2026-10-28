package com.mustangos.launcher.ui

import android.app.AlertDialog
import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.AdapterView
import android.view.View
import android.graphics.drawable.GradientDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.mustangos.core.MustangBody
import com.mustangos.core.MustangPaint
import com.mustangos.core.MustangProfile
import com.mustangos.core.MustangVersion
import com.mustangos.launcher.R
import java.util.Calendar

class MustangProfileSheet(
    private val context: Context,
    private val profile: MustangProfile,
    private val hasPhoto: Boolean,
    private val onSave: (MustangProfile) -> Unit,
    private val onChoosePhoto: () -> Unit,
    private val onClearPhoto: () -> Unit,
    private val onDismiss: () -> Unit
) {
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun show() {
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(16)) }
        fun label(text: String) { content.addView(TextView(context).apply { this.text = text; setTextAppearance(R.style.Body); setPadding(0, dp(14), 0, dp(6)) }) }
        fun spinner(labels: List<String>, selected: Int) = Spinner(context).also {
            it.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, labels)
            it.setPopupBackgroundDrawable(context.getDrawable(R.drawable.bg_dialog))
            it.minimumHeight = dp(48); it.setSelection(selected)
            content.addView(it, LinearLayout.LayoutParams(-1, dp(52)))
        }
        label("Año")
        val year = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; filters = arrayOf(InputFilter.LengthFilter(4))
            setText(profile.year.toString()); contentDescription = "Año del Mustang"
        }
        content.addView(year, LinearLayout.LayoutParams(-1, dp(52)))
        label("Versión")
        val version = spinner(MustangVersion.entries.map { it.label }, profile.version.ordinal)
        version.contentDescription = "Versión del Mustang"
        label("Carrocería")
        val body = spinner(MustangBody.entries.map { it.label }, profile.body.ordinal)
        body.contentDescription = "Carrocería del Mustang"
        label("Color")
        val paint = spinner(MustangPaint.entries.map { it.label }, profile.paint.ordinal)
        paint.contentDescription = "Color del Mustang"
        val colorSample = View(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        content.addView(colorSample, LinearLayout.LayoutParams(-1, dp(12)))
        paint.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                colorSample.background = GradientDrawable().apply { cornerRadius = dp(5).toFloat(); setColor(MustangPaint.entries[position].argb) }
            }
        }
        label("Imagen de referencia")
        content.addView(TextView(context).apply {
            setTextAppearance(R.style.Muted)
            text = "El catálogo elige automáticamente una referencia por año, versión, carrocería y color disponible. Si no hay coincidencia exacta se indica como aproximada. Puedes usar tu propia foto. Los faros animados se reservan para la imagen original calibrada."
        })
        val choose = Button(context, null, 0, R.style.ActionButton).apply { text = if (hasPhoto) "Guardar y cambiar foto" else "Guardar y elegir foto" }
        val clear = Button(context, null, 0, R.style.ActionButton).apply { text = "Guardar y usar fotos automáticas"; isEnabled = hasPhoto }
        content.addView(choose, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) })
        content.addView(clear, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        val dialog = AlertDialog.Builder(context).setTitle("Mi Mustang")
            .setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton("Guardar", null).setNegativeButton("Cancelar", null).create()
        fun save(): Boolean {
            val selectedYear = year.text.toString().toIntOrNull()
            val maximum = maxOf(2027, Calendar.getInstance().get(Calendar.YEAR) + 1)
            if (selectedYear == null || selectedYear !in 1964..maximum) { year.error = "Escribe un año entre 1964 y $maximum"; year.requestFocus(); return false }
            onSave(MustangProfile(selectedYear, MustangVersion.entries[version.selectedItemPosition], MustangBody.entries[body.selectedItemPosition], MustangPaint.entries[paint.selectedItemPosition]))
            return true
        }
        choose.setOnClickListener { if (save()) { dialog.dismiss(); onChoosePhoto() } }
        clear.setOnClickListener { if (save()) { dialog.dismiss(); onClearPhoto() } }
        val owner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) dialog.dismiss() }
        owner?.lifecycle?.addObserver(observer)
        dialog.setOnDismissListener { owner?.lifecycle?.removeObserver(observer); onDismiss() }
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { if (save()) dialog.dismiss() } }
        dialog.show()
    }
}
