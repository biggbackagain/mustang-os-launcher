package com.mustangos.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import com.mustangos.launcher.ui.MustangCarView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "w1280dp-h720dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualIntegrationTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun drawerUsesTheCarPhotoAndHeaderHasVisiblePony() {
        val inflater = LayoutInflater.from(context)
        val drawer = inflater.inflate(R.layout.dialog_app_drawer, null)
        val wallpaper = drawer.findViewById<ImageView>(R.id.drawerWallpaper)
        assertNotNull(wallpaper.drawable)
        assertEquals(ImageView.ScaleType.CENTER_CROP, wallpaper.scaleType)
        assertNotNull(drawer.findViewById<View>(R.id.drawerScrim).background)
        val dashboard = inflater.inflate(R.layout.activity_main, null)
        val pony = dashboard.findViewById<ImageView>(R.id.brandLogo)
        assertNotNull(pony.drawable)
        assertEquals(View.VISIBLE, pony.visibility)
        assertTrue(pony.layoutParams.width >= 40)
    }

    @Test fun lampLayerChangesHeadlampRegionAndClearsWhenDisabled() {
        val view = MustangCarView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.mustang_2010)
            measure(View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
            layout(0, 0, 900, 600)
        }
        val off = draw(view)
        view.setHeadlightsOn(true)
        val on = draw(view)
        save(on, "mustang-lights-on.png")
        assertNotEquals(off.getPixel(339, 306), on.getPixel(339, 306))
        assertEquals(off.getPixel(550, 400), on.getPixel(550, 400))
        view.setHeadlightsOn(false)
        val reset = draw(view)
        assertTrue(off.sameAs(reset))
        off.recycle(); on.recycle(); reset.recycle()
    }

    @Test fun renderAppsDrawerWithActualPhotoAndNativeFixtureTiles() {
        val inflater = LayoutInflater.from(context)
        val drawer = inflater.inflate(R.layout.dialog_app_drawer, null)
        val labels = listOf("CarPlay", "Navegación", "Música", "Teléfono", "Radio", "Ajustes", "Archivos", "Calendario", "Cámara", "Bluetooth", "Clima", "Vídeo", "Ecualizador", "OriginalCar", "CarMate", "Ayuda")
        val icons = listOf(R.drawable.ic_carplay, R.drawable.ic_navigation, R.drawable.ic_music, R.drawable.ic_phone, R.drawable.ic_radio, R.drawable.ic_settings)
        drawer.findViewById<TextView>(R.id.appCount).text = "16 aplicaciones · muestra visual"
        val grid = drawer.findViewById<GridView>(R.id.appsGrid)
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = labels.size
            override fun getItem(position: Int) = labels[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (convertView ?: inflater.inflate(R.layout.item_app_drawer, parent, false)).apply {
                    findViewById<TextView>(R.id.appLabel).text = labels[position]
                    findViewById<ImageView>(R.id.appIcon).setImageResource(icons[position % icons.size])
                }
        }
        drawer.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
        drawer.layout(0, 0, 1280, 720)
        assertTrue(grid.childCount > 0)
        val bitmap = draw(drawer)
        save(bitmap, "apps-1280x720.png")
        bitmap.recycle()
    }

    @Test fun welcomeCanBeStoppedWithoutLeavingAnActiveAnimator() {
        val view = MustangCarView(context)
        view.playWelcomeLights()
        view.stopAnimation()
        assertFalse(view.isLightAnimationRunning)
    }

    private fun draw(view: View): Bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    private fun save(bitmap: Bitmap, name: String) {
        val file = File("build/reports/screenshots", name)
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
