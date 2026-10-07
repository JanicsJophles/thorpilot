# Thorpilot

A dual-screen emulation companion for Android handhelds, designed to live alongside Cocoon.

**Early prototype, not a finished Android app.** The current build is a working browser design prototype with a read-only request adapter. It does not control a handheld, tune an emulator, run an LLM, or download games. The concept chat clearly uses scripted responses.

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
