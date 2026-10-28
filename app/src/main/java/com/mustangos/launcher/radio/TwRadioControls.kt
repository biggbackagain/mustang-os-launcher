package com.mustangos.launcher.radio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import kotlinx.coroutines.*

interface RadioControls {
    fun start(onAvailable: (Boolean) -> Unit)
    fun stop()
    fun submit(action: RadioAction): Boolean
}

/** A void AIDL acknowledgment proves delivery only, never a tuned station or an audible result. */
internal class VerifiedRadioBinder(private val binder: IBinder) {
    fun valid(): Boolean = runCatching { binder.isBinderAlive && binder.interfaceDescriptor == DESCRIPTOR }.getOrDefault(false)
    fun submit(action: RadioAction): Boolean {
        if (!valid()) return false
        val request = Parcel.obtain(); val response = Parcel.obtain()
        return try {
            request.writeInterfaceToken(DESCRIPTOR)
            if (!binder.transact(action.transaction, request, response, 0)) false
            else { response.readException(); true }
        } catch (_: Exception) { false }
        finally { request.recycle(); response.recycle() }
    }
    companion object { const val DESCRIPTOR = "com.tw.service.xt.aidl.ITWCommandAidl" }
}

/** Connects only to the installed, already-running XT service. Never auto-starts a vendor service. */
class TwRadioControls(context: Context) : RadioControls {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var endpoint: VerifiedRadioBinder? = null
    private var connection: ServiceConnection? = null
    private var revision = 0L
    override fun start(onAvailable: (Boolean) -> Unit) {
        stop()
        val stamp = revision
        scope.launch {
            val permitted = withContext(Dispatchers.IO) { runCatching {
                val service = app.packageManager.getServiceInfo(COMPONENT, 0)
                service.enabled && service.exported && service.applicationInfo.enabled &&
                    (service.permission.isNullOrBlank() || app.checkSelfPermission(service.permission) == PackageManager.PERMISSION_GRANTED)
            }.getOrDefault(false) }
            if (stamp != revision) return@launch
            if (!permitted) { onAvailable(false); return@launch }
            val listener = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    scope.launch {
                        val checked = withContext(Dispatchers.IO) { VerifiedRadioBinder(binder).takeIf { it.valid() } }
                        if (stamp == revision && name == COMPONENT) { endpoint = checked; onAvailable(checked != null) }
                    }
                }
                override fun onServiceDisconnected(name: ComponentName) { if (stamp == revision) { endpoint = null; onAvailable(false) } }
                override fun onBindingDied(name: ComponentName) { onServiceDisconnected(name) }
                override fun onNullBinding(name: ComponentName) { onServiceDisconnected(name) }
            }
            connection = listener
            val bound = runCatching { app.bindService(Intent(ACTION).setComponent(COMPONENT), listener, 0) }.getOrDefault(false)
            if (!bound) { connection = null; onAvailable(false) }
        }
    }
    override fun stop() {
        revision++; endpoint = null
        connection?.let { runCatching { app.unbindService(it) } }; connection = null
    }
    override fun submit(action: RadioAction): Boolean = endpoint?.submit(action) ?: false
    private companion object {
        const val ACTION = "com.tw.service.xt.CommandService.Bind"
        val COMPONENT = ComponentName("com.tw.service.xt", "com.tw.service.xt.CommandService")
    }
}
