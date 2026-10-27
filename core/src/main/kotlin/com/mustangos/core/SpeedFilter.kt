package com.mustangos.core

data class SpeedSample(val metersPerSecond: Float, val accuracyMeters: Float, val speedAccuracy: Float?, val timeMs: Long)
data class SpeedReading(val kmh: Float? = null, val lastFixMs: Long? = null)

class SpeedFilter {
    private var reading = SpeedReading()
    fun reset(): SpeedReading { reading = SpeedReading(); return reading }
    fun update(sample: SpeedSample, nowMs: Long): SpeedReading {
        val age = nowMs - sample.timeMs
        if (!sample.metersPerSecond.isFinite() || sample.metersPerSecond !in 0f..100f ||
            !sample.accuracyMeters.isFinite() || sample.accuracyMeters !in 0f..50f ||
            sample.speedAccuracy?.let { !it.isFinite() || it !in 0f..5f } == true ||
            age !in 0..5_000 || sample.timeMs <= (reading.lastFixMs ?: -1)) return current(nowMs)
        val fresh = sample.metersPerSecond * 3.6f
        val previous = current(nowMs).kmh
        val filtered = when { fresh < 1f -> 0f; previous == null -> fresh; else -> previous + 0.4f * (fresh - previous) }
        reading = SpeedReading(filtered, sample.timeMs)
        return reading
    }
    fun current(nowMs: Long): SpeedReading = if (reading.lastFixMs?.let { nowMs - it <= 5_000 } == true) reading else SpeedReading()
}
