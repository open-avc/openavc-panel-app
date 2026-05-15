package com.openavc.panel.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-JVM unit tests for [ServerInfo].
 *
 * Covers scheme propagation through [ServerInfo.fromPanelUrl] and the
 * derived `statusUrl`/`healthUrl` getters. The mDNS-resolution path
 * itself ([MDNSDiscovery.toServerInfo]) still needs on-device
 * verification — see Phase 9 of the HTTPS/TLS plan.
 */
class ServerInfoTest {

    @Test
    fun fromPanelUrl_http_preservesScheme() {
        val info = ServerInfo.fromPanelUrl("http://192.168.1.10:8080/panel")!!
        assertEquals("http", info.scheme)
        assertEquals("192.168.1.10", info.host)
        assertEquals(8080, info.port)
        assertEquals("http://192.168.1.10:8080/panel", info.panelUrl)
    }

    @Test
    fun fromPanelUrl_https_preservesScheme() {
        val info = ServerInfo.fromPanelUrl("https://192.168.1.10:8443/panel")!!
        assertEquals("https", info.scheme)
        assertEquals(8443, info.port)
        assertEquals("https://192.168.1.10:8443/panel", info.panelUrl)
    }

    @Test
    fun fromPanelUrl_https_noPort_defaultsTo8443() {
        // Bare HTTPS host with no explicit port falls back to OpenAVC's
        // default TLS port, not the IANA HTTPS port (443).
        val info = ServerInfo.fromPanelUrl("https://server.local/panel")!!
        assertEquals("https", info.scheme)
        assertEquals(8443, info.port)
    }

    @Test
    fun fromPanelUrl_http_noPort_defaultsTo8080() {
        val info = ServerInfo.fromPanelUrl("http://server.local/panel")!!
        assertEquals("http", info.scheme)
        assertEquals(8080, info.port)
    }

    @Test
    fun fromPanelUrl_noPath_defaultsToPanel() {
        val info = ServerInfo.fromPanelUrl("https://192.168.1.10:8443")!!
        assertEquals("https://192.168.1.10:8443/panel", info.panelUrl)
    }

    @Test
    fun fromPanelUrl_mixedCaseScheme_normalizesToLowercase() {
        val info = ServerInfo.fromPanelUrl("HTTPS://192.168.1.10:8443/panel")!!
        assertEquals("https", info.scheme)
    }

    @Test
    fun fromPanelUrl_garbage_returnsNull() {
        assertNull(ServerInfo.fromPanelUrl("not a url"))
        assertNull(ServerInfo.fromPanelUrl("ftp://server/panel"))
        assertNull(ServerInfo.fromPanelUrl(""))
    }

    @Test
    fun statusUrl_usesScheme() {
        val http = ServerInfo(
            name = "x",
            instanceId = "",
            host = "h",
            port = 8080,
            version = "",
            panelUrl = "http://h:8080/panel",
            scheme = "http",
        )
        assertEquals("http://h:8080/api/status", http.statusUrl)

        val https = http.copy(scheme = "https", port = 8443)
        assertEquals("https://h:8443/api/status", https.statusUrl)
    }

    @Test
    fun healthUrl_usesScheme() {
        val info = ServerInfo(
            name = "x",
            instanceId = "",
            host = "h",
            port = 8443,
            version = "",
            panelUrl = "https://h:8443/panel",
            scheme = "https",
        )
        assertEquals("https://h:8443/api/health", info.healthUrl)
    }

    @Test
    fun defaultScheme_isHttp() {
        // Back-compat: callers that don't pass scheme (manual entry,
        // legacy prefs without the scheme key) stay on plain HTTP.
        val info = ServerInfo(
            name = "x",
            instanceId = "",
            host = "h",
            port = 8080,
            version = "",
            panelUrl = "http://h:8080/panel",
        )
        assertEquals("http", info.scheme)
        assertEquals("http://h:8080/api/status", info.statusUrl)
    }
}
