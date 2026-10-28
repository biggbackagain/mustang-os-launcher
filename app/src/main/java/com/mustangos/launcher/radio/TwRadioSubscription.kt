package com.mustangos.launcher.radio

import android.os.Handler
import java.lang.reflect.Modifier

interface RadioSubscription {
    fun open(handler: Handler): Boolean
    fun requestSnapshot()
    fun close()
}

/** Independent TWUtil client; reads only the radio channels observed in TWRadio/RadioModel. */
class TwRadioSubscription(private val loadClass: () -> Class<*> = { Class.forName("android.tw.john.TWUtil") }) : RadioSubscription {
    private var type: Class<*>? = null
    private var instance: Any? = null
    override fun open(handler: Handler): Boolean {
        check(instance == null)
        val found = loadClass()
        val factory = found.getMethod("open", Int::class.javaPrimitiveType, ShortArray::class.java, Int::class.javaPrimitiveType)
        check(Modifier.isStatic(factory.modifiers))
        found.getMethod("removeHandler", String::class.java); found.getMethod("stop"); found.getMethod("close")
        type = found
        instance = factory.invoke(null, 0, RadioEvents.codes.copyOf(), 0) ?: return false
        try { found.getMethod("addHandler", String::class.java, Handler::class.java).invoke(instance, HANDLER, handler) }
        catch (failure: Exception) { close(); throw failure }
        return true
    }
    override fun requestSnapshot() {
        val value = instance ?: return
        val write = type?.getMethod("write", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType) ?: return
        // RadioModel's initial read requests. None selects a source, frequency, region or preset.
        for (code in intArrayOf(769, 1025, 1028)) write.invoke(value, code, 255)
    }
    override fun close() {
        val value = instance ?: return
        instance = null
        type?.let { found ->
            runCatching { found.getMethod("removeHandler", String::class.java).invoke(value, HANDLER) }
            runCatching { found.getMethod("stop").invoke(value) }
            runCatching { found.getMethod("close").invoke(value) }
        }
        type = null
    }
    private companion object { const val HANDLER = "MustangOS.Radio.ReadOnly" }
}
