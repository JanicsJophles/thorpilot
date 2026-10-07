# Local library sync

Open **My Thor → Library sync**. Choose the source ROMs folder and destination ROMs folder using Android’s folder picker. For an SD-to-internal copy, the source is the card’s ROMs folder and the destination is your existing internal ROMs folder (for example Cocoon/Games/ROMs). Android remembers access to those selected folders. No broad storage permission is requested.

Preview scans and hashes both selected libraries off the UI thread. Platform aliases map into existing folder names: Nintendo 3DS → n3ds, Nintendo DS → nds, PSP → psp, PlayStation → psx. Nested disc files keep their relative paths. Unknown platforms or misplaced formats require review.

The preview distinguishes missing files, identical files (same path, size and SHA-256), conflicting content, and excluded files. Only **Copy missing files** writes data. It does not overwrite or remove existing files. Saves, configuration, artwork, archives and .3ds sources are excluded; n3ds accepts CIA. Existing .3ds games at the destination remain untouched. A copied CIA still requires installation through the emulator.

Copies use new temporary files, check the source against the preview hash, then read the new file back before renaming it. A destination that appears after preview is a conflict. Failed copies attempt to remove only their own new temporary file. Stop other library writers during copying: the Android provider does not offer an application-level transactional batch or a universal atomic no-replace rename. A killed app or detached destination may leave a hidden `.thorpilot-*.partial` file; a future cleanup/resume workflow is not implemented.

## Card insertion

While Thorpilot is active, Android mount/eject events invalidate a preview and ask for a fresh scan. Returning to the app checks selected folder availability. Copies do not start automatically, and no background service runs while the app is closed. A selected card is remembered by its document-tree URI, rather than its display label. Moving a folder or revoking access requires choosing it again. **Forget selected folders** clears the pairing, not any library files.

## Verification

Physical AYN Thor, Android 13, 2026-10-07: two synthetic local ROMs trees selected through the real Android picker. Preview reported one copy, one identical file and one conflict. The missing file copied with matching SHA-256; the conflicting file and a save file were unchanged. Android device checks passed, including native request parsing and navigation. JVM planner tests cover folder aliases, filename collisions, wrong formats, traversal, conflicts and exclusions.

A real removable-card insertion/ejection cycle and a large transfer have not yet been verified. This feature is a local copy workflow, not a ROMarr downloader or a guarantee that every requested game is present. ROMarr’s server-library status and the handheld’s file inventory are separate facts.

Android access follows the [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files).
