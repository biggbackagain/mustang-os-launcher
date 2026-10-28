package com.mustangos.core

import org.junit.Assert.*
import org.junit.Test

class MustangProfileTest {
    @Test fun defaultPreservesTheOwnersCurrentCar() {
        assertEquals(2010, MustangProfile.DEFAULT.year)
        assertEquals(MustangVersion.V6, MustangProfile.DEFAULT.version)
        assertEquals(MustangBody.CONVERTIBLE, MustangProfile.DEFAULT.body)
        assertEquals(MustangPaint.RED, MustangProfile.DEFAULT.paint)
    }
    @Test fun versionBodyYearAndPaintRemainIndependent() {
        val profile = MustangProfile(2020, MustangVersion.SHELBY_GT500, MustangBody.FASTBACK, MustangPaint.BLUE)
        assertEquals("MUSTANG · 2020", profile.title)
        assertTrue(profile.subtitle.contains("Shelby GT500"))
        assertTrue(profile.subtitle.contains("Fastback"))
        assertTrue(profile.subtitle.contains("Azul"))
        assertFalse(profile.description.contains("4.0"))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidYearIsRejected() { MustangProfile(1200) }
}
