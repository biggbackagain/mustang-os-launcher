package com.mustangos.launcher

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.app.AlertDialog
import android.os.Looper
import com.mustangos.core.MustangBody
import com.mustangos.core.MustangPaint
import com.mustangos.core.MustangProfile
import com.mustangos.core.MustangVersion
import com.mustangos.launcher.settings.LauncherSettings
import com.mustangos.launcher.settings.ReferenceImageStore
import com.mustangos.launcher.ui.MustangCarView
import com.mustangos.launcher.ui.MustangProfileSheet
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MustangProfileTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    @Before fun clear() = runBlocking { app.getSharedPreferences("launcher", 0).edit().clear().commit(); ReferenceImageStore(app).clear() }
    @Test fun profileSurvivesRecreationAndDoesNotReconfigureCarPlayOrOem() {
        val settings = LauncherSettings(app)
        val zlink = ComponentName("com.zjinnova.zlink", "Main")
        settings.setComponent("zlink", zlink); settings.mediaPackage = "com.zjinnova.zlink"; settings.oemReadEnabled = true
        val selected = MustangProfile(2020, MustangVersion.SHELBY_GT500, MustangBody.FASTBACK, MustangPaint.BLUE)
        settings.mustangProfile = selected
        val restored = LauncherSettings(app)
        assertEquals(selected, restored.mustangProfile)
        assertEquals(zlink, restored.component("zlink")); assertEquals("com.zjinnova.zlink", restored.mediaPackage)
        assertTrue(restored.oemReadEnabled)
    }
    @Test fun invalidPersistedDataDoesNotCrashStartup() {
        app.getSharedPreferences("launcher", 0).edit().putInt("profile_year", -1).putString("profile_version", "future").commit()
        assertEquals(MustangProfile.DEFAULT, LauncherSettings(app).mustangProfile)
    }
    @Test fun photoImportPersistsABoundedImageAndBadInputKeepsPreviousPhoto() = runBlocking {
        val store = ReferenceImageStore(app)
        val source = Bitmap.createBitmap(1800, 1000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val bytes = ByteArrayOutputStream().also { source.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        source.recycle()
        assertTrue(store.importStream(ByteArrayInputStream(bytes)))
        val photo = ReferenceImageStore(app).load()!!
        assertTrue(maxOf(photo.width, photo.height) <= 1280)
        assertTrue(Color.blue(photo.getPixel(photo.width / 2, photo.height / 2)) > 200)
        photo.recycle()
        assertFalse(store.importStream(ByteArrayInputStream("not an image".toByteArray())))
        assertTrue(store.hasImage())
        store.load()!!.recycle()
        store.clear(); assertFalse(store.hasImage())
    }
    @Test fun oversizedPhotoIsRejectedWithoutCreatingAReference() = runBlocking {
        val store = ReferenceImageStore(app)
        assertFalse(store.importStream(ByteArrayInputStream(ByteArray(12 * 1024 * 1024 + 1))))
        assertFalse(store.hasImage())
    }
    @Test fun usingBundledImageWhileImportIsPendingPreventsPhotoFromReappearing() = runBlocking {
        val store = ReferenceImageStore(app)
        val source = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val bytes = ByteArrayOutputStream().also { source.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        source.recycle()
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val stream = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                entered.countDown(); check(release.await(5, TimeUnit.SECONDS))
                return super.read(buffer, offset, length)
            }
        }
        val importing = async(Dispatchers.IO) { store.importStream(stream) }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            store.clear()
        } finally { release.countDown() }
        assertFalse(importing.await())
        assertFalse(ReferenceImageStore(app).hasImage())
    }
    @Test fun customPhotoDoesNotReceiveHeadlightsAtCoordinatesFromTheBundledCar() {
        val view = MustangCarView(app).apply {
            setImageResource(R.drawable.mustang_2010); lampOverlayEnabled = false
            measure(View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
            layout(0,0,900,600)
        }
        fun draw() = Bitmap.createBitmap(900,600,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        val original = draw(); view.setHeadlightsOn(true); view.playWelcomeLights(); val after = draw()
        assertTrue(original.sameAs(after)); assertFalse(view.isLightAnimationRunning)
        original.recycle(); after.recycle()
    }
    @Test fun profileFormSeparatesTrimBodyAndColorAndValidatesYear() {
        var saved: MustangProfile? = null
        MustangProfileSheet(app, MustangProfile.DEFAULT, false, { saved = it }, {}, {}, {}).show()
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
        val children = descendants(dialog.window!!.decorView)
        val year = children.filterIsInstance<EditText>().first { it.contentDescription == "Año del Mustang" }
        val version = children.filterIsInstance<Spinner>().first { it.contentDescription == "Versión del Mustang" }
        val body = children.filterIsInstance<Spinner>().first { it.contentDescription == "Carrocería del Mustang" }
        val paint = children.filterIsInstance<Spinner>().first { it.contentDescription == "Color del Mustang" }
        year.setText("1200"); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertNull(saved); assertTrue(dialog.isShowing); assertNotNull(year.error)
        year.setText("2020"); version.setSelection(MustangVersion.GT.ordinal); body.setSelection(MustangBody.COUPE.ordinal)
        paint.setSelection(MustangPaint.BLACK.ordinal)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals(MustangProfile(2020, MustangVersion.GT, MustangBody.COUPE, MustangPaint.BLACK), saved)
    }
}
