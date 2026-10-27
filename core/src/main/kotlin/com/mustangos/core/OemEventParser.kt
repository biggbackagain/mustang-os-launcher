package com.mustangos.core

sealed interface OemEvent {
    data class Voltage(val volts: Float) : OemEvent
    data class Temperature(val celsius: Float) : OemEvent
    data class Doors(val mask: Int) : OemEvent
    data class Protocol(val profile: OemProtocol) : OemEvent
    data object Heartbeat : OemEvent
}

data class OemProtocol(val model: Int, val type: Int)

/** Formats verified against the SystemUI/TWService code exported from the user's T100. */
object OemEventParser {
    // TWService extracts these two protocol fields from event 266. Never retain/export the full version string.
    fun parseProtocol(code: Int, arg1: Int, value: String?): OemEvent.Protocol? {
        if (code != 266 || arg1 != 0 || value == null || value.length > 256) return null
        val part = value.split('-').getOrNull(2) ?: return null
        if (!part.matches(Regex("[0-9a-fA-F]{3}"))) return null
        return OemEvent.Protocol(OemProtocol(part.take(2).toInt(16), part.last().digitToInt(16)))
    }

    fun parse(code: Int, arg1: Int, payload: ByteArray?, fahrenheitSource: Boolean = false, protocol: OemProtocol? = null): OemEvent? = when (code) {
        514 -> OemEvent.Heartbeat
        1292 -> {
            if (arg1 != 0 || payload == null || payload.size < 2) null
            else {
                // StatusBar$5 reads getValue(byte[0], 0, 7) * 256 + (byte[1] & 0xFF), multiplied by 0.01.
                // The upper bit (0x80) on byte 0 is the data-ready flag from the MCU.
                val b0 = payload[0].toInt() and 0x7f
                val b1 = payload[1].toInt() and 0xff
                val voltage = ((b0 shl 8) or b1) / 100f
                voltage.takeIf { it in 3f..32f }?.let(OemEvent::Voltage)
            }
        }
        1288 -> {
            val raw = payload?.getOrNull(1)?.toInt()
            if (raw == null || raw !in -39..79) null
            else OemEvent.Temperature(if (fahrenheitSource) (raw - 32) / 1.8f else raw.toFloat())
        }
        1285 -> payload?.getOrNull(0)?.let { OemEvent.Doors(it.toInt() and 0xf8) }
        1281 -> if (protocol == OemProtocol(0x41, 2) && arg1 in intArrayOf(56, 34, 36, 96)) {
            payload?.getOrNull(0)?.let { OemEvent.Doors(it.toInt() and 0xf8) }
        } else null
        else -> null
    }
}
