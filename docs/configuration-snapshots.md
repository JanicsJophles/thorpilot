# Configuration snapshots

A configuration snapshot is the recovery foundation for a graphics experiment. It preserves the selected emulator configuration before a change. It does not diagnose a texture defect, select a graphics preset or measure game performance.

## Select the right document

Azahar 2126.0 on Android resolves its configuration as `config/config.ini` beneath the user directory selected in Azahar. In Thorpilot, select that document through Android's file picker. A copy in Downloads may have the same name and valid contents while being unrelated to the running emulator. Thorpilot cannot infer the active configuration from its name or renderer settings. [Azahar's document lookup](https://github.com/azahar-emu/azahar/blob/2126.0/src/android/app/src/main/java/org/citra/citra_emu/features/settings/utils/SettingsFile.kt), [native reader](https://github.com/azahar-emu/azahar/blob/2126.0/src/android/app/src/main/jni/config.cpp).

The file picker grants access to the selected document. An earlier ROM-folder grant does not automatically grant access to an unrelated configuration file. A moved file or revoked grant requires selection again. Access to another app's private storage is not provided by this feature. [Android document access](https://developer.android.com/training/data-storage/shared/documents-files).

## Save, compare and restore

Open **A way back** in the Android app. Choose `config.ini`, give the copy a name and select **Save exact backup**. This reads the selected document without changing emulator settings. The selected file must be UTF-8 text named `config.ini`, at most 1 MiB, with exactly one `[Renderer]` section containing a recognized renderer setting. A valid file is still not proof that Azahar uses it.

Saved copies live in Thorpilot's private storage. Up to 20 copies are retained, including recovery copies; capacity never silently evicts an older backup. Remove an unneeded copy explicitly when storage is full. Clearing app data or uninstalling Thorpilot removes these local copies.

Choose the original document again if necessary, then **Compare / restore** beside a backup. The comparison shows version, byte count and SHA-256 hashes, not a setting-by-setting diff. Matching bytes require no write. A different document URI is rejected even when the name matches.

Restoration is enabled only when both the installed and recorded versions match exactly and are either `2126.0` or `2126.0-vanilla`. Other versions can be backed up but are not enabled for restore. The confirmation requires acknowledging that Azahar is fully closed and the chosen file is its active global configuration. Those are user attestations, not conditions Thorpilot can independently verify.

## Recovery contract

The implementation:

- Preserves the original bytes, including comments and line endings, with a SHA-256 digest and local metadata.
- Validates a bounded text configuration with a recognizable renderer section before accepting it. This is a format check, not proof of the active emulator document.
- Previews the current document before restoration, and refuses a stale preview if its bytes changed before the write.
- Preserves the pre-restore bytes as a recovery copy before replacing the selected document.
- Reads the document back after writing and compares the bytes with the requested snapshot before reporting a verified restore.

A document provider can fail after a partial write. Readback verification detects a mismatch; it cannot make all providers' writes atomic or guarantee recovery during a power loss. A write or readback failure is reported as unverified, and the pre-restore recovery copy is retained. There is no automatic rollback or durable pending-operation journal; after an interrupted restore, reopen the app and compare the selected document before using it. A successful result means the readback matched at that moment, not that another app cannot change it afterward. [Android output-stream semantics](https://developer.android.com/reference/android/content/ContentResolver#openOutputStream(android.net.Uri,%20java.lang.String)).

Before a write, exit the game and stop Azahar through Android's app settings. A paused game or backgrounded activity is not proof that the emulator stopped. Thorpilot does not have privileged authority to force-stop another application or guarantee that it will not reopen during a write.

## Global settings, local copies

In the inspected Azahar Android 2126.0 source, the per-game settings save branch is still unimplemented. Treat `config.ini` as global configuration: restoring it can change settings used by other games, including controls, display layout and audio. A journal entry named after a game does not turn the snapshot into an emulator-supported per-game profile. [Android settings model](https://github.com/azahar-emu/azahar/blob/2126.0/src/android/app/src/main/java/org/citra/citra_emu/features/settings/model/Settings.kt).

Configuration contents can include personal settings or connection details. Raw configuration copies belong in the app's local private storage and must not be automatically attached to chat, issues or diagnostic uploads. A configuration snapshot is not a backup of game saves, shader caches, firmware, installed games or the entire emulator user directory.

## From snapshot to “Help fix this game”

The next diagnosis workflow should combine a reproducible symptom with version-specific evidence:

1. Record the exact game and revision when known, emulator build, selected graphics backend, device and a scene that reproduces the problem.
2. Describe visual correctness separately from speed: missing textures, incorrect colors, flickering shadows, and compilation stutter have different possible causes. Attach a screenshot only through an explicit user action.
3. Check official emulator documentation and issues for the exact version. Separate a documented setting effect from an unverified hypothesis about this game.
4. Save a baseline, propose one change and explain its possible cost. Re-run the same scene with comparable warm-up and power conditions.
5. Record the visual result and responsiveness independently; restore the baseline when the change is worse or inconclusive.

For example, Azahar describes Accurate Multiplication as potentially fixing graphical bugs at a performance cost. That supports a targeted experiment when relevant, not a universal recommendation or a claim that it fixes every Pokémon texture issue. [Official setting description](https://github.com/azahar-emu/azahar/blob/2126.0/src/android/app/src/main/res/values/strings.xml).

An automated profile engine, automatic screenshots, live issue research and automatic configuration tuning remain future work. The current [Game care journal](game-care.md) provides the observation record; snapshot restoration is a separate explicit action and does not automatically apply an experiment or create a game profile. Hardware acceptance results should be recorded separately from these implementation guarantees.
