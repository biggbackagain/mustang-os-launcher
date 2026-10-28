package com.mustangos.core

/** Counts and bounded CAN opcode headers only. The API deliberately cannot receive payload contents. */
class OemEventDiagnostics {
    private data class Count(val received: Int = 0, val decoded: Int = 0)
    private data class Header(val arg1: Int, val arg2: Int, val bytes: Int?)
    private val channels = linkedMapOf<Int, Count>()
    private val headers = linkedMapOf<Header, Int>()
    private var omitted = 0
    fun clear() { channels.clear(); headers.clear(); omitted = 0 }
    fun record(code: Int, arg1: Int, arg2: Int, byteCount: Int?, decoded: Boolean) {
        if (code !in CODES) return
        val current = channels[code] ?: Count()
        channels[code] = Count(current.received + 1, current.decoded + if (decoded) 1 else 0)
        if (code != 1281) return
        if (arg1 !in 0..255 || arg2 !in 0..255 || (byteCount != null && byteCount !in 0..1024)) { omitted++; return }
        val header = Header(arg1, arg2, byteCount)
        if (header in headers || headers.size < 24) headers[header] = (headers[header] ?: 0) + 1 else omitted++
    }
    fun report(): String = buildString {
        appendLine("CANALES OEM · sesión actual (conteos, sin contenido de tramas)")
        for (code in CODES) {
            val count = channels[code] ?: Count()
            appendLine("$code: recibidos=${count.received}; interpretados=${count.decoded}")
        }
        for ((header, count) in headers) appendLine("CAN arg1=${header.arg1}; arg2=${header.arg2}; bytes=${header.bytes ?: "sin byte[]"}; recibidos=$count")
        if (omitted > 0) appendLine("Cabeceras adicionales omitidas: $omitted")
        append("Un evento sin interpretar no se presenta como estado del coche.")
    }
    companion object { val CODES = listOf(266, 514, 1281, 1285, 1288, 1291, 1292) }
}
