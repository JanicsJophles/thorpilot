# Testing on a handheld

Enable Developer options and USB debugging, connect a data cable, and approve the workstation on the handheld. ADB is the development transport; the app does not require ADB, root, or accessibility privileges to run.

```sh
adb devices -l
adb shell dumpsys display
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.thorpilot/.MainActivity
```

Use `adb shell dumpsys display` to discover logical display IDs. Never assume the second display is 1. `adb shell input -d DISPLAY_ID tap X Y` routes a test tap to a particular display. Use `adb shell dumpsys SurfaceFlinger --display-id` for the physical IDs accepted by `adb exec-out screencap -p -d PHYSICAL_ID`.

Check the following on real hardware:

- Both screens render, with readable text and reachable buttons.
- Companion actions change the main workspace.
- Releasing the companion leaves the main workspace usable.
- Leaving Thorpilot dismisses its presentation; returning restores it only if enabled.
- A force-stop/relaunch preserves the screen preference and saved connection.
- An invalid server URL is rejected, and authentication/network errors stay visible.
- Requests remain read-only. No imported/requested state is inferred from a button click.

Hardware screenshots, logs, serial numbers, server addresses, and credentials belong in ignored local artifacts, not commits. Debug APKs are for development; distribution signing remains separate.

## Instrumented checks

```sh
cd android
./gradlew assembleDebug assembleDebugAndroidTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w dev.thorpilot.test/dev.thorpilot.DeviceChecks
```

The small instrumentation runner prints PASS or FAIL. It uses a separate test preference file and checks HTTPS URL validation, actual Android Keystore encryption/round-trip, clearing the connection, device inventory, and native navigation. It does not alter the user's saved connection. CI builds the test APK; running it requires a connected device or emulator.

First hardware pass: AYN Thor on Android 13, two internal displays. Both rendered; secondary-display touches changed the main workspace. Releasing the presentation persisted across restart. Going Home removed the presentation and returning restored it when enabled. Keystore and navigation checks passed. Live request loading and server-backed chat with catalog suggestions were verified on hardware. Physical controller input, rotation, other Android versions, and emulator launch/resume compatibility still need dedicated testing.

## Repeatable workstation loop

`python3 tools/device.py inspect` discovers the device and physical screens. After building both APKs, `python3 tools/device.py test` installs and runs checks, and fails unless the runner reports PASS. `install` launches the app; `capture` saves every physical screen into ignored `artifacts/device/`. Use `--serial` only when multiple devices are connected. No fixed screen IDs are baked into the helper.

The native test suite also checks chat parsing/history, bounded request transport, redirects, malformed responses, credential isolation, and repeated tab navigation retaining the same shell.

## Acceptance loop for each meaningful change

Build and lint first, then run instrumentation on the identified Thor using an explicit serial when more than one device is attached. Run the affected journey with real UI interaction; inspect both displays, not just the test runner output.

For a release candidate, walk through:

1. Enter from Cocoon's normal Thorpilot app cell (or the optional widget), visit each tab, and return to Cocoon.
2. Open a saved conversation, navigate away/back, and restart. Confirm history and retry behavior without submitting accidental duplicate messages.
3. Refresh requests against a configured test service. Check loading, empty, unavailable, and failed states; distinguish download metadata from game data.
4. Release and reclaim the second screen, then leave and resume the app. Confirm it does not cover an emulator after yielding.
5. Navigate by touch and an actual controller. Injected ADB D-pad events are useful regression evidence but do not verify physical gamepad mapping.
6. Exercise changed-connection and forget-connection paths using synthetic servers and isolated test storage, never by moving real credentials between services.
7. Inspect screenshots for clipped text, scrunched spacing, oversized controls, keyboard obstruction, and tab flicker.

Record the build, device/OS version, exact journeys, observed results, and untested areas locally. A passing build, instrumentation suite, or single walkthrough is not a production-readiness claim. Rotation, long sessions, offline recovery, controller behavior, and emulator handoffs need their own coverage before being described as seamless.

## Game-care and emulator handoff evidence

A physical AYN Thor check on 2026-10-07 used Azahar **2126.0-vanilla** and an existing Pokémon Omega Ruby save in Littleroot. Character movement was exercised. Azahar's own performance overlay was observed at approximately **30 FPS** during sampled moments. This was not a timed benchmark, a frame-time distribution, or a before/after optimization comparison. No reproducible graphical defect was established in that observation, and no setting improvement is claimed.

Azahar's secondary-display Presentation covered the lower screen when Thorpilot was launched there during the game. Simultaneous Thorpilot-over-game display use is therefore **not supported by this observed path**. Use the explicit handoff: Thorpilot dismisses its companion and preserves the yielded state; return through the app action when appropriate, then reclaim the companion explicitly. The emulator controls whether its game resumes. Do not describe launching another activity as a verified seamless dual-screen overlay.

The manual [Game care journal](game-care.md) records a baseline, proposed one-setting trial and result. Its notes are not measured telemetry, a verified configuration backup or automatic rollback. A future acceptance pass must reproduce a specific visual symptom, record exact original settings, compare the same scene, restore the original and verify recovery. Check force-stop/relaunch and failed-launch behavior independently from a successful app launch.

The same check returned from the game to Thorpilot and used **Return to Azahar** to resume the existing scene. The temporary controller and performance overlays were restored to their original disabled states. A baseline-only note was entered through the real Android form and persisted; isolated device instrumentation also covers step retention, save/reopen/edit, bounded journal storage and session-state rollback. Cross-display activity moves showed input-focus trouble before a fresh launch, so arbitrary live display relocation remains unverified.

## Configuration snapshot acceptance

On AYN Thor (Android 13), the Android document picker granted a single selected Azahar `config.ini`. The app saved an exact 18,133-byte private backup and the comparison showed matching SHA-256 values; the live emulator file was not overwritten. A separate marked 99-byte fixture under `Documents/ThorpilotTest/config.ini` received its own picker grant. Optional `ConfigDocumentChecks` wrote a longer fixture through the real external-storage provider, restored the shorter original, and verified exact bytes and SHA-256. This verifies truncating provider writes on this device, not every provider or a power-loss recovery guarantee.

The isolated suite also exercises stale previews, changes during recovery capture, partial writes, false-success readback, corrupt backups, bounded storage and cross-instance concurrency. To repeat the real-provider test, create only the documented marked fixture, select it in the snapshot picker, and supply `-e configFixtureUri` to instrumentation. The test refuses any other document ID or unmarked content and restores the original fixture in cleanup. Never substitute a live emulator file for this test.

## Z-A graphics trial

On the same Thor, Pokémon Legends: Z-A showed flashing building textures in the opening Lumiose station scene with Eden v0.2.1 (build `1f6734c`), Turnip `26.3.0-T30-1.4.359`, 1× resolution and Fast GPU mode. The device owner observed the flicker even while standing still. Changing only the game's GPU mode to **Balanced** saved a per-game override (`gpu_accuracy=1`, global inheritance disabled). After resuming the scene, the owner reported that the flashing stopped and the scene looked much better.

This is a user-confirmed result for that scene, not a full-game compatibility claim or a performance benchmark. Battles, other areas and long sessions remain untested. The change was made manually in Eden; Thorpilot does not yet apply or restore Eden profiles. The in-app configuration snapshot adapter currently supports Azahar only.

## Eden read-only inspector acceptance

On 2026-10-07, a physical Thor selected two synthetic INIs through Android's real document picker. The per-game file explicitly selected Balanced GPU mode and inherited resolution. Before selecting global configuration, resolution showed Unknown. The global fixture deliberately paired `resolution_setup=12` with `resolution_setup\\default=true`; the inspector correctly displayed 1× from the reviewed compiled default rather than the stale raw number. GPU mode remained Balanced from the per-game override. Clear removed both selections. These were fixtures under `Documents/ThorpilotTest/EdenInspection`, not live emulator configuration or a game-performance test.

Local JVM tests cover default precedence, global inheritance, missing global context, exact-byte hashes, supported-build boundaries, malformed/oversized documents, duplicate keys and invalid values. The inspector does not write files.

## Distinguishing firmware OLED protection from app flicker

The tested Thor firmware has **OLED Screen Protection** controls for pixel
shifting and pixel refreshing. Its observed defaults were a one-pixel shift
after 3 seconds of static content and a visible refresher after 30 seconds.
Android window logs identify the latter as `Burn-in Protection Refresher` on
both displays. These overlays are not Thorpilot rendering or a panel-fault diagnosis.

On 2026-10-07, with the device owner's approval, the firmware UI saved a
60-second shift threshold and a 300-second refresh threshold, retaining both
enabled controls and the one-pixel radius. Reopening the settings confirmed
both values. These are static-screen thresholds, not guaranteed recurring
intervals or universal recommendations for every firmware. Use the firmware
UI to apply changes live; writing a persisted setting alone may not update
WindowManager. Thorpilot does not automatically change OLED protection.


## Native setup design pass — 2026-10-07

The development build uses shared persisted setup state with separate top-screen guidance and lower-screen controls. On the Android 13 Thor, verified: entering setup with the companion yielded uses the combined view; explicit reclaim enables separate screens; Next on the lower screen updates the upper explanation; Release this screen restores the combined controls without resetting the step. First-step actions and navigation fit the lower display at default settings after removing the redundant companion header. The wide single-screen fallback places guidance beside controls; smaller windows retain a scrolling vertical flow.

`SetupChecks` exercises isolated preferences, checkpoint migration, saved current step, failed/successful/untested first-launch outcomes, frontend-change invalidation, retained folder confirmation, shared screen state, callback routing and stable action IDs. These checks do not mark the user's real setup complete or launch a game. A passed checkpoint is user confirmation, not automatic emulator verification.

Build validation: debug unit tests, both APK assemblies, Android lint, and the complete device instrumentation runner. Quick Settings hardware coverage and remaining limitations are recorded in [copilot summon](copilot-summon.md). This development UI is newer than the published `v0.1.0-preview.1` APK.


## Compact copilot — 2026-10-07

Tested the compact dialog through the actual Quick Settings tile over Cocoon on the Android 13 Thor. No keyboard appeared at entry. Typed a game-discovery draft, hid the keyboard, dismissed, reopened the tile, and verified the exact draft returned. Sent that draft through the configured server and received catalog-backed game cards plus the existing warning for unverified suggestions. Expanded and scrolled the reply with Workspace/Dismiss remaining available. This tested discovery only; no download was started.

The compact activity has separate connection-bound chat storage and does not create a secondary Presentation or modify GameSession. Manifest contract checks verify the private activity, task affinity and Recents exclusion; the full device runner, unit tests, APK assemblies and lint pass. Emulator rendering/audio continuity, physical gamepad focus, Android 14+ tile launching and narrow-phone layout remain unverified. The public preview APK has not been replaced.


## Glass surfaces and compact Game care — 2026-10-07

On the Android 13 Thor, opened the updated compact copilot over Cocoon and selected Game care from the mode picker. Verified the expanded journal and glass treatment on the device. Compact layout removes duplicate headings and hides optional build/driver fields behind a disclosure.

Added isolated instrumentation coverage for selecting an Eden draft, disclosure controls, bounded draft roundtrip across store instances, malformed/oversized data, independent namespaces, and preservation of existing journal entries. The complete device runner and local unit/build/lint checks pass. No live game was tuned in this pass; no game files or emulator settings were modified.

## Discovery artwork — 2026-10-07

Verified Animal Crossing: Wild World artwork in a restored compact discovery conversation on the physical Android 13 Thor, opened through Quick Settings over Cocoon. Cards retain the glass treatment and scroll below the fixed copilot header. No new discovery request or game download was required.

The complete device runner passes, including image URL allowlist, invalid/oversized payload and downsampling checks. Debug unit tests, both APK assemblies and Android lint also pass. This covers IGDB recommendation artwork only; other providers and direct Cocoon artwork integration remain unimplemented. The installed development build is newer than the public preview APK.

## Cocoon artwork diagnosis and library preparation — 2026-10-07

On the Thor, missing 3DS artwork appeared as `0` placeholders. The affected display names began with a 16-character title ID and retained `.legit`/`.piratelegit` packaging labels. Editing only Cocoon's title, saving, and scraping that individual entry restored covers for Pokémon Super Mystery Dungeon, Kirby: Planet Robobot, Animal Crossing: New Leaf, Tomodachi Life, Luigi's Mansion: Dark Moon, The Legend of Zelda: Ocarina of Time 3D, New Super Mario Bros. 2 and both Super Mario 3D Land entries. Original ROM paths and saves were not changed. These are manual repair observations, not an automatic Cocoon integration.

New-transfer filename preparation is documented in [Download to Thor](device-downloads.md#library-preparation). Tests cover exact suffix cleanup, preserving region/version and content identity, refusing unsafe source names, retaining legacy saved destinations, and normal no-overwrite conflicts. Frontend scraping can still make incorrect matches; visually check the selected game rather than treating any image as success.

Debug unit tests, both APK assemblies, Android lint and the complete physical-device runner pass. The updated development APK is installed in place. Cocoon’s temporary **Replace protected fields** setting was returned to off after the repairs.

## Shared game identity and review — 2026-10-07

The physical Android 13 Thor runs the updated development APK. Isolated UI fixtures exercise the real metadata review dialog: opening an uncertain match, rejecting an empty title, cancelling without writing, selecting a candidate, persisting the choice, manually correcting the title, clearing stale artwork/provider IDs, and resetting to server metadata. Tests use disposable preferences and dummy connection credentials; no real ROM is downloaded, renamed or replaced by these checks.

Gateway/exporter tests cover exact platform/hash correspondence, conflicting matches, filename-only candidates, bounded metadata, private URL filtering, atomic replacement and authenticated HTTP responses. Existing files retain their bytes and filesystem identity. Android checks cover metadata serialization, old transfer compatibility and correction isolation by connection/platform/checksum.

After connectivity returned, the live RomM export and download gateway were deployed and verified: 18 files, 17 exact file-hash matches with IGDB artwork, and one filename-only review candidate. The Thor displayed live Pokémon FireRed metadata and artwork in the library and review dialog. A real sandboxed hourly-refresh service run completed successfully and replaced the index atomically. No game download was needed for this verification. Direct Cocoon artwork export remains unsupported.

Validation completed: 31 companion tests, Android unit tests, debug/test/release APK builds, Android lint, the full physical-device runner, Node tests and the static site's link/asset checks pass locally. A physical-device screenshot confirms the review dialog renders with the app's glass surface; this screenshot uses clearly named test fixtures, not live catalog results.

## Compact workspace and duplicate audit — 2026-10-08

The workspace illustration and introduction now leave room for the device controls, and emulator handoff uses a compact row. The installed APK was compared byte-for-byte with the previous local artifact before changing it: the earlier layout was current, not a stale installation. My Thor now identifies build type and source revision.

Validation: Android unit tests, debug/test APK builds, lint and the physical-device runner pass. The updated workspace was inspected on the top display of the Thor. Forty companion tests cover the metadata bridge and read-only duplicate audit; website tests and generated link checks pass. This pass does not validate a full redesign, all font sizes, or every orientation.
