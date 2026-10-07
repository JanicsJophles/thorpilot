# Local library sync

Open **My Thor → Library sync**. Choose the source ROMs folder and destination ROMs folder using Android’s folder picker. For an SD-to-internal copy, the source is the card’s ROMs folder and the destination is your existing internal ROMs folder (for example Cocoon/Games/ROMs). Android remembers access to those selected folders. No broad storage permission is requested.

The directory reference is [Retro Game Corps’ ES-DE Directories v1.0](https://github.com/retrogamecorps/ES-DE-Directories/releases/tag/v1.0), based on ES-DE 3.4.0. Use its existing platform subfolders rather than creating a second directory convention. The template supplies folders; it does not supply games.

Preview hashes source files off the UI thread and reads destination metadata first. It only hashes destination files that could match a source file at the routed path and size; unrelated large games are not read. Progress reports the file and bytes checked. Platform aliases map into existing folder names: Nintendo 3DS → n3ds, Nintendo DS → nds, PSP → psp, PlayStation → psx. Nested disc files keep their relative paths. Unknown platforms or misplaced formats require review.

The preview distinguishes missing files, identical files (same path, size and SHA-256), conflicting content, and excluded files. Only **Copy missing files** writes data. It does not overwrite or remove existing files. Saves, configuration, artwork, archives and .3ds sources are excluded; n3ds accepts CIA. Existing .3ds games at the destination remain untouched. A copied CIA still requires installation through the emulator.

Copies use new temporary files, check the source against the preview hash, then read the new file back before renaming it. A destination that appears after preview is a conflict. Failed copies attempt to remove only their own new temporary file. Stop other library writers during copying: the Android provider does not offer an application-level transactional batch or a universal atomic no-replace rename. A killed app or detached destination may leave a hidden `.thorpilot-*.partial` file; a future cleanup/resume workflow is not implemented.

## Card insertion

While Thorpilot is active, Android mount/eject events invalidate a preview and ask for a fresh scan. Returning to the app checks selected folder availability. Copies do not start automatically, and no background service runs while the app is closed. A selected card is remembered by its document-tree URI, rather than its display label. Moving a folder or revoking access requires choosing it again. **Forget selected folders** clears the pairing, not any library files.

## Verification

Physical AYN Thor, Android 13, 2026-10-07: two synthetic local ROMs trees selected through the real Android picker. Preview reported one copy, one identical file and one conflict. The missing file copied with matching SHA-256; the conflicting file and a save file were unchanged. Android device checks passed, including native request parsing and navigation. JVM planner tests cover folder aliases, filename collisions, wrong formats, traversal, conflicts and exclusions.

The same physical Thor subsequently mounted a real removable card. Both card
and internal ROMs folders were selected through Android's picker. Preview
reported 17 copies and no conflicts; the native flow copied and verified all
17 files (about 9.24 GiB). An independent check found every destination file
with the expected size, no leftover partials, and unchanged SHA-256 values
for all seven pre-existing game, save, and state files.

A second native preview reported zero copies, 17 identical files, and zero
conflicts, confirming repeat sync did not offer duplicates.

Removal during transfer, crash recovery and a complete eject/reinsert cycle
remain unverified. This feature is a local copy workflow, not a ROMarr
downloader or a guarantee that every requested game is present. ROMarr's
server-library status and the handheld's file inventory are separate facts.

Android access follows the [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files).

## Play without copying

Copying is optional. **My Thor → On this device** browses a selected SD-card or
internal ROMs folder using filenames and sizes; it does not read full game
contents, verify compatibility, install games, or launch an individual file.
Folder access is remembered independently for both locations. The inventory
can reuse compatible selections already made in Library sync.

[Cocoon supports multiple ROM folders per platform](https://cocoon-shell.com/wiki/emulator-setup/).
In Cocoon, open **Settings → Library & Data → Platforms**, add each location's
platform folder, keep the existing folder, select the default player and
rescan. Games can then remain where they are. Thorpilot offers a launcher
handoff and setup guidance; it does not silently change Cocoon's configuration.

A CIA is an installation package, so selecting its location is not equivalent
to launching a playable 3DS cartridge file. Use Azahar's installation flow
before launching the installed title. Keep emulator saves and save states
separate from ROM copying; the sync never moves or replaces them.

### Physical launcher check

On the tested Thor, Cocoon retained the existing internal DS folder and
MelonDualDS player while adding the SD DS folder. A rescan of DS/GBA added
entries without removing any. Pokémon Diamond launched through Cocoon into
MelonDualDS using a verified SD-card content URI and rendered its intro; the
test exited without starting or saving a game. This establishes the tested
launcher path, not full-game compatibility. The existing SoulSilver ROM,
save and save state retained their pre-transfer hashes afterward.

If identical game files exist on both storage roots, Cocoon may list both.
The sync does not delete either copy to hide duplicates. GBA folder discovery
alone does not install or select a GBA emulator.

Cocoon remains responsible for artwork and metadata scraping. Configure its
providers there and retain existing credentials; this inventory does not
copy API keys or create a second scraping pipeline.
