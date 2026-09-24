package com.openavc.panel

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.openavc.panel.databinding.ActivityDiscoveryBinding
import com.openavc.panel.databinding.DialogManualEntryBinding
import com.openavc.panel.discovery.DiscoveryNotesAdapter
import com.openavc.panel.discovery.DiscoveryStatus
import com.openavc.panel.discovery.DiscoveryStatusAdapter
import com.openavc.panel.discovery.MDNSDiscovery
import com.openavc.panel.discovery.QRScannerActivity
import com.openavc.panel.discovery.ServerInfo
import com.openavc.panel.discovery.ServerListAdapter
import com.openavc.panel.discovery.ServerValidator
import com.openavc.panel.prefs.AppPreferences
import com.openavc.panel.util.applyImmersive
import com.openavc.panel.util.showImmersive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ServerDiscoveryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiscoveryBinding
    private lateinit var mdns: MDNSDiscovery
    private lateinit var prefs: AppPreferences
    private lateinit var statusAdapter: DiscoveryStatusAdapter
    private lateinit var serverAdapter: ServerListAdapter

    private val qrLauncher = registerForActivityResult(QRScannerActivity.Contract()) { url ->
        if (url != null) handleScannedUrl(url)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiscoveryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyImmersive()

        prefs = AppPreferences(this)
        mdns = MDNSDiscovery(this)

        // One scrolling list: the status block while it is empty, the systems,
        // then the two notes. The notes scroll with the rows so a network with
        // many systems never pushes them over the buttons.
        statusAdapter = DiscoveryStatusAdapter()
        serverAdapter = ServerListAdapter { server -> onServerSelected(server) }
        binding.serverList.layoutManager = LinearLayoutManager(this)
        binding.serverList.adapter =
            ConcatAdapter(statusAdapter, serverAdapter, DiscoveryNotesAdapter())

        binding.scanQrButton.setOnClickListener { qrLauncher.launch(Unit) }
        binding.manualEntryButton.setOnClickListener { showManualEntryDialog() }
        binding.wifiSettingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mdns.servers.combine(mdns.nothingFound) { servers, nothingFound ->
                    servers to DiscoveryStatus.of(servers.size, nothingFound)
                }.collect { (servers, status) ->
                    serverAdapter.submitList(servers)
                    statusAdapter.status = status
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mdns.start()
    }

    override fun onResume() {
        super.onResume()
        applyImmersive()
    }

    override fun onStop() {
        super.onStop()
        mdns.stop()
    }

    private fun onServerSelected(server: ServerInfo) {
        connect(server.host, server.port, fallbackName = server.name, scheme = server.scheme) {
            Toast.makeText(this, R.string.discovery_unreachable, Toast.LENGTH_LONG).show()
        }
    }

    private fun handleScannedUrl(url: String) {
        val parsed = ServerInfo.fromPanelUrl(url)
        if (parsed == null) {
            showScanFailure(getString(R.string.qr_failed_invalid))
            return
        }
        connect(parsed.host, parsed.port, fallbackName = parsed.name, scheme = parsed.scheme) {
            showScanFailure(getString(R.string.qr_failed_unreachable, parsed.host, parsed.port))
        }
    }

    private fun showScanFailure(message: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.qr_failed_title)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .create()
        dialog.setOnDismissListener { applyImmersive() }
        dialog.showImmersive()
    }

    private fun showManualEntryDialog() {
        val dialogBinding = DialogManualEntryBinding.inflate(LayoutInflater.from(this))
        // Mirror the OpenAVC default ports as the user toggles HTTPS, but only
        // when the field is still showing the previous default (don't clobber
        // a port the user typed themselves).
        val defaultHttpPort = getString(R.string.default_port)
        val defaultHttpsPort = getString(R.string.default_port_https)
        dialogBinding.httpsSwitch.setOnCheckedChangeListener { _, isChecked ->
            val current = dialogBinding.portInput.text?.toString().orEmpty()
            if (isChecked && current == defaultHttpPort) {
                dialogBinding.portInput.setText(defaultHttpsPort)
            } else if (!isChecked && current == defaultHttpsPort) {
                dialogBinding.portInput.setText(defaultHttpPort)
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.manual_dialog_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.manual_connect, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        // The dialog stays open while the address is checked, and after a
        // failure, so a mistyped address can be corrected rather than retyped.
        var check: Job? = null
        var connected = false
        fun setChecking(checking: Boolean) {
            dialogBinding.checkingProgress.visibility = if (checking) View.VISIBLE else View.GONE
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !checking
            dialogBinding.hostInput.isEnabled = !checking
            dialogBinding.portInput.isEnabled = !checking
            dialogBinding.httpsSwitch.isEnabled = !checking
        }
        fun showError(message: String) {
            dialogBinding.errorText.text = message
            dialogBinding.errorText.visibility = View.VISIBLE
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialogBinding.hostLayout.error = null
                dialogBinding.portLayout.error = null
                dialogBinding.errorText.visibility = View.GONE
                val host = dialogBinding.hostInput.text?.toString()?.trim().orEmpty()
                val portText = dialogBinding.portInput.text?.toString()?.trim().orEmpty()
                val port = portText.toIntOrNull()
                if (host.isEmpty()) {
                    dialogBinding.hostLayout.error = getString(R.string.manual_host_hint)
                    return@setOnClickListener
                }
                if (port == null || port !in 1..65535) {
                    dialogBinding.portLayout.error = getString(R.string.manual_port_invalid)
                    return@setOnClickListener
                }
                val scheme = if (dialogBinding.httpsSwitch.isChecked) "https" else "http"
                setChecking(true)
                check = lifecycleScope.launch {
                    val validated = validate(host, port, scheme)
                    setChecking(false)
                    if (validated == null) {
                        showError(getString(R.string.manual_unreachable, host, port))
                        return@launch
                    }
                    connected = true
                    dialog.dismiss()
                    openPanel(validated, host, fallbackName = host)
                }
            }
        }
        dialog.setOnDismissListener {
            if (!connected) check?.cancel()
            applyImmersive()
        }
        // Raise the keyboard through showImmersive, which does it after clearing
        // FLAG_NOT_FOCUSABLE. The previous setSoftInputMode call could not work:
        // it takes effect at show time, and the window is shown NOT_FOCUSABLE so
        // that the system bars stay hidden, and a window that cannot take focus
        // cannot raise a keyboard. This is why the field looked focused with no
        // keyboard even though 1G recorded it as fixed.
        dialog.showImmersive(dialogBinding.hostInput)
    }

    /** Validate with the full-screen spinner, then open the panel or call [onFailure]. */
    private fun connect(
        host: String,
        port: Int,
        fallbackName: String,
        scheme: String = "http",
        onFailure: () -> Unit,
    ) {
        setConnecting(true)
        lifecycleScope.launch {
            val validated = validate(host, port, scheme)
            setConnecting(false)
            if (validated == null) {
                onFailure()
                return@launch
            }
            openPanel(validated, host, fallbackName)
        }
    }

    private suspend fun validate(host: String, port: Int, scheme: String): ServerInfo? {
        // Quality of life: when the user types a known-HTTPS port without
        // ticking the HTTPS checkbox, try TLS first so the dialog doesn't
        // demand the right combination of inputs to reach a TLS server.
        val httpsProbe = if (scheme == "http" && port in HTTPS_GUESS_PORTS) {
            ServerValidator.validate(this, host, port, "https")
        } else null
        return httpsProbe ?: ServerValidator.validate(this, host, port, scheme)
    }

    private fun openPanel(validated: ServerInfo, host: String, fallbackName: String) {
        val final = if (validated.name.isBlank() || validated.name == host) {
            validated.copy(name = fallbackName)
        } else validated
        prefs.saveLastServer(final)
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_SERVER, final)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    private fun setConnecting(connecting: Boolean) {
        binding.connectingOverlay.visibility = if (connecting) View.VISIBLE else View.GONE
        binding.scanQrButton.isEnabled = !connecting
        binding.manualEntryButton.isEnabled = !connecting
        binding.serverList.isEnabled = !connecting
    }

    companion object {
        private val HTTPS_GUESS_PORTS = setOf(8443, 443, 4443, 9443)
    }
}
