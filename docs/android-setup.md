# Android Development Setup

How to build and run the OpenAVC Panel Android app from source.

## Prerequisites

- Android Studio Ladybug (2024.2) or newer.
- Android SDK Platform 35 and Platform 26 installed via the SDK Manager.
- JDK 17 (bundled with Android Studio).

## First-time setup

1. Open Android Studio and choose **Open**, then select the `android/` directory inside `openavc-panel-app`.
2. Android Studio will prompt to trust the project and to sync Gradle. Accept. The first sync downloads the Gradle distribution declared in `gradle/wrapper/gradle-wrapper.properties` (Gradle 8.9) and populates the wrapper JAR.
3. If Android Studio prompts to install the Android Gradle Plugin, accept.
4. When sync completes, the tree view should show a single `app` module with `src/main/java/com/openavc/panel/`.

## Generating the Gradle wrapper JAR from the command line

The wrapper JAR (`gradle/wrapper/gradle-wrapper.jar`) is a binary that must exist for `./gradlew` to run. Android Studio generates it during sync. To generate it manually:

```bash
cd android
gradle wrapper --gradle-version 8.9
```

This requires a system Gradle install (`brew install gradle`, `choco install gradle`, etc.). Once generated, `./gradlew` works from the `android/` directory.

## Building

From Android Studio: **Build > Make Project**, or run the `app` configuration.

From the command line (after the wrapper JAR exists):

```bash
cd android
./gradlew assembleDebug            # Debug APK -> app/build/outputs/apk/debug/
./gradlew installDebug             # Install to connected device
./gradlew lint                     # Lint check
```

## Running on a device

Enable Developer Options and USB debugging on the tablet, connect via USB, and run from Android Studio. The scaffold build is a placeholder screen; the WebView panel arrives in Phase 1C.

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
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/openavc/panel/
        │   └── MainActivity.kt    # Stub; Phase 1C adds WebView
        └── res/
            ├── drawable/          # Launcher icon foreground/background
            ├── layout/            # activity_main.xml
            ├── mipmap-anydpi-v26/ # Adaptive launcher icons
            ├── values/            # strings, colors, themes
            └── xml/               # backup + data-extraction rules
```

## Dependency policy

All runtime dependencies are MIT or Apache-2.0 licensed. When adding a new library, check the license before committing. Version bumps go through `gradle/libs.versions.toml`.

## Minimum/target SDK

- `minSdk = 26` (Android 8.0 Oreo). Covers ~97% of active devices as of 2026 and gives us adaptive launcher icons and modern WebView APIs.
- `targetSdk = 35` (Android 15). Required by current AndroidX libraries and by Play Store for new submissions after Aug 2025.
- `compileSdk = 35`.
