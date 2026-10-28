package com.mustangos.launcher

import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.os.ParcelFileDescriptor
import com.mustangos.launcher.media.ArtworkStatus
import com.mustangos.launcher.media.ContentArtworkLoader
import com.mustangos.launcher.media.MediaAccessService
import com.mustangos.launcher.media.MediaIntegrationManager
import com.mustangos.launcher.settings.LauncherSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.util.ReflectionHelpers
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContentArtworkTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private lateinit var provider: ArtworkProvider
    private val uri = Uri.parse("content://artwork.fixture/cover")

    @Before fun setup() {
        app.getSharedPreferences("launcher", 0).edit().clear().commit()
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(ComponentName(app, MediaAccessService::class.java), true)
        provider = ArtworkProvider().also {
            it.attachInfo(app, ProviderInfo().apply { authority = "artwork.fixture"; exported = true })
            ShadowContentResolver.registerProviderInternal("artwork.fixture", it)
        }
    }

    private fun png(color: Int = Color.RED, width: Int = 12, height: Int = 8): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return ByteArrayOutputStream().use { bytes ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes))
            bitmap.recycle()
            bytes.toByteArray()
        }
    }

    @Test fun contentProviderImageIsDecodedAndDownsampledWithoutMainThreadIo() = runBlocking {
        provider.write(png(width = 1024, height = 512))
        val result = ContentArtworkLoader(app.contentResolver).load(uri)
        assertEquals(ArtworkStatus.CONTENT, result.status)
        val bitmap = requireNotNull(result.bitmap)
        assertTrue(bitmap.width <= 384); assertTrue(bitmap.height <= 384)
        assertEquals(Color.RED, bitmap.getPixel(0, 0))
        assertFalse(provider.openedOnMain)
        bitmap.recycle()
    }

    @Test fun truncatedImageHasNoBitmap() = runBlocking {
        provider.write(png().copyOf(12))
        val result = ContentArtworkLoader(app.contentResolver).load(uri)
        assertEquals(ArtworkStatus.INVALID_IMAGE, result.status)
        assertNull(result.bitmap)
    }

    @Test fun advertisedOversizedContentIsRejectedBeforeReadingPixels() = runBlocking {
        provider.write(byteArrayOf())
        RandomAccessFile(provider.file, "rw").use { it.setLength(4L * 1024 * 1024 + 1) }
        val result = ContentArtworkLoader(app.contentResolver).load(uri)
        assertEquals(ArtworkStatus.TOO_LARGE, result.status)
        assertNull(result.bitmap)
    }

    @Test fun unknownLengthContentStillHasAHardReadLimit() = runBlocking {
        provider.write(byteArrayOf())
        RandomAccessFile(provider.file, "rw").use { it.setLength(4L * 1024 * 1024 + 1) }
        provider.unknownLength = true
        val result = ContentArtworkLoader(app.contentResolver).load(uri)
        assertEquals(ArtworkStatus.TOO_LARGE, result.status)
        assertNull(result.bitmap)
    }

    @Test fun oversizedDimensionsAreRejectedBeforePixelDecode() = runBlocking {
        provider.write(png(width = 16385, height = 1))
        val result = ContentArtworkLoader(app.contentResolver).load(uri)
        assertEquals(ArtworkStatus.TOO_LARGE, result.status)
        assertNull(result.bitmap)
    }

    @Test fun slowProviderTimesOutAndReceivesCancellation() = runBlocking {
        provider.write(png()); provider.blockFirst = true
        try {
            val result = ContentArtworkLoader(app.contentResolver).load(uri)
            assertEquals(ArtworkStatus.TIMED_OUT, result.status)
            assertNull(result.bitmap)
            assertTrue(provider.cancelled.await(2, TimeUnit.SECONDS))
        } finally {
            provider.release.countDown()
            assertTrue(provider.finished.await(2, TimeUnit.SECONDS))
        }
    }

    @Test fun nonContentSchemesNeverReachTheProvider() = runBlocking {
        val loader = ContentArtworkLoader(app.contentResolver)
        for (value in listOf("https://example.test/cover", "file:///private/cover", "data:image/png;base64,AA==", "android.resource://app/1")) {
            val result = loader.load(Uri.parse(value))
            assertEquals(ArtworkStatus.REJECTED, result.status)
            assertNull(result.bitmap)
        }
        assertEquals(0, provider.opens.get())
    }

    @Test fun permissionDenialProducesGenericStatusWithoutPrivateUri() = runBlocking {
        provider.denied = true
        val result = ContentArtworkLoader(app.contentResolver).load(uri)
        assertEquals(ArtworkStatus.PERMISSION_DENIED, result.status)
        assertFalse(result.status.label.contains("artwork.fixture"))
        assertNull(result.bitmap)
    }

    @Test fun selectedUriPublishesArtworkAndKeepsUrisOutOfDiagnostics() {
        provider.write(png())
        controller("com.zjinnova.zlink", metadata("Private song", uri))
        val media = MediaIntegrationManager(app, LauncherSettings(app))
        try {
            media.refresh()
            await { media.state.value.artwork != null }
            assertEquals(Color.RED, media.state.value.artwork!!.getPixel(0, 0))
            assertEquals(ArtworkStatus.CONTENT.label, media.state.value.coverStatus)
            val report = media.sessionDiagnostics()
            assertFalse(report.contains("artwork.fixture")); assertFalse(report.contains("Private song"))
            assertTrue(report.contains("ALBUM_ART_URI{clave=true,valor=true,content=true}"))
            val opens = provider.opens.get()
            media.poll(); shadowOf(Looper.getMainLooper()).idle()
            assertEquals(opens, provider.opens.get())
        } finally { media.stop() }
    }

    @Test fun artAndDisplayIconUriFieldsAlsoProduceRealArtwork() {
        provider.write(png())
        val session = controller("com.zjinnova.zlink", MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_ART_URI, uri.toString()).build())
        val media = MediaIntegrationManager(app, LauncherSettings(app))
        try {
            media.refresh(); await { media.state.value.artwork != null }
            assertEquals(Color.RED, media.state.value.artwork!!.getPixel(0, 0))
            provider.write(png(Color.GREEN))
            shadowOf(session).executeOnMetadataChanged(MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, uri.toString()).build())
            shadowOf(Looper.getMainLooper()).idle()
            await { media.state.value.artwork?.getPixel(0, 0) == Color.GREEN }
            assertEquals(2, provider.opens.get())
        } finally { media.stop() }
    }

    @Test fun bitmapMetadataKeepsPriorityOverContentUri() {
        provider.write(png())
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val published = MediaMetadata.Builder(metadata("Song", uri)).putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap).build()
        val session = controller("com.zjinnova.zlink", published)
        val media = MediaIntegrationManager(app, LauncherSettings(app))
        try {
            media.refresh()
            assertSame(bitmap, media.state.value.artwork)
            assertEquals(ArtworkStatus.BITMAP.label, media.state.value.coverStatus)
            assertEquals(0, provider.opens.get())
            shadowOf(session).setMetadata(MediaMetadata.Builder(published)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap.copy(Bitmap.Config.ARGB_8888, false)).build())
            media.poll()
            assertSame("Equivalent parcel copies keep the existing published bitmap", bitmap, media.state.value.artwork)
        } finally { media.stop() }
    }

    @Test fun equivalentFreshMetadataOnPollDoesNotCancelOrReloadAnInflightCover() {
        provider.write(png()); provider.blockFirst = true
        val published = metadata("Song", uri)
        val session = controller("com.zjinnova.zlink", published)
        val media = MediaIntegrationManager(app, LauncherSettings(app))
        try {
            media.refresh(); assertTrue(provider.entered.await(2, TimeUnit.SECONDS))
            val freshParcel = MediaMetadata.Builder(published).build()
            assertNotSame(published, freshParcel)
            shadowOf(session).setMetadata(freshParcel)
            media.poll()
            assertFalse("Polling equivalent fields must preserve the active read", provider.cancelled.await(150, TimeUnit.MILLISECONDS))
            provider.release.countDown()
            await { media.state.value.artwork != null }
            val displayed = media.state.value.artwork
            shadowOf(session).setMetadata(MediaMetadata.Builder(published).build())
            media.poll(); shadowOf(Looper.getMainLooper()).idle()
            assertSame(displayed, media.state.value.artwork)
            assertEquals(1, provider.opens.get())
        } finally { provider.release.countDown(); media.stop() }
    }

    @Test fun metadataCallbackReloadsChangedCoverWithTheSameUriAndIdenticalSongFields() {
        provider.write(png())
        val published = metadata("Unchanged song fields", uri)
        val session = controller("com.zjinnova.zlink", published)
        val media = MediaIntegrationManager(app, LauncherSettings(app))
        try {
            media.refresh(); await { media.state.value.artwork?.getPixel(0, 0) == Color.RED }
            provider.write(png(Color.GREEN))
            shadowOf(session).executeOnMetadataChanged(MediaMetadata.Builder(published).build())
            shadowOf(Looper.getMainLooper()).idle()
            await { media.state.value.artwork?.getPixel(0, 0) == Color.GREEN }
            assertEquals(2, provider.opens.get())
            assertEquals("Unchanged song fields", media.state.value.displayTitle)
        } finally { media.stop() }
    }

    @Test fun onlyTheSelectedSessionsUriIsOpened() {
        provider.write(png())
        controller("com.zjinnova.zlink", null)
        controller("com.tw.bt", metadata("Other source", uri))
        val media = MediaIntegrationManager(app, LauncherSettings(app))
        try {
            media.refresh(); shadowOf(Looper.getMainLooper()).idle()
            assertEquals("com.zjinnova.zlink", media.state.value.facts.source)
            assertNull(media.state.value.artwork)
            assertEquals(0, provider.opens.get())
        } finally { media.stop() }
    }

    @Test fun changingSongCancelsTheOldProviderAndCannotPublishItsLateCover() {
        provider.write(png())
        provider.blockFirst = true
        val session = controller("com.zjinnova.zlink", metadata("First song", uri))
        val media = MediaIntegrationManager(app, LauncherSettings(app))
        try {
            media.refresh()
            assertTrue(provider.entered.await(2, TimeUnit.SECONDS))
            val replacement = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
            shadowOf(session).executeOnMetadataChanged(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Second song")
                .putBitmap(MediaMetadata.METADATA_KEY_ART, replacement).build())
            shadowOf(Looper.getMainLooper()).idle()
            assertSame(replacement, media.state.value.artwork)
            assertTrue(provider.cancelled.await(2, TimeUnit.SECONDS))
            provider.release.countDown()
            assertTrue(provider.finished.await(2, TimeUnit.SECONDS))
            shadowOf(Looper.getMainLooper()).idle()
            assertSame(replacement, media.state.value.artwork)
            assertEquals("Second song", media.state.value.displayTitle)
        } finally { provider.release.countDown(); media.stop() }
    }

    @Test fun newMetadataWithTheSameUriStartsAFreshReadAndClearsTheOldImage() {
        provider.write(png()); provider.blockFirst = true
        val session = controller("com.zjinnova.zlink", metadata("First song", uri))
        val media = MediaIntegrationManager(app, LauncherSettings(app))
        try {
            media.refresh(); assertTrue(provider.entered.await(2, TimeUnit.SECONDS))
            provider.write(png(Color.GREEN))
            shadowOf(session).executeOnMetadataChanged(metadata("Second song", uri))
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(media.state.value.artwork)
            assertEquals("Second song", media.state.value.displayTitle)
            assertTrue(provider.cancelled.await(2, TimeUnit.SECONDS))
            provider.release.countDown()
            await { media.state.value.artwork?.getPixel(0, 0) == Color.GREEN }
            assertEquals(2, provider.opens.get())
        } finally { provider.release.countDown(); media.stop() }
    }

    @Test fun sessionChangeInvalidatesOldUriEvenIfItsMetadataArrivesLate() {
        provider.write(png()); provider.blockFirst = true
        controller("com.zjinnova.zlink", metadata("Old source", uri))
        val replacement = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        controller("com.android.player", MediaMetadata.Builder().putBitmap(MediaMetadata.METADATA_KEY_ART, replacement).build())
        val settings = LauncherSettings(app)
        val media = MediaIntegrationManager(app, settings)
        try {
            media.refresh(); assertTrue(provider.entered.await(2, TimeUnit.SECONDS))
            settings.mediaPackage = "com.android.player"; media.refresh()
            assertSame(replacement, media.state.value.artwork)
            provider.release.countDown(); assertTrue(provider.finished.await(2, TimeUnit.SECONDS))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("com.android.player", media.state.value.facts.source)
            assertSame(replacement, media.state.value.artwork)
        } finally { provider.release.countDown(); media.stop() }
    }

    @Test fun stoppingMediaClearsAndCancelsAnInflightCover() {
        provider.write(png()); provider.blockFirst = true
        controller("com.zjinnova.zlink", metadata("Song", uri))
        val media = MediaIntegrationManager(app, LauncherSettings(app))
        try {
            media.refresh(); assertTrue(provider.entered.await(2, TimeUnit.SECONDS))
            media.stop()
            assertTrue(provider.cancelled.await(2, TimeUnit.SECONDS))
            provider.release.countDown(); assertTrue(provider.finished.await(2, TimeUnit.SECONDS))
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(media.state.value.artwork)
            assertNull(media.state.value.facts.source)
            assertEquals("Multimedia detenida", media.state.value.status)
        } finally { provider.release.countDown(); media.stop() }
    }

    private fun metadata(title: String, value: Uri) = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title)
        .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, value.toString()).build()

    private fun controller(pkg: String, metadata: MediaMetadata?): MediaController {
        val token = Shadow.newInstanceOf(MediaSession.Token::class.java)
        val contract = Class.forName("android.media.session.ISessionController")
        val binder = Binder()
        ReflectionHelpers.setField(token, "mBinder", java.lang.reflect.Proxy.newProxyInstance(contract.classLoader, arrayOf(contract)) { _, method, _ ->
            when {
                method.name == "asBinder" -> binder
                method.returnType == Boolean::class.javaPrimitiveType -> false
                method.returnType == Int::class.javaPrimitiveType -> 0
                method.returnType == Long::class.javaPrimitiveType -> 0L
                else -> null
            }
        })
        return MediaController(app, token).also {
            shadowOf(it).setPackageName(pkg); shadowOf(it).setMetadata(metadata)
            shadowOf(it).setPlaybackState(PlaybackState.Builder().setActions(0).setState(PlaybackState.STATE_NONE, 0, 0f).build())
            shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(it)
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Artwork state did not reach the expected result", condition())
    }

    class ArtworkProvider : ContentProvider() {
        lateinit var file: File
        val opens = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val finished = CountDownLatch(1)
        @Volatile var openedOnMain = false
        @Volatile var denied = false
        @Volatile var unknownLength = false
        @Volatile var blockFirst = false
        override fun onCreate(): Boolean { file = File.createTempFile("cover-fixture", ".png", requireNotNull(context).cacheDir); return true }
        fun write(bytes: ByteArray) = file.writeBytes(bytes)
        override fun openAssetFile(uri: Uri, mode: String, signal: CancellationSignal?): AssetFileDescriptor {
            val first = opens.incrementAndGet() == 1
            openedOnMain = openedOnMain || Looper.myLooper() == Looper.getMainLooper()
            if (denied) throw SecurityException("Private path must never be exported")
            if (blockFirst && first) {
                signal?.setOnCancelListener { cancelled.countDown() }
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                finished.countDown()
            }
            return AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0,
                if (unknownLength) AssetFileDescriptor.UNKNOWN_LENGTH else file.length())
        }
        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor = openAssetFile(uri, mode, null)
        override fun openTypedAssetFile(uri: Uri, mimeTypeFilter: String, opts: Bundle?, signal: CancellationSignal?): AssetFileDescriptor =
            openAssetFile(uri, "r", signal)
        override fun getType(uri: Uri) = "image/png"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
