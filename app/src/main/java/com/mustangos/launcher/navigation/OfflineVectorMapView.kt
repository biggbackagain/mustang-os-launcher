package com.mustangos.launcher.navigation

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.MapPosition
import org.mapsforge.core.util.Parameters
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.cache.InMemoryTileCache
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.model.common.Observer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import org.mapsforge.map.view.InputListener

/** Local vector renderer. The supplied MapFile was opened and validated on IO. */
internal class OfflineVectorMapView private constructor(
    context: Context,
    openedMap: MapFile,
    savedPosition: MapPosition?,
    onExplore: () -> Unit
) : FrameLayout(context) {
    private val map = MapView(context)
    private val cache = InMemoryTileCache(40) // ~5MiB at 256px RGB565; no persistent renderer cache.
    private val gps = GpsOverlay(context, map)
    private val observer = Observer { gps.postInvalidate() }
    private var closed = false

    init {
        setBackgroundColor(Color.rgb(10, 25, 40))
        map.model.displayModel.setFixedTileSize(256)
        map.model.frameBufferModel.overdrawFactor = 1.1
        map.setBuiltInZoomControls(false)
        map.setZoomLevelMin(4)
        map.setZoomLevelMax(20)
        map.mapScaleBar.isVisible = false
        val renderer = TileRendererLayer(cache, openedMap, map.model.mapViewPosition, AndroidGraphicFactory.INSTANCE)
        renderer.setXmlRenderTheme(MapsforgeThemes.DARK)
        map.layerManager.layers.add(renderer)
        map.model.mapViewPosition.mapLimit = openedMap.boundingBox()
        if (savedPosition != null) map.model.mapViewPosition.mapPosition = savedPosition
        else {
            map.setCenter(LatLong(23.6, -102.5))
            map.setZoomLevel(4)
        }
        map.addInputListener(object : InputListener {
            override fun onMoveEvent() { onExplore() }
            override fun onZoomEvent() { onExplore() }
        })
        map.model.mapViewPosition.addObserver(observer)
        addView(map, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(gps, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(TextView(context).apply {
            text = "© OpenStreetMap contributors · Mapsforge"
            textSize = 12f; setTextColor(Color.WHITE); setBackgroundColor(0xe6101820.toInt())
            setPadding(dp(6), dp(4), dp(6), dp(4)); minHeight = dp(32)
            gravity = Gravity.CENTER_VERTICAL
            contentDescription = "Licencia del mapa OpenStreetMap y Mapsforge"
            setOnClickListener { runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright")))
            } }
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    fun zoomIn() { map.model.mapViewPosition.zoomIn() }
    fun zoomOut() { map.model.mapViewPosition.zoomOut() }
    fun mapPosition(): MapPosition = map.model.mapViewPosition.mapPosition
    fun setViewport(latitude: Double, longitude: Double, zoom: Byte) {
        map.model.mapViewPosition.mapPosition = MapPosition(LatLong(latitude, longitude), zoom)
    }
    fun setPosition(latitude: Double?, longitude: Double?, accuracy: Float?, follow: Boolean) {
        gps.latitude = latitude; gps.longitude = longitude; gps.accuracy = accuracy
        if (follow && latitude != null && longitude != null) {
            map.setCenter(LatLong(latitude, longitude))
            if (map.model.mapViewPosition.zoomLevel < 13) map.setZoomLevel(15)
        }
        gps.invalidate()
    }
    fun release() {
        if (closed) return
        closed = true
        map.model.mapViewPosition.removeObserver(observer)
        map.destroyAll()
        AndroidGraphicFactory.clearResourceMemoryCache()
    }
    private class GpsOverlay(context: Context, private val map: MapView) : View(context) {
        var latitude: Double? = null
        var longitude: Double? = null
        var accuracy: Float? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun onDraw(canvas: Canvas) {
            val lat = latitude ?: return
            val lon = longitude ?: return
            val point = map.mapViewProjection.toPixels(LatLong(lat, lon)) ?: return
            val x = point.x.toFloat(); val y = point.y.toFloat()
            val density = resources.displayMetrics.density
            accuracy?.let {
                val radius = (it / WebMercator.metersPerPixel(lat, map.model.mapViewPosition.zoomLevel.toInt()))
                    .coerceIn(0.0, maxOf(width, height).toDouble()).toFloat()
                paint.color = 0x283ba9ff; canvas.drawCircle(x, y, radius, paint)
            }
            if (x !in -30f..width + 30f || y !in -30f..height + 30f) return
            paint.color = Color.WHITE; canvas.drawCircle(x, y, 8 * density, paint)
            paint.color = 0xff3ba9ff.toInt(); canvas.drawCircle(x, y, 5.5f * density, paint)
        }
    }
    companion object {
        private var initialized = false
        fun create(context: Context, map: MapFile, position: MapPosition?, onExplore: () -> Unit): OfflineVectorMapView {
            if (!initialized) {
                Parameters.NUMBER_OF_THREADS = 2
                Parameters.SQUARE_FRAME_BUFFER = false
                AndroidGraphicFactory.createInstance(context.applicationContext as Application)
                initialized = true
            }
            return OfflineVectorMapView(context, map, position, onExplore)
        }
    }
}
