package com.mustangos.core

import org.junit.Assert.*
import org.junit.Test

class VehicleDoorMappingTest {
    @Test fun unknownPositionDoesNotAnimateAnInventedDoor() {
        assertEquals(null to null,VehicleDoorMapping().states(128))
        assertEquals(null to null,VehicleDoorMapping(8,16).states(null))
    }
    @Test fun eachSideUsesOnlyItsLearnedBitAndZeroClosesBoth() {
        val mapping=VehicleDoorMapping().assignLeft(64).assignRight(8)
        assertEquals(true to false,mapping.states(64)); assertEquals(false to true,mapping.states(8))
        assertEquals(true to true,mapping.states(72)); assertEquals(false to false,mapping.states(0))
    }
    @Test fun multipleOpeningsInvalidBitsAndSameBitForBothSidesAreRejected() {
        for(mask in listOf(0,1,3,24,256,-1)) {
            try { VehicleDoorMapping().assignLeft(mask); fail("Ambiguous opening accepted") } catch(_: IllegalArgumentException) { }
        }
        try { VehicleDoorMapping(left=8).assignRight(8); fail("Same bit accepted twice") } catch(_: IllegalArgumentException) { }
    }
}
