package com.openavc.panel.discovery

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64

/**
 * Persists pinned certificates for OpenAVC servers.
 *
 * Two lookup keys per server:
 *   - `instance_id` (preferred, stable across DHCP rotation)
 *   - `host:port`   (fallback for first-pair before we've fetched /api/status)
 *
 * Stored value is the PEM-encoded certificate. In Auto-generate TLS mode this
 * is the server's CA; in Provided mode it's the leaf cert. The PinnedTrustManager
 * + WebView trust check decide which comparison rule to apply.
 */
class CertTrustStore internal constructor(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    )

    fun pin(instanceId: String?, hostPort: String?, pemBytes: ByteArray) {
        val pem = String(pemBytes, Charsets.UTF_8)
        prefs.edit {
            if (!instanceId.isNullOrBlank()) putString(keyForInstance(instanceId), pem)
            if (!hostPort.isNullOrBlank()) putString(keyForHostPort(hostPort), pem)
        }
    }

    /**
     * Returns the pinned cert (instance_id-first, then host:port), or null if
     * neither has been pinned. Throws nothing on parse failure — a corrupt entry
     * is treated as "no pin" so the user gets the re-pair flow instead of a crash.
     */
    fun lookup(instanceId: String?, hostPort: String?): X509Certificate? {
        val pem = lookupPem(instanceId, hostPort) ?: return null
        return runCatching { parsePem(pem) }.getOrNull()
    }

    fun lookupPem(instanceId: String?, hostPort: String?): String? {
        if (!instanceId.isNullOrBlank()) {
            prefs.getString(keyForInstance(instanceId), null)?.let { return it }
        }
        if (!hostPort.isNullOrBlank()) {
            prefs.getString(keyForHostPort(hostPort), null)?.let { return it }
        }
        return null
    }

    fun clear(instanceId: String?, hostPort: String? = null) {
        prefs.edit {
            if (!instanceId.isNullOrBlank()) remove(keyForInstance(instanceId))
            if (!hostPort.isNullOrBlank()) remove(keyForHostPort(hostPort))
        }
    }

    companion object {
        private const val FILE_NAME = "openavc_cert_trust"
        private const val PREFIX_INSTANCE = "i:"
        private const val PREFIX_HOSTPORT = "h:"

        private fun keyForInstance(instanceId: String) = PREFIX_INSTANCE + instanceId
        private fun keyForHostPort(hostPort: String) = PREFIX_HOSTPORT + hostPort.lowercase()

        fun parsePem(pem: String): X509Certificate {
            // Allow either a PEM block or raw base64 bytes — strip headers, decode.
            val body = pem
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replace("\\s+".toRegex(), "")
            val der = Base64.getDecoder().decode(body)
            val factory = CertificateFactory.getInstance("X.509")
            return factory.generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        }

        fun hostPortKey(host: String, port: Int): String = "${host.lowercase()}:$port"
    }
}
