package com.openavc.panel.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wraps [NsdManager] to discover `_openavc._tcp.` services on the local network
 * and expose them as a [StateFlow] of [ServerInfo].
 *
 * Serializes resolve() calls because the pre-API-30 NsdManager rejects concurrent
 * resolutions with FAILURE_ALREADY_ACTIVE.
 */
class MDNSDiscovery(context: Context) {

    private val nsdManager: NsdManager =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _servers = MutableStateFlow<List<ServerInfo>>(emptyList())
    val servers: StateFlow<List<ServerInfo>> = _servers.asStateFlow()

    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    private val resolving = AtomicBoolean(false)
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun start() {
        if (discoveryListener != null) return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "discovery started: $regType")
            }

            override fun onDiscoveryStopped(regType: String) {
                Log.d(TAG, "discovery stopped: $regType")
            }

            override fun onStartDiscoveryFailed(regType: String, errorCode: Int) {
                Log.w(TAG, "start discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(regType: String, errorCode: Int) {
                Log.w(TAG, "stop discovery failed: $errorCode")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "found: ${service.serviceName}")
                resolveQueue.offer(service)
                pumpResolveQueue()
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "lost: ${service.serviceName}")
                _servers.value = _servers.value.filterNot { it.name == service.serviceName }
            }
        }
        discoveryListener = listener
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: IllegalArgumentException) {
                // listener not registered; ignore
            }
        }
        discoveryListener = null
        resolveQueue.clear()
        resolving.set(false)
    }

    private fun pumpResolveQueue() {
        if (!resolving.compareAndSet(false, true)) return
        val next = resolveQueue.poll()
        if (next == null) {
            resolving.set(false)
            return
        }
        nsdManager.resolveService(next, object : NsdManager.ResolveListener {
            override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "resolve failed for ${service.serviceName}: $errorCode")
                resolving.set(false)
                pumpResolveQueue()
            }

            override fun onServiceResolved(service: NsdServiceInfo) {
                val info = toServerInfo(service)
                if (info != null) {
                    _servers.value = (_servers.value.filterNot { it.name == info.name } + info)
                        .sortedBy { it.name.lowercase() }
                }
                resolving.set(false)
                pumpResolveQueue()
            }
        })
    }

    private fun toServerInfo(service: NsdServiceInfo): ServerInfo? {
        val host = service.host ?: return null
        // Prefer IPv4 — the panel URL is used in a WebView and link-local IPv6
        // addresses can trip up the loopback-proxy path on some devices.
        val address = (host as? Inet4Address)?.hostAddress ?: host.hostAddress ?: return null
        val port = service.port
        val attrs = service.attributes.mapValues { it.value?.let { bytes -> String(bytes) } ?: "" }
        val name = attrs["name"].orEmpty().ifEmpty { service.serviceName }
        val instanceId = attrs["id"].orEmpty()
        val version = attrs["version"].orEmpty()
        val path = attrs["path"].ifNullOrEmpty("/panel")
        return ServerInfo(
            name = name,
            instanceId = instanceId,
            host = address,
            port = port,
            version = version,
            panelUrl = "http://$address:$port$path",
        )
    }

    private fun String?.ifNullOrEmpty(fallback: String): String =
        if (this.isNullOrEmpty()) fallback else this

    companion object {
        const val SERVICE_TYPE = "_openavc._tcp."
        private const val TAG = "MDNSDiscovery"
    }
}
