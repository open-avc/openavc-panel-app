package com.openavc.panel.discovery

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM tests for [ServerValidator]'s URL construction.
 *
 * The full IO path (HttpURLConnection + JSON parse) lives behind the
 * Android framework, so it's exercised in the manual-test plan rather
 * than here. We only assert that the scheme propagates into the URL
 * the IO call would target.
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
}
