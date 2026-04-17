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
) : Parcelable {
    val statusUrl: String get() = "http://$host:$port/api/status"
    val healthUrl: String get() = "http://$host:$port/api/health"

    companion object {
        fun fromPanelUrl(url: String, name: String? = null): ServerInfo? {
            val trimmed = url.trim().trimEnd('/')
            val regex = Regex("^https?://([^:/]+)(?::(\\d+))?(/.*)?$")
            val match = regex.matchEntire(trimmed) ?: return null
            val host = match.groupValues[1]
            val port = match.groupValues[2].ifEmpty { "8080" }.toInt()
            val path = match.groupValues[3].ifEmpty { "/panel" }
            return ServerInfo(
                name = name ?: host,
                instanceId = "",
                host = host,
                port = port,
                version = "",
                panelUrl = "http://$host:$port$path",
            )
        }
    }
}
