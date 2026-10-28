package com.mustangos.launcher

import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Looper
import com.mustangos.core.MediaCommand
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
import org.robolectric.shadows.ShadowMediaSessionManager
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class MediaSessionSelectionTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val settings get() = LauncherSettings(app)
    @Before fun setup() {
        app.getSharedPreferences("launcher", 0).edit().clear().commit()
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(ComponentName(app, MediaAccessService::class.java), true)
    }
    private fun controller(pkg: String = "com.tw.bt", title: String? = null, state: Int = PlaybackState.STATE_NONE,
        updated: Long = 1, token: MediaSession.Token? = null, add: Boolean = true): MediaController {
        val sessionToken = token ?: Shadow.newInstanceOf(MediaSession.Token::class.java).also {
            val contract = Class.forName("android.media.session.ISessionController")
            val binder = Binder()
            ReflectionHelpers.setField(it, "mBinder", java.lang.reflect.Proxy.newProxyInstance(contract.classLoader, arrayOf(contract)) { _, method, _ ->
                when {
                    method.name == "asBinder" -> binder
                    method.returnType == Boolean::class.javaPrimitiveType -> false
                    method.returnType == Int::class.javaPrimitiveType -> 0
                    method.returnType == Long::class.javaPrimitiveType -> 0L
                    else -> null
                }
            })
        }
        return MediaController(app, sessionToken).also {
            shadowOf(it).setPackageName(pkg)
            shadowOf(it).setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_SKIP_TO_NEXT)
                .setState(state, 0, 1f, updated).build())
            shadowOf(it).setMetadata(title?.let { value -> MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, value)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Private artist").putLong(MediaMetadata.METADATA_KEY_DURATION, 1000).build() })
            if (add) shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(it)
        }
    }
    private fun manager() = MediaIntegrationManager(app, settings).also { it.refresh() }

    @Test fun repeatedBtPackageSelectsThePlayingInformativeTokenAndControlsThatSameToken() {
        val empty = controller()
        val paused = controller(title = "Previous song", state = PlaybackState.STATE_PAUSED)
        repeat(10) { controller() }
        val playing = controller(title = "Current song", state = PlaybackState.STATE_PLAYING)
        val media = manager()
        try {
            assertEquals("Current song", media.state.value.displayTitle)
            assertTrue(media.state.value.facts.playing)
            assertTrue(media.command(MediaCommand.NEXT))
            assertEquals(PlaybackState.ACTION_SKIP_TO_NEXT, shadowOf(playing.transportControls).lastPerformedAction)
            assertEquals(0L, shadowOf(empty.transportControls).lastPerformedAction)
            assertEquals(0L, shadowOf(paused.transportControls).lastPerformedAction)
        } finally { media.stop() }
    }

    @Test fun explicitMissingSourceRemainsMissingAndExplainsThePreferenceInsteadOfSwitchingToBt() {
        controller(title = "Available BT song", state = PlaybackState.STATE_PLAYING)
        settings.mediaPackage = "com.zjinnova.zlink"
        val media = manager()
        try {
            assertNull(media.state.value.facts.source)
            assertEquals("Esperando la fuente multimedia elegida", media.state.value.status)
            assertFalse(media.command(MediaCommand.NEXT))
            val report = media.sessionDiagnostics()
            assertTrue(report.contains("preferencia=com.zjinnova.zlink"))
            assertTrue(report.contains("motivo=preferida_ausente"))
            assertFalse(report.contains("Available BT song"))
        } finally { media.stop() }
    }

    @Test fun explicitBtChoosesItsBestTokenAndNeverBorrowsAnotherPackagesTitle() {
        controller()
        val expected = controller(title = "BT song", state = PlaybackState.STATE_PAUSED)
        val other = controller(pkg = "com.other.player", title = "Other song", state = PlaybackState.STATE_PLAYING)
        settings.mediaPackage = "com.tw.bt"
        val media = manager()
        try {
            assertEquals("BT song", media.state.value.displayTitle)
            assertEquals("com.tw.bt", media.state.value.facts.source)
            assertTrue(media.command(MediaCommand.NEXT))
            assertEquals(PlaybackState.ACTION_SKIP_TO_NEXT, shadowOf(expected.transportControls).lastPerformedAction)
            assertEquals(0L, shadowOf(other.transportControls).lastPerformedAction)
        } finally { media.stop() }
    }

    @Test fun equallyUsefulNewTokenDoesNotDisplaceTheCurrentTokenOnPoll() {
        val original = controller(title = "First", state = PlaybackState.STATE_PLAYING, updated = 10)
        val media = manager()
        try {
            controller(title = "Other copy", state = PlaybackState.STATE_PLAYING, updated = 1000)
            val active = ReflectionHelpers.getStaticField<MutableList<MediaController>>(ShadowMediaSessionManager::class.java, "controllers")
            active.reverse()
            media.poll()
            assertEquals("First", media.state.value.displayTitle)
            assertTrue(media.command(MediaCommand.NEXT))
            assertEquals(PlaybackState.ACTION_SKIP_TO_NEXT, shadowOf(original.transportControls).lastPerformedAction)
        } finally { media.stop() }
    }

    @Test fun aCurrentPausedTokenYieldsToAPlayingTokenFromTheSamePackage() {
        controller(title = "Paused", state = PlaybackState.STATE_PAUSED)
        val media = manager()
        try {
            controller(title = "Playing", state = PlaybackState.STATE_PLAYING)
            media.poll()
            assertEquals("Playing", media.state.value.displayTitle)
        } finally { media.stop() }
    }

    @Test fun whenNoTokenIsSelectedTheNewestEquallyUsefulSessionWins() {
        controller(title = "Older", state = PlaybackState.STATE_PLAYING, updated = 10)
        controller(title = "Newer", state = PlaybackState.STATE_PLAYING, updated = 100)
        val media = manager()
        try { assertEquals("Newer", media.state.value.displayTitle) } finally { media.stop() }
    }

    @Test fun freshWrapperWithTheSameTokenRefreshesMetadataAndTransfersCallbacks() {
        val old = controller(title = "Old cached fields", state = PlaybackState.STATE_PLAYING)
        val media = manager()
        try {
            val fresh = controller(title = "Fresh fields", state = PlaybackState.STATE_PLAYING, token = old.sessionToken, add = false)
            // Simulate getActiveSessions returning a fresh wrapper for the same live token,
            // without notifying a temporary empty session list between snapshots.
            val active = ReflectionHelpers.getStaticField<MutableList<MediaController>>(ShadowMediaSessionManager::class.java, "controllers")
            active.clear(); active.add(fresh)
            media.poll()
            assertEquals("Fresh fields", media.state.value.displayTitle)
            assertTrue(shadowOf(old).callbacks.isEmpty())
            assertEquals(1, shadowOf(fresh).callbacks.size)
            shadowOf(fresh).executeOnMetadataChanged(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Callback fields").build())
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Callback fields", media.state.value.displayTitle)
        } finally { media.stop() }
    }

    @Test fun duplicateWrappersForTheSameBinderTokenCountAsOneSession() {
        val original = controller(title = "Song", state = PlaybackState.STATE_PLAYING)
        controller(title = "Song", state = PlaybackState.STATE_PLAYING, token = original.sessionToken)
        val media = manager()
        try {
            assertEquals(1, media.sessions.size)
            assertTrue(media.sessionDiagnostics().contains("paquetes=1; tokens=1"))
            assertEquals("Song", media.state.value.displayTitle)
        } finally { media.stop() }
    }

    @Test fun metadataQualityBreaksEqualPlaybackStateBeforeListOrder() {
        controller(state = PlaybackState.STATE_PAUSED)
        controller(title = "Useful paused song", state = PlaybackState.STATE_PAUSED)
        val media = manager()
        try { assertEquals("Useful paused song", media.state.value.displayTitle) } finally { media.stop() }
    }
}
