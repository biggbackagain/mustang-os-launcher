package com.mustangos.core

import org.junit.Assert.*
import org.junit.Test

class PhotoReferenceMatcherTest {
    private fun reference(id: String, from: Int = 2015, to: Int = 2023, year: Int = 2018,
        version: MustangVersion? = MustangVersion.GT, body: MustangBody? = MustangBody.FASTBACK,
        paint: MustangPaint? = MustangPaint.BLUE) = PhotoReference(id, "cars/$id.jpg", from, to, year, version, body, paint)

    @Test fun exactDefaultKeepsTheCalibratedIncludedImageEvenIfTheCatalogHasAlternatives() {
        val equivalent = reference("another", 2010, 2010, 2010, MustangVersion.V6, MustangBody.CONVERTIBLE, MustangPaint.RED)
        assertEquals("stock_2010", PhotoReferenceMatcher.select(MustangProfile.DEFAULT, listOf(equivalent)).id)
    }

    @Test fun yearRangePrecedesTrimFromAnUnrelatedGeneration() {
        val profile = MustangProfile(2020, MustangVersion.V6, MustangBody.CONVERTIBLE, MustangPaint.RED)
        val oldExactTrim = reference("old", 2010, 2014, 2010, MustangVersion.V6, MustangBody.CONVERTIBLE, MustangPaint.RED)
        val currentGeneration = reference("new", version = MustangVersion.GT, body = MustangBody.CONVERTIBLE)
        assertEquals("new", PhotoReferenceMatcher.select(profile, listOf(oldExactTrim, currentGeneration)).id)
    }

    @Test fun trimAndBodyPrecedePaintButExactPaintBreaksAnOtherwiseEquivalentChoice() {
        val profile = MustangProfile(2020, MustangVersion.GT, MustangBody.COUPE, MustangPaint.RED)
        val blueGt = reference("blue", paint = MustangPaint.BLUE)
        val redOtherTrim = reference("red-ecoboost", version = MustangVersion.ECOBOOST, paint = MustangPaint.RED)
        val redConvertible = reference("red-convertible", body = MustangBody.CONVERTIBLE, paint = MustangPaint.RED)
        assertEquals("blue", PhotoReferenceMatcher.select(profile, listOf(redOtherTrim, redConvertible, blueGt)).id)
        val redGt = reference("red", paint = MustangPaint.RED)
        assertEquals("red", PhotoReferenceMatcher.select(profile, listOf(blueGt, redGt)).id)
    }

    @Test fun coupeAndFastbackAreTheSameFixedRoofGroup() {
        assertTrue(PhotoReferenceMatcher.equivalentBody(MustangBody.COUPE, MustangBody.FASTBACK))
        assertFalse(PhotoReferenceMatcher.equivalentBody(MustangBody.COUPE, MustangBody.CONVERTIBLE))
        val profile = MustangProfile(2020, MustangVersion.GT, MustangBody.COUPE, MustangPaint.BLUE)
        assertEquals("roof", PhotoReferenceMatcher.select(profile,
            listOf(reference("convertible", body = MustangBody.CONVERTIBLE), reference("roof"))).id)
    }

    @Test fun unknownPaintDoesNotTurnIntoTheRequestedColorAndTheChosenRecordIsNotMutated() {
        val profile = MustangProfile(2020, MustangVersion.GT, MustangBody.FASTBACK, MustangPaint.GREEN)
        val blue = reference("blue")
        val selected = PhotoReferenceMatcher.select(profile, listOf(blue))
        assertSame(blue, selected)
        assertEquals(MustangPaint.BLUE, selected.paint)
        assertEquals(2018, selected.referenceYear)
        val unknown = reference("unknown", paint = null)
        assertEquals("unknown", PhotoReferenceMatcher.select(profile, listOf(blue, unknown)).id)
    }

    @Test fun emptyOrInvalidCatalogFallsBackToStockAndTiesAreStableById() {
        val profile = MustangProfile(2020, MustangVersion.GT, MustangBody.FASTBACK, MustangPaint.BLUE)
        assertEquals("stock_2010", PhotoReferenceMatcher.select(profile, emptyList()).id)
        val a = reference("a"); val b = reference("b")
        assertEquals("a", PhotoReferenceMatcher.select(profile, listOf(b, a)).id)
        assertEquals("a", PhotoReferenceMatcher.select(profile, listOf(a, b)).id)
    }

    @Test fun uncoveredYearsChooseTheNearestReferenceWithoutChangingItsActualYear() {
        val profile = MustangProfile(2025, MustangVersion.GT, MustangBody.FASTBACK, MustangPaint.BLUE)
        val newer = reference("near", 2015, 2023, 2023)
        assertEquals(2023, PhotoReferenceMatcher.select(profile, listOf(newer)).referenceYear)
    }
}
