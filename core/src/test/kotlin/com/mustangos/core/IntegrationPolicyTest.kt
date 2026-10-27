package com.mustangos.core

import org.junit.Assert.*
import org.junit.Test

class IntegrationPolicyTest {
    @Test fun absenceDoesNotInventPackage() { assertNull(IntegrationPolicy.selectMedia(emptyList(), null, null)) }
    @Test fun detectsByInstalledLabelWithoutFixedPackage() {
        assertTrue(IntegrationPolicy.isZlinkCandidate(LaunchableApp("vendor.projection", "Main", "ZLink")))
        assertFalse(IntegrationPolicy.isZlinkCandidate(LaunchableApp("vendor.player", "Main", "Music")))
    }
    @Test fun ambiguousSessionsRequireSelection() { assertNull(IntegrationPolicy.selectMedia(listOf("a", "b"), null, null)) }
    @Test fun lostPreferredSourceDoesNotControlAnotherApp() { assertNull(IntegrationPolicy.selectMedia(listOf("b"), "a", null)) }
    @Test fun sourceReconnects() { assertEquals("a", IntegrationPolicy.selectMedia(listOf("a"), "a", null)) }
    @Test fun zlinkSessionCanBeSelectedWithoutClaimingCarplayConnected() { assertEquals("z", IntegrationPolicy.selectMedia(listOf("a", "z"), null, "z")) }
    @Test fun missingMetadataRemainsMissing() { val m = MediaFacts(source = "player"); assertNull(m.title); assertEquals(0, m.progress(1000)) }
    @Test fun controlsRequireCapabilitiesAndSession() {
        assertFalse(MediaFacts(actions = 32).supports(MediaCommand.NEXT))
        assertTrue(MediaFacts(source = "p", actions = 32).supports(MediaCommand.NEXT))
        assertFalse(MediaFacts(source = "p", actions = 32).supports(MediaCommand.PAUSE))
        assertTrue(MediaFacts(source = "p", actions = 512).supports(MediaCommand.PLAY))
    }
    @Test fun progressIsClampedAndPausedDoesNotAdvance() {
        val m = MediaFacts(durationMs = 2000, positionMs = 1000, updatedAtMs = 100, playing = true, speed = 1f)
        assertEquals(1500, m.progress(600)); assertEquals(2000, m.progress(6000))
        assertEquals(1000, m.copy(playing = false).progress(6000))
    }
}
