package com.mustangos.launcher

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import com.mustangos.core.MustangBody
import com.mustangos.core.MustangPaint
import com.mustangos.core.MustangProfile
import com.mustangos.core.MustangVersion
import com.mustangos.launcher.settings.CarPhotoCatalog
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CarPhotoCatalogTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val profile = MustangProfile(2020, MustangVersion.GT, MustangBody.FASTBACK, MustangPaint.GREEN)
    private fun entry(id: String = "blue_gt", asset: String = "cars/blue_gt.jpg") = JSONObject()
        .put("id", id).put("asset", asset).put("yearFrom", 2015).put("yearTo", 2023).put("referenceYear", 2018)
        .put("version", "GT").put("body", "FASTBACK").put("paint", "BLUE")
        .put("title", "Actual blue Mustang photograph").put("author", "Photo author")
        .put("license", "CC BY-SA 4.0").put("licenseUrl", "https://creativecommons.org/licenses/by-sa/4.0/")
        .put("source", "https://commons.wikimedia.org/wiki/File:Example.jpg").put("changes", "Thumbnail only")
    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        return ByteArrayOutputStream().use { bytes -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes); bitmap.recycle(); bytes.toByteArray() }
    }

    @Test fun decodesOffMainOnceAndReusesTheBoundedPhotoWithHonestCaptionAndCredits() = runBlocking {
        val image = png()
        val counts = ConcurrentHashMap<String, Int>()
        val catalog = CarPhotoCatalog(app) { path ->
            assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
            counts.merge(path, 1, Int::plus)
            ByteArrayInputStream(if (path.endsWith("catalog.json")) JSONArray().put(entry()).toString().toByteArray() else image)
        }
        val first = catalog.load(profile)
        assertEquals("blue_gt", first.referenceId)
        assertFalse(first.usesCalibratedStock)
        assertTrue(maxOf(first.bitmap!!.width, first.bitmap!!.height) <= 800)
        assertTrue(first.referenceCaption.contains("2018")); assertTrue(first.referenceCaption.contains("Azul"))
        assertFalse(first.description.contains("2020")); assertFalse(first.description.contains("Verde"))
        assertEquals("Photo author", first.credits!!.author)
        assertEquals("Thumbnail only", first.credits!!.changes)
        val second = catalog.load(profile.copy(paint = MustangPaint.BLACK))
        assertSame(first.bitmap, second.bitmap)
        assertEquals(1, counts["cars/catalog.json"]); assertEquals(1, counts["cars/blue_gt.jpg"])
        assertEquals(1, catalog.credits().size)
    }

    @Test fun corruptOrMissingAssetFallsBackToTheActualStockIdentity() = runBlocking {
        for (missing in listOf(true, false)) {
            val catalog = CarPhotoCatalog(app) { path ->
                if (path.endsWith("catalog.json")) ByteArrayInputStream(JSONArray().put(entry()).toString().toByteArray())
                else if (missing) throw FileNotFoundException() else ByteArrayInputStream("truncated image".toByteArray())
            }
            val result = catalog.load(profile)
            assertEquals("stock_2010", result.referenceId); assertTrue(result.usesCalibratedStock)
            assertNull(result.bitmap); assertNull(result.credits)
            assertTrue(result.description.contains("2010")); assertFalse(result.description.contains("2020"))
        }
    }

    @Test fun unsafePathsAndUnknownValuesCannotOpenOutsideTheBundledCatalog() = runBlocking {
        val attempted = mutableListOf<String>()
        val catalog = CarPhotoCatalog(app) { path ->
            attempted += path
            if (path != "cars/catalog.json") throw AssertionError("Unsafe asset path was opened")
            ByteArrayInputStream(JSONArray().put(entry(asset = "../private.jpg")).put(entry("future").put("version", "UNKNOWN_TRIM")).toString().toByteArray())
        }
        val result = catalog.load(profile)
        assertTrue(result.usesCalibratedStock)
        assertEquals(listOf("cars/catalog.json"), attempted)
    }

    @Test fun defaultStockDoesNotDecodeCatalogImagesOrEnableLightsForAnotherPhoto() = runBlocking {
        val catalog = CarPhotoCatalog(app) { path ->
            if (path != "cars/catalog.json") throw AssertionError("Default must retain its bundled calibrated image")
            ByteArrayInputStream(JSONArray().put(entry()).toString().toByteArray())
        }
        val result = catalog.load(MustangProfile.DEFAULT)
        assertTrue(result.usesCalibratedStock); assertNull(result.bitmap)
    }
}
