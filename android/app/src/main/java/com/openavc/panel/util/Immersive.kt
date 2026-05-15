package com.openavc.panel.util

import android.app.Activity
import android.app.Dialog
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Apply the panel app's standard immersive treatment to a window: status
 * bar and nav bar hidden, transient swipe-to-reveal allowed. Idempotent.
 */
fun Window.applyImmersive() {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    val controller = WindowInsetsControllerCompat(this, decorView)
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.captionBar()
    )
}

fun Activity.applyImmersive() {
    window.applyImmersive()
}

/**
 * Show a dialog without letting Android's system bars pop back into view.
 *
 * Each dialog gets its own [Window], which by default doesn't inherit the
 * parent activity's immersive flags — so opening any dialog (bottom sheet,
 * alert) flashes the status and nav bars into view. The standard fix is to
 * mark the dialog window NOT_FOCUSABLE so it can't steal focus during the
 * show, hide the system bars, then drop NOT_FOCUSABLE so the dialog accepts
 * input again. Mirrors what every kiosk Android app in the wild does.
 */
fun Dialog.showImmersive() {
    val w = window
    if (w == null) {
        show()
        return
    }
    w.setFlags(
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
    )
    show()
    w.applyImmersive()
    w.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
}
