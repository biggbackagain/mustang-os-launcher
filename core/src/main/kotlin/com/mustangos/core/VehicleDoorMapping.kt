package com.mustangos.core

/** Door positions are learned from a single physical opening, never guessed from vendor bit order. */
data class VehicleDoorMapping(val left: Int?=null,val right: Int?=null) {
    fun states(mask: Int?): Pair<Boolean?,Boolean?> {
        fun open(bit: Int?)=if(mask==null || !singleBit(bit)) null else mask and requireNotNull(bit)!=0
        return open(left) to open(right)
    }
    fun assignLeft(mask: Int): VehicleDoorMapping {
        require(singleBit(mask) && mask!=right); return copy(left=mask)
    }
    fun assignRight(mask: Int): VehicleDoorMapping {
        require(singleBit(mask) && mask!=left); return copy(right=mask)
    }
    companion object {
        fun singleBit(value: Int?)=value!=null && value in 8..128 && value and 0xf8==value && value and (value-1)==0
    }
}
