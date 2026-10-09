# Design direction

Thorpilot feels like a calm handheld companion. Cocoon is a compatibility target and visual quality reference, not a source of copied artwork or UI code.

The native app now adopts the Claude Design reference's black/amber tokens: AMOLED ground #000000, sheet #0b0c10, control #13151b, raised #1c1f27, line #2a2e38, text #f4f2ec, secondary #a9a69e and pilot amber #ffb547. Device observations use cool blue, server context violet, verified results green and failures coral. Decorative wallpaper and oversized colorful launcher tiles have been removed from the workspace and lower-screen dock.

Bundled Bricolage Grotesque headings and Instrument Sans body text work offline; JetBrains Mono is available for technical labels. Font sources and redistribution licenses are in [licenses/fonts](licenses/fonts/README.md). Explicit typography roles survive hierarchy refreshes.

Controller focus uses a reflective inset amber rim with a 180ms reveal, drawn above the control without layout changes or blur. Confirm presses have a 160ms inward pulse. Navigation ticks and confirm haptics use Android feedback APIs and honor system settings; held-key repeats do not trigger repeated confirm pulses. Disabling system animations makes focus immediate. Moving focus restores the old foreground. Selected navigation has a separate thin amber border. Device checks verify focus follows navigation and does not leave stale decorations.

Home and the companion display use one shared action-row component and one destination list. Home keeps the emulator handoff above those actions. The former workspace landing page and separate shortcut-tile builder have been deleted. Keep 48dp touch targets. My Thor shows the build type and source revision; `dirty` means local tracked changes and `unknown` means revision information was unavailable.

This is the native reference-theme implementation, not completion of every reference artboard. The existing top workspace and lower companion ownership remain. Universal in-game overlays, automatic game telemetry and all proposed summon interactions are not implied by the visual update. The web concept remains a separate surface; it is not an APK screenshot.

My Thor puts four common tasks first: downloading, browsing local games, folder sync and Game care. Each rounded control has an icon, action name and short explanation. It uses two columns on wide screens and one on narrow screens or with larger text; emulator shortcuts and hardware details follow. Hardware specifications never displace the primary tasks.

The top display carries a spacious library/task workspace. The lower display carries the conversation, contextual actions and a reachable composer. The browser preview presents them side by side on desktop and vertically on smaller screens. Native layouts must respect actual display bounds, insets and controller focus.

Use one small star motif, gentle surfaces and quiet motion. No game artwork or branding is bundled. Clear progress and honest uncertainty outrank decoration. Keyboard focus, reduced-motion settings, readable text and mobile layouts are part of the baseline.

The next design pass explores a compact **summon → ask/act → dismiss** interaction. See [Summoning the copilot](copilot-summon.md) for display ownership, motion constraints, Android feasibility, and the distinction between a visual concept and shipped functionality.

## Native UI ownership

`PilotSurface` is the shared reflective glass control surface for the app, panels and summon activity. `PilotActionRow` is the shared home/companion navigation component. The previous `PilotGlass`, `PilotBubble`, `PilotWallpaper` and `PilotJourney` implementations are removed rather than retained as a fallback theme. `PilotIcon` contains only the currently used vector action icons.

Functional panels remain connected to their existing stores, clients and transfer services. Changing the design must not reset preferences, save files, transfer history or connection credentials. Home row navigation is exercised by the on-device checks alongside tab stability and focus restoration.

Cocoon is a visual/interaction reference, not copied source. Its public repository does not include the launcher UI source, and its author explains the closed-source status at https://cocoon-shell.com/news/post-2-0/ . The glass shading, focus transition and controller feedback here are original native implementations.

## Controller navigation and preferences

B/Back returns nested tools to their parent (Eden inspector and snapshots → Game care; storage tools → My Thor), then returns sections to Home. On Home, Back retains Android’s normal exit behavior. The lower presentation routes back through the same navigation; B dismisses the compact copilot. Returning to Home/My Thor restores focus to the action that opened the child when available.

Settings includes persisted Interface animations and Navigation haptics switches, shared across both displays and the compact copilot. System-disabled motion/haptics remain authoritative. Disabling motion affects page transitions, focus reveal, press pulses and onboarding transitions; the visible focus outline remains.

## Section position restoration

The native shell remembers outer scroll offsets independently for each section. Tagged navigation actions can regain controller focus on return; touch navigation does not force focus or open a keyboard. Positions are saved with Android activity state and only known route identifiers are accepted. Pending restore callbacks are invalidated when another render wins, so rapid navigation does not apply a previous screen's position.

This restores the shell viewport, not an item anchor inside asynchronously replaced game/request lists. It does not save text-field contents or credentials. The navigation-position change has local unit/build coverage; its physical Thor walkthrough remains pending while the device is disconnected.

## Handheld shell (draft, hardware verification pending)

The full companion workspace and its owned lower presentation use immersive mode by default. Swiping from an edge reveals Android system navigation; Settings can disable fullscreen. The compact summon activity and emulator windows are not changed.

L1/R1 switch between the same sections as the touch tabs, including wrapping at either end. Nested tools map to My Thor. Held repeats do not repeatedly change sections, and focused text editors keep shoulder input. Both screens display A/B/shoulder hints, and the lower screen labels the active section. The top header is smaller while preserving touch target height.

These changes are a first handheld-shell pass, not an OS replacement or completion of the screen redesign. Local JVM/build checks cover the section policy; instrumentation adds shoulder wrapping and text-editor protection. Physical fullscreen, controller and dual-display walkthroughs require a connected Thor before this draft is promoted.
