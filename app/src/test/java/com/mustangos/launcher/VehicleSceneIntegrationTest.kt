package com.mustangos.launcher

import android.os.*
import android.view.*
import android.widget.*
import androidx.lifecycle.ViewModelProvider
import com.mustangos.launcher.settings.LauncherSettings
import com.mustangos.launcher.ui.*
import com.mustangos.launcher.vehicle.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w1280dp-h720dp-land-mdpi")
class VehicleSceneIntegrationTest {
    @Before fun setup() {
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory::class.java,"_instance",null)
        RuntimeEnvironment.getApplication().getSharedPreferences("launcher",0).edit().clear().commit()
    }
    private fun children(v: View): List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { children(v.getChildAt(it)) } else emptyList()
    @Test fun doorLearningRequiresClosedBaselineSingleNewOpeningAndDifferentSides() {
        val host=Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val settings=LauncherSettings(host.get()); settings.oemReadEnabled=true
        var channel: Handler?=null
        val reader=OemReadOnlyManager(host.get(),settings) { object : OemSubscription {
            override fun open(codes: ShortArray,handler: Handler): Boolean { channel=handler; return true }
            override fun close() { }
        } }
        reader.start()
        DoorCalibrationSheet(host.get(),reader,settings) { }.show()
        val dialog=ShadowAlertDialog.getLatestAlertDialog()
        val buttons=children(dialog.window!!.decorView).filterIsInstance<Button>()
        val left=buttons.first { it.text=="Asignar apertura izquierda" }
        val right=buttons.first { it.text=="Asignar apertura derecha" }
        fun event(mask: Int) { channel!!.sendMessage(Message.obtain().apply { what=1285; obj=byteArrayOf(mask.toByte()) }); Shadows.shadowOf(Looper.getMainLooper()).idle() }
        event(128); left.performClick(); assertNull(settings.doorMapping.left)
        event(0); event(128); left.performClick(); assertEquals(128,settings.doorMapping.left)
        event(0); event(128); right.performClick(); assertNull(settings.doorMapping.right)
        event(0); event(64); right.performClick(); assertEquals(64,settings.doorMapping.right)
        dialog.dismiss(); reader.stop(); host.pause().stop().destroy()
    }
    @Test fun dashboardReceivesCachedIlluminationAndFreshRpmWithoutInventingSpeed() {
        val host=Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity=host.get(); val model=ViewModelProvider(activity)[LauncherViewModel::class.java]
        val cache=ReflectionHelpers.getField<MutableStateFlow<CarInfoReadings>>(model.vehicle.carInfo,"mutable")
        cache.value=CarInfoReadings(sourceActive=true,illuminationSignal=CarInfoCachedValue(true,"OEM",SystemClock.elapsedRealtime()))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        val car=activity.findViewById<MustangCarView>(R.id.carImage)
        assertNotNull(car.sceneProfile); assertTrue(ReflectionHelpers.getField<Float>(car,"lightLevel")>.9f)
        assertNull(car.motionSpeedKmh)
        val oem=ReflectionHelpers.getField<MutableStateFlow<OemReadings>>(model.oem,"mutable")
        oem.value=OemReadings(sourceActive=true,rpm=850,lastPowertrainMs=SystemClock.elapsedRealtime(),illuminationOn=false)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertEquals("850 RPM · OEM",activity.findViewById<TextView>(R.id.vehicleRpm).text.toString())
        assertTrue(ReflectionHelpers.getField<Float>(car,"lightLevel")<.1f)
        host.pause().stop().destroy()
    }
    @Test fun motionNeedsValidSpeedAndStopsUpdatingWhenTheViewIsInactive() {
        val host=Robolectric.buildActivity(android.app.Activity::class.java).setup().visible()
        val car=MustangCarView(host.get()).apply { sceneProfile=com.mustangos.core.MustangProfile.DEFAULT }
        host.get().setContentView(car); host.visible(); car.layout(0,0,900,510)
        // The bare Robolectric Activity retains GONE in AttachInfo; model a visible Android window.
        ReflectionHelpers.setField(ReflectionHelpers.getField<Any>(car,"mAttachInfo"),"mWindowVisibility",View.VISIBLE)
        car.setSceneSignals(true,false,40f); car.setSceneActive(true)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700))
        val scene=ReflectionHelpers.getField<MustangSceneDrawable>(car,"scene")
        assertTrue("shown=${car.isShown}; window=${car.windowVisibility}; active=${ReflectionHelpers.getField<Boolean>(car,"sceneActive")}",scene.travelMeters>0)
        car.draw(android.graphics.Canvas(android.graphics.Bitmap.createBitmap(900,510,android.graphics.Bitmap.Config.ARGB_8888)))
        assertTrue(scene.leftDoor>.9f); assertEquals(0f,scene.rightDoor)
        car.setSceneActive(false); val travel=scene.travelMeters
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2)); assertEquals(travel,scene.travelMeters)
        car.setSceneSignals(null,null,Float.NaN); assertNull(car.motionSpeedKmh)
        host.pause().stop().destroy()
    }
}
