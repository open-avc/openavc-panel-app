package com.openavc.panel.discovery

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoveryStatusTest {

    @Test
    fun `an empty list is still searching until discovery goes quiet`() {
        assertEquals(DiscoveryStatus.SEARCHING, DiscoveryStatus.of(serverCount = 0, nothingFound = false))
    }

    @Test
    fun `an empty list after a quiet spell says nothing was found`() {
        assertEquals(DiscoveryStatus.NOTHING_FOUND, DiscoveryStatus.of(serverCount = 0, nothingFound = true))
    }

    @Test
    fun `any listed system hides the status block`() {
        assertEquals(DiscoveryStatus.HIDDEN, DiscoveryStatus.of(serverCount = 1, nothingFound = false))
        assertEquals(DiscoveryStatus.HIDDEN, DiscoveryStatus.of(serverCount = 3, nothingFound = true))
    }
}
