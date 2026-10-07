# Summoning the copilot

Thorpilot should be easy to call up for one question or action, then dismiss. Its default presence during play should be absent, not a permanent large widget. The launcher cell remains an intentional entry point.

## Delivery status

The current native app has a Cocoon widget, explicit emulator handoff, companion screen release/reclaim, and short navigation transitions. It does **not** yet ship a global controller hotkey, a draw-over-games bar, or universal live game detection. The full summon experience is a design proposal under evaluation.

The design explores three states:

1. **Hidden:** no ongoing animation, model request, or polling for a summon gesture.
2. **Summoned:** a compact bar with one input and a few contextual actions. Do not open the keyboard automatically. Explain whether context was selected, observed, or inferred.
3. **Expanded:** a conversation or one action preview. Back/dismiss returns to the previous surface and retains the draft. Potentially disruptive changes need an explicit action; showing a proposal must not apply it.

The proposed identity is a small luminous control surface against AMOLED black, with restrained typography, compact rows, and clear controller focus. Cocoon sets the quality target; its assets and components are not presumed available for copying.

## Display ownership

The physical test device captures a 1920×1080 top display and a 1240×1080 lower display. These are observations, not hardcoded layout bounds or persistent Android display IDs.

- When the lower screen is free, a user may choose it for the copilot.
- During DS/3DS play, both screens belong to the game unless the user explicitly opens Thorpilot. A yielded screen must stay yielded.
- Closing the summon surface must not launch another app or reclaim a display as a side effect.
- Android multi-resume does not guarantee that an emulator continues rendering or accepts controller input when a companion takes focus. Verify each emulator/version on hardware.

Existing Azahar focus/display findings are recorded in [device testing](device-testing.md). Keep them as acceptance constraints rather than assuming a second screen solves focus management.

## First implementation candidate

A dedicated compact activity, entered through an Android Quick Settings tile, Cocoon widget, or app shortcut, is the smallest useful slice. It would avoid creating the main activity's secondary Presentation and preserve the recorded yielded state. It needs no accessibility service or draw-over-apps permission.

This is a quick handoff, **not** a promise of uninterrupted gameplay. Android 14+ tiles use the PendingIntent form of `startActivityAndCollapse`; earlier supported Android versions use the Intent form. Android 13+ can offer the system add-tile request. See the [official tile guide](https://developer.android.com/develop/ui/views/quicksettings-tiles).

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
