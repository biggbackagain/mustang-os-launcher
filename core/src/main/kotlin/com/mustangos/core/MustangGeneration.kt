package com.mustangos.core

/** Visual families, not an engine/VIN catalog or a CAN configuration. */
enum class MustangGeneration(val label: String, val first: Int, val last: Int, val example: Int) {
    CLASSIC("Clásico", 1964, 1973, 1965), II("Mustang II", 1974, 1978, 1977),
    FOX("Fox Body", 1979, 1993, 1990), SN95("SN95 / New Edge", 1994, 2004, 1999),
    S197("S197", 2005, 2014, 2010), S550("S550", 2015, 2023, 2020), S650("S650", 2024, 2099, 2024);
    companion object { fun forYear(year: Int) = entries.first { year in it.first..it.last } }
}
