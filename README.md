# OpenAVC Panel App

Native Android and iOS apps that turn a commodity tablet into a dedicated AV touch panel for an [OpenAVC](https://github.com/open-avc/openavc) system.

<p align="center">
  <a href="https://raw.githubusercontent.com/open-avc/openavc-panel-app/main/docs/images/panel-tablet-conference-room.jpg">
    <img src="https://raw.githubusercontent.com/open-avc/openavc-panel-app/main/docs/images/panel-tablet-conference-room.jpg" alt="OpenAVC touch panel running on an Android tablet, controlling a conference room" width="70%">
  </a>
</p>

<p align="center"><sub><i>The OpenAVC touch panel rendered by the Android app on a wall-mounted tablet. Click to enlarge.</i></sub></p>

Get it on [Google Play](https://play.google.com/store/apps/details?id=com.openavc.panel) or the [App Store](https://apps.apple.com/app/id6811610982). For a locked-down Android panel, use the signed APK from [Releases](https://github.com/open-avc/openavc-panel-app/releases) (see [Install](#android)).

## What These Apps Do

OpenAVC's web panel is already touch-optimized and runs great in any mobile browser. These apps exist for the things a browser cannot do on a wall-mounted tablet:

- **Auto-discovery.** Find the OpenAVC server on the network automatically via mDNS. No typing IP addresses.
- **QR pairing.** Scan the QR code shown in the Programmer IDE to connect. A new tablet then waits until it is approved in the Programmer, once; approved tablets stay approved.
- **Dedicated panel mode.** Lock the tablet to the panel. Users cannot exit to the home screen, open other apps, or pull down the notification shade. (Android's developer docs call this "Lock Task Mode" or "kiosk mode"; we use the AV term.)
- **Boot to panel.** The panel launches automatically when the tablet powers on.
- **Keep screen on.** The display stays awake. No tapping through a lock screen to adjust the volume.

The apps are thin wrappers around the existing web panel. All control logic, UI design, and device communication happens on the OpenAVC server. If the panel UI works in your browser, it works here, and a tablet is approved the same way a browser is.

## Install

### Android

Install from [Google Play](https://play.google.com/store/apps/details?id=com.openavc.panel).

To lock a tablet so it only runs the panel, install the signed APK instead. Android will not lock a tablet to one app while a Google account is signed in, and Google Play needs one. The [Android dedicated panel guide](https://docs.openavc.com/panel-app-dedicated-android/) covers both routes.

1. Download the latest signed APK from [Releases](https://github.com/open-avc/openavc-panel-app/releases).
2. Install via `adb install OpenAVCPanel-<version>.apk`, or copy the file to the tablet and open it in Files (you will need to allow "Install unknown apps" for your file manager or browser the first time).
3. Android shows a warning about unknown sources. That is expected for an app installed outside Google Play.

Minimum: Android 8.0 (API 26).

### iOS

Install from the [App Store](https://apps.apple.com/app/id6811610982). Runs on iPad and iPhone, iOS 16 or newer.

### Prefer a landing page?

The marketing page at [openavc.com/panel-app](https://openavc.com/panel-app) has download buttons and install instructions in a friendlier format.

## Documentation

Everything a user needs lives on the docs site:

- [Panel App overview](https://docs.openavc.com/panel-app/) - install, pairing, when to use the app vs. a browser
- [Android dedicated panel setup](https://docs.openavc.com/panel-app-dedicated-android/) - basic and full dedicated-panel modes, ADB and Android Enterprise QR provisioning
- [iOS dedicated panel setup](https://docs.openavc.com/panel-app-dedicated-ios/) - Guided Access, Autonomous Single App Mode via MDM

## Repository Layout

```
android/   # Android app (Kotlin, Gradle)
ios/       # iOS app (Swift + SwiftUI, Xcode project)
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
- [`docs/ios-setup.md`](docs/ios-setup.md) - Xcode, build, running on your own iPad

## Security

A panel is approved once. With the OpenAVC system's Panel access set to Approved panels only (the default), a new tablet shows a code and waits until it is approved in the Programmer, or with the admin password typed on the tablet. Approved tablets stay approved until revoked. The app has nothing to configure for this; the system's own screen and OpenAVC Cloud's Remote Panel need no approval.

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
