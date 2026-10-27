package com.mustangos.launcher

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.BatteryManager
import com.mustangos.launcher.diagnostics.CompatibilityExporter
import com.mustangos.launcher.diagnostics.OemInspector
import com.mustangos.launcher.vehicle.VehicleTelemetryManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class NativeInspectionTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    @Test fun androidVoltageIsNotPromotedToMcuVoltage() {
        app.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_VOLTAGE, 12100).putExtra(BatteryManager.EXTRA_PRESENT, false))
        val manager = VehicleTelemetryManager(app)
        assertTrue(manager.nativeReport().contains("12.1 V"))
        assertTrue(manager.nativeReport().contains("no se atribuye"))
        assertNull(manager.state.value.voltage)
        assertNull(manager.state.value.rpm)
        assertNull(manager.state.value.coolantC)
    }
    @Test fun absenceOfBatteryDataDoesNotInventTwelveVolts() {
        val report = VehicleTelemetryManager(app).nativeReport()
        assertTrue(report.contains("no disponible"))
        assertFalse(report.contains("12.1 V"))
    }
    @Test fun inspectionReportsApiRestrictionWithoutStartingVendorServices() {
        val report = OemInspector(app).report()
        assertTrue(report.contains("antes de API 33"))
        assertTrue(report.contains("com.tw.uart: no visible o no instalado"))
        assertNull(shadowOf(app).nextStartedService)
        assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun exportIncludesOnlyStaticManifestAndDexFromAllowedPackages() {
        val source = File(app.cacheDir, "oem-test.apk")
        ZipOutputStream(source.outputStream()).use { zip ->
            mapOf("AndroidManifest.xml" to "static manifest", "classes.dex" to "static code", "assets/activation.txt" to "do not export", "../escape.txt" to "do not export").forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry()
            }
        }
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = "com.tw.car"; versionName = "test"
            applicationInfo = ApplicationInfo().apply { packageName = "com.tw.car"; sourceDir = source.path; publicSourceDir = source.path }
        })
        val bytes = ByteArrayOutputStream()
        CompatibilityExporter(app).write(bytes, "test diagnostic without coordinates")
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8) }
        }
        assertEquals("static code", entries["packages/com.tw.car/classes.dex"])
        assertEquals("static manifest", entries["packages/com.tw.car/AndroidManifest.xml"])
        assertFalse(entries.keys.any { it.contains("activation") || it.contains("escape") })
        assertTrue(entries.containsKey("diagnostic.txt"))
    }
}
