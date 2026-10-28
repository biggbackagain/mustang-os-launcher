package com.mustangos.core

enum class MustangVersion(val label: String) {
    BASE("Base"), V6("V6"), ECOBOOST("EcoBoost"), GT("GT"), BULLITT("Bullitt"),
    BOSS_302("Boss 302"), MACH_1("Mach 1"), SHELBY("Shelby"),
    SHELBY_GT350("Shelby GT350"), SHELBY_GT500("Shelby GT500"), DARK_HORSE("Dark Horse")
}
enum class MustangBody(val label: String) { COUPE("Coupé"), FASTBACK("Fastback"), CONVERTIBLE("Convertible") }
enum class MustangPaint(val label: String, val argb: Int) {
    RED("Rojo", 0xffc72b3b.toInt()), BLUE("Azul", 0xff246bb4.toInt()), BLACK("Negro", 0xff16191f.toInt()),
    WHITE("Blanco", 0xffeceef1.toInt()), SILVER("Plata", 0xffb5bdc8.toInt()), GRAY("Gris", 0xff69737f.toInt()),
    YELLOW("Amarillo", 0xffebc532.toInt()), GREEN("Verde", 0xff2b7355.toInt()),
    ORANGE("Naranja", 0xffe17629.toInt()), BURGUNDY("Vino", 0xff722a42.toInt())
}

/** A presentation profile, never a source of CAN, engine or sensor configuration. */
data class MustangProfile(
    val year: Int = 2010,
    val version: MustangVersion = MustangVersion.V6,
    val body: MustangBody = MustangBody.CONVERTIBLE,
    val paint: MustangPaint = MustangPaint.RED
) {
    init { require(year in 1964..2099) }
    val title get() = "MUSTANG · $year"
    val subtitle get() = "${version.label} · ${body.label} · ${paint.label}"
    val header get() = "${body.label} · $year"
    val description get() = "Ford Mustang $year ${version.label}, ${body.label}, color ${paint.label.lowercase()}"
    companion object { val DEFAULT = MustangProfile() }
}
