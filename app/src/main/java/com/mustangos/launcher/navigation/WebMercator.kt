package com.mustangos.launcher.navigation

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/** Standard slippy-map coordinates. These functions do not assign a user's position. */
object WebMercator {
    const val MAX_LATITUDE = 85.0511287798066
    const val TILE_SIZE = 256
    const val MIN_ZOOM = 0
    const val MAX_ZOOM = 19
    private const val EARTH_RADIUS_METERS = 6_378_137.0
    data class Point(val x: Double, val y: Double)
    data class Coordinate(val latitude: Double, val longitude: Double)
    data class Placement(val key: MapTileKey, val left: Float, val top: Float)

    fun worldSize(zoom: Int): Double {
        require(zoom in MIN_ZOOM..MAX_ZOOM)
        return TILE_SIZE * (1 shl zoom).toDouble()
    }
    fun wrapX(value: Double, zoom: Int): Double {
        val size = worldSize(zoom)
        return ((value % size) + size) % size
    }
    fun project(latitude: Double, longitude: Double, zoom: Int): Point {
        require(latitude.isFinite() && longitude.isFinite())
        val size = worldSize(zoom)
        val radians = latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE) * PI / 180
        val x = ((longitude + 180) / 360) * size
        val y = (1 - ln(tan(radians) + 1 / cos(radians)) / PI) * size / 2
        return Point(wrapX(x, zoom), y.coerceIn(0.0, size))
    }
    fun unproject(x: Double, y: Double, zoom: Int): Coordinate {
        require(x.isFinite() && y.isFinite())
        val size = worldSize(zoom)
        val latitude = atan(sinh(PI * (1 - 2 * y.coerceIn(0.0, size) / size))) * 180 / PI
        return Coordinate(latitude, wrapX(x, zoom) / size * 360 - 180)
    }
    fun wrappedDelta(x: Double, centerX: Double, zoom: Int): Double {
        val size = worldSize(zoom)
        return ((x - centerX + size * 1.5) % size + size) % size - size / 2
    }
    fun metersPerPixel(latitude: Double, zoom: Int): Double =
        cos(latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE) * PI / 180) * 2 * PI * EARTH_RADIUS_METERS / worldSize(zoom)

    /** Only tile cells intersecting the visible rectangle, never a surrounding prefetch ring. */
    fun visibleTiles(center: Point, zoom: Int, width: Int, height: Int, limit: Int = 64): List<Placement> {
        if (width <= 0 || height <= 0 || limit <= 0) return emptyList()
        require(center.x.isFinite() && center.y.isFinite())
        val count = 1 shl zoom
        worldSize(zoom)
        val left = center.x - width / 2.0
        val top = center.y - height / 2.0
        val startX = floor(left / TILE_SIZE).toInt()
        val endX = floor((left + width - 0.000001) / TILE_SIZE).toInt()
        val startY = floor(top / TILE_SIZE).toInt().coerceAtLeast(0)
        val endY = floor((top + height - 0.000001) / TILE_SIZE).toInt().coerceAtMost(count - 1)
        if (endY < startY) return emptyList()
        // Real panels fit well under this bound; clamp oversized hostile layouts before allocating.
        val xCount = (endX.toLong() - startX + 1).coerceIn(0, limit.toLong()).toInt()
        val rows = (endY.toLong() - startY + 1).coerceIn(0, limit.toLong()).toInt()
        val result = ArrayList<Placement>(minOf(limit, xCount * rows))
        for (row in 0 until rows) for (column in 0 until xCount) {
            if (result.size >= limit) break
            val x = startX + column
            val y = startY + row
            val wrappedX = ((x % count) + count) % count
            result += Placement(MapTileKey(zoom, wrappedX, y), (x * TILE_SIZE - left).toFloat(), (y * TILE_SIZE - top).toFloat())
        }
        return result
    }
    fun rescale(point: Point, fromZoom: Int, toZoom: Int): Point {
        val scale = 2.0.pow(toZoom - fromZoom)
        val size = worldSize(toZoom)
        return Point(wrapX(point.x * scale, toZoom), (point.y * scale).coerceIn(0.0, size))
    }
}
