package com.openavc.panel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.openavc.panel.databinding.ActivityKioskSetupBinding
import com.openavc.panel.databinding.DialogPinEntryBinding
import com.openavc.panel.kiosk.KioskManager
import com.openavc.panel.kiosk.KioskPreferences

/**
 * Admin-only screen reached from the in-panel admin sheet. Shows current
 * kiosk state, lets the admin toggle kiosk on/off, manage the PIN, and
 * read the ADB provisioning command for true-kiosk setup.
 */
class KioskSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKioskSetupBinding
    private lateinit var kiosk: KioskManager
    private lateinit var prefs: KioskPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKioskSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        kiosk = KioskManager(this)
        prefs = KioskPreferences(this)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.adbCommand.text = KioskManager.ADB_SET_OWNER_COMMAND
        binding.copyButton.setOnClickListener { copyAdbCommand() }
        binding.docsButton.setOnClickListener { openDocs() }
        binding.pinButton.setOnClickListener { showPinDialog() }
        binding.kioskSwitch.setOnCheckedChangeListener { _, isChecked ->
            onKioskToggle(isChecked)
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val capability = kiosk.capability()
        val (stateTitle, stateDetail) = when (capability) {
            KioskManager.Capability.SOFT -> {
                getString(R.string.kiosk_state_soft) to
                    getString(R.string.kiosk_state_soft_detail)
            }
            KioskManager.Capability.TRUE_KIOSK_AVAILABLE -> {
                getString(R.string.kiosk_state_true_ready) to
                    getString(R.string.kiosk_state_true_ready_detail)
            }
            KioskManager.Capability.TRUE_KIOSK_ACTIVE -> {
                getString(R.string.kiosk_state_true_active) to
                    getString(R.string.kiosk_state_true_active_detail)
            }
        }
        binding.stateText.text = stateTitle
        binding.stateDetail.text = stateDetail

        val toggleOn = prefs.kioskEnabled
        // Suspend the listener while we restore state so we don't trigger
        // the toggle handler as part of rendering.
        binding.kioskSwitch.setOnCheckedChangeListener(null)
        binding.kioskSwitch.isChecked = toggleOn
        binding.kioskSwitch.setOnCheckedChangeListener { _, isChecked ->
            onKioskToggle(isChecked)
        }

        val hasPin = prefs.hasPin()
        binding.pinState.text = getString(
            if (hasPin) R.string.kiosk_pin_set_detail else R.string.kiosk_pin_unset_detail
        )
        binding.pinButton.text = getString(
            if (hasPin) R.string.kiosk_pin_change else R.string.kiosk_pin_set
        )
    }

    private fun onKioskToggle(isChecked: Boolean) {
        if (isChecked && !prefs.hasPin()) {
            Toast.makeText(this, R.string.kiosk_toggle_requires_pin, Toast.LENGTH_LONG).show()
            binding.kioskSwitch.isChecked = false
            return
        }
        prefs.kioskEnabled = isChecked
        // Actual Lock Task start/stop happens on MainActivity's next resume.
        // Show a short hint that they need to return to the panel for the
        // change to take effect.
        val messageRes = if (isChecked) R.string.kiosk_toggle_on_hint else R.string.kiosk_toggle_off_hint
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun showPinDialog() {
        val changing = prefs.hasPin()
        val dialogBinding = DialogPinEntryBinding.inflate(LayoutInflater.from(this))
        if (changing) {
            dialogBinding.currentPinLayout.visibility = View.VISIBLE
        }
        dialogBinding.newPinInput.requestFocus()
        AlertDialog.Builder(this)
            .setTitle(
                if (changing) R.string.kiosk_pin_change_title else R.string.kiosk_pin_set_title
            )
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val current = dialogBinding.currentPinInput.text?.toString().orEmpty()
                val proposed = dialogBinding.newPinInput.text?.toString().orEmpty()
                val confirm = dialogBinding.confirmPinInput.text?.toString().orEmpty()
                handlePinSave(changing, current, proposed, confirm)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun handlePinSave(
        changing: Boolean,
        current: String,
        proposed: String,
        confirm: String,
    ) {
        if (changing && !prefs.checkPin(current)) {
            Toast.makeText(this, R.string.kiosk_pin_wrong, Toast.LENGTH_SHORT).show()
            return
        }
        if (proposed.length < MIN_PIN_LENGTH) {
            Toast.makeText(this, R.string.kiosk_pin_too_short, Toast.LENGTH_SHORT).show()
            return
        }
        if (proposed != confirm) {
            Toast.makeText(this, R.string.kiosk_pin_mismatch, Toast.LENGTH_SHORT).show()
            return
        }
        prefs.setPin(proposed)
        Toast.makeText(this, R.string.kiosk_pin_saved, Toast.LENGTH_SHORT).show()
        render()
    }

    private fun copyAdbCommand() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(
            ClipData.newPlainText("adb command", KioskManager.ADB_SET_OWNER_COMMAND)
        )
        Toast.makeText(this, R.string.kiosk_copied, Toast.LENGTH_SHORT).show()
    }

    private fun openDocs() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DOCS_URL))
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.kiosk_docs_failed, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val MIN_PIN_LENGTH = 4
        private const val DOCS_URL =
            "https://docs.openavc.com/panel-app-kiosk-android"
    }
}
