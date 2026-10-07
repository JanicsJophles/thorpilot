# Thorpilot

[Project website](https://thorpilot.rackmind.ai) · [Documentation](https://thorpilot.rackmind.ai/docs/) · [Copilot vision](docs/copilot-vision.md)

A dual-screen emulation companion for Android handhelds, designed to live alongside Cocoon.

**Early native Android companion and browser design prototype.** The Android app discovers physical displays, offers a touch companion on a secondary display, inspects the device, provides explicit emulator screen handoffs and a manual game-care journal, and reads requests from an optional server. Its optional server-backed chat offers game suggestions with catalog matches; it does not run a local LLM or tune emulators. An optional self-hosted library gateway supports verified, resumable downloads of files already in your library to the device. The browser concept chat uses scripted responses.

## Get started on your handheld

The [installation guide](https://thorpilot.rackmind.ai/docs/install.html) provides the public Android preview download and a short setup walkthrough. No computer, root, Thorpilot account, server, or API key is required for local setup. New users get a resumable guide; existing users can open **My Thor → Set up my handheld** anytime. Choose Cocoon or another frontend, keep your existing game folders, and test one game before adding optional services.

Advanced tools remain available from My Thor and Connection. Setup is a guide with your own checkpoints; it does not silently install or configure other apps. Public preview and development builds use different signing identities; see the install guide before switching between them.

## Build the Android app

Requires Android 11 or newer. Build with JDK 17+ and Android SDK 35:

```sh
cd android
./gradlew assembleDebug lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.thorpilot/.MainActivity
```

Launch Thorpilot from Cocoon like any other installed app. The main display shows the workspace; a presentation-capable secondary display shows companion actions. Display IDs are discovered at runtime. Release the companion from either screen, or leave the app to return the second screen to other apps. Single-screen devices keep every action in the main workspace. An explicit emulator handoff releases the companion screen and keeps it yielded until you reclaim it. Returning to an emulator opens its app; Thorpilot does not guarantee that a particular game resumes or provide a simultaneous gameplay overlay.

Connection settings accept an HTTPS ROMarr server and API key. Keys are encrypted using Android Keystore and app backup is disabled. The optional custom `/api/v1/game-requests` adapter is required; redirects are rejected and no write requests are issued. Native chat uses the optional custom `/api/v1/game-chat` endpoint, with bounded local conversation/draft persistence. A launcher widget and manual [Game care journal](docs/game-care.md) are available. Scoped storage inventory, additive local transfers, and [Download to Thor](docs/device-downloads.md) are available. Automatic tuning remains future work.

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

The initial adapter targets the custom `/api/v1/game-requests` endpoint from our ROMarr integration, **not an endpoint guaranteed in upstream ROMarr**. A stock ROMarr adapter is planned. No hosted service or private homelab access is included with this project. The browser prototype only reads request status. Native device downloads use a separate, operator-configured [library gateway](companion/README.md); they do not acquire games from providers.

## Design and contribution

See [architecture](docs/architecture.md), [design direction](docs/design.md), and [roadmap](docs/roadmap.md). Contributions are welcome; discuss larger changes in an issue first. Keep tests passing and separate observed device facts from model suggestions. Never commit credentials, ROMs, BIOS files, saves or personal logs.

MIT licensed. Not affiliated with AYN, Cocoon, emulator authors or game publishers.

## Development workflow

Changes go through pull requests with required `test` and `android` checks. Trusted repository pushes run on the dedicated self-hosted runner; public fork code does not execute on it. See [CONTRIBUTING.md](CONTRIBUTING.md). No private service configuration is included.
