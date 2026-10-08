# Summoning the copilot

Thorpilot should be easy to call up for one question or action, then dismiss. Its default presence during play should be absent, not a permanent large widget. The launcher cell remains an intentional entry point.

## Delivery status

The published 0.1.0-preview.1 app has a Cocoon widget, explicit emulator handoff, companion screen release/reclaim, and short navigation transitions. The development app adds an optional Quick Settings tile that opens a compact game-discovery conversation. It has Expand, Workspace and Dismiss controls and saves its draft and conversation separately from Workspace chat. It does **not** yet ship a global controller hotkey, a draw-over-games bar, or universal live game detection. The full summon experience is a design proposal under evaluation.

The design explores three states:

1. **Hidden:** no ongoing animation, model request, or polling for a summon gesture.
2. **Summoned:** a compact bar with one input and a few contextual actions. Do not open the keyboard automatically. Explain whether context was selected, observed, or inferred.
3. **Expanded:** a conversation or one action preview. Back/dismiss returns to the previous surface and retains the draft. Potentially disruptive changes need an explicit action; showing a proposal must not apply it.

The proposed identity is a small luminous control surface against AMOLED black, with restrained typography, compact rows, and clear controller focus. Cocoon sets the quality target; its assets and components are not presumed available for copying.

## Add the shortcut

In **My Thor**, choose **Add Quick Settings tile**. Android 13 and later show a system confirmation; you choose whether to add it. On older supported versions, open Quick Settings, choose **Edit**, and drag **Thorpilot** into the active tiles. The same manual route works if the system cannot show the confirmation.

Tap the tile to open the compact copilot. Expand gives longer replies more room; Dismiss, Android Back, or a tap outside closes it. The keyboard stays hidden until you choose the input. Local device tools remain available without a server connection. A locked device must be unlocked first. The tile does not request overlay, accessibility, microphone, or screen-capture access. It has no polling loop or ongoing foreground service.

The compact activity never creates a companion presentation. Its Workspace action opens the full app with companion presentation suppressed, including when a game was launched outside Thorpilot. Existing game-screen handoff state stays intact. Reclaiming the companion remains an explicit action. Android and the emulator determine which display receives the app and whether the game pauses; automatic lower-screen placement is not promised.

The existing Cocoon widget and normal app icon remain alternative entry points.

## Implementation and verification

`ThorpilotTileService` uses the intent launch API on Android 11–13 and the required `PendingIntent` launch API on Android 14 and later. The exported service is protected by Android's `BIND_QUICK_SETTINGS_TILE` permission. Tile-add requests use `StatusBarManager` on API 33+ and report cancellation without claiming success. [Android Quick Settings guide](https://developer.android.com/develop/ui/views/quicksettings-tiles), [TileService API](https://developer.android.com/reference/android/service/quicksettings/TileService), [tile-add API](https://developer.android.com/reference/android/app/StatusBarManager)

Instrumentation checks pass on the Android 13 Thor for registration, launch destination, task flags, companion suppression and preservation of game-session state. The native system add-tile prompt and an actual System UI tile tap were exercised on hardware: the shade collapsed, Workspace opened with the pause notice, and Reclaim remained explicit. This is limited shortcut evidence, not a verified uninterrupted in-game overlay.

Remaining hardware coverage: lock/unlock, tile removal, full warm/cold/recreation regression, and emulator pause/resume. Android 14+ PendingIntent behavior is compiled but not runtime-tested on this Android 13 device. Include this regression sequence: warm tile launch while already on Home → verify the notice and Reclaim control → recreate → explicitly reclaim → recreate again with the original tile intent retained. The companion must stay suppressed until the explicit reclaim, and saved-state `false` must override that old intent afterward.

## Display ownership

The physical test device captures a 1920×1080 top display and a 1240×1080 lower display. These are observations, not hardcoded layout bounds or persistent Android display IDs.

- When the lower screen is free, a user may choose it for the copilot.
- During DS/3DS play, both screens belong to the game unless the user explicitly opens Thorpilot. A yielded screen must stay yielded.
- Closing the summon surface must not launch another app or reclaim a display as a side effect.
- Android multi-resume does not guarantee that an emulator continues rendering or accepts controller input when a companion takes focus. Verify each emulator/version on hardware.

Existing Azahar focus/display findings are recorded in [device testing](device-testing.md). Keep them as acceptance constraints rather than assuming a second screen solves focus management.

## Compact activity

`SummonActivity` is a non-exported dialog activity in its own task, excluded from Recents. It uses the saved server connection and existing bounded chat client. Its separate connection-bound conversation prevents a paused Workspace chat from overwriting the quick conversation, or vice versa. Closing during a request stops UI delivery; an unanswered saved message offers Retry on reopening. It does not execute downloads or modify emulator settings.

The initial bottom window is 250 dp high and can expand to 390 dp, bounded by the available display. The header remains visible while the conversation scrolls. No overlay or accessibility permission is required, no second display is claimed, and there is no background summon loop. The native dialog transition is used. This remains an activity handoff: the previous app may pause. Android manages the chosen display; uninterrupted game rendering and universal controller hotkeys are not promised. [Android activity configuration](https://developer.android.com/guide/topics/manifest/activity-element).

## Optional overlay candidate

A true floating bar would require explicit draw-over-apps access and `TYPE_APPLICATION_OVERLAY`. Keep its window bounded to the visible bar rather than adding a full-screen transparent touch surface. A non-focusable collapsed bar can receive touch without claiming keyboard focus; typing or controller navigation needs a deliberate focus transition. Some apps hide overlays, so the basic launcher/tile entry point must remain useful.

Detect permission revocation, display removal, and lock state. Remove the window and release listeners when dismissed. Do not keep a wake lock or a busy service just to animate a handle. Display placement must resolve current display IDs and check launch permission, handling rejection gracefully.

References: [overlay approval](https://developer.android.com/reference/android/provider/Settings#canDrawOverlays(android.content.Context)), [window types and flags](https://developer.android.com/reference/android/view/WindowManager.LayoutParams), [display launch options](https://developer.android.com/reference/android/app/ActivityOptions#setLaunchDisplayId(int)), [multi-window behavior](https://developer.android.com/develop/adaptive-apps/guides/support-multi-window-mode).

## Controller access

An ordinary activity cannot capture a universal key chord while an emulator owns input focus. Do not label `dispatchKeyEvent` in Thorpilot as a global shortcut. An optional accessibility key-filter service has a separate user-enabled permission/capability model and can conflict with another filtering service. Hardware mapping and analog-trigger behavior also vary.

Do not adopt a default chord until it is tested against emulator shortcuts and normal gameplay. Touch, launcher, and tile access should stand on their own. Reference: [accessibility key filtering](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_REQUEST_FILTER_KEY_EVENTS).

## Motion and acceptance

Native page navigation uses a short opacity/translation transition on content only. It cancels prior movement before navigation, resets on detach, respects disabled system animators, and leaves the navigation shell stable. Passive request/download updates must not replay page entrances. Reference: [system animation availability](https://developer.android.com/reference/android/animation/ValueAnimator#areAnimatorsEnabled()).

For the proposed summon surface, target 140–220ms user-triggered motion, with immediate/brief opacity in reduced motion. Avoid full-screen blur, animated backgrounds, constant pulsing and particle effects during gameplay. Performance numbers are design budgets, not measured emulator guarantees.

Before calling the summon flow shipped, test repeated summon/dismiss in Azahar, Eden and melonDualDS; audio/render continuity; physical controller focus and recovery; IME placement; both occupied screens; lock/unlock; permission rejection/revocation; display removal; process recreation; and reduced motion. Record any emulator that pauses or changes its display behavior. Hardware evidence outranks the visual prototype.


## Local Game care mode

The compact copilot's mode picker switches between game discovery and Game care. Game care expands the window and opens the existing manual experiment journal without needing a server. Start an Azahar or Eden note, record a game, symptom and repeatable scene, then reveal build and driver details when needed. Trial records the original value and one proposed manual change; Result records what actually happened.

Unfinished care work uses a separate bounded local draft, restored after dismissal or process restart. Saved notes continue to use the existing Game care journal. Mode selection persists; switching modes preserves the draft. Starting a replacement note asks before discarding a populated draft. Notes are not sent to the discovery model. This is explicit user-provided context, not live game detection or automatic optimization.

The compact surfaces use locally drawn translucent glass with rounded pill controls, subtle reflections and rims. No sampled blur, continuous visual effect or background rendering loop is used.

## Recommendation artwork

The development app displays public IGDB covers already returned by the configured ROMarr chat adapter, in both Workspace and compact discovery cards. Provider keys remain on the server. The Android loader only accepts HTTPS images from the IGDB image host, refuses redirects, bounds response size and decoded dimensions, and uses two background workers with an 8 MiB memory cache. Missing or unsupported artwork retains a local icon. Other artwork providers and Cocoon credential import are not yet integrated.
