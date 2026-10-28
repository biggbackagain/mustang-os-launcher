package com.mustangos.launcher

import android.graphics.*
import android.view.View
import com.mustangos.core.*
import com.mustangos.launcher.ui.MustangCarView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DigitalMustangTest {
    private fun car(profile: MustangProfile) = MustangCarView(RuntimeEnvironment.getApplication()).apply {
        digitalProfile = profile; lampOverlayEnabled = true
        measure(View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(510,View.MeasureSpec.EXACTLY)); layout(0,0,900,510)
    }
    private fun render(car: MustangCarView) = Bitmap.createBitmap(900,510,Bitmap.Config.ARGB_8888).also { car.draw(Canvas(it)) }
    @Test fun changingOnlyPaintActuallyChangesTheVehiclePixelsAndKeepsWheels() {
        val view = car(MustangProfile.DEFAULT)
        val red = render(view)
        view.digitalProfile = MustangProfile.DEFAULT.copy(paint=MustangPaint.BLUE)
        val blue = render(view)
        assertNotEquals(red.getPixel(600,300),blue.getPixel(600,300))
        assertTrue(Color.blue(blue.getPixel(600,300)) > Color.red(blue.getPixel(600,300)))
        assertEquals(red.getPixel(447,371),blue.getPixel(447,371))
        val out = File("build/reports/screenshots/mustang-digital-blue.png"); out.parentFile?.mkdirs(); out.outputStream().use { blue.compress(Bitmap.CompressFormat.PNG,100,it) }
        red.recycle(); blue.recycle()
    }
    @Test fun lampsFollowExplicitStateAndEveryFamilyAndPaintRenders() {
        val view = car(MustangProfile.DEFAULT)
        val off=render(view); view.setHeadlightsOn(true); val on=render(view)
        assertNotEquals(off.getPixel(315,318),on.getPixel(315,318))
        view.setHeadlightsOn(false); val reset=render(view); assertTrue(off.sameAs(reset))
        for(family in MustangGeneration.entries) for(paint in MustangPaint.entries) {
            view.digitalProfile=MustangProfile.DEFAULT.copy(year=family.example,paint=paint)
            render(view).recycle()
        }
        off.recycle(); on.recycle(); reset.recycle()
    }
}
