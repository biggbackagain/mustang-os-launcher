package com.mustangos.core

import kotlin.math.abs

/** Facts about a photograph, kept separate from the profile the user wants to represent. */
data class PhotoReference(
    val id: String,
    val asset: String?,
    val yearFrom: Int,
    val yearTo: Int,
    val referenceYear: Int,
    val version: MustangVersion?,
    val body: MustangBody?,
    val paint: MustangPaint?
) {
    init {
        require(id.isNotBlank())
        require(yearFrom in 1964..2099 && yearTo in yearFrom..2099 && referenceYear in yearFrom..yearTo)
    }
}

object PhotoReferenceMatcher {
    val stock = PhotoReference("stock_2010", null, 2010, 2010, 2010,
        MustangVersion.V6, MustangBody.CONVERTIBLE, MustangPaint.RED)

    fun equivalentBody(actual: MustangBody?, requested: MustangBody?): Boolean =
        actual != null && requested != null && (actual == requested ||
            actual != MustangBody.CONVERTIBLE && requested != MustangBody.CONVERTIBLE)

    fun select(profile: MustangProfile, references: List<PhotoReference>): PhotoReference {
        if (profile == MustangProfile.DEFAULT) return stock
        val candidates = references.filter { it.id != stock.id } + stock
        val inRange = candidates.filter { profile.year in it.yearFrom..it.yearTo }
        val pool = if (inRange.isNotEmpty()) inRange else {
            val distance = candidates.minOf { rangeDistance(profile.year, it) }
            candidates.filter { rangeDistance(profile.year, it) == distance }
        }
        return pool.sortedWith(compareByDescending<PhotoReference> { it.version == profile.version }
            .thenByDescending { equivalentBody(it.body, profile.body) }
            .thenByDescending { when (it.paint) { profile.paint -> 2; null -> 1; else -> 0 } }
            .thenBy { abs(it.referenceYear - profile.year) }
            .thenBy { it.id }).first()
    }
    private fun rangeDistance(year: Int, reference: PhotoReference) = when {
        year < reference.yearFrom -> reference.yearFrom - year
        year > reference.yearTo -> year - reference.yearTo
        else -> 0
    }
}
