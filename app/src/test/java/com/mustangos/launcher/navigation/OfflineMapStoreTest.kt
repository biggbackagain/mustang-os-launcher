package com.mustangos.launcher.navigation

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class OfflineMapStoreTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    class Provider(private val file: File) : ContentProvider() {
        override fun onCreate() = true
        override fun openFile(uri: Uri, mode: String) = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
        override fun getType(u: Uri) = "application/octet-stream"
        override fun insert(u: Uri, v: ContentValues?): Uri? = null
        override fun delete(u: Uri, s: String?, a: Array<out String>?) = 0
        override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
    }
    private fun root() = File(app.cacheDir, "maps-${UUID.randomUUID()}").apply { mkdirs() }
    private fun uri(file: File): Uri {
        val authority = "fixture.map.${UUID.randomUUID()}"
        val provider = Provider(file).apply {
            attachInfo(app, android.content.pm.ProviderInfo().apply { this.authority = authority; exported = true })
        }
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(authority, provider)
        return Uri.parse("content://$authority/map")
    }
    @Test fun onlyValidatedCompleteImportReplacesTheLocalMap() = runBlocking {
        val directory = root()
        val bytes = ByteArray(80_000) { (it % 251).toByte() }
        val incoming = File(root(), "input.map").apply { writeBytes(bytes) }
        var validations = 0
        val store = OfflineMapStore(app, directory) { file ->
            assertArrayEquals(bytes, file.readBytes()); validations++
            OfflineMapDescriptor(file, file.length(), file.lastModified(), 1)
        }
        val imported = store.import(uri(incoming))
        assertTrue(store.state.value.message, imported)
        assertEquals(1, validations)
        assertArrayEquals(bytes, File(directory, "mexico.map").readBytes())
        assertTrue(store.state.value.enabled); assertFalse(store.state.value.busy)
        assertEquals("mexico.map", store.state.value.map!!.file.name)
    }
    @Test fun rejectedFileKeepsThePreviouslyInstalledMapIntact() = runBlocking {
        val directory = root()
        val old = File(directory, "mexico.map").apply { writeText("previous map") }
        val incoming = File(root(), "bad.map").apply { writeText("not a map") }
        val store = OfflineMapStore(app, directory) { throw IllegalArgumentException("invalid fixture") }
        assertFalse(store.import(uri(incoming)))
        assertEquals("previous map", old.readText())
        assertFalse(store.state.value.busy)
        assertTrue(directory.listFiles()!!.none { it.extension == "part" })
    }
    @Test fun nonContentUrisCannotImportFilesOrCreateAnActiveMap() = runBlocking {
        val directory = root()
        val store = OfflineMapStore(app, directory)
        assertFalse(store.import(Uri.parse("file:///outside.map")))
        assertFalse(store.import(Uri.parse("https://example.com/fake.map")))
        assertFalse(File(directory, "mexico.map").exists())
    }
    @Test fun truncatedOrIncorrectMapHeadersAreRejectedBeforeActivation() {
        val invalid = File(root(), "bad.map").apply { writeBytes(ByteArray(70_000)) }
        assertThrows(IllegalArgumentException::class.java) { OfflineMapValidator.validate(invalid) }
        invalid.writeText("mapsforge binary OSM")
        assertThrows(IllegalArgumentException::class.java) { OfflineMapValidator.validate(invalid) }
    }
}
