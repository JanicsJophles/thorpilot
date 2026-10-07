# Game care

Game care is a local experiment journal for improving how a game looks and plays. The current Android `GameCarePanel` has three stages: **Baseline**, **Trial**, and **Result**. It does not apply emulator settings, capture performance counters, or restore a configuration automatically. The separate [configuration snapshots](configuration-snapshots.md) screen can save exact local backups and explicitly restore a reviewed Azahar configuration after comparison.

## Available now

Baseline records the game, a symptom category (Texture, Flicker, Shadows, Stutter or Other), a repeatable scene and a before observation. Trial records the original setting and one proposed manual change. Result records Untested, Better, Same or Worse plus an after observation. Device and recorded emulator build accompany the note. **New experiment** starts an Azahar note; **New Eden experiment** starts an Eden note and detects its installed build. Baseline also records game revision and graphics driver/backend when known. Trial records Unknown, Per-game or Global scope. These are manual observations; package detection does not read the running game's configuration.

**Reuse as untested trial** creates an unsaved draft from an existing note. It keeps the game, symptom, scene and proposed change, but assigns a new ID and records the source note. It resets the outcome, before/after observations, original setting, revision, driver and scope. The current device and detected emulator build replace the old environment. Record the current original setting again before testing. The source note stays unchanged until explicitly edited; a successful experiment is not promoted to a universal preset.

Older records without an emulator identity retain their original Azahar meaning. Saved notes preserve their recorded build; a mismatch with the installed build is displayed rather than silently rewriting historical evidence. My Thor now offers an Eden app handoff alongside the existing emulator shortcuts. This opens Eden, not a particular game or its settings. Eden configuration writes, snapshots and automatic rollback are not supported.

Use **Save local note** to persist an entry. Saved notes can be reopened and edited. The app keeps up to 30 most recently saved experiments in its private preferences; saving beyond that limit replaces the oldest note. Draft text and the selected step participate in Android activity-state restoration. Unsaved text is not a durable experiment record: force-stop, discarding the task, opening another note or starting a new experiment can discard it. Save the original setting before leaving to run a trial. These notes are local plaintext inside the Android app sandbox, with no network submission by this feature. They are not an emulator backup.

No measured game-FPS improvement or verified game-specific graphical fix is claimed by this feature.

## A useful A/B comparison

1. **Define the symptom and scene.** Name the object or effect, camera angle, location and action that reproduce it. Record game revision, emulator build and graphics backend when known. “Blue character becomes pale during this animation” is more useful than “graphics bad.”
2. **Record A before changing anything.** Note the exact original setting and whether the defect is persistent or intermittent. Use the same save, route or scene for repeat observations; avoid creating a new game-progress difference merely to test rendering. Save the baseline note.
3. **Change one value manually.** Keep resolution, backend, driver, power mode and other settings fixed unless one of those is the selected variable. Follow the emulator's restart requirements. Do not replace several settings with an unverified preset.
4. **Repeat B under comparable conditions.** Match the scene, duration, charger/power conditions and approximate device temperature. Shader compilation can make a first visit unusually slow; distinguish cold-cache from repeated warm runs rather than comparing unlike passes.
5. **Record correctness and responsiveness separately.** A higher FPS reading does not compensate for missing textures, wrong colors or broken shadows. If the visual defect improved but stutter increased, record both rather than declaring an unqualified win. Repeat the observation when practical.
6. **Restore A if worse or inconclusive.** Manually restore the recorded original value and repeat the scene. This A/B/A check helps distinguish a setting effect from a changing workload. The journal's original-setting field is a reminder, not a verified backup or rollback button.

Use this rubric in the before/after text:

| Aspect | Record |
| --- | --- |
| Texture/color | Which surface or character is wrong; missing, corrupted, unusually bright or incorrect color; persistent or intermittent. |
| Flicker/shadows | Which element changes unexpectedly and under what camera motion; whether the defect is reproducible. |
| Stutter/pacing | Where pauses occur, approximate frequency, and whether repeated visits change them. |
| Gameplay/audio | Input response, audio breakup, hangs or newly introduced faults. |
| Evidence limits | Scene differences, unknown original values, missing counters, warm-up differences or uncertain reproduction. |

Leave the result **Untested** until a comparison has actually occurred. Better/Same/Worse is a user observation, not an automatically scored benchmark. A mixed result belongs in the written observation.

## Azahar version boundary

Source inspection of official **Azahar 2126.0** found that Android's settings saver still leaves the per-game branch unimplemented, while its native reader loads the selected user directory's `config/config.ini`. An existing custom-game INI reader is not evidence that writing such a file provides working Android per-title settings. Treat manual settings changes in this version as potentially affecting other games. [Android settings model](https://github.com/azahar-emu/azahar/blob/2126.0/src/android/app/src/main/java/org/citra/citra_emu/features/settings/model/Settings.kt), [native configuration reader](https://github.com/azahar-emu/azahar/blob/2126.0/src/android/app/src/main/jni/config.cpp).

Accurate Multiplication is a possible single-variable graphics experiment when a reproducible shader defect exists. The official Android description says it may fix graphical bugs at a performance cost. It is not a universal fix or a tested recommendation for every device/title. The 2126.0 reader uses `[Renderer] shaders_accurate_mul`; preserve the actual original value instead of assuming a default. These references explain the setting, not a request to edit configuration files. [Official setting descriptions](https://github.com/azahar-emu/azahar/blob/2126.0/src/android/app/src/main/res/values/strings.xml).

Azahar's own performance overlay receives game FPS, emulation speed and timing values from its emulator core. Thorpilot currently does not collect those counters. Display refresh rate in Hz describes the screen and must never be relabeled game FPS. If a user transcribes an overlay value, identify its source and observation interval; do not invent averages or low-percentile frame statistics from a single reading. [Native performance interface](https://github.com/azahar-emu/azahar/blob/2126.0/src/android/app/src/main/jni/native.cpp), [counter implementation](https://github.com/azahar-emu/azahar/blob/2126.0/src/core/perf_stats.cpp).

## Configuration snapshots and the future adapter

The separate [configuration snapshots](configuration-snapshots.md) screen now provides exact local backups, hash comparisons, conflict checks and explicit restoration with a pre-restore recovery copy. Restore is limited to matching Azahar `2126.0` or `2126.0-vanilla` versions. This is whole-file restoration of a user-selected global configuration, not automatic per-game tuning. The snapshot documentation explains selection, storage limits, stop-emulator requirements and provider-write limitations.

An adapter that applies individual experimental settings remains proposed. It needs narrow setting allowlists, preservation of unrelated settings, a durable recovery journal and validation across emulator builds. A stopped emulator is required when editing a file that the emulator may also save. The snapshot foundation does not make external writes atomic or establish that the selected document is the active config.

Storage Access Framework access to a ROM directory does not grant another app's private files or an unrelated configuration directory. Providers differ in write/rename behavior; an adapter must establish its actual recovery guarantees rather than assume filesystem-atomic writes. For a build with only global settings, any temporary game profile must clearly disclose its global effect and restoration status. [Android document access](https://developer.android.com/training/data-storage/shared/documents-files).

Game care remains a manual experiment journal. Neither saving a note nor selecting Better changes an emulator; configuration restoration requires its separate preview and confirmation.

## Eden workflow verification

The reusable-trial model has local JVM tests for identity, provenance, reset of prior results and environment, and preservation of the source record. Android instrumentation was executed successfully on a physical AYN Thor, including Eden persistence, legacy-note defaults and real panel callbacks for save/reopen/reuse. Touch navigation opened the Eden library through the app handoff; Cocoon remained visible on the lower display. The Eden form detected build `1f6734c`. This verifies the observed app path, not physical controller mapping, a full-game session or automatic configuration changes. Earlier manual Z-A results are separate evidence.
