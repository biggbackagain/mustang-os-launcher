package com.mustangos.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.LayerDrawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.util.TypedValue
import com.mustangos.launcher.ui.MustangBackgroundDrawable
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
class MustangBackgroundTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun allOwnPageLayoutsInflateTheCommonBackgroundOnTheHeadUnitApi() {
        val inflater = LayoutInflater.from(context)
        listOf(R.layout.activity_main, R.layout.dialog_app_drawer,
            R.layout.dialog_launcher_settings, R.layout.dialog_vehicle_dashboard).forEach { layout ->
            assertTrue(inflater.inflate(layout, null).background is MustangBackgroundDrawable)
        }
        val themed = ContextThemeWrapper(context, R.style.Theme_Mustang_Dialog)
        val attribute = TypedValue()
        assertTrue(themed.theme.resolveAttribute(android.R.attr.windowBackground, attribute, true))
        val background = themed.getDrawable(attribute.resourceId) as LayerDrawable
        assertTrue(background.getDrawable(0) is MustangBackgroundDrawable)
    }

    @Test fun themeDrawsAtBothScreenSizesAndInAShortMenuWithoutLeakingOutsideBounds() {
        for ((width, height) in listOf(1280 to 720, 853 to 480, 960 to 240)) {
            val background = context.getDrawable(R.drawable.bg_mustang_theme)!!
            val image = Bitmap.createBitmap(width + 16, height + 16, Bitmap.Config.ARGB_8888)
            background.setBounds(8, 8, width + 8, height + 8)
            background.draw(Canvas(image))
            assertEquals(0, image.getPixel(0, 0))
            assertEquals(0, image.getPixel(width + 15, height + 15))
            assertNotEquals(0, image.getPixel(width / 2 + 8, height / 2 + 8))
            assertNotEquals(image.getPixel(8, 8), image.getPixel(width / 2 + 8, height / 2 + 8))
            val file = File("build/reports/screenshots/background-${width}x${height}.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
    }
}
