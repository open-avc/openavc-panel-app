package com.openavc.panel.kiosk

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Where this copy of the app came from, used only to choose which
 * dedicated-panel provisioning advice to show.
 *
 * The thing that actually blocks device-owner provisioning is **accounts on the
 * device**, not the install source — Android refuses `dpm set-device-owner`
 * while any account exists. We cannot read that reliably from inside the app
 * (since API 26 `AccountManager` only returns accounts we have visibility of,
 * so an empty list proves nothing). Install source is a sound proxy in one
 * direction, which is the direction that matters: a Play install means a Google
 * account was signed in at install time, so the ADB route will not work on this
 * tablet as it stands.
 *
 * The reverse does not hold — a sideloaded app says nothing about whether
 * someone signed in afterwards — so the sideload copy still states the
 * no-account requirement rather than promising it is satisfied.
 */
enum class InstallSource {
    /** Installed by the Play Store, so an account was present. */
    PLAY_STORE,

    /** Installed by ADB or by tapping an APK. No account implied either way. */
    SIDELOADED,

    /** Could not be determined. Callers show the advice for both routes. */
    UNKNOWN,
    ;

    companion object {
        const val PLAY_STORE_PACKAGE = "com.android.vending"

        /**
         * The package installers that mean "a human put this APK here".
         *
         * `adb install` records no installer at all, which is why null maps to
         * SIDELOADED rather than UNKNOWN — it is the single most common way an
         * integrator installs this app, and treating it as unknown would hide
         * the ADB command from exactly the people who need it.
         */
        private val SIDELOAD_PACKAGES = setOf(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.android.shell",
        )

        /**
         * Pure classification, split out from the Android lookup so it can be
         * tested without a device.
         */
        fun classify(installerPackage: String?): InstallSource = when {
            installerPackage == null -> SIDELOADED
            installerPackage.isBlank() -> UNKNOWN
            installerPackage == PLAY_STORE_PACKAGE -> PLAY_STORE
            installerPackage in SIDELOAD_PACKAGES -> SIDELOADED
            // Some other store, an MDM, or an OEM preload. We have no idea what
            // it implies about accounts, so say so and show both routes.
            else -> UNKNOWN
        }

        /** Reads the installing package, then classifies it. */
        fun detect(context: Context): InstallSource {
            val pm = context.packageManager
            val name = context.packageName
            val installer = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pm.getInstallSourceInfo(name).installingPackageName
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstallerPackageName(name)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                return UNKNOWN
            } catch (e: IllegalArgumentException) {
                return UNKNOWN
            }
            return classify(installer)
        }
    }
}
