package com.mustangos.launcher.navigation

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mapsforge.map.reader.MapFile
import org.mapsforge.core.model.MapPosition
import android.view.ViewConfiguration
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.hypot

/** Lightweight, opt-in Android map. It never embeds or controls the CarPlay video surface. */
class NativeMapView(context: Context) : FrameLayout(context) {
    private data class Image(val bitmap: Bitmap, val expiresAtMs: Long, val cacheable: Boolean, val stale: Boolean)
    private data class Request(val cancellation: TileCancellation, var future: Future<*>? = null)
    private data class Position(val latitude: Double, val longitude: Double, val accuracy: Float?, val bearing: Float?)
    private val offlineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var offlineStore: OfflineMapStore? = null
    private var offlineEvents: Job? = null
    private var vectorOpening: Job? = null
    private var vector: OfflineVectorMapView? = null
    private var vectorPosition: MapPosition? = null
    private var vectorError: String? = null
    private val main = Handler(Looper.getMainLooper())
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    // Dim monochrome blue cartography; only rendering changes, never the original cached tile bytes.
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(floatArrayOf(
        -0.136f, -0.458f, -0.046f, 0f, 190f,
        -0.132f, -0.443f, -0.045f, 0f, 198f,
        -0.125f, -0.422f, -0.043f, 0f, 211f,
        0f, 0f, 0f, 1f, 0f
    )) }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * resources.displayMetrics.scaledDensity; color = Color.WHITE
    }
    private val attribution = RectF()
    private val bitmaps = object : LruCache<MapTileKey, Image>(4 * 1024 * 1024) {
        override fun sizeOf(key: MapTileKey, value: Image) = value.bitmap.byteCount
    }
    private var source: MapTileSource? = null
    private var executor: ThreadPoolExecutor? = null
    private val requests = linkedMapOf<MapTileKey, Request>()
    private var placements = emptyList<WebMercator.Placement>()
    private var attempted = emptySet<MapTileKey>()
    private var active = false
    private var networkEnabled = false
    private var ready = false
    private var generation = 0L
    private var zoom = 1
    private var center = WebMercator.project(0.0, 0.0, zoom)
    private var position: Position? = null
    private var following = true
    private var positioned = false
    private var status = "Toca centrar para activar el mapa Android"
    private val failedTiles = mutableSetOf<MapTileKey>()
    private var nextRetry = Long.MAX_VALUE
    var onStatusChanged: ((String) -> Unit)? = null
        set(value) { field = value; value?.invoke(status) }
    private val refresh = Runnable { loadViewport() }
    private val retry = Runnable {
        attempted = emptySet(); nextRetry = Long.MAX_VALUE
        if (canWork()) loadViewport()
    }
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var scaled = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var scaleAmount = 1f
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean { scaleAmount = 1f; scaled = true; return true }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleAmount *= detector.scaleFactor
            if (scaleAmount >= 1.3f) { changeZoom(1, detector.focusX, detector.focusY); scaleAmount = 1f }
            else if (scaleAmount <= 0.77f) { changeZoom(-1, detector.focusX, detector.focusY); scaleAmount = 1f }
            return true
        }
    })

    init {
        setWillNotDraw(false)
        isClickable = true
        contentDescription = "Mapa Android. Arrastra para explorar; pellizca para cambiar el zoom."
        ready = true
    }

    /** The Activity must turn this off when hidden, including when ZLink is in the foreground. */
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (canWork()) resumeWork() else stopWork()
    }
    /** Network is disabled until explicit user consent. Existing cached tiles remain usable. */
    fun setNetworkEnabled(value: Boolean) {
        if (networkEnabled == value) return
        networkEnabled = value
        stopWork()
        if (canWork()) resumeWork() else updateStatus()
    }
    fun setLocation(latitude: Double?, longitude: Double?, accuracy: Float? = null, bearing: Float? = null) {
        position = if (latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0) Position(latitude, longitude,
            accuracy?.takeIf { it.isFinite() && it >= 0 }, bearing?.takeIf(Float::isFinite)?.let { ((it % 360) + 360) % 360 }) else null
        if (offlineWanted()) vector?.setPosition(position?.latitude, position?.longitude, position?.accuracy, following)
        if (following && position != null) {
            if (!positioned) { zoom = 15; positioned = true }
            center = WebMercator.project(position!!.latitude, position!!.longitude, zoom)
            updateViewport()
        } else invalidate()
        updateStatus()
    }
    fun zoomIn() { if (offlineWanted()) { following = false; vector?.zoomIn() } else changeZoom(1, width / 2f, height / 2f) }
    fun zoomOut() { if (offlineWanted()) { following = false; vector?.zoomOut() } else changeZoom(-1, width / 2f, height / 2f) }
    fun followLocation() {
        following = true
        if (offlineWanted()) {
            vector?.setPosition(position?.latitude, position?.longitude, position?.accuracy, true)
            updateStatus(); return
        }
        position?.let {
            if (!positioned) { zoom = 15; positioned = true }
            center = WebMercator.project(it.latitude, it.longitude, zoom)
            updateViewport()
        }
        updateStatus()
    }
    private fun changeZoom(delta: Int, focusX: Float, focusY: Float) {
        val target = (zoom + delta).coerceIn(WebMercator.MIN_ZOOM, WebMercator.MAX_ZOOM)
        if (target == zoom) return
        val focus = WebMercator.Point(center.x + focusX - width / 2.0, center.y + focusY - height / 2.0)
        val next = WebMercator.rescale(focus, zoom, target)
        zoom = target
        center = WebMercator.Point(WebMercator.wrapX(next.x - focusX + width / 2.0, zoom),
            (next.y - focusY + height / 2.0).coerceIn(0.0, WebMercator.worldSize(zoom)))
        following = false
        updateViewport()
    }
    private fun canWork() = ready && active && isAttachedToWindow && isShown && windowVisibility == VISIBLE && width > 0 && height > 0
    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (canWork()) resumeWork() }
    override fun onDetachedFromWindow() {
        stopWork(); offlineEvents?.cancel(); offlineEvents = null
        super.onDetachedFromWindow()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (!ready) return
        if (canWork()) resumeWork() else stopWork()
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (!ready) return
        if (canWork()) resumeWork() else stopWork()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); if (ready) updateViewport() }
    private fun resumeWork() {
        if (!canWork()) return
        observeOfflineMap()
        if (offlineWanted()) { startVector(); updateStatus(); return }
        if (offlineStore?.state?.value?.checking == true) { updateStatus(); return }
        if (executor == null) {
            executor = sharedExecutor
            attempted = emptySet()
            val now = System.currentTimeMillis()
            bitmaps.snapshot().filterValues { it.expiresAtMs <= now }.keys.forEach(bitmaps::remove)
        }
        updateViewport()
    }
    private fun stopWork() {
        if (!ready) return
        generation++
        vectorOpening?.cancel(); vectorOpening = null
        vector?.let { old ->
            vectorPosition = old.mapPosition()
            vector = null; removeView(old); old.release()
        }
        main.removeCallbacks(refresh); main.removeCallbacks(retry)
        requests.values.forEach { it.cancellation.cancel(); it.future?.cancel(true) }; requests.clear()
        source?.cancelAll()
        executor?.purge(); executor = null
        attempted = emptySet(); nextRetry = Long.MAX_VALUE
        bitmaps.snapshot().filterValues { !it.cacheable }.keys.forEach(bitmaps::remove)
        updateStatus()
    }
    private fun updateViewport() {
        if (offlineWanted()) { invalidate(); updateStatus(); return }
        placements = WebMercator.visibleTiles(center, zoom, width, height)
        val visible = placements.mapTo(linkedSetOf()) { it.key }
        failedTiles.retainAll(visible)
        val departed = requests.keys.filter { it !in visible }
        departed.forEach { key ->
            requests.remove(key)?.let { it.cancellation.cancel(); it.future?.cancel(true) }
        }
        if (departed.isNotEmpty()) executor?.purge()
        bitmaps.snapshot().filter { (key, image) -> !image.cacheable && key !in visible }.keys.forEach(bitmaps::remove)
        attempted = attempted.intersect(visible)
        main.removeCallbacks(refresh)
        if (canWork()) {
            if (executor == null) { resumeWork(); return }
            // Coalesce drag/zoom callbacks so only the final viewport is requested.
            main.postDelayed(refresh, 120)
        }
        invalidate(); updateStatus()
    }
    private fun tileSource(): MapTileSource = source ?: (tileSourceFactory?.invoke(context.applicationContext) ?: MapTileRepository(
        File(context.cacheDir, "android-map-tiles"), "MustangOS/0.4 (Android launcher; OSM raster map)"
    )).also { source = it }
    private fun loadViewport() {
        if (!canWork() || offlineWanted() || offlineStore?.state?.value?.checking == true) return
        val pool = executor ?: return
        val provider = tileSource()
        val stamp = generation
        val allowNetwork = networkEnabled
        val time = System.currentTimeMillis()
        for (key in placements.map { it.key }.distinct()) {
            if (key in requests || key in attempted) continue
            val image = bitmaps.get(key)
            if (image != null && time < image.expiresAtMs) {
                failedTiles.remove(key); attempted = attempted + key; continue
            }
            attempted = attempted + key
            val request = Request(TileCancellation()); requests[key] = request
            try { request.future = pool.submit {
                var result = MapTileData(retryAtMs = System.currentTimeMillis() + 30_000)
                var bitmap: Bitmap? = null
                try {
                    result = provider.load(key, allowNetwork, request.cancellation)
                    if (!request.cancellation.isCancelled && result.bytes != null) {
                        val bytes = result.bytes!!
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        if (bounds.outWidth == 256 && bounds.outHeight == 256) bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
                        if (bitmap == null) provider.discard(key)
                    }
                } catch (_: Exception) { /* Status stays generic; no tile coordinates or URLs enter diagnostics. */ }
                if (request.cancellation.isCancelled) { bitmap?.recycle(); return@submit }
                val decoded = bitmap
                val data = result
                main.post {
                    if (stamp != generation || request.cancellation.isCancelled || !canWork() || requests[key] !== request) {
                        decoded?.recycle(); return@post
                    }
                    requests.remove(key)
                    if (decoded != null) bitmaps.put(key, Image(decoded, data.expiresAtMs, data.cacheable, data.stale))
                    else bitmaps.remove(key)
                    if (decoded == null) failedTiles.add(key) else failedTiles.remove(key)
                    if (networkEnabled && (decoded == null || data.stale) && data.retryAtMs > 0) scheduleRetry(data.retryAtMs)
                    invalidate(); updateStatus()
                }
            } } catch (_: RejectedExecutionException) {
                requests.remove(key); failedTiles.add(key)
                scheduleRetry(System.currentTimeMillis() + 30_000)
            }
        }
        updateStatus()
    }
    private fun scheduleRetry(at: Long) {
        if (at >= nextRetry) return
        nextRetry = at
        main.removeCallbacks(retry)
        main.postDelayed(retry, (at - System.currentTimeMillis()).coerceAtLeast(1_000))
    }
    private fun updateStatus() {
        if (offlineWanted() || offlineStore?.state?.value?.checking == true) {
            val text = when {
                vectorError != null -> "No se pudo abrir México · revisa Mapas offline"
                !offlineWanted() -> "Revisando mapa offline…"
                vector == null -> "Cargando mapa México local…"
                else -> "Mapa México sin conexión" + if (position == null) " · sin posición GPS" else ""
            }
            if (status != text) { status = text; onStatusChanged?.invoke(text) }
            return
        }
        val anyTile = placements.any { bitmaps.get(it.key) != null }
        val failure = placements.any { it.key in failedTiles || bitmaps.get(it.key)?.stale == true }
        val text = when {
            !networkEnabled && !anyTile -> "Toca centrar para activar el mapa Android"
            !networkEnabled -> "Mapa Android en caché · red desactivada"
            requests.isNotEmpty() -> "Cargando mapa Android…"
            failure && !anyTile -> "Mapa Android sin conexión · sin caché para esta zona"
            failure -> "Mapa Android en caché · sin conexión"
            else -> "Mapa Android"
        } + if (position == null && networkEnabled) " · esperando GPS" else ""
        if (status != text) { status = text; onStatusChanged?.invoke(text) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(19, 26, 34))
        if (offlineWanted()) { if (vector == null) drawAttribution(canvas); return }
        paint.color = Color.rgb(37, 47, 58); paint.style = Paint.Style.STROKE; paint.strokeWidth = density
        for (tile in placements) {
            val destination = RectF(tile.left, tile.top, tile.left + 256, tile.top + 256)
            val image = bitmaps.get(tile.key)
            if (image == null) canvas.drawRect(destination, paint)
            else canvas.drawBitmap(image.bitmap, null, destination, tilePaint)
        }
        position?.let { drawPosition(canvas, it) }
        drawAttribution(canvas)
    }
    private fun drawPosition(canvas: Canvas, location: Position) {
        val projected = WebMercator.project(location.latitude, location.longitude, zoom)
        val x = (width / 2.0 + WebMercator.wrappedDelta(projected.x, center.x, zoom)).toFloat()
        val y = (height / 2.0 + projected.y - center.y).toFloat()
        val metersPerPixel = WebMercator.metersPerPixel(location.latitude, zoom)
        location.accuracy?.let {
            val radius = (it / metersPerPixel).coerceAtMost(hypot(width.toDouble(), height.toDouble())).toFloat()
            paint.style = Paint.Style.FILL; paint.color = 0x283ba9ff; canvas.drawCircle(x, y, radius, paint)
            paint.style = Paint.Style.STROKE; paint.color = 0x773ba9ff; paint.strokeWidth = density; canvas.drawCircle(x, y, radius, paint)
        }
        if (x !in -30f..(width + 30f) || y !in -30f..(height + 30f)) return
        location.bearing?.let {
            canvas.save(); canvas.rotate(it, x, y)
            val arrow = Path().apply { moveTo(x, y - 27 * density); lineTo(x - 9 * density, y - 12 * density); lineTo(x + 9 * density, y - 12 * density); close() }
            paint.style = Paint.Style.FILL; paint.color = 0xcc3ba9ff.toInt(); canvas.drawPath(arrow, paint); canvas.restore()
        }
        paint.style = Paint.Style.FILL; paint.color = Color.WHITE; canvas.drawCircle(x, y, 8 * density, paint)
        paint.color = 0xff3ba9ff.toInt(); canvas.drawCircle(x, y, 5.5f * density, paint)
    }
    private fun drawAttribution(canvas: Canvas) {
        val text = "© OpenStreetMap contributors"
        val padding = 6 * density
        val textWidth = labelPaint.measureText(text)
        attribution.set((width - textWidth - padding * 2).coerceAtLeast(0f),
            (height - labelPaint.textSize - padding * 2).coerceAtLeast(0f), width.toFloat(), height.toFloat())
        paint.style = Paint.Style.FILL; paint.color = 0xdd101820.toInt()
        canvas.drawRect(attribution, paint)
        canvas.drawText(text, attribution.left + padding, height - padding - labelPaint.fontMetrics.descent, labelPaint)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (offlineWanted()) return super.onTouchEvent(event)
        scale.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; lastX = event.x; lastY = event.y
                dragging = false; scaled = false; parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !scale.isInProgress) {
                    if (hypot(event.x - downX, event.y - downY) > touchSlop) dragging = true
                    if (dragging) {
                        following = false
                        center = WebMercator.Point(WebMercator.wrapX(center.x - (event.x - lastX), zoom),
                            (center.y - (event.y - lastY)).coerceIn(0.0, WebMercator.worldSize(zoom)))
                        updateViewport()
                    }
                }
                lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_POINTER_UP -> { dragging = false; scaled = true }
            MotionEvent.ACTION_UP -> {
                if (!dragging && !scaled) {
                    performClick()
                    if (attribution.contains(event.x, event.y)) runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright")))
                    }
                }
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }

    /** Refresh after SAF import; the local renderer never enables the online raster source. */
    fun refreshOfflineMap() { observeOfflineMap(); offlineStore?.refresh() }
    fun isUsingOfflineMap(): Boolean = offlineWanted()
    fun focusOffline(latitude: Double, longitude: Double, zoom: Byte) {
        require(latitude in -85.0..85.0 && longitude in -180.0..180.0 && zoom in 4..20)
        following = false
        vectorPosition = MapPosition(org.mapsforge.core.model.LatLong(latitude, longitude), zoom)
        vector?.setViewport(latitude, longitude, zoom)
    }
    private fun offlineWanted() = offlineStore?.state?.value?.let { it.enabled && it.map != null } == true
    private fun observeOfflineMap() {
        if (tileSourceFactory != null || offlineEvents != null) return
        val store = offlineStore ?: OfflineMapStore.get(context).also { offlineStore = it }
        offlineEvents = offlineScope.launch(start = CoroutineStart.LAZY) {
            var previous: Triple<OfflineMapDescriptor?, Boolean, Boolean>? = null
            store.state.collect { state ->
                val key = Triple(state.map, state.enabled, state.checking)
                if (key != previous) {
                    previous = key
                    stopWork()
                    if (canWork()) resumeWork() else updateStatus()
                }
            }
        }
        offlineEvents?.start()
    }
    private fun startVector() {
        if (vector != null || vectorOpening?.isActive == true) return
        val descriptor = offlineStore?.state?.value?.map ?: return
        val stamp = generation
        vectorError = null
        vectorOpening = offlineScope.launch {
            var opened: MapFile? = null
            try {
                withContext(Dispatchers.IO) { opened = MapFile(descriptor.file, "es") }
                if (stamp != generation || !canWork() || !offlineWanted()) return@launch
                val next = OfflineVectorMapView.create(context, opened!!, vectorPosition) { following = false }
                opened = null
                vector = next
                addView(next, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                next.setPosition(position?.latitude, position?.longitude, position?.accuracy, following)
                updateStatus()
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { vectorError = "No se pudo abrir el mapa local"; updateStatus() }
            finally { withContext(NonCancellable + Dispatchers.IO) { opened?.close() } }
        }
    }

    companion object {
        // Shared and bounded across reparent/recreation, so cancelled old requests cannot double the worker count.
        private val sharedExecutor by lazy { ThreadPoolExecutor(2, 2, 20, TimeUnit.SECONDS, ArrayBlockingQueue(64), { task ->
            Thread(task, "mustang-map-tile").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
        }).apply { allowCoreThreadTimeOut(true) } }
        /** Test injection; production always uses the HTTPS repository unless explicitly replaced. */
        internal var tileSourceFactory: ((Context) -> MapTileSource)? = null
    }
}
