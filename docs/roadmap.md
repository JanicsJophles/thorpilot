# Roadmap

## Present

- [x] Original responsive dual-panel design preview
- [x] Optional read-only custom ROMarr request adapter
- [x] Tab-local conversation/draft persistence
- [x] Tests and CI
- [x] Native Kotlin app with physical display discovery and lifecycle handling
- [x] Encrypted on-device connection settings
- [x] AYN Thor USB installation, touch routing, and device tests
- [x] Local manual Game care journal: baseline, one-setting trial and observed result
- [x] Explicit allowlisted emulator handoff, persisted screen yield and user-triggered reclaim

## Next: real handheld foundation

- [ ] Inventory Cocoon version, device variant, firmware and emulator packages
- [ ] Refine native UI and introduce Compose where useful
- [x] Android widget and Cocoon hosted-tap verification (resize/controller checks remain)
- [ ] Explicit pairing and scoped device inventory
- [ ] Stock ROMarr adapter and documented capability negotiation
- [x] Optional server-backed game discovery chat with catalog matches
- [ ] General device assistant grounded in scoped device inventory

## Then: complete useful workflows

- [ ] Persisted request history and idempotent actions across reconnects
- [ ] Verified resumable device transfers and storage grants
- [ ] Save backup/restore adapters and conflict handling
- [ ] Multidisc and region/revision-aware library organization
- [ ] Version-specific emulator settings and reversible changes
- [ ] Measured optimization experiments on real hardware; the manual journal does not auto-apply or benchmark

No milestone implies untested device compatibility or guaranteed game performance.

See [Game care](game-care.md) for the currently available manual workflow and proposed version-aware configuration adapter.
