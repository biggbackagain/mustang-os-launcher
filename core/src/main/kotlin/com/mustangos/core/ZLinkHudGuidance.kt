package com.mustangos.core

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Types 8/9 carry iAP2 RouteGuidanceUpdate / RouteGuidanceManeuverUpdate parameters with the same 16-bit TLV layout
 * that the music frames proved on the unit. Only fields with the expected width are applied; anything else is ignored.
 */
data class ZLinkManeuver(
    val index: Int, val description: String?=null, val type: Int?=null, val afterRoad: String?=null,
    val distanceMeters: Long?=null, val distanceText: String?=null, val distanceUnits: Int?=null,
    val drivingSide: Int?=null, val junctionType: Int?=null, val exitAngle: Int?=null
)

data class ZLinkRouteGuidance(
    val state: Int?=null, val maneuverState: Int?=null, val currentRoad: String?=null, val destination: String?=null,
    val etaEpochSeconds: Long?=null, val timeRemainingSeconds: Long?=null,
    val distanceRemainingMeters: Long?=null, val distanceRemainingText: String?=null, val distanceRemainingUnits: Int?=null,
    val distanceToManeuverMeters: Long?=null, val distanceToManeuverText: String?=null, val distanceToManeuverUnits: Int?=null,
    val currentList: List<Int> = emptyList(), val maneuvers: Map<Int,ZLinkManeuver> = emptyMap(),
    val lastManeuver: Int?=null, val updatedAtMs: Long=0
) {
    /** RouteGuidanceState: 0 no route, 1 route set, 2 arrived, 3 loading, 4 locating, 5 rerouting, 6 proceed to route. */
    val active: Boolean get() = state in setOf(1,3,4,5,6) && (current!=null || distanceToManeuverText!=null || distanceToManeuverMeters!=null)
    val arrived: Boolean get() = state==2
    val current: ZLinkManeuver? get() = currentList.firstNotNullOfOrNull { maneuvers[it] } ?: lastManeuver?.let { maneuvers[it] }
    val next: ZLinkManeuver? get() = currentList.drop(1).firstNotNullOfOrNull { maneuvers[it] }
    val stateLabel: String? get() = when(state) { 3 -> "Calculando ruta"; 4 -> "Buscando ubicación"; 5 -> "Recalculando"
        6 -> "Dirígete a la ruta"; 2 -> "Llegaste a tu destino"; else -> null }
    val distanceToManeuver: String? get() = format(distanceToManeuverText,distanceToManeuverUnits,distanceToManeuverMeters)
    val distanceRemaining: String? get() = format(distanceRemainingText,distanceRemainingUnits,distanceRemainingMeters)

    fun apply(update: ZLinkRouteGuidanceDelta,now: Long): ZLinkRouteGuidance {
        val f=update.fields
        val ended=f.state==0
        val base=if(ended) ZLinkRouteGuidance() else this
        return base.copy(
            state=f.state ?: base.state, maneuverState=f.maneuverState ?: base.maneuverState,
            currentRoad=if(update.has(3)) f.currentRoad else base.currentRoad,
            destination=if(update.has(4)) f.destination else base.destination,
            etaEpochSeconds=f.etaEpochSeconds ?: base.etaEpochSeconds, timeRemainingSeconds=f.timeRemainingSeconds ?: base.timeRemainingSeconds,
            distanceRemainingMeters=f.distanceRemainingMeters ?: base.distanceRemainingMeters,
            distanceRemainingText=if(update.has(8)) f.distanceRemainingText else base.distanceRemainingText,
            distanceRemainingUnits=f.distanceRemainingUnits ?: base.distanceRemainingUnits,
            distanceToManeuverMeters=f.distanceToManeuverMeters ?: base.distanceToManeuverMeters,
            distanceToManeuverText=if(update.has(11)) f.distanceToManeuverText else base.distanceToManeuverText,
            distanceToManeuverUnits=f.distanceToManeuverUnits ?: base.distanceToManeuverUnits,
            currentList=if(update.has(13)) f.currentList else base.currentList, updatedAtMs=now)
    }
    fun apply(maneuver: ZLinkManeuver,now: Long): ZLinkRouteGuidance {
        val merged=(maneuvers+(maneuver.index to maneuver)).let { all -> if(all.size<=64) all else all.entries.sortedBy { it.key }.takeLast(64).associate { it.toPair() } }
        return copy(maneuvers=merged,lastManeuver=maneuver.index,updatedAtMs=now)
    }
    companion object {
        fun unitLabel(units: Int?) = when(units) { 0 -> "km"; 1 -> "mi"; 2 -> "m"; 3 -> "yd"; 4 -> "ft"; else -> null }
        fun format(text: String?,units: Int?,meters: Long?): String? {
            val unit=unitLabel(units)
            if(text!=null) return if(unit!=null && !text.endsWith(unit)) "$text $unit" else text
            val m=meters ?: return null
            return if(m>=1000) "%.1f km".format(java.util.Locale.US,m/1000.0) else "${(m/10)*10} m"
        }
    }
}

class ZLinkRouteGuidanceDelta(val ids: Set<Int>,val fields: ZLinkRouteGuidance) { fun has(id: Int)=id in ids }

object ZLinkHudGuidanceDecoder {
    private class Fields(val map: Map<Int,ByteArray>) {
        fun number(id: Int,vararg widths: Int): Long? = map[id]?.takeIf { it.size in widths }?.fold(0L) { n,b -> (n shl 8) or (b.toLong() and 255) }
        fun signed16(id: Int): Int? = map[id]?.takeIf { it.size==2 }?.let { ((it[0].toInt() shl 8) or (it[1].toInt() and 255)).toShort().toInt() }
        fun text(id: Int): String? = map[id]?.takeIf { it.size<=1024 }?.let { bytes ->
            runCatching { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString() }.getOrNull()
                ?.trimEnd('\u0000')?.filter { !it.isISOControl() }?.trim()?.takeIf { it.isNotEmpty() }
        }
        fun list16(id: Int): List<Int>? = map[id]?.takeIf { it.size%2==0 && it.size<=128 }?.let { b -> (b.indices step 2).map { ((b[it].toInt() and 255) shl 8) or (b[it+1].toInt() and 255) } }
    }
    private fun fields(body: ByteArray): Fields {
        require(body.size<=ZLinkHudFrameReader.MAX_PAYLOAD_BYTES)
        val map=LinkedHashMap<Int,ByteArray>(); var offset=0
        fun u16(i: Int)=((body[i].toInt() and 255) shl 8) or (body[i+1].toInt() and 255)
        while(offset<body.size) {
            require(map.size<128 && body.size-offset>=4) { "Invalid TLV boundary" }
            val length=u16(offset); val id=u16(offset+2)
            require(length>=4 && length<=body.size-offset) { "Invalid TLV length" }
            map.putIfAbsent(id,body.copyOfRange(offset+4,offset+length)); offset+=length
        }
        require(map.isNotEmpty())
        return Fields(map)
    }
    fun route(body: ByteArray): ZLinkRouteGuidanceDelta {
        val f=fields(body)
        val state=f.number(1,1)?.toInt()?.takeIf { it in 0..6 }
        val value=ZLinkRouteGuidance(state=state,maneuverState=f.number(2,1)?.toInt(),currentRoad=f.text(3),destination=f.text(4),
            etaEpochSeconds=f.number(5,8,4),timeRemainingSeconds=f.number(6,8,4),distanceRemainingMeters=f.number(7,4,8),
            distanceRemainingText=f.text(8),distanceRemainingUnits=f.number(9,1)?.toInt(),distanceToManeuverMeters=f.number(10,4,8),
            distanceToManeuverText=f.text(11),distanceToManeuverUnits=f.number(12,1)?.toInt(),currentList=f.list16(13) ?: emptyList())
        // A route frame without a recognisable state or distance is not trusted as guidance.
        require(state!=null || value.distanceToManeuverMeters!=null || value.distanceToManeuverText!=null || value.currentList.isNotEmpty())
        return ZLinkRouteGuidanceDelta(f.map.keys,value)
    }
    fun maneuver(body: ByteArray): ZLinkManeuver {
        val f=fields(body)
        val index=requireNotNull(f.number(1,2)?.toInt()) { "Maneuver without index" }
        return ZLinkManeuver(index,f.text(2),f.number(3,1)?.toInt(),f.text(4),f.number(5,4,8),f.text(6),f.number(7,1)?.toInt(),
            f.number(8,1)?.toInt(),f.number(9,1)?.toInt(),f.signed16(11))
    }
}

/** Visual families of iAP2/CarPlay maneuver types; the iPhone's own description text always accompanies them. */
enum class ManeuverGlyph { STRAIGHT, LEFT, RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, SHARP_LEFT, SHARP_RIGHT, UTURN, KEEP_LEFT, KEEP_RIGHT,
    EXIT_LEFT, EXIT_RIGHT, ROUNDABOUT, ARRIVE, FERRY, START;
    companion object {
        fun of(type: Int?,drivingSide: Int?=null): ManeuverGlyph = when(type) {
            1,20 -> LEFT; 2,21 -> RIGHT; 3,5,51 -> STRAIGHT; 4,18,19,26 -> UTURN
            6,7,in 28..46 -> ROUNDABOUT; 8,23,53 -> EXIT_RIGHT; 22,52 -> EXIT_LEFT; 9 -> if(drivingSide==1) SLIGHT_LEFT else SLIGHT_RIGHT
            10,12,24,25,27 -> ARRIVE; 11 -> START; 13 -> KEEP_LEFT; 14 -> KEEP_RIGHT; 15,16,17 -> FERRY
            47 -> SHARP_LEFT; 48 -> SHARP_RIGHT; 49 -> SLIGHT_LEFT; 50 -> SLIGHT_RIGHT
            else -> STRAIGHT
        }
    }
}
