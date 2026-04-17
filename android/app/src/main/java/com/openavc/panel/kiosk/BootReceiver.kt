package com.openavc.panel.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.openavc.panel.MainActivity
import com.openavc.panel.prefs.AppPreferences

/**
 * Auto-launches the panel when the tablet finishes booting. The receiver
 * only fires when [Intent.ACTION_BOOT_COMPLETED] is broadcast and the app
 * previously connected to a server (saved in [AppPreferences]).
 *
 * When a saved server exists, MainActivity itself re-verifies reachability
 * and routes to Discovery if the host is down, so we don't duplicate the
 * health check here.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                launchPanel(context)
            }
        }
    }

    private fun launchPanel(context: Context) {
        val prefs = AppPreferences(context)
        if (prefs.getLastServer() == null) {
            Log.d(TAG, "no saved server; skipping boot launch")
            return
        }
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(launch)
        } catch (e: Exception) {
            Log.w(TAG, "boot launch failed", e)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
