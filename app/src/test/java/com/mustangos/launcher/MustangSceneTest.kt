package com.mustangos.launcher

import android.graphics.*
import com.mustangos.core.*
import com.mustangos.launcher.ui.MustangSceneDrawable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MustangSceneTest {
    private fun render(model: MustangSceneDrawable)=Bitmap.createBitmap(900,510,Bitmap.Config.ARGB_8888).also {
        model.setBounds(0,0,900,510); model.draw(Canvas(it))
    }
    @Test fun doorsLightsWheelsAndPaintChangeRenderedGeometryIndependently() {
        val model=MustangSceneDrawable(MustangProfile.DEFAULT)
        val closed=render(model)
        model.leftDoor=1f; val left=render(model); assertFalse(closed.sameAs(left))
        model.leftDoor=0f; model.rightDoor=1f; val right=render(model); assertFalse(left.sameAs(right)); assertFalse(closed.sameAs(right))
        model.rightDoor=0f; model.lights=1f; val lit=render(model); assertFalse(closed.sameAs(lit))
        model.lights=0f; model.travelMeters=.25f; val moving=render(model); assertFalse(closed.sameAs(moving))
        val blue=render(MustangSceneDrawable(MustangProfile.DEFAULT.copy(paint=MustangPaint.BLUE)))
        assertFalse(closed.sameAs(blue))
        val out=File("build/reports/screenshots/scene-3d"); out.mkdirs()
        for((name,bitmap) in listOf("closed" to closed,"left-open" to left,"right-open" to right,"lights" to lit,"blue" to blue))
            File(out,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        listOf(closed,left,right,lit,moving,blue).forEach(Bitmap::recycle)
    }
}
