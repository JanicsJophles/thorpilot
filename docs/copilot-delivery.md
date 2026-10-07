# Copilot delivery and verification

This roadmap stages useful work behind evidence gates. It supplements the [current roadmap](roadmap.md); it does not mark planned integrations as shipped.

| Stage | Deliverable | Exit evidence |
| --- | --- | --- |
| 1. Reliable companion | Stable navigation, readable chat, reconnect and interruption recovery, actionable connection states. | Physical-device repeated navigation, app pause/resume, keyboard and input tests; no duplicated requests or lost drafts. |
| 2. Cocoon entry point | Android widget and app launch integration. | Place/resize/remove widget in Cocoon; verify display selection and return path. No privileged permission required for basic use. |
| 3. Context that helps | Read-only selected-folder inventory, emulator identity/version, honest service capabilities. | Revoked grant, unplugged SD, large directory and unsupported emulator tests. Answers cite observed state instead of inferring access. |
| 4. One complete library flow | Preview, approve, transfer, verify, show next launcher/emulator step. | Interrupted network/process/storage recovery; collision and free-space checks; no archive slip or path traversal; hashes verified before ready state. |
| 5. One reliable emulator adapter | Start with a documented launch path and accessible save backup, selected by feasibility tests. | Explicit URI grants, app/version checks, emulator-close handling, backup integrity and restore conflict resolution. |
| 6. Measured tuning pilot | One emulator/version and small allowlist of reversible settings. | Repeatable baseline/change/rollback experiment; unavailable metrics clearly disclosed; no unsupported performance promises. |

## Physical-device matrix

Test both display roles, main-only use, lower-screen release, Cocoon → Thorpilot → emulator → Cocoon, screen disconnect, docking, Android back, screen lock and process recreation. A two-screen DS/3DS session must not compete with a Thorpilot presentation.

Test touch and actual hardware D-pad/confirm/back separately. Synthetic navigation events do not prove physical controller mapping. Verify focus after tab changes and asynchronous updates. Check landscape IME placement, long messages, large system text, and every primary action without obscured controls. Preserve 48dp targets while controlling visual bulk.

Keep golden screenshots for representative main/lower pages with documented emulator/runtime density. Review them at actual handheld size, not just enlarged on a monitor. Do not include private conversations or library data in public screenshots.

## Tool and state tests

- Reject unknown tool names, app packages, URL schemes, settings, and expired approvals.
- Put hostile instructions in filenames/catalog descriptions/tool responses; verify they cannot trigger actions.
- Interrupt a job before/after every durable transition; retry with the same idempotency key.
- Change destination contents between preview and approval; require a fresh conflict decision.
- Simulate unavailable backend, revoked key, slow response, malformed JSON and partially supported capabilities.
- Sanitize deeply nested logs and URLs, not only top-level fields.
- Confirm failed downloads, infrastructure outages, completed downloads and completed imports remain distinct.

## Performance experiments

For each candidate optimization, pin app/core version, game revision and test scene. Record baseline and changed run under comparable brightness, charging state, temperature and warm-up. Use repeated paired runs and report variation. If only user-observed smoothness is available, label it subjective; Android's app-level frame counters do not automatically measure a separate emulator.

Measure companion overhead while idle, in chat, and during a transfer. Establish budgets from baseline devices before announcing numeric targets. Thermal throttling, low battery, or user cancellation stops experiments. Never run a self-optimizing loop indefinitely.

## Release evidence

A PR should say exactly which capability was implemented, which devices/versions were tested, and what remains unsupported. Required CI and maintainer review precede merge. Public fork code stays off private runners until reviewed under the contribution policy. Release notes link the relevant adapter record and list behavior changes, rollback limitations, and migration steps.

Useful next slices: widget launch; read-only ROM directory picker; missing-game diagnostic; one save-backup adapter. Each should be independently useful before adding broad autonomous control.
