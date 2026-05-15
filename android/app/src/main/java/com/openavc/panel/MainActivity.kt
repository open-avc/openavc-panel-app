package com.openavc.panel

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.openavc.panel.databinding.ActivityMainBinding
import com.openavc.panel.databinding.DialogUnlockPinBinding
import com.openavc.panel.databinding.SheetAdminBinding
import com.openavc.panel.discovery.ServerInfo
import com.openavc.panel.discovery.ServerValidator
import com.openavc.panel.kiosk.KioskManager
import com.openavc.panel.kiosk.KioskPreferences
import com.openavc.panel.prefs.AppPreferences
import com.openavc.panel.util.applyImmersive
import com.openavc.panel.util.showImmersive
import kotlinx.coroutines.launch

/**
 * Panel host. Displays the OpenAVC web panel inside a full-screen WebView and
 * handles reconnection UX when the server drops off.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPreferences
    private lateinit var kiosk: KioskManager
    private lateinit var kioskPrefs: KioskPreferences
    private lateinit var backCallback: OnBackPressedCallback
    private var server: ServerInfo? = null

    private var cornerTapCount = 0
    private var cornerTapWindowEnd = 0L
    private var cornerHotspotPx = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences(this)
        kiosk = KioskManager(this)
        kioskPrefs = KioskPreferences(this)
        cornerHotspotPx = (CORNER_HOTSPOT_DP * resources.displayMetrics.density).toInt()

        server = intentServerExtra() ?: prefs.getLastServer()
        val activeServer = server
        if (activeServer == null) {
            launchDiscovery()
            return
        }

        setupFullscreen()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        configureWebView(binding.webView)

        binding.retryButton.setOnClickListener { reload() }
        binding.changeServerFromError.setOnClickListener { launchDiscovery() }

        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    showExitConfirmation()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)

        binding.webView.loadUrl(activeServer.panelUrl)
    }

    override fun onResume() {
        super.onResume()
        setupFullscreen()
        applyKioskState()
        binding.webView.onResume()
    }

    override fun onPause() {
        binding.webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        (binding.webView.parent as? android.view.ViewGroup)?.removeView(binding.webView)
        binding.webView.destroy()
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN &&
            ev.x <= cornerHotspotPx && ev.y <= cornerHotspotPx
        ) {
            val now = System.currentTimeMillis()
            if (now > cornerTapWindowEnd) {
                cornerTapCount = 1
                cornerTapWindowEnd = now + CORNER_TAP_WINDOW_MS
            } else {
                cornerTapCount++
                if (cornerTapCount >= CORNER_TAPS_REQUIRED) {
                    cornerTapCount = 0
                    cornerTapWindowEnd = 0L
                    onAdminHotspot()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun onAdminHotspot() {
        if (kioskPrefs.hasPin() && kioskPrefs.kioskEnabled) {
            promptForPin { showAdminSheet() }
        } else {
            showAdminSheet()
        }
    }

    private fun promptForPin(onSuccess: () -> Unit) {
        val dialogBinding = DialogUnlockPinBinding.inflate(LayoutInflater.from(this))
        dialogBinding.unlockPinInput.requestFocus()
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.admin_unlock_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.admin_unlock_submit, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnDismissListener { setupFullscreen() }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = dialogBinding.unlockPinInput.text?.toString().orEmpty()
                if (kioskPrefs.checkPin(pin)) {
                    dialog.dismiss()
                    onSuccess()
                } else {
                    Toast.makeText(this, R.string.kiosk_pin_wrong, Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.showImmersive()
    }

    private fun showAdminSheet() {
        val sheet = BottomSheetDialog(this)
        sheet.setOnDismissListener { setupFullscreen() }
        val sheetBinding = SheetAdminBinding.inflate(LayoutInflater.from(this))
        sheetBinding.changeServer.setOnClickListener {
            sheet.dismiss()
            launchDiscovery()
        }
        sheetBinding.kioskSettings.setOnClickListener {
            sheet.dismiss()
            startActivity(Intent(this, KioskSetupActivity::class.java))
        }
        sheetBinding.closeApp.setOnClickListener {
            sheet.dismiss()
            closeApp()
        }
        sheetBinding.closeSheet.setOnClickListener { sheet.dismiss() }
        sheet.setContentView(sheetBinding.root)
        sheet.showImmersive()
    }

    /** Hard exit out of the panel. Stops lock-task first when applicable. */
    private fun closeApp() {
        if (kiosk.inLockTaskMode()) kiosk.stopLockTask(this)
        finish()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        webView.isLongClickable = false
        webView.setOnLongClickListener { true }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean = false

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                hideError()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (!request.isForMainFrame) return
                showError(error.description?.toString())
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (!request.isForMainFrame) return
                showError("HTTP ${errorResponse.statusCode}")
            }
        }
    }

    private fun reload() {
        val url = server?.panelUrl ?: return launchDiscovery()
        hideError()
        lifecycleScope.launch {
            val active = server ?: return@launch
            val reachable = ServerValidator.ping(active.host, active.port, active.scheme)
            if (reachable) {
                binding.webView.loadUrl(url)
            } else {
                showError(getString(R.string.discovery_unreachable))
            }
        }
    }

    private fun showError(detail: String?) {
        val active = server
        binding.errorDetail.text = getString(
            R.string.error_detail_format,
            active?.name ?: "The system"
        ) + (detail?.let { "\n\n$it" } ?: "")
        binding.errorOverlay.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.errorOverlay.visibility = View.GONE
    }

    private fun showExitConfirmation() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.exit_dialog_title)
            .setMessage(R.string.exit_dialog_message)
            .setPositiveButton(R.string.exit_dialog_stay, null)
            .setNegativeButton(R.string.change_server) { _, _ -> launchDiscovery() }
            .setNeutralButton(R.string.exit_dialog_close) { _, _ -> closeApp() }
            .create()
        dialog.setOnDismissListener { setupFullscreen() }
        dialog.showImmersive()
    }

    private fun setupFullscreen() {
        applyImmersive()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.decorView.systemGestureExclusionRects =
                listOf(Rect(0, 0, cornerHotspotPx, cornerHotspotPx))
        }
    }

    private fun applyKioskState() {
        val shouldLock = kioskPrefs.kioskEnabled
        val inLock = kiosk.inLockTaskMode()
        when {
            shouldLock && !inLock -> kiosk.startLockTask(this)
            !shouldLock && inLock -> kiosk.stopLockTask(this)
        }
        if (shouldLock) kiosk.suppressOemWindowControls()
        backCallback.isEnabled = !shouldLock
    }

    private fun launchDiscovery() {
        startActivity(
            Intent(this, ServerDiscoveryActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    @Suppress("DEPRECATION")
    private fun intentServerExtra(): ServerInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_SERVER, ServerInfo::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_SERVER)
        }

    companion object {
        const val EXTRA_SERVER = "server"
        private const val CORNER_HOTSPOT_DP = 80
        private const val CORNER_TAPS_REQUIRED = 3
        private const val CORNER_TAP_WINDOW_MS = 2_000L
    }
}
