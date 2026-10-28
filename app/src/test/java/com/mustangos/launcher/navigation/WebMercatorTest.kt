package com.mustangos.launcher.navigation

import org.junit.Assert.*
import org.junit.Test

class WebMercatorTest {
    @Test fun publishedSlippyMapCoordinatesAndRoundTripRemainAccurate() {
        val origin = WebMercator.project(0.0, 0.0, 1)
        assertEquals(256.0, origin.x, 0.00001); assertEquals(256.0, origin.y, 0.00001)
        for (zoom in listOf(0, 1, 9, 19)) for ((lat, lon) in listOf(19.4326 to -99.1332, -33.86 to 151.2, 80.0 to 179.99)) {
            val projected = WebMercator.project(lat, lon, zoom)
            val restored = WebMercator.unproject(projected.x, projected.y, zoom)
            assertEquals(lat, restored.latitude, 0.000001); assertEquals(lon, restored.longitude, 0.000001)
        }
    }
    @Test fun polesClampAndAntimeridianWrapWithoutInvalidTileIndices() {
        assertEquals(0.0, WebMercator.project(90.0, 180.0, 4).x, 0.0001)
        assertEquals(0.0, WebMercator.project(90.0, 180.0, 4).y, 0.0001)
        assertEquals(-2.0, WebMercator.wrappedDelta(255.0, 1.0, 0), 0.0001)
        val placements = WebMercator.visibleTiles(WebMercator.Point(0.0, 0.0), 2, 700, 500)
        assertTrue(placements.isNotEmpty())
        assertTrue(placements.all { it.key.x in 0..3 && it.key.y in 0..3 })
        assertTrue(placements.all { it.top >= 250f })
    }
    @Test fun viewportLoadsOnlyIntersectingTilesAndNeverAddsPrefetchRing() {
        val one = WebMercator.visibleTiles(WebMercator.Point(128.0, 128.0), 4, 256, 256)
        assertEquals(listOf(MapTileKey(4, 0, 0)), one.map { it.key })
        assertEquals(0f, one.single().left); assertEquals(0f, one.single().top)
        val actualPanel = WebMercator.visibleTiles(WebMercator.project(19.4, -99.1, 15), 15, 1280, 720)
        assertTrue(actualPanel.size in 15..24)
        assertTrue(actualPanel.all { it.left < 1280 && it.left + 256 > 0 && it.top < 720 && it.top + 256 > 0 })
        assertTrue(WebMercator.visibleTiles(WebMercator.Point(0.0, 0.0), 19, Int.MAX_VALUE, Int.MAX_VALUE).size <= 64)
        assertTrue(WebMercator.visibleTiles(WebMercator.Point(0.0, 0.0), 1, 0, 10).isEmpty())
    }
    @Test fun zoomPreservesCoordinateAndScaleUsesLatitude() {
        val original = WebMercator.project(19.4, -99.1, 8)
        val scaled = WebMercator.rescale(original, 8, 15)
        val expected = WebMercator.project(19.4, -99.1, 15)
        assertEquals(expected.x, scaled.x, 0.00001); assertEquals(expected.y, scaled.y, 0.00001)
        assertEquals(WebMercator.metersPerPixel(0.0, 10) / 2, WebMercator.metersPerPixel(60.0, 10), 0.0001)
    }
    @Test fun untrustedNonFiniteCoordinatesAndInvalidKeysAreRejected() {
        for (action in listOf<() -> Any>({ WebMercator.project(Double.NaN, 0.0, 1) },
            { WebMercator.unproject(0.0, Double.POSITIVE_INFINITY, 1) }, { WebMercator.worldSize(20) },
            { MapTileKey(3, -1, 0) }, { MapTileKey(3, 0, 8) })) {
            assertThrows(IllegalArgumentException::class.java) { action() }
        }
    }
}
