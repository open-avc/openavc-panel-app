# iOS Development Setup

How to build and run the OpenAVC Panel iOS app from source.

## Prerequisites

- A Mac with Xcode 26 or newer, selected as the active developer directory (`sudo xcode-select -s /Applications/Xcode.app`).
- An Apple ID signed in under Xcode > Settings > Accounts. A free account is enough to run the app on your own iPad; App Store distribution uses the OpenAVC LLC team.
- An iPad or iPhone on iOS 16 or newer, or one of the iPad simulators that ship with Xcode.

## Building

Open `ios/OpenAVCPanel.xcodeproj` in Xcode and run the shared `OpenAVCPanel` scheme, or from the command line:

```bash
cd ios
xcodebuild build -project OpenAVCPanel.xcodeproj -scheme OpenAVCPanel \
  -destination 'platform=iOS Simulator,name=iPad Air 11-inch (M4)'
xcodebuild test  -project OpenAVCPanel.xcodeproj -scheme OpenAVCPanel \
  -destination 'platform=iOS Simulator,name=iPad Air 11-inch (M4)'
```

The tests mirror the Android unit tests case for case, so the two apps keep agreeing on what a server is and how a pairing code is read.

## Running on a device

1. Connect the iPad over USB and trust the Mac when the iPad asks.
2. On the iPad, turn on Settings > Privacy & Security > Developer Mode (the row appears once Xcode has talked to the iPad once) and let it restart.
3. In Xcode, set your team under the target's Signing & Capabilities, pick the iPad as the run destination, and run. Automatic signing registers the device and creates a development certificate on the way.

From the command line, build for the device with `-destination 'id=<device udid>' -allowProvisioningUpdates DEVELOPMENT_TEAM=<team id>`, then install and launch with `xcrun devicectl device install app` and `xcrun devicectl device process launch`.

## Trying it against a server

The app needs an OpenAVC server on the same network as the iPad. Start one on the Mac bound to all interfaces with mDNS advertising on, and the iPad lists it on the first screen:

```bash
OPENAVC_BIND=0.0.0.0 OPENAVC_MDNS_ADVERTISE=true python -m openavc.main
```

The iOS simulator never sees that advertisement: the server deliberately does not loop its multicast back to the machine it runs on. In the simulator, use the + button and enter the Mac's address instead.

## Project layout

```
ios/
├── OpenAVCPanel.xcodeproj         # Xcode project with a shared scheme
├── OpenAVCPanel/
│   ├── App/                       # App entry and the router between discovery and the panel
│   ├── Discovery/                 # Bonjour browse, QR scan, manual entry, server validation, certificate pinning
│   ├── Panel/                     # The WKWebView that hosts the panel, and the admin menu
│   ├── Kiosk/                     # Dedicated-panel settings, Guided Access helper, admin PIN
│   ├── Storage/                   # The remembered server
│   ├── Assets.xcassets/           # App icon, accent color, launch background
│   └── Info.plist                 # Permissions, orientations, App Transport Security exceptions
└── OpenAVCPanelTests/             # Unit tests
```

## Notes

- The app keeps its minimum at iOS 16 so an older iPad can be repurposed as a panel; a newer API goes behind an availability check.
- The panel itself is the server's web UI. The app's own screens follow the device's light or dark setting; the panel takes its colors from the project's theme.
