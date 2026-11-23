package com.openavc.panel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which answers to the panel's own page the app covers with its unreachable
 * overlay. The server answers every page with 503 while it starts and after a
 * start that failed, and that page says why and offers Start again, so it is
 * shown as it is.
 */
class ServerAnswerTest {

    @Test
    fun theServersStartingPageIsShown() {
        assertFalse(ServerAnswer.coveredByOverlay(503))
    }

    @Test
    fun anyOtherErrorIsCovered() {
        for (status in listOf(400, 401, 404, 500, 502, 504)) {
            assertTrue("HTTP $status", ServerAnswer.coveredByOverlay(status))
        }
    }
}
