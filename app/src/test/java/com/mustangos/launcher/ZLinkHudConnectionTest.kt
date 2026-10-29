package com.mustangos.launcher

import android.os.Looper
import com.mustangos.launcher.carplay.ZLinkHudConnection
import com.mustangos.launcher.carplay.ZLinkHudProbe
import com.mustangos.launcher.settings.LauncherSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import java.io.*
import java.net.*
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class ZLinkHudConnectionTest {
    @Test fun optInOwnsOneSocketAcrossHomeBarHandoffAndReleasesAfterLastOwner() {
        val settings=LauncherSettings(RuntimeEnvironment.getApplication()); settings.zlinkHudEnabled=false
        val entered=CountDownLatch(1); val closed=CountDownLatch(1)
        var attempts=0
        val socket=object : Socket() {
            override fun setSoTimeout(timeout: Int) { }
            override fun connect(endpoint: SocketAddress,timeout: Int) { attempts++ }
            override fun getInputStream()=object : InputStream() {
                override fun read(): Int { entered.countDown(); closed.await(2,TimeUnit.SECONDS); return -1 }
            }
            override fun getOutputStream(): OutputStream = throw AssertionError("Write forbidden")
            override fun close() { closed.countDown() }
        }
        val probe=ZLinkHudProbe { socket }; val connection=ZLinkHudConnection(settings,probe)
        connection.visible("home",true); assertEquals(0,attempts)
        connection.setEnabled(true); assertTrue(entered.await(2,TimeUnit.SECONDS)); assertEquals(1,attempts)
        connection.visible("home",false); connection.visible("bar",true)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertEquals(1L,closed.count); assertEquals(1,attempts)
        connection.visible("bar",false)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertEquals(0L,closed.count); assertFalse(probe.state.value.running)
        probe.close()
    }
    @Test fun anotherProjectionAndDisabledPreferenceDoNotConnectAutomatically() {
        val settings=LauncherSettings(RuntimeEnvironment.getApplication())
        settings.setComponent("zlink",android.content.ComponentName("other.zlink","Activity"))
        var factories=0
        val probe=ZLinkHudProbe { factories++; Socket() }; val connection=ZLinkHudConnection(settings,probe)
        connection.visible("home",true); connection.setEnabled(true); connection.retryOrSample()
        assertEquals(0,factories); assertFalse(probe.state.value.running)
        connection.setEnabled(false); assertFalse(settings.zlinkHudEnabled)
        connection.visible("home",false); probe.close()
    }
}
