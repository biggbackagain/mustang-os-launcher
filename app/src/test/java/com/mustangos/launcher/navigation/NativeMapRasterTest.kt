package com.mustangos.launcher.navigation

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], qualifiers = "w512dp-h512dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeMapRasterTest {
    private var controller: ActivityController<Activity>? = null
    private var map: NativeMapView? = null
    @After fun cleanup() {
        map?.setActive(false); controller?.pause()?.stop()?.destroy()
        NativeMapView.tileSourceFactory = null
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun tileBytes(): ByteArray {
        val tile = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tile)
        canvas.drawColor(Color.rgb(236, 239, 232))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(180, 211, 228) }
        canvas.drawRect(0f, 0f, 96f, 256f, paint)
        paint.color = Color.WHITE; paint.strokeWidth = 14f; canvas.drawLine(100f, 256f, 220f, 0f, paint)
        paint.color = Color.DKGRAY; paint.textSize = 14f; canvas.drawText("Prueba local", 105f, 120f, paint)
        val bytes = ByteArrayOutputStream().also { tile.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        tile.recycle()
        return bytes
    }
    private fun fixture(expired: Boolean = false): NativeMapView {
        val bytes = tileBytes()
        val loaded = CountDownLatch(4)
        val provider = object : MapTileSource {
            override fun load(key: MapTileKey, networkEnabled: Boolean, cancellation: TileCancellation): MapTileData {
                assertFalse("Raster fixture uses no network", networkEnabled)
                loaded.countDown()
                return MapTileData(bytes, if (expired) 1 else System.currentTimeMillis() + 604_800_000)
            }
            override fun cancelAll() = Unit
        }
        val view = attached(provider, false)
        var loadedStatus = false
        view.onStatusChanged = { if (it.contains("en caché")) loadedStatus = true }
        view.setActive(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
        assertTrue(loaded.await(3, TimeUnit.SECONDS))
        for (attempt in 0 until 30) {
            shadowOf(Looper.getMainLooper()).idle()
            if (loadedStatus) break
            Thread.sleep(10)
        }
        // Wait for all four visible cells through rendered output, including decoder/main deliveries.
        var allCells = false
        for (attempt in 0 until 100) {
            shadowOf(Looper.getMainLooper()).idle()
            val frame = render(view)
            allCells = listOf(128 to 128, 384 to 128, 128 to 384, 384 to 384)
                .all { (x, y) -> frame.getPixel(x, y) != Color.rgb(19, 26, 34) }
            frame.recycle()
            if (allCells) break
            Thread.sleep(10)
        }
        assertTrue("Decoded raster tiles reached the view", loadedStatus)
        assertTrue("All visible raster cells were decoded", allCells)
        return view
    }
    private fun attached(provider: MapTileSource, network: Boolean): NativeMapView {
        NativeMapView.tileSourceFactory = { provider }
        val lifecycle = Robolectric.buildActivity(Activity::class.java).create().also { controller = it }
        val view = NativeMapView(lifecycle.get()).also { map = it }
        lifecycle.get().setContentView(view, ViewGroup.LayoutParams(512, 512))
        lifecycle.start().resume().visible().windowFocusChanged(true)
        shadowOf(Looper.getMainLooper()).idle()
        val attachInfo = ReflectionHelpers.getField<Any>(view, "mAttachInfo")
        ReflectionHelpers.setField(attachInfo, "mWindowVisibility", View.VISIBLE)
        view.dispatchWindowVisibilityChanged(View.VISIBLE)
        view.measure(View.MeasureSpec.makeMeasureSpec(512, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(512, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 512, 512)
        view.setNetworkEnabled(network)
        return view
    }
    private fun awaitUi(predicate: () -> Boolean) {
        repeat(300) {
            shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) return
            Thread.sleep(10)
        }
        assertTrue("Tile callbacks settled on Main", predicate())
    }
    private fun render(view: View) = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    @Test fun rasterTilesUseNightPaletteAndPinRequiresAnActualLocation() {
        val view = fixture()
        val initial = render(view)
        assertNotEquals(Color.rgb(19, 26, 34), initial.getPixel(128, 128))
        assertTrue(Color.red(initial.getPixel(128, 128)) < 120)
        assertNotEquals(0xff3ba9ff.toInt(), initial.getPixel(256, 256))
        view.setLocation(19.4326, -99.1332, 5f, 45f)
        val located = render(view)
        assertEquals(0xff3ba9ff.toInt(), located.getPixel(256, 256))
        view.setLocation(null, null)
        val missing = render(view)
        assertNotEquals(0xff3ba9ff.toInt(), missing.getPixel(256, 256))
        val target = File("build/reports/screenshots/map-local-raster-night.png")
        target.parentFile?.mkdirs()
        target.outputStream().use { initial.compress(Bitmap.CompressFormat.PNG, 100, it) }
        initial.recycle(); located.recycle(); missing.recycle()
    }
    @Test fun resumedViewDoesNotFlashExpiredMemoryBeforeCacheRevalidation() {
        val view = fixture(expired = true)
        val loaded = render(view)
        assertNotEquals(Color.rgb(19, 26, 34), loaded.getPixel(128, 128)); loaded.recycle()
        view.setActive(false); view.setActive(true)
        val firstFrame = render(view)
        assertEquals(Color.rgb(19, 26, 34), firstFrame.getPixel(128, 128))
        firstFrame.recycle()
    }
    @Test fun successfulTilesCannotHideAnotherVisibleTileFailure() {
        val bytes = tileBytes()
        val failedStarted = CountDownLatch(1)
        val loaded = CountDownLatch(4)
        val provider = object : MapTileSource {
            override fun load(key: MapTileKey, networkEnabled: Boolean, cancellation: TileCancellation): MapTileData {
                loaded.countDown()
                if (key == MapTileKey(1, 0, 0)) { failedStarted.countDown(); return MapTileData() }
                failedStarted.await(3, TimeUnit.SECONDS)
                return MapTileData(bytes, System.currentTimeMillis() + 604_800_000)
            }
            override fun cancelAll() = Unit
        }
        val view = attached(provider, true)
        var status = ""; view.onStatusChanged = { status = it }
        view.setActive(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
        assertTrue(loaded.await(3, TimeUnit.SECONDS))
        awaitUi { !status.contains("Cargando") }
        assertTrue("Partial viewport failure remains visible after successes: $status", status.contains("sin conexión"))
    }
    @Test fun returningToFullyCachedViewportClearsFailuresFromAnotherZoomWithoutFetchingAgain() {
        val bytes = tileBytes()
        val loaded = CountDownLatch(4)
        val failed = CountDownLatch(4)
        val calls = java.util.concurrent.atomic.AtomicInteger(0)
        val provider = object : MapTileSource {
            override fun load(key: MapTileKey, networkEnabled: Boolean, cancellation: TileCancellation): MapTileData {
                calls.incrementAndGet()
                return if (key.zoom == 1) { loaded.countDown(); MapTileData(bytes, System.currentTimeMillis() + 604_800_000) }
                else { failed.countDown(); MapTileData() }
            }
            override fun cancelAll() = Unit
        }
        val view = attached(provider, true)
        var status = ""; view.onStatusChanged = { status = it }
        view.setActive(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
        assertTrue(loaded.await(3, TimeUnit.SECONDS)); awaitUi { !status.contains("Cargando") }
        assertFalse(status.contains("sin conexión"))
        view.zoomIn()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
        assertTrue(failed.await(3, TimeUnit.SECONDS)); awaitUi { !status.contains("Cargando") }
        assertTrue(status.contains("sin conexión"))
        val beforeReturn = calls.get()
        view.zoomOut()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
        assertEquals("Healthy viewport is already in memory", beforeReturn, calls.get())
        assertFalse("A different viewport's error cannot remain latched: $status", status.contains("sin conexión"))
    }
}
