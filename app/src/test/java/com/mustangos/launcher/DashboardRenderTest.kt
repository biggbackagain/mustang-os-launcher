package com.mustangos.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.lifecycle.ViewModelProvider
import com.mustangos.launcher.ui.MainActivity
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], qualifiers = "w1280dp-h720dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardRenderTest {
    @Before fun resetProcessApplicationFactory() {
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory::class.java, "_instance", null)
    }
    @Test fun renderActualDashboardWithoutInventedData() = render(1280, 720)
    @Test @Config(qualifiers = "w853dp-h480dp-land-mdpi") fun renderCompactDashboard() = render(853, 480)
    private fun render(width: Int, height: Int) {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val root = controller.get().findViewById<View>(R.id.root)
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(image))
        val file = File("build/reports/screenshots/dashboard-${width}x${height}.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        controller.pause().stop().destroy()
    }
}
