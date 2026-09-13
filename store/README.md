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
| `demo-executive-boardroom.avc` | The OpenAVC project shown in the screenshots. Variables only, no devices, so it looks the same on any server. Its logo asset is `openavc-logo-wide` from openavc.com, uploaded as `openavc-logo-wide.png`. |

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
