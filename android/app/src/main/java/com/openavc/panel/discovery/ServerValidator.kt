package com.openavc.panel.discovery

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Performs a one-shot `/api/status` fetch against a candidate server and
 * returns a populated [ServerInfo] on success, or null if unreachable or
 * the response does not parse.
 */
object ServerValidator {

    private const val TAG = "ServerValidator"
    private const val CONNECT_TIMEOUT_MS = 3_000
    private const val READ_TIMEOUT_MS = 3_000

    internal fun buildUrl(host: String, port: Int, scheme: String, path: String): URL =
        URL("$scheme://$host:$port$path")

    suspend fun validate(host: String, port: Int, scheme: String = "http"): ServerInfo? =
        withContext(Dispatchers.IO) {
            val statusUrl = buildUrl(host, port, scheme, "/api/status")
            val conn = statusUrl.openConnection() as? HttpURLConnection ?: return@withContext null
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

    suspend fun ping(host: String, port: Int, scheme: String = "http"): Boolean =
        withContext(Dispatchers.IO) {
            val url = buildUrl(host, port, scheme, "/api/health")
            val conn = url.openConnection() as? HttpURLConnection ?: return@withContext false
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
}
