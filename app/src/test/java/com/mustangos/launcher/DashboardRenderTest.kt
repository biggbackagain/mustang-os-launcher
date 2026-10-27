package com.mustangos.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import com.mustangos.launcher.ui.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], qualifiers = "w1280dp-h720dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardRenderTest {
    @Test fun renderActualDashboardWithoutInventedData() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val root = controller.get().findViewById<View>(R.id.root)
        root.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1280, 720)
        val image = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(image))
        val file = File("build/reports/screenshots/dashboard-1280x720.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        controller.pause().stop().destroy()
    }
}
