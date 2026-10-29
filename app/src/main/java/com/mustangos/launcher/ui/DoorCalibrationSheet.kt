package com.mustangos.launcher.ui

import android.app.AlertDialog
import android.content.Context
import android.os.SystemClock
import android.widget.*
import androidx.lifecycle.*
import com.mustangos.launcher.R
import com.mustangos.launcher.settings.LauncherSettings
import com.mustangos.launcher.vehicle.OemReadOnlyManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Learns local artwork mapping only; never sends a door/MCU command. */
class DoorCalibrationSheet(private val context: Context,private val oem: OemReadOnlyManager,
    private val settings: LauncherSettings,private val onDismiss: () -> Unit) {
    fun show() {
        val owner=context as LifecycleOwner
        val started=SystemClock.elapsedRealtime(); var baseline=false; var job: Job?=null
        val column=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL; setPadding(28,12,28,16) }
        column.addView(TextView(context).apply { setTextAppearance(R.style.Body)
            text="Con el coche estacionado y «Datos del vehículo y luces» activo, cierra todas las puertas. Después abre solo la izquierda y asígnala; ciérrala y repite con la derecha. La asignación cambia únicamente el dibujo de Mustang OS." })
        val status=TextView(context).apply { setTextAppearance(R.style.Muted); setPadding(0,16,0,16) }; column.addView(status)
        fun assign(left: Boolean) {
            val sample=oem.state.value
            val mask=sample.doorMask
            val fresh=sample.sourceActive && sample.lastDoorMs?.let { it>=started && SystemClock.elapsedRealtime()-it<15000 }==true
            if(!baseline || !fresh || !com.mustangos.core.VehicleDoorMapping.singleBit(mask)) {
                Toast.makeText(context,"Primero cierra todas las puertas y después abre solo el lado que vas a asignar",Toast.LENGTH_LONG).show(); return
            }
            val updated=runCatching { if(left) settings.doorMapping.assignLeft(requireNotNull(mask)) else settings.doorMapping.assignRight(requireNotNull(mask)) }.getOrNull()
            if(updated==null) { Toast.makeText(context,"Esa señal ya pertenece al otro lado",Toast.LENGTH_LONG).show(); return }
            settings.doorMapping=updated; baseline=false
            status.text="Lado guardado. Cierra todas las puertas antes de asignar el otro."
        }
        for(left in listOf(true,false)) column.addView(Button(context).apply {
            text=if(left) "Asignar apertura izquierda" else "Asignar apertura derecha"
            setOnClickListener { assign(left) }
        })
        val dialog=AlertDialog.Builder(context).setTitle("Puertas del Mustang 3D").setView(ScrollView(context).apply { addView(column) })
            .setPositiveButton("Listo",null).setNeutralButton("Borrar asignación") { _,_ -> settings.doorMapping=com.mustangos.core.VehicleDoorMapping() }.create()
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_DESTROY) dialog.dismiss() }
        owner.lifecycle.addObserver(observer)
        dialog.setOnDismissListener { job?.cancel(); owner.lifecycle.removeObserver(observer); onDismiss() }
        dialog.setOnShowListener {
            job=owner.lifecycleScope.launch {
                oem.state.collect { sample ->
                    val newEvent=sample.lastDoorMs?.let { it>=started }==true
                    if(sample.sourceActive && newEvent && sample.doorMask==0) baseline=true
                    status.text=when {
                        !sample.sourceActive -> "Activa Datos del vehículo y luces para escuchar las aperturas."
                        !baseline -> "Esperando que cierres todas las puertas."
                        sample.doorMask==0 -> "Cierre recibido. Abre solo la puerta que vas a asignar."
                        else -> "Apertura recibida. Asigna el lado abierto."
                    }+"\nIzquierda: ${if(settings.doorMapping.left!=null) "asignada" else "pendiente"} · Derecha: ${if(settings.doorMapping.right!=null) "asignada" else "pendiente"}"
                }
            }
        }
        dialog.show()
    }
}
