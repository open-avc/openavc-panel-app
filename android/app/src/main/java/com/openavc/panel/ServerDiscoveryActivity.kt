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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.textfield.TextInputEditText
import com.openavc.panel.databinding.ActivityDiscoveryBinding
import com.openavc.panel.databinding.DialogManualEntryBinding
import com.openavc.panel.discovery.MDNSDiscovery
import com.openavc.panel.discovery.QRScannerActivity
import com.openavc.panel.discovery.ServerInfo
import com.openavc.panel.discovery.ServerListAdapter
import com.openavc.panel.discovery.ServerValidator
import com.openavc.panel.prefs.AppPreferences
import com.openavc.panel.util.applyImmersive
import com.openavc.panel.util.showImmersive
import kotlinx.coroutines.launch

class ServerDiscoveryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiscoveryBinding
    private lateinit var mdns: MDNSDiscovery
    private lateinit var prefs: AppPreferences
    private lateinit var adapter: ServerListAdapter

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

        adapter = ServerListAdapter { server -> onServerSelected(server) }
        binding.serverList.layoutManager = LinearLayoutManager(this)
        binding.serverList.adapter = adapter

        binding.scanQrButton.setOnClickListener { qrLauncher.launch(Unit) }
        binding.manualEntryButton.setOnClickListener { showManualEntryDialog() }
        binding.wifiSettingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mdns.servers.collect { servers ->
                    adapter.submitList(servers)
                    binding.emptyState.visibility =
                        if (servers.isEmpty()) View.VISIBLE else View.GONE
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
        connect(server.host, server.port, fallbackName = server.name, scheme = server.scheme)
    }

    private fun handleScannedUrl(url: String) {
        val parsed = ServerInfo.fromPanelUrl(url)
        if (parsed == null) {
            Toast.makeText(this, R.string.discovery_invalid_url, Toast.LENGTH_LONG).show()
            return
        }
        connect(parsed.host, parsed.port, fallbackName = parsed.name, scheme = parsed.scheme)
    }

    private fun showManualEntryDialog() {
        val dialogBinding = DialogManualEntryBinding.inflate(LayoutInflater.from(this))
        dialogBinding.hostInput.requestFocus()
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
        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val host = dialogBinding.hostInput.text?.toString()?.trim().orEmpty()
                val portText = dialogBinding.portInput.text?.toString()?.trim().orEmpty()
                val port = portText.toIntOrNull()
                if (host.isEmpty()) {
                    dialogBinding.hostLayout.error = getString(R.string.manual_host_hint)
                    return@setOnClickListener
                }
                if (port == null || port !in 1..65535) {
                    Toast.makeText(this, R.string.manual_port_hint, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val scheme = if (dialogBinding.httpsSwitch.isChecked) "https" else "http"
                dialog.dismiss()
                connect(host, port, fallbackName = host, scheme = scheme)
            }
        }
        dialog.setOnDismissListener { applyImmersive() }
        dialog.showImmersive()
    }

    private fun connect(host: String, port: Int, fallbackName: String, scheme: String = "http") {
        setConnecting(true)
        lifecycleScope.launch {
            val validated = ServerValidator.validate(host, port, scheme)
            setConnecting(false)
            if (validated == null) {
                Toast.makeText(
                    this@ServerDiscoveryActivity,
                    R.string.discovery_unreachable,
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            val final = if (validated.name.isBlank() || validated.name == host) {
                validated.copy(name = fallbackName)
            } else validated
            prefs.saveLastServer(final)
            val intent = Intent(this@ServerDiscoveryActivity, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SERVER, final)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
    }

    private fun setConnecting(connecting: Boolean) {
        binding.connectingOverlay.visibility = if (connecting) View.VISIBLE else View.GONE
        binding.scanQrButton.isEnabled = !connecting
        binding.manualEntryButton.isEnabled = !connecting
        binding.serverList.isEnabled = !connecting
    }

}
