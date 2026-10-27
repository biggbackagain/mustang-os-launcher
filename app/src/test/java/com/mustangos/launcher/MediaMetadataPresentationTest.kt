package com.mustangos.launcher

import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Looper
import com.mustangos.launcher.media.MediaAccessService
import com.mustangos.launcher.media.MediaIntegrationManager
import com.mustangos.launcher.settings.LauncherSettings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class MediaMetadataPresentationTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    @Before fun setup() {
        app.getSharedPreferences("launcher", 0).edit().clear().commit()
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(ComponentName(app, MediaAccessService::class.java), true)
    }
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
            shadowOf(it).setPackageName(pkg)
            shadowOf(it).setMetadata(metadata)
            shadowOf(it).setPlaybackState(PlaybackState.Builder().setActions(0).setState(PlaybackState.STATE_NONE, 0, 0f).build())
            shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(it)
        }
    }
    private fun media() = MediaIntegrationManager(app, LauncherSettings(app)).also { it.refresh() }
    @Test fun mainTextAndExistingAlbumBitmapRemainPreferred() {
        val albumArt = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        controller("com.zjinnova.zlink", MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, "Primary title")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "Primary artist")
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "Primary album")
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, "Display title")
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, "Display subtitle")
            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArt)
            .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888)).build())
        val media = media(); val ui = media.state.value
        assertEquals("Primary title", ui.displayTitle); assertEquals("Primary artist", ui.displaySubtitle)
        assertEquals("TITLE", ui.titleOrigin); assertEquals("ARTIST", ui.subtitleOrigin)
        assertEquals("Primary album", ui.facts.album); assertSame(albumArt, ui.artwork)
        media.stop()
    }
    @Test fun displayFieldsArePresentationFallbacksWithoutInventingArtistFacts() {
        controller("com.zjinnova.zlink", MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, "Display-only private title")
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, "Display-only private subtitle")
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, "Generic private description").build())
        val media = media(); val ui = media.state.value
        assertNull(ui.facts.title); assertNull(ui.facts.artist)
        assertEquals("Display-only private title", ui.displayTitle)
        assertEquals("Display-only private subtitle", ui.displaySubtitle)
        assertEquals("DISPLAY_TITLE", ui.titleOrigin); assertEquals("DISPLAY_SUBTITLE", ui.subtitleOrigin)
        val report = media.sessionDiagnostics()
        assertTrue(report.contains("DISPLAY_TITLE{clave=true,valor=true}"))
        assertTrue(report.contains("description.title=true"))
        assertFalse(report.contains("Display-only private")); assertFalse(report.contains("Generic private"))
        media.stop()
    }
    @Test fun genericDescriptionDoesNotBecomeArtistOrSubtitle() {
        controller("com.zjinnova.zlink", MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, "Heading")
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, "Not an artist").build())
        val media = media()
        assertNull(media.state.value.facts.artist)
        assertNull(media.state.value.displaySubtitle)
        assertNull(media.state.value.subtitleOrigin)
        assertTrue(media.sessionDiagnostics().contains("description.description=true"))
        media.stop()
    }
    @Test fun contentArtworkUriIsReportedWithoutLoadingOrExportingItsContents() {
        controller("com.zjinnova.zlink", MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, "content://test.private/art/private-name?token=secret")
            .putString("test.private.custom_key", "private-value").build())
        val media = media(); val report = media.sessionDiagnostics()
        assertNull(media.state.value.artwork)
        assertTrue(report.contains("ALBUM_ART_URI{clave=true,valor=true,content=true}"))
        assertFalse(report.contains("test.private")); assertFalse(report.contains("private-name")); assertFalse(report.contains("secret"))
        media.stop()
    }
    @Test fun everySessionIsInspectedWithoutMixingBluetoothMetadataIntoZlink() {
        controller("com.zjinnova.zlink", null)
        controller("com.tw.bt", MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "BT private title").build())
        val media = media(); val report = media.sessionDiagnostics()
        assertEquals("com.zjinnova.zlink", media.state.value.facts.source)
        assertNull(media.state.value.displayTitle)
        assertTrue(report.contains("com.zjinnova.zlink; seleccionada=true;"))
        assertTrue(report.contains("com.tw.bt; seleccionada=false;"))
        assertTrue(report.contains("metadata=false")); assertTrue(report.contains("TITLE{clave=true,valor=true}"))
        assertFalse(report.contains("BT private title"))
        media.stop()
    }
    @Test fun metadataCallbacksReplaceDisplayFallbackAndClearMissingValues() {
        val controller = controller("com.zjinnova.zlink", MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, "First").build())
        val media = media()
        assertEquals("First", media.state.value.displayTitle)
        shadowOf(controller).executeOnMetadataChanged(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, "Second").build())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Second", media.state.value.displayTitle)
        shadowOf(controller).executeOnMetadataChanged(MediaMetadata.Builder().build())
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(media.state.value.displayTitle); assertNull(media.state.value.titleOrigin)
        media.stop()
    }
}
