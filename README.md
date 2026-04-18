# OpenAVC Panel App

Native Android and iOS apps that turn a commodity tablet into a dedicated AV touch panel for an [OpenAVC](https://github.com/open-avc/openavc) system.

> **Status:** Android in active development. First signed APK is on the [Releases](https://github.com/open-avc/openavc-panel-app/releases) page. Google Play listing is in progress. iOS build not yet started; App Store listing coming after.

## What These Apps Do

OpenAVC's web panel is already touch-optimized and runs great in any mobile browser. These apps exist for the things a browser cannot do on a wall-mounted tablet:

- **Auto-discovery.** Find the OpenAVC server on the network automatically via mDNS. No typing IP addresses.
- **QR pairing.** Scan the QR code shown in the Programmer IDE to connect instantly.
- **Kiosk mode.** Lock the tablet to the panel. Users cannot exit to the home screen, open other apps, or pull down the notification shade.
- **Boot to panel.** The panel launches automatically when the tablet powers on.
- **Keep screen on.** The display stays awake. No tapping through a lock screen to adjust the volume.

The apps are thin wrappers around the existing web panel. All control logic, UI design, and device communication happens on the OpenAVC server. If the panel UI works in your browser, it works here.

## Install

### Android

1. Download the latest signed APK from [Releases](https://github.com/open-avc/openavc-panel-app/releases).
2. Install via `adb install OpenAVCPanel-<version>.apk`, or copy the file to the tablet and open it in Files (you will need to allow "Install unknown apps" for your file manager or browser the first time).
3. Android shows a warning about unknown sources. That is expected for apps not yet on the Play Store. Once we ship to Google Play, the warning will be gone for Play Store installs.

Minimum: Android 8.0 (API 26).

### iOS

Not yet released. Coming to the App Store.

### Prefer a landing page?

The marketing page at [openavc.com/panel-app](https://openavc.com/panel-app) has download buttons and install instructions in a friendlier format.

## Documentation

Everything a user needs lives on the docs site:

- [Panel App overview](https://docs.openavc.com/panel-app/) - install, pairing, when to use the app vs. a browser
- [Android kiosk setup](https://docs.openavc.com/panel-app-kiosk-android/) - soft kiosk, true kiosk via ADB, Android Enterprise QR provisioning
- [iOS kiosk setup](https://docs.openavc.com/panel-app-kiosk-ios/) - Guided Access, Autonomous Single App Mode via MDM

## Repository Layout

```
android/   # Android app (Kotlin, Gradle)
ios/       # iOS app (Swift + SwiftUI) - not yet started
docs/      # Contributor-facing dev setup notes
```

Each platform is built natively because the interesting behavior (kiosk lockdown, mDNS, boot receivers) is deeply platform-specific and the app shell itself is small.

## Requirements

- An OpenAVC server running on the same network, with its web panel accessible. See the [main OpenAVC repo](https://github.com/open-avc/openavc) for setup.
- **Android:** Android 8.0 (API 26) or newer.
- **iOS:** iOS 16 or newer.

## Development

Platform-specific setup for contributors:

- [`docs/android-setup.md`](docs/android-setup.md) - Android Studio, build, signing
- `docs/ios-setup.md` - Xcode, provisioning, TestFlight (coming with the iOS build)

## Security

The signed APK attached to each Release page includes a SHA-256 checksum in the release notes. Verify it before installing on production tablets:

```bash
sha256sum OpenAVCPanel-<version>.apk
```

The production signing keystore fingerprint is `39:38:63:04:5E:D4:E1:B5:28:7B:35:A6:F5:08:A0:78:88:1E:87:43:28:32:CC:74:DC:17:2C:EF:16:F6:05:46`.

## License

MIT. See [`LICENSE`](LICENSE).

## Contributing

Pull requests welcome. Keep changes small and focused. Open an issue first for anything non-trivial so we can discuss scope.

## Related Repositories

- [openavc](https://github.com/open-avc/openavc) - the control platform
- [openavc-drivers](https://github.com/open-avc/openavc-drivers) - community device drivers
- [openavc-plugins](https://github.com/open-avc/openavc-plugins) - community plugins
