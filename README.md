# OpenAVC Panel App

Native Android and iOS apps that turn a commodity tablet into a dedicated AV touch panel for an [OpenAVC](https://github.com/open-avc/openavc) system.

## What These Apps Do

OpenAVC's web panel is already touch-optimized and runs great in any mobile browser. These apps exist for the things a browser can't do on a wall-mounted tablet:

- **Auto-discovery.** Find the OpenAVC server on the network automatically via mDNS. No typing IP addresses.
- **QR pairing.** Scan the QR code shown in the Programmer IDE to connect instantly.
- **Kiosk mode.** Lock the tablet to the panel. Users can't exit to the home screen, open other apps, or pull down the notification shade.
- **Boot to panel.** The panel launches automatically when the tablet powers on.
- **Keep screen on.** The display stays awake. No tapping through a lock screen to adjust the volume.

The apps are thin wrappers around the existing web panel. All control logic, UI design, and device communication happens on the OpenAVC server. If the panel UI works in your browser, it works here.

## Repository Layout

```
android/   # Android app (Kotlin)
ios/       # iOS app (Swift + SwiftUI)
docs/      # Setup and kiosk provisioning guides
```

Each platform is built natively because the interesting behavior (kiosk lockdown, mDNS, boot receivers) is deeply platform-specific and the app shell itself is small.

## Installation

**Android:** Install the signed APK from [Releases](https://github.com/open-avc/openavc-panel-app/releases). Kiosk-mode provisioning via ADB is documented in [`docs/android-kiosk-guide.md`](docs/android-kiosk-guide.md).

**iOS:** App Store listing coming soon. iOS kiosk mode uses Guided Access (free, manual per-session) or MDM-managed Autonomous Single App Mode. See [`docs/ios-kiosk-guide.md`](docs/ios-kiosk-guide.md).

## Requirements

- An OpenAVC server running on the same network, with its web panel accessible. See the [main OpenAVC repo](https://github.com/open-avc/openavc) for setup.
- **Android:** Android 8.0 (API 26) or newer.
- **iOS:** iOS 16 or newer.

## Development

Platform-specific setup:

- [`docs/android-setup.md`](docs/android-setup.md) — Android Studio, build, signing
- [`docs/ios-setup.md`](docs/ios-setup.md) — Xcode, provisioning, TestFlight

## License

MIT. See [`LICENSE`](LICENSE).

## Contributing

Pull requests welcome. Keep changes small and focused. Open an issue first for anything non-trivial so we can discuss scope.

## Related Repositories

- [openavc](https://github.com/open-avc/openavc) — the control platform
- [openavc-drivers](https://github.com/open-avc/openavc-drivers) — community device drivers
- [openavc-plugins](https://github.com/open-avc/openavc-plugins) — community plugins
