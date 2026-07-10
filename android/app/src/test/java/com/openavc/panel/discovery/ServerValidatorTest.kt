package com.openavc.panel.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-JVM tests for [ServerValidator]'s URL construction and the
 * http-to-https TLS handoff decision.
 *
 * The full IO path (HttpURLConnection + JSON parse) lives behind the
 * Android framework, so it's exercised in the manual-test plan rather
 * than here. We only assert on the pure pieces: the URL the IO call
 * would target, and how a probe response classifies as a handoff.
 */
class ServerValidatorTest {

    @Test
    fun buildUrl_http_status() {
        val url = ServerValidator.buildUrl("192.168.1.10", 8080, "http", "/api/status")
        assertEquals("http://192.168.1.10:8080/api/status", url.toString())
    }

    @Test
    fun buildUrl_https_status() {
        val url = ServerValidator.buildUrl("192.168.1.10", 8443, "https", "/api/status")
        assertEquals("https://192.168.1.10:8443/api/status", url.toString())
    }

    @Test
    fun buildUrl_https_health() {
        val url = ServerValidator.buildUrl("server.local", 8443, "https", "/api/health")
        assertEquals("https://server.local:8443/api/health", url.toString())
    }

    @Test
    fun handoff_302_httpsLocation_usesExplicitPort() {
        val port = ServerValidator.tlsHandoffPort(302, "https://192.168.1.10:8443/api/status")
        assertEquals(8443, port)
    }

    @Test
    fun handoff_302_httpsLocation_defaultsTo443() {
        val port = ServerValidator.tlsHandoffPort(302, "https://192.168.1.10/api/status")
        assertEquals(443, port)
    }

    @Test
    fun handoff_anyRedirectCode_qualifies() {
        assertEquals(8443, ServerValidator.tlsHandoffPort(301, "https://192.168.1.10:8443/"))
        assertEquals(8443, ServerValidator.tlsHandoffPort(307, "https://192.168.1.10:8443/"))
        assertEquals(8443, ServerValidator.tlsHandoffPort(308, "https://192.168.1.10:8443/"))
    }

    @Test
    fun handoff_certifiedNameLocation_portStillHonored() {
        // A server with a cloud-issued certificate redirects to its public
        // DNS name. The port carries over; the caller keeps probing the
        // scanned IP rather than following that name.
        val port = ServerValidator.tlsHandoffPort(
            302,
            "https://192-168-1-10.abc123.example.net:8443/api/status?probe=1",
        )
        assertEquals(8443, port)
    }

    @Test
    fun handoff_httpLocation_isNotAHandoff() {
        assertNull(ServerValidator.tlsHandoffPort(302, "http://192.168.1.10:8080/panel"))
    }

    @Test
    fun handoff_non3xxCode_isNotAHandoff() {
        assertNull(ServerValidator.tlsHandoffPort(200, "https://192.168.1.10:8443/"))
        assertNull(ServerValidator.tlsHandoffPort(404, "https://192.168.1.10:8443/"))
        assertNull(ServerValidator.tlsHandoffPort(500, "https://192.168.1.10:8443/"))
    }

    @Test
    fun handoff_missingOrBlankLocation_isNotAHandoff() {
        assertNull(ServerValidator.tlsHandoffPort(302, null))
        assertNull(ServerValidator.tlsHandoffPort(302, ""))
        assertNull(ServerValidator.tlsHandoffPort(302, "   "))
    }

    @Test
    fun handoff_relativeLocation_isNotAHandoff() {
        assertNull(ServerValidator.tlsHandoffPort(302, "/panel"))
    }

    @Test
    fun handoff_malformedLocation_isNotAHandoff() {
        assertNull(ServerValidator.tlsHandoffPort(302, "not a url"))
        assertNull(ServerValidator.tlsHandoffPort(302, "https://"))
    }
}
