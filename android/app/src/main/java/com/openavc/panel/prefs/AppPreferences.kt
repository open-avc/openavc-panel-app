package com.openavc.panel.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.openavc.panel.discovery.ServerInfo

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun saveLastServer(server: ServerInfo) {
        prefs.edit {
            putString(KEY_NAME, server.name)
            putString(KEY_INSTANCE_ID, server.instanceId)
            putString(KEY_HOST, server.host)
            putInt(KEY_PORT, server.port)
            putString(KEY_VERSION, server.version)
            putString(KEY_PANEL_URL, server.panelUrl)
            putString(KEY_SCHEME, server.scheme)
        }
    }

    fun getLastServer(): ServerInfo? {
        val host = prefs.getString(KEY_HOST, null) ?: return null
        val port = prefs.getInt(KEY_PORT, 0)
        if (port == 0) return null
        val scheme = prefs.getString(KEY_SCHEME, "http") ?: "http"
        return ServerInfo(
            name = prefs.getString(KEY_NAME, host) ?: host,
            instanceId = prefs.getString(KEY_INSTANCE_ID, "") ?: "",
            host = host,
            port = port,
            version = prefs.getString(KEY_VERSION, "") ?: "",
            panelUrl = prefs.getString(KEY_PANEL_URL, "$scheme://$host:$port/panel")
                ?: "$scheme://$host:$port/panel",
            scheme = scheme,
        )
    }

    fun clearLastServer() {
        prefs.edit { clear() }
    }

    companion object {
        private const val FILE_NAME = "openavc_panel_prefs"
        private const val KEY_NAME = "server_name"
        private const val KEY_INSTANCE_ID = "server_instance_id"
        private const val KEY_HOST = "server_host"
        private const val KEY_PORT = "server_port"
        private const val KEY_VERSION = "server_version"
        private const val KEY_PANEL_URL = "server_panel_url"
        private const val KEY_SCHEME = "server_scheme"
    }
}
