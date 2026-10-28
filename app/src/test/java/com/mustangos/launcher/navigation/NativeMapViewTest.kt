package com.mustangos.launcher.navigation

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], qualifiers = "w512dp-h512dp-mdpi")
class NativeMapViewTest {
    private var controller: ActivityController<Activity>? = null
    private var map: NativeMapView? = null
    private class Source(private val block: Boolean = false) : MapTileSource {
        val calls = CopyOnWriteArrayList<Pair<MapTileKey, Boolean>>()
        val tokens = CopyOnWriteArrayList<TileCancellation>()
        val entered = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val cancelCalls = AtomicInteger(0)
        override fun load(key: MapTileKey, networkEnabled: Boolean, cancellation: TileCancellation): MapTileData {
            calls += key to networkEnabled; tokens += cancellation
            cancellation.onCancel { cancelled.countDown() }
            entered.countDown()
            if (block) try { cancelled.await(3, TimeUnit.SECONDS) } catch (_: InterruptedException) { /* Cancellation fixture. */ }
            finished.countDown()
            return MapTileData()
        }
        override fun cancelAll() { cancelCalls.incrementAndGet(); tokens.forEach(TileCancellation::cancel) }
    }
    private fun attached(source: Source): NativeMapView {
        NativeMapView.tileSourceFactory = { source }
        val lifecycle = Robolectric.buildActivity(Activity::class.java).create().also { controller = it }
        val activity = lifecycle.get()
        return NativeMapView(activity).also {
            map = it; activity.setContentView(it, ViewGroup.LayoutParams(512, 512))
            lifecycle.start().resume().visible().windowFocusChanged(true)
            shadowOf(Looper.getMainLooper()).idle()
            // Robolectric's plain Activity has no real WindowManager visibility notification.
            // Model that notification rather than weakening the production foreground guard.
            val attachInfo = ReflectionHelpers.getField<Any>(it, "mAttachInfo")
            ReflectionHelpers.setField(attachInfo, "mWindowVisibility", View.VISIBLE)
            it.dispatchWindowVisibilityChanged(View.VISIBLE)
            it.measure(View.MeasureSpec.makeMeasureSpec(512, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(512, View.MeasureSpec.EXACTLY))
            it.layout(0, 0, 512, 512)
            assertTrue("Fixture must attach map to the visible Activity", it.isAttachedToWindow)
            assertTrue("Fixture must show map", it.isShown)
            assertEquals("Fixture window must be visible", View.VISIBLE, it.windowVisibility)
        }
    }
    private fun drain() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
    @After fun cleanup() {
        map?.setActive(false)
        controller?.pause()?.stop()?.destroy()
        NativeMapView.tileSourceFactory = null
        shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun constructionAndHeadlessDrawNeverReadTilesOrStartNetwork() {
        val source = Source(); NativeMapView.tileSourceFactory = { source }
        val view = NativeMapView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 512, 512)
        view.setLocation(19.4, -99.1, 5f); view.setNetworkEnabled(true); view.setActive(true)
        view.draw(Canvas(Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)))
        drain()
        assertTrue(source.calls.isEmpty())
        view.setActive(false)
    }
    @Test fun attachedInactiveMapStaysIdleAndActiveMapDefaultsToCacheOnly() {
        val source = Source(); val view = attached(source)
        var status = ""; view.onStatusChanged = { status = it }
        drain(); assertTrue(source.calls.isEmpty())
        assertTrue(status.contains("Toca centrar"))
        view.setActive(true); drain()
        assertTrue("After idle: attached=${view.isAttachedToWindow}, shown=${view.isShown}, window=${view.windowVisibility}, size=${view.width}x${view.height}", source.entered.await(3, TimeUnit.SECONDS))
        assertTrue(source.calls.all { !it.second })
        assertTrue(source.calls.all { it.first.zoom == 1 }) // neutral world; never a fabricated GPS point
        view.setActive(false)
    }
    @Test fun networkRequiresSeparateOptInAndCallbacksNeverDiscloseCoordinates() {
        val source = Source(); val view = attached(source)
        val statuses = mutableListOf<String>(); view.onStatusChanged = { statuses += it }
        view.setLocation(19.4326, -99.1332, 4f)
        view.setNetworkEnabled(true); drain(); assertTrue(source.calls.isEmpty())
        view.setActive(true); drain()
        assertTrue(source.entered.await(3, TimeUnit.SECONDS))
        assertTrue(source.calls.all { it.second })
        assertTrue(source.calls.all { it.first.zoom == 15 })
        assertFalse(statuses.any { it.contains("19.4326") || it.contains("99.1332") || it.contains("http") })
    }
    @Test fun hiddenOrStoppedMapCancelsInFlightWorkAndRejectsLateCallbacks() {
        val source = Source(block = true); val view = attached(source)
        val statuses = mutableListOf<String>(); view.onStatusChanged = { statuses += it }
        view.setNetworkEnabled(true); view.setActive(true); drain()
        assertTrue(source.entered.await(3, TimeUnit.SECONDS))
        view.setActive(false)
        val count = statuses.size
        assertTrue(source.cancelled.await(3, TimeUnit.SECONDS))
        assertTrue(source.finished.await(3, TimeUnit.SECONDS))
        drain()
        assertTrue(source.tokens.all { it.isCancelled })
        assertTrue(source.cancelCalls.get() > 0)
        assertEquals(count, statuses.size)
    }
    @Test fun hidingAttachedViewCancelsWithoutWaitingForDetach() {
        val source = Source(block = true); val view = attached(source)
        view.setActive(true); drain(); assertTrue(source.entered.await(3, TimeUnit.SECONDS))
        view.visibility = View.GONE
        assertTrue(source.cancelled.await(3, TimeUnit.SECONDS))
        assertTrue(source.tokens.all { it.isCancelled })
    }
}
