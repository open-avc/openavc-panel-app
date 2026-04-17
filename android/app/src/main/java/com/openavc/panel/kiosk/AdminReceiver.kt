package com.openavc.panel.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context

/**
 * Minimal [DeviceAdminReceiver] subclass. Only exists so the app package can
 * be provisioned as a Device Owner (required to programmatically enable
 * true-kiosk Lock Task Mode). The receiver itself has no runtime behavior.
 */
class AdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, AdminReceiver::class.java)
    }
}
