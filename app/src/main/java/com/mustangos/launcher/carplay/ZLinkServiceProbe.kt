package com.mustangos.launcher.carplay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Reads only the descriptor of the exported service discovered in the user's manifest. */
class ZLinkServiceProbe(private val context: Context) {
    private val mutable = MutableStateFlow("Servicio público ZLink todavía no inspeccionado")
    val state = mutable.asStateFlow()
    private val handler = Handler(Looper.getMainLooper())
    private var bound = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (!bound) return
            mutable.value = try { "Interfaz Binder: ${service.interfaceDescriptor?.take(200) ?: "sin descriptor"}. No se invocaron métodos del servicio." }
                catch (_: Exception) { "El servicio no permitió consultar su descriptor" }
            stop()
        }
        override fun onServiceDisconnected(name: ComponentName) { if (bound) { mutable.value = "Servicio ZLink desconectado"; stop() } }
        override fun onNullBinding(name: ComponentName) { if (bound) { mutable.value = "ZLink devolvió una vinculación vacía"; stop() } }
        override fun onBindingDied(name: ComponentName) { if (bound) { mutable.value = "La vinculación ZLink terminó"; stop() } }
    }
    fun probe() {
        stop()
        try {
            val info = context.packageManager.getPermissionInfo(PERMISSION, 0)
            if (info.packageName != COMPONENT.packageName || info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE != PermissionInfo.PROTECTION_NORMAL ||
                context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                mutable.value = "Permiso ZLink no concedido como permiso normal; no se intenta acceder"; return
            }
            val service = context.packageManager.getServiceInfo(COMPONENT, 0)
            if (!service.exported || !service.enabled || service.permission != PERMISSION) {
                mutable.value = "La declaración del servicio ZLink difiere de la interfaz inspeccionada"; return
            }
            mutable.value = "Consultando el servicio ya activo de ZLink…"
            // Zero flags: never start/restart ZLink, activate it or create its service.
            bound = context.bindService(Intent().setComponent(COMPONENT), connection, 0)
            if (!bound) { mutable.value = "Servicio ZLink no activo o no vinculable; abre CarPlay antes de repetir"; return }
            handler.postDelayed({ mutable.value = "El servicio no respondió al diagnóstico"; stop() }, 5000)
        } catch (_: PackageManager.NameNotFoundException) { mutable.value = "No se encontró el permiso declarado por ZLink" }
          catch (_: RuntimeException) { mutable.value = "El firmware rechazó la vinculación de diagnóstico"; stop() }
    }
    fun stop() {
        handler.removeCallbacksAndMessages(null)
        if (bound) { bound = false; runCatching { context.unbindService(connection) } }
    }
    companion object {
        const val PERMISSION = "zjinnova.android.permission.ZLINK_SERVICE"
        val COMPONENT = ComponentName("com.zjinnova.zlink", "com.zjinnova.android.zlink.features.service.DaemonService")
    }
}
