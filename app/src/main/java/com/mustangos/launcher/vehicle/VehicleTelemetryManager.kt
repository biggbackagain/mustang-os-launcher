package com.mustangos.launcher.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VehicleTelemetry(val rpm: Int? = null, val coolantC: Float? = null, val voltage: Float? = null, val odometerKm: Long? = null)
interface VehicleTelemetrySource { suspend fun read(): VehicleTelemetry }
class VehicleTelemetryManager {
    private val mutable = MutableStateFlow(VehicleTelemetry())
    val state = mutable.asStateFlow()
    val status = "Sin adaptador OBD2 configurado"
    // No CAN/MCU access; a future verified OBD2 source implements VehicleTelemetrySource.
}
