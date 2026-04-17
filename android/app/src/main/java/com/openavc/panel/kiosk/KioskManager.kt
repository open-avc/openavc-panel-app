package com.openavc.panel.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Wraps Android Lock Task Mode so the panel activity can lock itself to the
 * foreground. Capability tiers:
 *
 *  - SOFT: immersive fullscreen only. No device-owner provisioning. System
 *    bars are hidden and back is intercepted, but a determined user can still
 *    swipe out to the home screen via system gestures.
 *  - TRUE: app is provisioned as Device Owner via
 *    `adb shell dpm set-device-owner com.openavc.panel/.kiosk.AdminReceiver`.
 *    [startLockTask] then makes the panel fully inescapable; home, recents,
 *    notifications, and the status bar are all blocked.
 */
class KioskManager(context: Context) {

    private val appContext = context.applicationContext
    private val dpm = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE)
        as DevicePolicyManager
    private val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE)
        as ActivityManager

    enum class Capability { SOFT, TRUE_KIOSK_AVAILABLE, TRUE_KIOSK_ACTIVE }

    fun capability(): Capability {
        val isOwner = dpm.isDeviceOwnerApp(appContext.packageName)
        return when {
            !isOwner -> Capability.SOFT
            inLockTaskMode() -> Capability.TRUE_KIOSK_ACTIVE
            else -> Capability.TRUE_KIOSK_AVAILABLE
        }
    }

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(appContext.packageName)

    fun inLockTaskMode(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            @Suppress("DEPRECATION")
            activityManager.isInLockTaskMode
        }
    }

    /**
     * Enable true kiosk Lock Task Mode. Safe to call when the app isn't
     * provisioned as Device Owner — the call just no-ops and the caller
     * continues in soft-kiosk immersive mode.
     */
    fun startLockTask(activity: Activity) {
        if (!dpm.isDeviceOwnerApp(appContext.packageName)) return
        val component = AdminReceiver.componentName(appContext)
        try {
            dpm.setLockTaskPackages(component, arrayOf(appContext.packageName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(
                    component,
                    DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
                )
            }
            activity.startLockTask()
        } catch (e: SecurityException) {
            Log.w(TAG, "startLockTask denied", e)
        }
    }

    fun stopLockTask(activity: Activity) {
        try {
            if (inLockTaskMode()) activity.stopLockTask()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "stopLockTask failed", e)
        }
    }

    /**
     * Relinquish device-owner status. Useful for uninstalling the app cleanly
     * during development. Only callable while the app is the current DO.
     */
    fun clearDeviceOwner() {
        if (dpm.isDeviceOwnerApp(appContext.packageName)) {
            try {
                dpm.clearDeviceOwnerApp(appContext.packageName)
            } catch (e: SecurityException) {
                Log.w(TAG, "clearDeviceOwner denied", e)
            }
        }
    }

    companion object {
        private const val TAG = "KioskManager"
        const val ADB_SET_OWNER_COMMAND =
            "adb shell dpm set-device-owner com.openavc.panel/.kiosk.AdminReceiver"
    }
}
