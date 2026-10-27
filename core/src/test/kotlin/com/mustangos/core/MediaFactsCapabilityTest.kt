package com.mustangos.core

import org.junit.Assert.*
import org.junit.Test

class MediaFactsCapabilityTest {
    @Test fun zeroActionsDoNotAdvertiseUnreportedCapabilities() {
        for (command in MediaCommand.entries) assertFalse(MediaFacts(source = "player", actions = 0).supports(command))
    }
    @Test fun toggleCapabilityAllowsPlayAndPauseButNotSkipping() {
        val facts = MediaFacts(source = "player", actions = 512)
        assertTrue(facts.supports(MediaCommand.PLAY))
        assertTrue(facts.supports(MediaCommand.PAUSE))
        assertFalse(facts.supports(MediaCommand.NEXT))
    }
}
