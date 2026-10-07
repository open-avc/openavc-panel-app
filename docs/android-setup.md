# Android Development Setup

How to build and run the OpenAVC Panel Android app from source.

## Prerequisites

- A current Android Studio. The project uses Android Gradle Plugin 8.13 (`gradle/libs.versions.toml`); a Studio release too old for it shows an error when it syncs the project.
- Android SDK Platform 36, installed via the SDK Manager.
- JDK 17 (bundled with Android Studio).

## First-time setup

1. Open Android Studio and choose **Open**, then select the `android/` directory inside `openavc-panel-app`.
2. Android Studio will prompt to trust the project and to sync Gradle. Accept. The first sync downloads the Gradle version named in `gradle/wrapper/gradle-wrapper.properties`.
3. If Android Studio prompts to install the Android Gradle Plugin, accept.
4. When sync completes, the tree view should show a single `app` module with `src/main/java/com/openavc/panel/`.

The Gradle wrapper (`gradlew` and `gradle/wrapper/gradle-wrapper.jar`) is committed, so the command line needs no separate Gradle install.

## Building

From Android Studio: **Build > Make Project**, or run the `app` configuration.

From the command line:

```bash
cd android
./gradlew assembleDebug            # Debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest        # Unit tests
./gradlew lint                     # Lint check
./gradlew installDebug             # Install to connected device
```

CI runs `assembleDebug`, `testDebugUnitTest` and `lintDebug` on every push and pull request. A lint error fails the build; warnings are reported and do not.

## Running on a device

Enable Developer Options and USB debugging on the tablet, connect via USB, and run from Android Studio. The debug build installs as its own app (`com.openavc.panel.debug`), so it sits beside a released copy instead of replacing it.

## Project layout

```
android/
├── settings.gradle.kts            # Declares the :app module
├── build.gradle.kts               # Root build (plugin aliases only)
├── gradle.properties              # JVM args, AndroidX flags
├── gradle/
│   ├── libs.versions.toml         # Version catalog (all deps)
│   └── wrapper/
│       └── gradle-wrapper.properties
└── app/
    ├── build.gradle.kts           # App module: SDK, deps, signing
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/openavc/panel/
        │   │   ├── MainActivity.kt              # Full-screen WebView that shows the panel
        │   │   ├── ServerDiscoveryActivity.kt   # "Find your OpenAVC" list
        │   │   ├── KioskSetupActivity.kt        # Dedicated panel settings: kiosk on/off, PIN
        │   │   ├── discovery/     # mDNS discovery, QR pairing, server checks, certificate pinning
        │   │   ├── kiosk/         # Dedicated panel mode: device owner, lock task, start on boot
        │   │   ├── prefs/         # The last server connected to
        │   │   └── util/          # Immersive full-screen helper
        │   └── res/
        │       ├── drawable/          # Icons
        │       ├── layout/            # Screens, dialogs and list rows
        │       ├── mipmap-*/          # Launcher icons
        │       ├── values/            # strings, colors, themes
        │       └── xml/               # backup, data-extraction and device-admin rules
        └── test/java/com/openavc/panel/   # Unit tests (discovery/, kiosk/)
```

## Dependency policy

Runtime dependencies are MIT or Apache-2.0 licensed, with one exception: Google's ML Kit (`com.google.mlkit:barcode-scanning`), which reads pairing QR codes, is under the [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms). ML Kit sends Google diagnostic and usage data, listed in its [data disclosure](https://developers.google.com/ml-kit/android-data-disclosure), and the app's Google Play Data safety answers declare it. When adding a new library, check the license before committing, and check whether it sends anything off the tablet, since that changes the Data safety answers too. Version bumps go through `gradle/libs.versions.toml`.

## Minimum/target SDK

- `minSdk = 26` (Android 8.0 Oreo). Covers ~97% of active devices as of 2026 and gives us adaptive launcher icons and modern WebView APIs.
- `targetSdk = 36` (Android 16). Google Play raises the minimum target level for new apps and updates each year; raise this with it.
- `compileSdk = 36`.
