# Google Play listing

What the Play Store listing for OpenAVC Panel is built from. The listing itself lives in the
Play Console; this folder is the source, so a change to the app's look or wording starts here.

| File | Play Console slot |
|---|---|
| `app-icon-512.png` | App icon, 512 x 512, 32-bit PNG with alpha. The launcher foreground on white. |
| `feature-graphic-1024x500.png` | Feature graphic, 1024 x 500, 24-bit PNG. |
| `screenshots/01-discovery.png` | Screenshot 1 in the phone, 7-inch and 10-inch tablet slots. |
| `screenshots/02-panel.png` | Screenshot 2, the demo panel with Laptop selected. |
| `screenshots/03-panel-video-call.png` | Screenshot 3, Video Call selected and mics muted. |
| `screenshots/04-admin-menu.png` | Screenshot 4, the admin menu. |
| `screenshots/05-dedicated-panel.png` | Screenshot 5, the Dedicated panel screen. |
| `demo-executive-boardroom.avc` | The OpenAVC project shown in the screenshots. Variables only, no devices, so it looks the same on any server. Its logo asset is the light wide logo from openavc.com (`logo-wide-light.png` in the site repo, white text for the panel's dark theme; the dark-text `logo-wide.png` disappears into the background), uploaded as `openavc-logo-wide.png`. |

Screenshots are 1920 x 1080, taken on a Lenovo Tab M11 with its display size set to 1080 x 1920
for the session (`adb shell wm size 1080x1920`, then `wm size reset`). Play uses 16:9 or 9:16
screenshots for its promotion placements; the tablet's native 16:10 is accepted but not used there.
The app follows the sensor for orientation, so `adb shell wm fixed-to-user-rotation enabled` is
what makes it obey `user_rotation` while capturing.

## Listing text

**App name:** OpenAVC Panel

**Short description:**

Turn a tablet into a touch panel for any OpenAVC space.

**Full description:**

OpenAVC Panel turns an Android tablet into the touch panel for a space controlled by OpenAVC, the open control platform for AV systems.

It finds the OpenAVC systems on your network and lists them. Pick one, scan the QR code the Programmer shows, or type an address. The panel the integrator built for that space opens full screen and stays connected.

What it does
- Finds systems on the network automatically
- Pairs by QR code or by address
- Runs the space's panel full screen, in landscape or portrait
- Reconnects on its own after a network drop
- Connects over HTTPS and remembers the system it trusts
- Starts at boot when set up as a dedicated panel

Locking the tablet to the panel
A dedicated panel keeps the tablet on the panel: no home screen, no other apps. Android only allows this on a tablet with no Google account signed in, and the Play Store needs one, so for that setup install the same app from the signed APK at github.com/open-avc/openavc-panel-app/releases. This Play version runs the panel full screen and is the right choice for a tablet that also does other things. The setup guide is at docs.openavc.com/panel-app-dedicated-android.

About OpenAVC
OpenAVC is free, open source software (MIT) that runs on hardware you already own: a small PC, a Raspberry Pi, a Docker host or a Windows machine. It controls projectors, displays, switchers, DSPs, cameras and lighting through a driver library the community maintains, and its Programmer builds the touch panel this app shows. The cloud platform for managing many systems has a free tier and paid plans; the panel app does not need it. More at openavc.com.

**Release notes, 0.1.0:**

First release. Finds the OpenAVC systems on your network, pairs by QR code or by address, and runs the space's panel full screen. Connects over HTTPS and remembers the system it trusts.

# App Store listing (iOS)

The App Store listing for OpenAVC Panel is built from the same folder. App Store Connect app
record: "OpenAVC Panel", Apple ID 6811610982, bundle `com.openavc.panel`, SKU `openavc-panel-ios`.
Apple requires screenshots for the 13-inch iPad (2064 x 2752 or 2752 x 2064) and, because the app
also runs on iPhone, for the 6.9-inch iPhone (1320 x 2868 or 2868 x 1320); both sets come from the
iOS simulator against the demo project above, in `screenshots-ios/`: `ipad13-*.png` (2064 x 2752, iPad Pro
13-inch simulator) and `iphone69-*.png` (1320 x 2868, iPhone 17 Pro Max simulator), the same five scenes as
the Android set. The first screen lists four systems the way the Android one does; the simulator cannot see
a real server's mDNS advert (the advertiser turns multicast loopback off on purpose), so a throwaway
responder with loopback on advertised the four names for the capture, and idb (`brew install
facebook/fb/idb-companion`, `pip install fb-idb`) delivered the taps the Simulator app will not take from
a script.

**Name:** OpenAVC Panel

**Subtitle (30):** Touch panel for OpenAVC spaces

**Promotional text (170):**

Turn an iPad into the touch panel for any OpenAVC space. Finds your systems, pairs by QR code or address, and runs the space's panel full screen.

**Description:**

OpenAVC Panel turns an iPad or iPhone into the touch panel for a space controlled by OpenAVC, the open control platform for AV systems.

It finds the OpenAVC systems on your network and lists them. Pick one, scan the QR code the Programmer shows, or type an address. The panel the integrator built for that space opens full screen and stays connected.

This app needs an OpenAVC server running on your network. Install the server on Windows, macOS, Linux, a Raspberry Pi or Docker from openavc.com, then connect this app to it.

What it does
- Finds systems on the network automatically
- Pairs by QR code or by address
- Runs the space's panel full screen, in landscape or portrait
- Reconnects on its own after a network drop
- Connects over HTTPS and remembers the system it trusts
- Keeps the screen awake while the panel is showing

Locking the iPad to the panel
A dedicated panel keeps the iPad on the panel: no Home Screen, no other apps. On iPadOS that is Guided Access (Apple's kiosk mode), started by pressing the power button three times, and the app's Panel settings walk through it. An iPad managed by an MDM can let the app lock itself every time the panel opens, restarts included. The setup guide is at docs.openavc.com/panel-app-dedicated-ios.

About OpenAVC
OpenAVC is free, open source software (MIT) that runs on hardware you already own: a small PC, a Raspberry Pi, a Docker host, a Mac or a Windows machine. It controls projectors, displays, switchers, DSPs, cameras and lighting through a driver library the community maintains, and its Programmer builds the touch panel this app shows. The cloud platform for managing many systems has a free tier and paid plans; the panel app does not need it. More at openavc.com.

**Keywords (100):** AV,control,touch panel,conference room,classroom,projector,display,kiosk,Crestron,Extron,AMX

**Support URL:** https://docs.openavc.com/panel-app/

**Marketing URL:** https://openavc.com/panel-app

**Privacy policy URL:** https://openavc.com/privacy

**Copyright:** 2026 OpenAVC LLC

**Category:** Utilities (primary), Business (secondary)

**Age rating:** none of the listed content; 4+.

**App privacy:** the app collects no data. It talks only to the OpenAVC server the user pairs it with, on the user's own network.

**Review notes:**

OpenAVC Panel is the touch panel for an OpenAVC control system, which runs on the user's own network. The app has no account, no sign-in and no cloud service of its own: it discovers an OpenAVC server on the local network, or takes its address, and shows the panel that server serves.

To try it without an OpenAVC server, tap the + button on the first screen, choose Enter address, and type the address of any OpenAVC server reachable from the review device. If none is reachable, the first screen, the address form and the QR scanner are the parts of the app that run standalone; the panel itself needs the server. Screenshots 2 and 3 show the panel against a server running the demo project.

Local network access and the camera are requested only for that discovery and for scanning the pairing QR code. The app collects no data.

**Release notes, 0.1.0:**

First release. Finds the OpenAVC systems on your network, pairs by QR code or by address, and runs the space's panel full screen. Connects over HTTPS and remembers the system it trusts.
