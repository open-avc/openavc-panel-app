package com.openavc.panel.discovery

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * First-contact certificate retrieval for OpenAVC HTTPS servers.
 *
 * Auto-generate TLS:
 *   GET https://host:port/api/certificate returns the server's CA in PEM.
 *   We pin that — the leaf rotates freely without re-pair.
 *
 * Provided TLS (user uploaded their own cert):
 *   /api/certificate returns 404. We open a trust-all handshake against
 *   /api/health and capture the leaf cert presented by the server, then pin it.
 *   A future leaf rotation requires re-pair (acceptable for the BYO-cert path).
 *
 * Both flows are TOFU on first contact. Mitigation: admin sheet shows the
 * pinned fingerprint so a paranoid integrator can compare against the value
 * shown in the Programmer IDE.
 */
class CertFetcher(context: Context) {

    private val trustStore = CertTrustStore(context)

    suspend fun fetchAndPin(
        host: String,
        port: Int,
        instanceId: String? = null,
    ): X509Certificate? = withContext(Dispatchers.IO) {
        val hostPort = CertTrustStore.hostPortKey(host, port)
        val auto = fetchAutoCa(host, port)
        if (auto != null) {
            trustStore.pin(instanceId, hostPort, auto.pem.toByteArray(Charsets.UTF_8))
            return@withContext auto.cert
        }
        val leaf = fetchProvidedLeaf(host, port)
        if (leaf != null) {
            trustStore.pin(instanceId, hostPort, pemEncode(leaf).toByteArray(Charsets.UTF_8))
            return@withContext leaf
        }
        null
    }

    private fun fetchAutoCa(host: String, port: Int): FetchedPem? {
        val url = URL("https://$host:$port/api/certificate")
        val conn = openTrustAll(url) ?: return null
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.requestMethod = "GET"
            val code = conn.responseCode
            if (code == 404) return null
            if (code !in 200..299) {
                Log.d(TAG, "auto-ca probe got $code from $url")
                return null
            }
            val pem = conn.inputStream.bufferedReader().use { it.readText() }
            val cert = CertTrustStore.parsePem(pem)
            return FetchedPem(cert, pem)
        } catch (e: Exception) {
            Log.d(TAG, "auto-ca fetch failed: ${e.message}")
            return null
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchProvidedLeaf(host: String, port: Int): X509Certificate? {
        val url = URL("https://$host:$port/api/health")
        val conn = openTrustAll(url) ?: return null
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.connect()
            val peer = conn.serverCertificates.firstOrNull() as? X509Certificate
            if (peer == null) {
                Log.d(TAG, "no peer cert from $url")
                return null
            }
            // Drain the body so the connection can be pooled cleanly.
            runCatching { conn.inputStream.use { it.readBytes() } }
            return peer
        } catch (e: Exception) {
            Log.d(TAG, "provided-leaf fetch failed: ${e.message}")
            return null
        } finally {
            conn.disconnect()
        }
    }

    private fun openTrustAll(url: URL): HttpsURLConnection? {
        val conn = url.openConnection() as? HttpsURLConnection ?: return null
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<X509TrustManager>(TRUST_ALL), java.security.SecureRandom())
        conn.sslSocketFactory = ctx.socketFactory
        conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
        return conn
    }

    private data class FetchedPem(val cert: X509Certificate, val pem: String)

    companion object {
        private const val TAG = "CertFetcher"
        private const val TIMEOUT_MS = 3_000

        private val TRUST_ALL = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        fun pemEncode(cert: X509Certificate): String {
            val b64 = java.util.Base64.getEncoder().encodeToString(cert.encoded)
            val wrapped = b64.chunked(64).joinToString("\n")
            return "-----BEGIN CERTIFICATE-----\n$wrapped\n-----END CERTIFICATE-----\n"
        }
    }
}
