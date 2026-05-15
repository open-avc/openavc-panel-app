package com.openavc.panel.discovery

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Performs a one-shot `/api/status` fetch against a candidate server and
 * returns a populated [ServerInfo] on success, or null if unreachable or
 * the response does not parse.
 *
 * HTTPS mode: fetches and pins the server's CA (auto-generate) or leaf
 * (provided cert) on first contact via [CertFetcher], then validates the
 * /api/status response under a [PinnedTrustManager]. After the server
 * returns its instance_id we re-pin under that key so the trust survives
 * IP changes across DHCP rotations.
 */
object ServerValidator {

    private const val TAG = "ServerValidator"
    private const val CONNECT_TIMEOUT_MS = 3_000
    private const val READ_TIMEOUT_MS = 3_000

    internal fun buildUrl(host: String, port: Int, scheme: String, path: String): URL =
        URL("$scheme://$host:$port$path")

    suspend fun validate(
        context: Context,
        host: String,
        port: Int,
        scheme: String = "http",
    ): ServerInfo? = withContext(Dispatchers.IO) {
        val statusUrl = buildUrl(host, port, scheme, "/api/status")
        val pinnedCert = if (scheme == "https") {
            CertFetcher(context).fetchAndPin(host, port, instanceId = null)
                ?: run {
                    Log.d(TAG, "could not fetch/pin cert for https://$host:$port")
                    return@withContext null
                }
        } else null

        val conn = openConnection(statusUrl, pinnedCert) ?: return@withContext null
        try {
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.d(TAG, "status $code from $statusUrl")
                return@withContext null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val name = json.optString("project_name")
                .ifEmpty { json.optString("name") }
                .ifEmpty { host }
            val version = json.optString("version")
            val instanceId = json.optString("instance_id")
            // Re-pin under instance_id so the pin survives DHCP-driven IP changes.
            if (pinnedCert != null && instanceId.isNotBlank()) {
                CertTrustStore(context).pin(
                    instanceId = instanceId,
                    hostPort = CertTrustStore.hostPortKey(host, port),
                    pemBytes = CertFetcher.pemEncode(pinnedCert).toByteArray(Charsets.UTF_8),
                )
            }
            ServerInfo(
                name = name,
                instanceId = instanceId,
                host = host,
                port = port,
                version = version,
                panelUrl = "$scheme://$host:$port/panel",
                scheme = scheme,
            )
        } catch (e: Exception) {
            Log.d(TAG, "validate failed for $host:$port: ${e.message}")
            null
        } finally {
            conn.disconnect()
        }
    }

    suspend fun ping(
        context: Context,
        host: String,
        port: Int,
        scheme: String = "http",
        instanceId: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val url = buildUrl(host, port, scheme, "/api/health")
        val pinnedCert = if (scheme == "https") {
            CertTrustStore(context).lookup(instanceId, CertTrustStore.hostPortKey(host, port))
                ?: CertFetcher(context).fetchAndPin(host, port, instanceId)
        } else null
        val conn = openConnection(url, pinnedCert) ?: return@withContext false
        try {
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    private fun openConnection(
        url: URL,
        pinnedCert: java.security.cert.X509Certificate?,
    ): HttpURLConnection? {
        val conn = url.openConnection() as? HttpURLConnection ?: return null
        if (pinnedCert != null && conn is HttpsURLConnection) {
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(
                null,
                arrayOf<X509TrustManager>(PinnedTrustManager(listOf(pinnedCert))),
                SecureRandom(),
            )
            conn.sslSocketFactory = ctx.socketFactory
            // The server cert's SANs cover localhost + the LAN IPs we advertise,
            // but a user can still hit it under a hostname we didn't enumerate.
            // Pinning the cert is the actual trust check; hostname is moot.
            conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
        }
        return conn
    }
}
