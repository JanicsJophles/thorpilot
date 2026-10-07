# Architecture

The browser prototype demonstrates presentation and a read-only integration boundary. Native Android work remains ahead.

Planned flow: Cocoon widget → Android companion → authenticated agent service → optional catalog/request/sync adapters and paired device tools.

Keep provider inference separate from device execution. Prefer documented APIs and structured operations; use UI automation only where needed. Android sandbox restrictions still apply. An app cannot assume access to another emulator's private settings or saves.

The companion must discover display IDs rather than hard-code them, support single-screen fallback, preserve controller focus, and yield the lower display when DS/3DS gameplay needs it. Physical placement and lifecycle behavior require testing on a real device.

Store durable jobs on the service, with local cached views, stable identifiers and idempotent commands. Track download, library import, device transfer and emulator installation separately. Offline views must display last-seen timestamps.

Device tools should cover inventory, granted storage, verified transfer, emulator-specific config backup/export, and constrained changes. Keep secrets on the service or in Android Keystore. Record changes and support rollback. Never expose unrestricted device command execution directly to model text.

Public integrations must use per-user configuration; no assumptions about the author's private homelab. The current adapter has an explicit custom API contract and read-only scope.

## Reference material

- https://cocoon-shell.com/wiki/widgets/
- https://cocoon-shell.com/wiki/dual-screen/
- https://cocoon-shell.com/wiki/emulator-setup/
- https://developer.android.com/training/data-storage/shared/documents-files
- https://github.com/callstack/agent-device

Cocoon documentation confirms Android widgets and app display choices. A third-party Pod SDK is not established by that documentation. Cocoon integration and emulator telemetry remain proposals until tested.

## Public project and private services

Thorpilot is user-operated companion software, not a shared game-hosting or download service. The public demo contains sample data and no acquisition actions. Integrations are optional and use the operator's own authenticated backend. No shared credentials, hosted game collection, built-in download-provider list, or access to the author's private infrastructure is distributed.

Keep source code, demonstration data, and each operator's private service configuration separate. Do not advertise anonymity or legal immunity. These architectural choices do not determine whether a particular user's activity is lawful.
