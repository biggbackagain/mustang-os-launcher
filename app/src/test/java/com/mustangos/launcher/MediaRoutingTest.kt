package com.mustangos.launcher

import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Looper
import android.view.KeyEvent
import com.mustangos.core.MediaCommand
import com.mustangos.launcher.appintegration.AppIntegrationManager
import com.mustangos.launcher.carplay.CarPlayIntegrationManager
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
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class MediaRoutingTest {
    private lateinit var app: Application
    private lateinit var settings: LauncherSettings
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("launcher", 0).edit().clear().commit()
        settings = LauncherSettings(app)
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(ComponentName(app, MediaAccessService::class.java), true)
    }
    private fun controller(pkg: String, actions: Long = 0, playback: Int = PlaybackState.STATE_NONE, keys: MutableList<KeyEvent> = mutableListOf()): MediaController {
        val token = Shadow.newInstanceOf(MediaSession.Token::class.java)
        val contract = Class.forName("android.media.session.ISessionController")
        val binder = Binder()
        val proxy = java.lang.reflect.Proxy.newProxyInstance(contract.classLoader, arrayOf(contract)) { _, method, args ->
            args.orEmpty().filterIsInstance<KeyEvent>().forEach { keys += KeyEvent(it) }
            when {
                method.name == "asBinder" -> binder
                method.name == "sendMediaButton" -> true
                method.returnType == Boolean::class.javaPrimitiveType -> false
                method.returnType == Int::class.javaPrimitiveType -> 0
                method.returnType == Long::class.javaPrimitiveType -> 0L
                else -> null
            }
        }
        ReflectionHelpers.setField(token, "mBinder", proxy)
        return MediaController(app, token).also {
            shadowOf(it).setPackageName(pkg)
            shadowOf(it).setPlaybackState(PlaybackState.Builder().setActions(actions).setState(playback, 0, 0f).build())
            shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(it)
        }
    }
    private fun manager() = MediaIntegrationManager(app, settings).also { it.refresh() }
    private fun install(pkg: String, label: String = pkg): ComponentName {
        val component = ComponentName(pkg, "$pkg.Start")
        val info = ActivityInfo().apply {
            packageName = pkg; name = component.className; exported = true; enabled = true
            applicationInfo = ApplicationInfo().apply { packageName = pkg; enabled = true }
        }
        val resolve = ResolveInfo().apply { activityInfo = info; nonLocalizedLabel = label }
        shadowOf(app.packageManager).addOrUpdateActivity(info)
        shadowOf(app.packageManager).addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), resolve)
        shadowOf(app.packageManager).addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg), resolve)
        return component
    }
    @Test fun unknownOemPlaybackUsesOneToggleWithoutInventingPausedStateOrCapabilities() {
        val keys = mutableListOf<KeyEvent>()
        val controller = controller("com.tw.bt", keys = keys)
        val media = manager()
        assertFalse(media.state.value.playbackStateKnown)
        assertEquals(0L, media.state.value.facts.actions)
        assertTrue(media.canCommand(MediaCommand.PAUSE))
        assertTrue(media.togglePlayPause())
        assertEquals(listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP), keys.map { it.action })
        assertTrue(keys.all { it.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE })
        assertEquals(0L, shadowOf(controller.transportControls).lastPerformedAction)
        assertTrue(shadowOf(app).broadcastIntents.isEmpty())
        media.stop()
    }
    @Test fun reportedPauseUsesTransportExactlyOnceWithoutMediaKeysOrBroadcasts() {
        val keys = mutableListOf<KeyEvent>()
        val controller = controller("test.player", PlaybackState.ACTION_PAUSE, PlaybackState.STATE_PLAYING, keys)
        val media = manager()
        assertTrue(media.togglePlayPause())
        assertEquals(PlaybackState.ACTION_PAUSE, shadowOf(controller.transportControls).lastPerformedAction)
        assertTrue(keys.isEmpty()); assertTrue(shadowOf(app).broadcastIntents.isEmpty())
        media.stop()
    }
    @Test fun nextOnLegacyOemUsesOneTransportWithoutDuplicateKeysOrBroadcasts() {
        val keys = mutableListOf<KeyEvent>()
        val controller = controller("com.tw.bt", keys = keys)
        val media = manager()
        assertTrue(media.command(MediaCommand.NEXT))
        assertEquals(PlaybackState.ACTION_SKIP_TO_NEXT, shadowOf(controller.transportControls).lastPerformedAction)
        assertTrue(keys.isEmpty()); assertTrue(shadowOf(app).broadcastIntents.isEmpty())
        media.stop()
    }
    @Test fun unknownThirdPartySessionCannotReceiveUnadvertisedControls() {
        val keys = mutableListOf<KeyEvent>()
        controller("test.player", keys = keys)
        val media = manager()
        assertFalse(media.canCommand(MediaCommand.PAUSE))
        assertFalse(media.command(MediaCommand.NEXT)); assertFalse(media.togglePlayPause())
        assertTrue(keys.isEmpty()); media.stop()
    }
    @Test fun absentExplicitSourceDoesNotFallBackToAnUnrelatedSession() {
        controller("com.tw.bt")
        settings.mediaPackage = "test.missing"
        val media = manager()
        assertNull(media.state.value.facts.source)
        assertFalse(media.command(MediaCommand.NEXT))
        assertEquals("test.missing", settings.mediaPackage)
        media.stop()
    }
    @Test fun revokedAccessIsClearedByPollingAndCannotDispatchControls() {
        val keys = mutableListOf<KeyEvent>()
        controller("com.tw.bt", keys = keys)
        val media = manager()
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(ComponentName(app, MediaAccessService::class.java), false)
        media.poll()
        assertNull(media.state.value.facts.source)
        assertFalse(media.togglePlayPause()); assertTrue(keys.isEmpty())
        media.stop()
    }
    @Test fun stoppingCancelsPostCommandPollsAndDoesNotReselectInBackground() {
        controller("com.tw.bt")
        val media = manager()
        media.command(MediaCommand.NEXT); media.stop()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertNull(media.state.value.facts.source)
        assertTrue(media.sessions.isEmpty())
    }
    @Test fun btBridgeOpensCarplayAndHonorsConfiguredProjectionComponent() {
        val expected = install("test.customprojection", "ZLink")
        install("com.zjinnova.zlink", "ZLINK")
        settings.setComponent("zlink", expected)
        controller("com.tw.bt")
        val media = manager()
        val apps = AppIntegrationManager(app, settings)
        assertTrue(media.openSource(apps, CarPlayIntegrationManager(apps)))
        assertEquals(expected, shadowOf(app).nextStartedActivity.component)
        media.stop()
    }
    @Test fun explicitAndroidSourceOpensItselfInsteadOfConfiguredCarplay() {
        val expected = install("test.androidplayer", "Music")
        settings.setComponent("zlink", install("com.zjinnova.zlink", "ZLINK"))
        settings.mediaPackage = expected.packageName
        controller(expected.packageName)
        val media = manager()
        val apps = AppIntegrationManager(app, settings)
        assertTrue(media.openSource(apps, CarPlayIntegrationManager(apps)))
        assertEquals(expected, shadowOf(app).nextStartedActivity.component)
        media.stop()
    }
    @Test fun configuredProjectionPermissionFailureDoesNotLaunchAnotherProjection() {
        val configured = install("test.customprojection", "ZLink")
        install("com.zjinnova.zlink", "ZLINK")
        val info = app.packageManager.getActivityInfo(configured, 0).apply { permission = "test.vendor.permission.PROJECTION" }
        shadowOf(app.packageManager).addOrUpdateActivity(info)
        settings.setComponent("zlink", configured)
        val apps = AppIntegrationManager(app, settings)
        assertFalse(CarPlayIntegrationManager(apps).open())
        assertTrue(apps.lastFailure!!.startsWith("Permiso OEM"))
        assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun missingExplicitAndroidSourceDoesNotLaunchDefaultMusicOrProjection() {
        install("com.zjinnova.zlink", "ZLINK")
        install("com.tw.music", "Music")
        settings.mediaPackage = "test.removedplayer"
        val media = manager()
        val apps = AppIntegrationManager(app, settings)
        assertFalse(media.openSource(apps, CarPlayIntegrationManager(apps)))
        assertNotNull(apps.lastFailure)
        assertNull(shadowOf(app).nextStartedActivity)
        media.stop()
    }
    @Test fun mapCandidateLabelsAloneCannotTurnUnrelatedAppsIntoNavigation() {
        install("test.navigationmusicsettings", "Maps Settings Music")
        assertTrue(AppIntegrationManager(app, settings).maps().isEmpty())
    }
    @Test fun mapCategoryCanIdentifyNavigationWithoutGuessingFromItsLabel() {
        val declared = install("test.atlas", "Atlas")
        val info = app.packageManager.getActivityInfo(declared, 0)
        shadowOf(app.packageManager).addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS), ResolveInfo().apply {
            activityInfo = info; nonLocalizedLabel = "Atlas"
        })
        val maps = AppIntegrationManager(app, settings).maps()
        assertEquals(1, maps.size)
        assertEquals(declared.packageName, maps.single().packageName)
    }
}
