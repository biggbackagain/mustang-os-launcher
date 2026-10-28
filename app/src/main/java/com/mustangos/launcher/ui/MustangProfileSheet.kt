package com.mustangos.launcher.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.*
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.*
import com.mustangos.core.*
import com.mustangos.launcher.R
import com.mustangos.launcher.settings.CarPhotoCatalog
import kotlinx.coroutines.*
import java.util.Calendar

/** A draft editor: controls update the preview, only Confirm writes the profile. */
class MustangProfileSheet(
    private val context: Context,
    private val profile: MustangProfile,
    private val hasPhoto: Boolean,
    private val onSave: (MustangProfile) -> Unit,
    private val onChoosePhoto: () -> Unit,
    private val onClearPhoto: () -> Unit,
    private val onDismiss: () -> Unit,
    private val digital: Boolean = true,
    private val onVisualMode: (Boolean) -> Unit = {}
) {
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun show() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val catalog = CarPhotoCatalog(context)
        val calibratedPreview = com.mustangos.launcher.settings.CalibratedVehiclePreview(context)
        var previewJob: Job? = null
        var draft = profile
        var digitalMode = digital
        val maximum = maxOf(2027, Calendar.getInstance().get(Calendar.YEAR) + 1)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(8)); setBackgroundResource(R.drawable.bg_mustang_theme)
        }
        val families = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(families) }, LinearLayout.LayoutParams(-1,dp(98)))
        val columns = LinearLayout(context)
        root.addView(columns,LinearLayout.LayoutParams(-1,0,1f))
        val stage = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8),dp(4),dp(14),dp(4)) }
        columns.addView(stage,LinearLayout.LayoutParams(0,-1,1.2f))
        val title = TextView(context).apply { setTextAppearance(R.style.Body); textSize = 23f; gravity = Gravity.CENTER }
        val car = MustangCarView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER; lampOverlayEnabled = true }
        val caption = TextView(context).apply { setTextAppearance(R.style.Muted); gravity = Gravity.CENTER; textSize = 13f }
        stage.addView(title,LinearLayout.LayoutParams(-1,-2))
        stage.addView(car,LinearLayout.LayoutParams(-1,0,1f))
        stage.addView(caption,LinearLayout.LayoutParams(-1,-2))
        val controls = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6),0,dp(6),dp(12)) }
        columns.addView(ScrollView(context).apply { addView(controls) },LinearLayout.LayoutParams(0,-1,1f))
        fun label(text: String) { controls.addView(TextView(context).apply { this.text = text; setTextAppearance(R.style.Muted); setPadding(0,dp(10),0,dp(4)) }) }
        label("AÑO DEL VEHÍCULO")
        val year = EditText(context).apply { inputType = InputType.TYPE_CLASS_NUMBER; filters = arrayOf(InputFilter.LengthFilter(4)); setText(profile.year.toString()); contentDescription = "Año del Mustang" }
        controls.addView(year,LinearLayout.LayoutParams(-1,dp(48)))
        fun spinner(text: String, items: List<String>, selected: Int) = Spinner(context).also {
            label(text); it.adapter = ArrayAdapter(context,android.R.layout.simple_spinner_dropdown_item,items)
            it.setPopupBackgroundDrawable(context.getDrawable(R.drawable.bg_dialog)); it.setSelection(selected); it.minimumHeight = dp(48)
            controls.addView(it,LinearLayout.LayoutParams(-1,dp(48)))
        }
        val version = spinner("VERSIÓN",MustangVersion.entries.map { it.label },profile.version.ordinal).apply { contentDescription = "Versión del Mustang" }
        val body = spinner("CARROCERÍA",MustangBody.entries.map { it.label },profile.body.ordinal).apply { contentDescription = "Carrocería del Mustang" }
        label("PINTURA")
        val paints = LinearLayout(context)
        controls.addView(HorizontalScrollView(context).apply { addView(paints) },LinearLayout.LayoutParams(-1,dp(60)))
        val colorName = TextView(context).apply { setTextAppearance(R.style.Muted) }
        controls.addView(colorName)
        val useDigital = Switch(context).apply { text = "Vista digital · color y luces"; isChecked = digitalMode; minHeight = dp(52); setTextAppearance(R.style.Body) }
        controls.addView(useDigital)
        controls.addView(TextView(context).apply { setTextAppearance(R.style.Muted); textSize = 12f; text = "La vista digital representa la familia del Mustang y la pintura elegida; no reproduce cada pieza o edición exacta. La fotografía es una referencia aparte. Este perfil no modifica el vehículo ni el CAN." })
        fun button(text: String) = Button(context,null,0,R.style.ActionButton).apply { this.text = text }.also { controls.addView(it,LinearLayout.LayoutParams(-1,dp(52)).apply { topMargin = dp(8) }) }
        val choose = button(if(hasPhoto) "Guardar y cambiar mi foto" else "Guardar y elegir mi foto")
        val clear = button("Usar fotografías del catálogo")
        var previewLights = false
        val lights = button("Probar luces de la vista")
        val generationTiles = mutableMapOf<MustangGeneration, LinearLayout>()
        val colorButtons = mutableMapOf<MustangPaint, Button>()
        fun updatePreview() {
            previewJob?.cancel()
            val selectedYear = year.text.toString().toIntOrNull()
            if(selectedYear == null || selectedYear !in 1964..maximum) return
            draft = MustangProfile(selectedYear,MustangVersion.entries[version.selectedItemPosition],MustangBody.entries[body.selectedItemPosition],draft.paint)
            title.text = "${draft.year} · ${draft.version.label}"
            colorName.text = "${draft.paint.label} · ${draft.body.label}"
            generationTiles.forEach { (family,tile) -> tile.alpha = if(family == MustangGeneration.forYear(draft.year)) 1f else .55f; tile.isSelected = family == MustangGeneration.forYear(draft.year) }
            colorButtons.forEach { (paint, control) -> control.text = if(paint == draft.paint) "✓" else ""; control.isSelected = paint == draft.paint }
            car.digitalProfile = if(digitalMode) draft else null
            car.lampOverlayEnabled = digitalMode
            car.setHeadlightsOn(previewLights)
            lights.isEnabled = digitalMode
            if(digitalMode) {
                caption.text = "${MustangGeneration.forYear(draft.year).label} · representación digital estilizada"
                car.contentDescription = "Vista digital · ${draft.description}"
                val selected=draft
                if(calibratedPreview.supports(selected)) previewJob=scope.launch {
                    calibratedPreview.load(selected)?.let { bitmap ->
                        car.digitalProfile=null; car.setImageBitmap(bitmap)
                        caption.text="Vista de pintura · referencia 2010 · ${selected.paint.label}"
                        car.setHeadlightsOn(previewLights)
                    }
                }
            } else {
                val selection = draft
                caption.text = "Cargando referencia…"
                previewJob = scope.launch {
                    val image = catalog.load(selection)
                    if(image.bitmap != null) car.setImageBitmap(image.bitmap) else car.setImageResource(R.drawable.mustang_2010)
                    caption.text = image.referenceCaption
                    car.contentDescription = image.description
                }
            }
        }
        for(family in MustangGeneration.entries) {
            val tile = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(4),dp(3),dp(4),dp(3)); setBackgroundResource(R.drawable.bg_card)
                contentDescription = "${family.label}, ${family.first} a ${minOf(family.last,maximum)}"; isFocusable = true; isClickable = true
            }
            val sampleVersion = when(family) {
                MustangGeneration.CLASSIC,MustangGeneration.II -> MustangVersion.BASE
                MustangGeneration.S197 -> MustangVersion.V6
                else -> MustangVersion.GT
            }
            val sample = profile.copy(year=family.example,version=sampleVersion)
            val thumbnail=ImageView(context).apply { setImageDrawable(DigitalMustangDrawable(sample)); scaleType=ImageView.ScaleType.FIT_CENTER; importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }
            tile.addView(thumbnail,LinearLayout.LayoutParams(-1,dp(62)))
            scope.launch { catalog.load(sample).bitmap?.let { thumbnail.setImageBitmap(it) } }
            tile.addView(TextView(context).apply { text = family.label; setTextAppearance(R.style.Muted); gravity = Gravity.CENTER; textSize = 12f })
            tile.setOnClickListener { version.setSelection(sampleVersion.ordinal); year.setText(family.example.toString()); year.clearFocus(); updatePreview() }
            families.addView(tile,LinearLayout.LayoutParams(dp(142),dp(90)).apply { marginEnd = dp(8) }); generationTiles[family] = tile
        }
        for(paint in MustangPaint.entries) {
            val control = Button(context).apply {
                contentDescription = "Pintura ${paint.label}"; textSize = 21f; setTextColor(if(paint in listOf(MustangPaint.WHITE,MustangPaint.YELLOW,MustangPaint.SILVER)) Color.BLACK else Color.WHITE)
                minWidth = 0; minimumWidth = 0; setPadding(0,0,0,0)
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(paint.argb); setStroke(dp(2),0xffa3bad0.toInt()) }
                setOnClickListener { draft = draft.copy(paint = paint); updatePreview() }
            }
            paints.addView(control,LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginEnd = dp(8) }); colorButtons[paint] = control
        }
        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,id: Long) = updatePreview()
        }
        version.onItemSelectedListener = listener; body.onItemSelectedListener = listener
        year.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) = Unit
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) = updatePreview()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        useDigital.setOnCheckedChangeListener { _, checked -> digitalMode = checked; updatePreview() }
        lights.setOnClickListener { previewLights = !previewLights; car.setHeadlightsOn(previewLights,animate = true) }
        val dialog = AlertDialog.Builder(context).setTitle("Mi Mustang · elige tu vehículo").setView(root)
            .setPositiveButton("Confirmar vehículo",null).setNegativeButton("Cancelar",null).create()
        fun save(): Boolean {
            val selectedYear = year.text.toString().toIntOrNull()
            if(selectedYear == null || selectedYear !in 1964..maximum) { year.error = "Escribe un año entre 1964 y $maximum"; year.requestFocus(); return false }
            updatePreview(); onVisualMode(digitalMode); onSave(draft); return true
        }
        choose.setOnClickListener { digitalMode = false; if(save()) { dialog.dismiss(); onChoosePhoto() } }
        clear.setOnClickListener { digitalMode = false; if(save()) { dialog.dismiss(); onClearPhoto() } }
        val owner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _,event -> if(event == Lifecycle.Event.ON_DESTROY) dialog.dismiss() }
        owner?.lifecycle?.addObserver(observer)
        dialog.setOnDismissListener { scope.cancel(); car.stopAnimation(); owner?.lifecycle?.removeObserver(observer); onDismiss() }
        dialog.setOnShowListener {
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { if(save()) dialog.dismiss() }
            updatePreview()
        }
        dialog.show()
    }
}
