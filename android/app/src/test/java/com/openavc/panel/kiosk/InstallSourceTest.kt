package com.openavc.panel.kiosk

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the classification rule without a device.
 *
 * The Play Store branch cannot be exercised on real hardware until the app is
 * actually on Play, so these cases are what stands behind that copy path.
 */
class InstallSourceTest {

    @Test
    fun playStoreInstallerIsPlayStore() {
        assertEquals(
            InstallSource.PLAY_STORE,
            InstallSource.classify("com.android.vending"),
        )
    }

    @Test
    fun nullInstallerIsSideloaded() {
        // adb install records no installer. This is the integrator's usual
        // route, so it has to land on the branch that shows the ADB command.
        assertEquals(InstallSource.SIDELOADED, InstallSource.classify(null))
    }

    @Test
    fun packageInstallerIsSideloaded() {
        assertEquals(
            InstallSource.SIDELOADED,
            InstallSource.classify("com.google.android.packageinstaller"),
        )
        assertEquals(
            InstallSource.SIDELOADED,
            InstallSource.classify("com.android.packageinstaller"),
        )
        assertEquals(
            InstallSource.SIDELOADED,
            InstallSource.classify("com.android.shell"),
        )
    }

    @Test
    fun anotherStoreIsUnknown() {
        // Amazon, Samsung, an MDM, an OEM preload: we cannot say what any of
        // them imply about accounts, so the UI falls back to showing both routes.
        assertEquals(InstallSource.UNKNOWN, InstallSource.classify("com.amazon.venezia"))
        assertEquals(
            InstallSource.UNKNOWN,
            InstallSource.classify("com.sec.android.app.samsungapps"),
        )
    }

    @Test
    fun blankInstallerIsUnknown() {
        assertEquals(InstallSource.UNKNOWN, InstallSource.classify(""))
        assertEquals(InstallSource.UNKNOWN, InstallSource.classify("   "))
    }
}
