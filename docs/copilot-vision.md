# Thorpilot: a companion that understands your handheld

Thorpilot should make the time between “I want to play” and actually playing shorter. Cocoon remains the home screen. Emulators remain responsible for emulation. Thorpilot brings together conversation, device context, library state, and a small set of dependable actions.

This is a product direction, not a list of shipped features. The native prototype currently has dual-display presentation, device/app launch controls, optional server-backed discovery chat, saved conversation state, connection storage, and read-only requests. General device reasoning, a Cocoon widget, storage tools, backup, and tuning are proposed.

Start with the [integration evidence](integration-feasibility.md), [architecture decisions](copilot-adrs.md), and [delivery/test plan](copilot-delivery.md). Research was checked on 2026-10-07; moving upstream branches require revalidation before implementation.

## The experience

**“Find something I can play for twenty minutes.”** Ask a useful follow-up, then show a few catalog-backed suggestions with platform, library availability, and why they fit. Let the user open a game already present. Requests to their own service are a separate, explicit action. A suggested title is neither proof of compatibility nor proof of availability.

**“Why isn't this showing up?”** Inspect only the granted library folder, compare extension and platform mapping, and identify the next specific step. Distinguish a server download, import, device transfer, emulator installation, and launcher rescan. Offer a guided Cocoon rescan when no supported automation exists.

**“Get my handheld ready for a trip.”** Preview a shortlist, total transfer size, free-space margin, and save-backup destinations. Transfer only approved items; resume interruptions and verify hashes. Show what is still missing before the user leaves Wi-Fi. This workflow is proposed, not implemented.

**“Keep my saves safe.”** Explain which accessible directory belongs to each supported emulator. Snapshot saves while the emulator is closed, verify the backup, and retain versions. Restoring presents the exact affected game and previous snapshot; simultaneous changes become a conflict, never a silent overwrite.

**“This game feels slow.”** Begin with the emulator/version, game identity, current settings, and a reproducible scene. Explain uncertainty. If an adapter can safely change one setting, preview the trade-off, save the original, compare measurements, and offer undo. Without reliable measurements or access, provide version-specific guidance instead of claiming optimization.

**“What happened to my request?”** Translate actual backend events into a useful explanation: waiting for peers, provider missing data, network unavailable, import pending, or ready to transfer. Do not blame a game source when the download client's network is unavailable.

## Two screens, one activity

The larger screen holds the object being discussed: game art, a comparison, transfer plan, or change preview. The lower screen holds conversation and a few contextual controls. The user can work entirely on one screen. Touch, controller, keyboard, and accessibility focus are peers.

When launching an emulator that needs both screens, Thorpilot yields its presentation. Returning should restore context without resurrecting a dismissed panel or replaying an action. A widget or notification can provide a lightweight route back; it must not claim ownership of Cocoon's second display.

## Useful initiative

A copilot may prepare a plan, notice an explicitly granted folder changed, or suggest a verified next step. It should not continuously watch every app or silently rewrite settings. Users choose persistent rules such as “back up this selected folder while charging”; those rules remain visible and revocable.

Every proposed action should answer: what will change, why, what evidence supports it, and how to undo it. Library text, filenames, metadata, and emulator logs are untrusted content, not instructions to the agent.

## Visual direction

Keep the original luminous icons, dark glass controls, and AMOLED depth. Artwork carries personality; utility controls stay quiet. Add space between tasks, not oversized controls everywhere. Keep at least 48dp interactive targets while allowing smaller painted surfaces. Avoid permanent bright static decoration during long idle periods; honor screen timeout and reduced motion.

The app should feel at home beside Cocoon without claiming affiliation or reusing unlicensed artwork, application code, or assets.
