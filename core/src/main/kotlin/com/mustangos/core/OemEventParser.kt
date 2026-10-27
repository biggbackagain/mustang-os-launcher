package com.mustangos.core

sealed interface OemEvent {
    data class Voltage(val volts: Float) : OemEvent
    data class Temperature(val celsius: Float) : OemEvent
    data class Doors(val mask: Int) : OemEvent
    data object Heartbeat : OemEvent
}

/** Formats verified against the SystemUI/TWService code exported from the user's T100. */
object OemEventParser {
    fun parse(code: Int, arg1: Int, payload: ByteArray?, fahrenheitSource: Boolean = false): OemEvent? = when (code) {
        514 -> OemEvent.Heartbeat
        1292 -> {
            if (arg1 != 0 || payload == null || payload.size < 2 || payload[0].toInt() and 0x80 != 0) null
            else {
                val voltage = (((payload[0].toInt() and 0x7f) shl 8) or (payload[1].toInt() and 0xff)) / 100f
                voltage.takeIf { it in 3f..32f }?.let(OemEvent::Voltage)
            }
        }
        1288 -> {
            val raw = payload?.getOrNull(1)?.toInt()
            if (raw == null || raw !in -39..79) null
            else OemEvent.Temperature(if (fahrenheitSource) (raw - 32) / 1.8f else raw.toFloat())
        }
        1285 -> payload?.getOrNull(0)?.let { OemEvent.Doors(it.toInt() and 0xf8) }
        else -> null
    }
}
