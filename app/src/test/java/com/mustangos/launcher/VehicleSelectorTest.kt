package com.mustangos.launcher

import android.app.*
import android.graphics.*
import android.os.Looper
import android.view.*
import com.mustangos.core.*
import com.mustangos.launcher.settings.CalibratedVehiclePreview
import com.mustangos.launcher.ui.MustangProfileSheet
import com.mustangos.launcher.ui.MustangCarView
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w1280dp-h720dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VehicleSelectorTest {
    private val app: Application get()=RuntimeEnvironment.getApplication()
    private fun descendants(view: View): List<View> = listOf(view)+if(view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    @Test fun carouselAndColorPreviewStayDraftUntilConfirmAndCancelDoesNotSave() {
        var saved: MustangProfile?=null
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible()
        MustangProfileSheet(host.get(),MustangProfile.DEFAULT,false,{saved=it},{},{},{}).show()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val dialog=ShadowAlertDialog.getLatestAlertDialog()
        val all=descendants(dialog.window!!.decorView)
        all.first { it.contentDescription?.startsWith("S650,") == true }.performClick()
        all.first { it.contentDescription == "Pintura Azul" }.performClick()
        val preview=all.filterIsInstance<MustangCarView>().single()
        assertEquals(2024,preview.sceneProfile!!.year)
        assertEquals(MustangPaint.BLUE,preview.sceneProfile!!.paint)
        assertNull(saved)
        val decor=dialog.window!!.decorView
        decor.measure(View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY)); decor.layout(0,0,1280,720)
        val confirm=dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        assertTrue(confirm.width > 0 && confirm.height > 0)
        val image=Bitmap.createBitmap(1280,720,Bitmap.Config.ARGB_8888); decor.draw(Canvas(image))
        val file=File("build/reports/screenshots/vehicle-selector-1280.png"); file.parentFile?.mkdirs(); file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
        decor.measure(View.MeasureSpec.makeMeasureSpec(853,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY)); decor.layout(0,0,853,480)
        val compact=Bitmap.createBitmap(853,480,Bitmap.Config.ARGB_8888); decor.draw(Canvas(compact))
        File("build/reports/screenshots/vehicle-selector-853.png").outputStream().use { compact.compress(Bitmap.CompressFormat.PNG,100,it) }; compact.recycle()
        assertTrue(confirm.bottom <= 480)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); assertNull(saved)
        host.pause().stop().destroy()
    }
    @Test fun calibratedBluePreviewKeepsRoofWheelsAndOriginalFile() = runBlocking {
        val renderer=CalibratedVehiclePreview(app)
        val red=renderer.load(MustangProfile.DEFAULT)!!
        val blue=renderer.load(MustangProfile.DEFAULT.copy(paint=MustangPaint.BLUE))!!
        assertNotEquals(red.getPixel(630,300),blue.getPixel(630,300))
        assertTrue(Color.blue(blue.getPixel(630,300)) > Color.red(blue.getPixel(630,300)))
        assertEquals(red.getPixel(665,135),blue.getPixel(665,135))
        assertEquals(red.getPixel(456,379),blue.getPixel(456,379))
        assertEquals(red.getPixel(359,375),blue.getPixel(359,375))
        assertSame(red,renderer.load(MustangProfile.DEFAULT))
        assertNull(renderer.load(MustangProfile.DEFAULT.copy(year=2024)))
        val file=File("build/reports/screenshots/mustang-blue-preview.png"); file.parentFile?.mkdirs(); file.outputStream().use { blue.compress(Bitmap.CompressFormat.PNG,100,it) }
        Unit
    }
}
