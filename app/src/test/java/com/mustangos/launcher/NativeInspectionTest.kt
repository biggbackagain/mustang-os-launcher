package com.mustangos.launcher

import android.app.Application
import android.content.Intent
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.os.BatteryManager
import com.mustangos.launcher.diagnostics.CompatibilityExporter
import com.mustangos.launcher.diagnostics.OemInspector
import com.mustangos.launcher.vehicle.VehicleTelemetryManager
import com.mustangos.launcher.settings.LauncherSettings
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
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class NativeInspectionTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private fun staticPackage(pkg: String, flags: Int = 0, entries: Map<String, String> = mapOf("AndroidManifest.xml" to "static manifest", "classes.dex" to "$pkg static code")): ApplicationInfo {
        val file = File(app.cacheDir, "$pkg.apk")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, value) -> zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry() }
        }
        val info = ApplicationInfo().apply { packageName = pkg; sourceDir = file.path; publicSourceDir = file.path; this.flags = flags; enabled = true }
        shadowOf(app.packageManager).installPackage(PackageInfo().apply { packageName = pkg; versionName = "fixture"; applicationInfo = info })
        return info
    }
    private fun home(info: ApplicationInfo, activityName: String = "${info.packageName}.Home") {
        val activity = ActivityInfo().apply { packageName = info.packageName; name = activityName; applicationInfo = info; enabled = true; exported = true }
        shadowOf(app.packageManager).addOrUpdateActivity(activity)
        shadowOf(app.packageManager).addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), ResolveInfo().apply { activityInfo = activity })
    }
    private fun exportEntries(): Map<String, String> {
        val bytes = ByteArrayOutputStream()
        CompatibilityExporter(app).write(bytes, "test diagnostic")
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8) }
        }
        return entries
    }
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
    @Test fun btMusicAndSystemHomeAreExportedWhileOtherHomesRemainInventoryOnly() {
        staticPackage("com.tw.bt"); staticPackage("com.tw.music")
        home(staticPackage("vendor.systemhome", ApplicationInfo.FLAG_UPDATED_SYSTEM_APP,
            mapOf("AndroidManifest.xml" to "home manifest", "classes.dex" to "home SDK client",
                "assets/license.dat" to "private asset", "lib/arm64-v8a/private.so" to "native library")))
        home(staticPackage("user.home"))
        home(staticPackage(app.packageName, ApplicationInfo.FLAG_SYSTEM))
        val entries = exportEntries()
        assertEquals("com.tw.bt static code", entries["packages/com.tw.bt/classes.dex"])
        assertEquals("com.tw.music static code", entries["packages/com.tw.music/classes.dex"])
        assertEquals("home SDK client", entries["packages/vendor.systemhome/classes.dex"])
        assertFalse(entries.keys.any { it.startsWith("packages/user.home/") || it.startsWith("packages/${app.packageName}/") })
        assertFalse(entries.keys.any { it.contains("license") || it.endsWith(".so") })
        val report = entries.getValue("oem-declarations.txt")
        assertTrue(report.contains("HOME user.home/user.home.Home"))
        assertTrue(report.contains("HOME vendor.systemhome/vendor.systemhome.Home"))
        assertTrue(report.contains("app de usuario; solo inventario"))
        assertNull(shadowOf(app).nextStartedService); assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun systemHomeExportHasPackageLimitWithoutHidingTheOtherHomeNames() {
        repeat(6) { index ->
            val info = staticPackage("vendor.home$index", ApplicationInfo.FLAG_SYSTEM)
            home(info)
            if (index == 0) home(info, "${info.packageName}.AlternativeHome")
        }
        val entries = exportEntries()
        val copied = entries.keys.filter { it.startsWith("packages/vendor.home") && it.endsWith("classes.dex") }
        assertEquals(OemInspector.MAX_HOME_PACKAGES, copied.size)
        assertEquals(1, copied.count { it.contains("vendor.home0/") })
        val report = entries.getValue("oem-declarations.txt")
        assertTrue(report.contains("HOME vendor.home5/vendor.home5.Home"))
        assertTrue(report.contains("máximo 4 paquetes"))
        assertFalse(entries.keys.any { it.startsWith("packages/vendor.home5/") })
    }
    @Test fun androidAuxiliaryHomesDoNotConsumeTheQuotaOrDisplaceTheVerifiedOemLauncher() {
        val auxiliaries = listOf(
            "android" to "com.android.internal.app.SystemUserHomeActivity",
            "com.android.managedprovisioning" to "com.android.managedprovisioning.preprovisioning.PostEncryptionActivity",
            "com.android.provision" to "com.android.provision.DefaultActivity",
            "com.android.settings" to "com.android.settings.CryptKeeper",
            "com.android.settings" to "com.android.settings.FallbackHome",
            "com.google.android.gms" to "com.google.android.gms.kids.SyncTailTrapperActivity"
        )
        auxiliaries.forEach { (pkg, activity) -> home(staticPackage(pkg, ApplicationInfo.FLAG_SYSTEM), activity) }
        val original = staticPackage("com.dofun.variety", ApplicationInfo.FLAG_SYSTEM,
            mapOf("AndroidManifest.xml" to "original launcher manifest", "classes.dex" to "original launcher client",
                "assets/license.dat" to "private", "lib/arm64-v8a/vendor.so" to "private"))
        home(original, "com.dofun.overseasvariety.Launcher")
        home(original, "com.dofun.variety.module.launcher.activity.HomeHelpActivity")
        val entries = exportEntries()
        assertEquals("original launcher client", entries["packages/com.dofun.variety/classes.dex"])
        assertFalse(entries.keys.any { name -> auxiliaries.any { (pkg, _) -> name.startsWith("packages/$pkg/") } })
        assertFalse(entries.keys.any { it.contains("license") || it.endsWith(".so") })
        val report = entries.getValue("oem-declarations.txt")
        assertTrue(report.contains("HOME com.android.settings/com.android.settings.FallbackHome: HOME auxiliar de Android; solo inventario"))
        assertTrue(report.contains("HOME com.dofun.variety/com.dofun.overseasvariety.Launcher"))
        assertNull(shadowOf(app).nextStartedService); assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun configuredSystemOriginalHasPriorityWithinTheFourHomePackages() {
        repeat(5) { index -> home(staticPackage("vendor.client$index", ApplicationInfo.FLAG_SYSTEM)) }
        val original = staticPackage("vendor.zzoriginal", ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)
        home(original)
        LauncherSettings(app).setComponent("home", ComponentName(original.packageName, "${original.packageName}.Home"))
        val inspector = OemInspector(app)
        val homes = inspector.discoverHomes()
        assertEquals(original.packageName, homes.configuredHomePackage)
        val selected = inspector.exportPackages(homes).filter { it.startsWith("vendor.") }
        assertEquals(OemInspector.MAX_HOME_PACKAGES, selected.size)
        assertEquals(original.packageName, selected.first())
        val entries = exportEntries()
        assertNotNull(entries["packages/vendor.zzoriginal/classes.dex"])
        assertFalse(entries.keys.any { it.startsWith("packages/vendor.client3/") })
        assertTrue(entries.getValue("oem-declarations.txt").contains("original configurado=true; seleccionado para exportar=true"))
    }
    @Test fun configuredUserHomeRemainsInventoryOnlyAndCannotOverrideSystemOnlyExportPolicy() {
        val user = staticPackage("user.original")
        home(user)
        repeat(5) { index -> home(staticPackage("vendor.client$index", ApplicationInfo.FLAG_SYSTEM)) }
        LauncherSettings(app).setComponent("home", ComponentName(user.packageName, "${user.packageName}.Home"))
        val entries = exportEntries()
        assertFalse(entries.keys.any { it.startsWith("packages/user.original/") })
        assertEquals(OemInspector.MAX_HOME_PACKAGES,
            entries.keys.count { it.startsWith("packages/vendor.client") && it.endsWith("classes.dex") })
        assertTrue(entries.getValue("oem-declarations.txt").contains("HOME user.original/user.original.Home: app de usuario; solo inventario; original configurado=true; seleccionado para exportar=false"))
    }
    @Test fun unreadableSystemHomeDoesNotPreventOtherStaticExports() {
        staticPackage("com.tw.bt")
        val missing = ApplicationInfo().apply {
            packageName = "vendor.missinghome"; flags = ApplicationInfo.FLAG_SYSTEM; sourceDir = File(app.cacheDir,"missing.apk").path
        }
        shadowOf(app.packageManager).installPackage(PackageInfo().apply { packageName = missing.packageName; applicationInfo = missing })
        home(missing)
        val entries = exportEntries()
        assertNotNull(entries["packages/com.tw.bt/classes.dex"])
        assertFalse(entries.keys.any { it.startsWith("packages/vendor.missinghome/") })
        assertTrue(entries.getValue("export-notes.txt").contains("vendor.missinghome: APK no legible"))
    }
    @Test fun deniedHomeDiscoveryDoesNotInventAClientOrRequestMorePermissions() {
        val inspector = OemInspector(app) { throw SecurityException("fixture") }
        val homes = inspector.discoverHomes()
        assertTrue(homes.unavailable)
        assertEquals(OemInspector.PACKAGES, inspector.exportPackages(homes))
        assertTrue(inspector.report(homes).contains("Inventario HOME no permitido"))
        assertNull(shadowOf(app).nextStartedService); assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun sharedLibraryInspectionOnlyReturnsDeclaredNames() {
        val parser = android.util.Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true)
        parser.setInput(StringReader("""<manifest xmlns:android="http://schemas.android.com/apk/res/android">
            <uses-library android:name="vendor.projection.sdk"/><uses-library android:name="vendor.projection.sdk"/>
            <uses-native-library android:name="libvendor.so"/><uses-library android:name="/private/do-not-read.jar"/>
            <application><activity android:name="private.Activity"/></application></manifest>"""))
        assertEquals(listOf("libvendor.so","vendor.projection.sdk"), OemInspector.declaredLibraryNames(parser))
    }
    @Test fun staticEntryLimitStopsBeforeExportingAnUnboundedDexList() {
        val entries = linkedMapOf("AndroidManifest.xml" to "manifest")
        repeat(70) { index -> entries[if (index == 0) "classes.dex" else "classes${index + 1}.dex"] = "code $index" }
        staticPackage("com.tw.music", entries = entries)
        val exported = exportEntries()
        assertEquals(64, exported.keys.count { it.startsWith("packages/com.tw.music/") })
        assertTrue(exported.getValue("export-notes.txt").contains("entradas restantes omitidas por límite de número"))
    }
}
