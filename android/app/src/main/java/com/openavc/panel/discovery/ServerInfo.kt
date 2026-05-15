package com.openavc.panel.discovery

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ServerInfo(
    val name: String,
    val instanceId: String,
    val host: String,
    val port: Int,
    val version: String,
    val panelUrl: String,
    // Default keeps older code paths (manual entry, prefs restore) on plain HTTP.
    // The mDNS path overrides this when the server advertises scheme=https.
    val scheme: String = "http",
) : Parcelable {
    val statusUrl: String get() = "$scheme://$host:$port/api/status"
    val healthUrl: String get() = "$scheme://$host:$port/api/health"

    companion object {
        fun fromPanelUrl(url: String, name: String? = null): ServerInfo? {
            val trimmed = url.trim().trimEnd('/')
            val regex = Regex("^(https?)://([^:/]+)(?::(\\d+))?(/.*)?$", RegexOption.IGNORE_CASE)
            val match = regex.matchEntire(trimmed) ?: return null
            val scheme = match.groupValues[1].lowercase()
            val host = match.groupValues[2]
            // No port in URL → fall back to OpenAVC's defaults (8080 for http, 8443 for https).
            val port = match.groupValues[3]
                .ifEmpty { if (scheme == "https") "8443" else "8080" }
                .toInt()
            val path = match.groupValues[4].ifEmpty { "/panel" }
            return ServerInfo(
                name = name ?: host,
                instanceId = "",
                host = host,
                port = port,
                version = "",
                panelUrl = "$scheme://$host:$port$path",
                scheme = scheme,
            )
        }
    }
}
