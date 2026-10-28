package com.mustangos.launcher.radio

import android.os.Message
import java.util.Locale

data class RadioPreset(val slot: Int, val band: Int?, val frequency: Int, val name: String? = null) {
    val frequencyText: String get() = formatFrequency(band, frequency)
}

data class RadioState(
    val reading: Boolean = false,
    val status: String = "Radio sin consultar",
    val radioSelected: Boolean? = null,
    val sourceFresh: Boolean = false,
    val band: Int? = null,
    val frequency: Int? = null,
    val stationName: String? = null,
    val radioText: String? = null,
    val presets: List<RadioPreset> = emptyList(),
    val controlsAvailable: Boolean = false,
    val commandPending: Boolean = false,
    val commandStatus: String? = null,
    val sourceAtMs: Long? = null
) {
    val frequencyText: String get() = formatFrequency(band, frequency)
    val canControl: Boolean get() = reading && sourceFresh && radioSelected == true && controlsAvailable && !commandPending
    val sourceLabel: String get() = when {
        !reading || !sourceFresh -> "Fuente de audio sin lectura actual"
        radioSelected == true -> "Radio activa"
        radioSelected == false -> "Otra fuente de audio activa"
        else -> "Esperando la fuente de audio"
    }
}

private fun formatFrequency(band: Int?, value: Int?): String = when {
    band == null || value == null -> "—"
    band == 2 -> "$value kHz"
    else -> String.format(Locale.US, "%.2f MHz", value / 100.0)
}

/** Only fields whose units and event mapping are present in the installed RadioModel. */
internal object RadioEvents {
    val codes = shortArrayOf(769, 1025, 1026, 1028, 1029)
    fun reduce(state: RadioState, event: Message, now: Long): RadioState = when (event.what) {
        769 -> state.copy(radioSelected = event.arg1 == 1, sourceFresh = true, sourceAtMs = now)
        1025 -> when (event.arg1) {
            1 -> if (event.arg2 in 0..2 && event.arg2 != state.band)
                state.copy(band = event.arg2, frequency = null, stationName = null, radioText = null, presets = emptyList()) else state
            2 -> if (event.arg2 in 1..30_000) {
                val changed = event.arg2 != state.frequency
                state.copy(frequency = event.arg2, stationName = if (event.obj is String) text(event.obj) else if (changed) null else state.stationName,
                    radioText = if (changed) null else state.radioText)
            } else state
            else -> state
        }
        1026 -> {
            val slot = event.arg1 and 255
            if (slot !in 0..17 || event.arg2 !in 0..30_000) state
            else if (event.arg2 == 0) state.copy(presets = state.presets.filterNot { it.slot == slot })
            // RadioActivity formats preset frequencies with the currently reported band.
            // The other arg1 bits have no demonstrated band meaning and are deliberately ignored.
            else state.copy(presets = (state.presets.filterNot { it.slot == slot } + RadioPreset(slot, state.band, event.arg2, text(event.obj))).sortedBy { it.slot })
        }
        1028 -> if (event.obj is String) state.copy(stationName = text(event.obj)) else state
        1029 -> if (event.obj is String) state.copy(radioText = text(event.obj)) else state
        else -> state
    }
    private fun text(value: Any?): String? = (value as? String)?.take(256)?.filterNot { it.code < 32 || it.code == 127 }?.trim()?.takeIf { it.isNotEmpty() }
}

/** Verified ITWCommandAidl methods. No source-selection, tuning-region or MCU configuration methods. */
enum class RadioAction(internal val transaction: Int) {
    NEXT(11), PREVIOUS(12), SEEK_NEXT(13), SEEK_PREVIOUS(14), NEXT_BAND(18)
}
