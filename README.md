# Thorpilot

A dual-screen emulation companion for Android handhelds, designed to live alongside Cocoon.

**Early native Android companion and browser design prototype.** The Android app discovers physical displays, offers a touch companion on a secondary display, inspects the device, launches installed emulators, and reads requests from an optional server. It does not yet run an LLM, tune emulators, or download games. The browser concept chat uses scripted responses.

## Android app

Requires Android 11 or newer. Build with JDK 17+ and Android SDK 35:

```sh
cd android
./gradlew assembleDebug lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.thorpilot/.MainActivity
```

Launch Thorpilot from Cocoon like any other installed app. The main display shows the workspace; a presentation-capable secondary display shows companion actions. Display IDs are discovered at runtime. Release the companion from either screen, or leave the app to return the second screen to other apps. Single-screen devices keep every action in the main workspace.

Connection settings accept an HTTPS ROMarr server and API key. Keys are encrypted using Android Keystore and app backup is disabled. The optional custom `/api/v1/game-requests` adapter is required; redirects are rejected and no write requests are issued. Native AI chat, a launcher widget, scoped SD-card access, and tuning are still on the roadmap.

See [device testing](docs/device-testing.md) for the ADB development loop.

## Try it

Requires Node.js 22 or newer. No package dependencies or account required.

```sh
npm start
# Open http://localhost:8791
npm test
```

The default preview uses clearly labeled example data. Try the two display views, request list, conversation, refresh persistence, and day/night themes. The two panels preview the layout; they do not yet bind to separate physical Android displays.

## Where this is going

- A Cocoon-launchable Android companion and widget, with a lower-screen conversation and a useful top-screen workspace.
- Game discovery that understands your library, device and installed emulators.
- Durable requests and verified transfers: accepted, downloading, imported and on-device are different states.
- Save backups, library organization and game-specific setup assistance.
- Measured optimization: baseline, back up, change one setting, compare, keep or roll back.
- Optional self-hosted integrations and interchangeable model providers.

Cocoon remains the launcher. Thorpilot is an independent companion. Neither Cocoon source nor proprietary assets are included.

## Optional ROMarr connection

Set `ROMARR_URL` and `ROMARR_API_KEY` in the **server environment** before starting. See `.env.example`; files are not auto-loaded. Keys never go to the browser. The server binds to localhost by default. Do not expose it publicly without adding authentication.

The initial adapter targets the custom `/api/v1/game-requests` endpoint from our ROMarr integration, **not an endpoint guaranteed in upstream ROMarr**. A stock ROMarr adapter is planned. No hosted service or private homelab access is included with this project. The prototype only reads status; no download actions are exposed.

## Design and contribution

See [architecture](docs/architecture.md), [design direction](docs/design.md), and [roadmap](docs/roadmap.md). Contributions are welcome; discuss larger changes in an issue first. Keep tests passing and separate observed device facts from model suggestions. Never commit credentials, ROMs, BIOS files, saves or personal logs.

MIT licensed. Not affiliated with AYN, Cocoon, emulator authors or game publishers.
