package com.openavc.panel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.openavc.panel.databinding.ActivityKioskSetupBinding
import com.openavc.panel.databinding.DialogPinEntryBinding
import com.openavc.panel.kiosk.InstallSource
import com.openavc.panel.kiosk.KioskManager
import com.openavc.panel.kiosk.KioskPreferences
import com.openavc.panel.util.applyImmersive
import com.openavc.panel.util.showImmersive

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
        applyImmersive()
        applyToolbarGestureExclusion()

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
        applyImmersive()
        applyToolbarGestureExclusion()
        render()
    }

    /**
     * Carve out the toolbar's leading edge from Android's system-gesture region
     * so the left-edge back-swipe doesn't eat taps aimed at the toolbar's back
     * arrow. The rect is generous (96dp wide x 72dp tall) because OEM gesture
     * navs (Lenovo Tab M10 in particular) are sloppy about respecting the
     * exclusion — a tight rect gets clipped and the back arrow becomes near-
     * untouchable. Pair with toolbar `paddingStart` so the icon is also
     * physically away from the screen edge.
     */
    private fun applyToolbarGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val widthPx = (96 * resources.displayMetrics.density).toInt()
        val heightPx = (72 * resources.displayMetrics.density).toInt()
        binding.root.systemGestureExclusionRects =
            listOf(Rect(0, 0, widthPx, heightPx))
    }

    private fun render() {
        val capability = kiosk.capability()
        val provisioned = capability != KioskManager.Capability.SOFT
        val armed = prefs.kioskEnabled

        // The heading has to follow the switch, not just the provisioning
        // state. Lock Task only actually starts when MainActivity resumes, so
        // capability() still reads AVAILABLE while we are standing on this
        // screen with the switch on -- which used to leave the heading saying
        // "Ready to lock. Flip the switch below" immediately after you had
        // flipped it.
        val (stateTitle, stateDetail) = when {
            !provisioned -> {
                getString(R.string.kiosk_state_soft) to
                    getString(R.string.kiosk_state_soft_detail)
            }
            capability == KioskManager.Capability.TRUE_KIOSK_ACTIVE -> {
                getString(R.string.kiosk_state_true_active) to
                    getString(R.string.kiosk_state_true_active_detail)
            }
            armed -> {
                getString(R.string.kiosk_state_true_armed) to
                    getString(R.string.kiosk_state_true_armed_detail)
            }
            else -> {
                getString(R.string.kiosk_state_true_ready) to
                    getString(R.string.kiosk_state_true_ready_detail)
            }
        }
        binding.stateText.text = stateTitle
        binding.stateDetail.text = stateDetail

        renderProvisioning(provisioned)

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

    /**
     * Show provisioning steps only when there is provisioning left to do, and
     * word them for the route this copy of the app is actually on.
     *
     * Once the tablet is the device owner the ADB command is not merely
     * redundant, it would refuse to run -- leaving it on screen under a heading
     * that says the tablet is already provisioned invites the reader to think
     * something went wrong.
     */
    private fun renderProvisioning(provisioned: Boolean) {
        binding.provisionedSection.visibility = if (provisioned) View.VISIBLE else View.GONE
        binding.provisioningSection.visibility = if (provisioned) View.GONE else View.VISIBLE
        if (provisioned) return

        val source = InstallSource.detect(this)
        binding.provisioningDetail.text = getString(
            when (source) {
                InstallSource.PLAY_STORE -> R.string.kiosk_provisioning_detail_play
                InstallSource.SIDELOADED -> R.string.kiosk_provisioning_detail_sideloaded
                InstallSource.UNKNOWN -> R.string.kiosk_provisioning_detail_unknown
            }
        )
        // Only a Play install gets the "here is what to do instead" block: it
        // is the one case where we know the command in front of them cannot
        // work on this tablet as it stands.
        binding.playNextSteps.visibility =
            if (source == InstallSource.PLAY_STORE) View.VISIBLE else View.GONE
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
        // Re-render so the heading follows the switch. render() only ran from
        // onResume before, so flipping the switch left the state text stale --
        // it still read "Ready to lock. Flip the switch below" with the switch
        // already on. render() suspends the switch listener while it restores
        // state, so calling it from the listener does not recurse.
        render()
    }

    private fun showPinDialog() {
        val changing = prefs.hasPin()
        val dialogBinding = DialogPinEntryBinding.inflate(LayoutInflater.from(this))
        if (changing) {
            dialogBinding.currentPinLayout.visibility = View.VISIBLE
        }
        dialogBinding.newPinInput.requestFocus()
        val dialog = AlertDialog.Builder(this)
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
            .create()
        dialog.setOnDismissListener { applyImmersive() }
        // Focus the field the admin is going to type in: the current PIN when
        // changing one, otherwise the new PIN.
        dialog.showImmersive(
            if (changing) dialogBinding.currentPinInput else dialogBinding.newPinInput
        )
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
            "https://docs.openavc.com/panel-app-dedicated-android"
    }
}
