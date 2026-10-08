# Design direction

Thorpilot feels like a calm handheld companion. Cocoon is a compatibility target and visual quality reference, not a source of copied artwork or UI code.

The native palette uses deep ink #030a11, glass slate #1a2730, mint #6cf7d0, ice #f2f6fc, muted blue #aec0d0 and violet accents. Rounded system headings and regular system body text avoid a font dependency. Glass rims and restrained gradients give controls depth without sampling the game screen or adding continuous blur. The web concept remains a separate surface; it is not an APK screenshot.

The workspace uses a compact illustration beside one primary action. Emulator handoff stays in a short row, with library and screen controls below. Keep 48dp touch targets even when the visible controls are smaller. My Thor shows the build type and source revision so development installs can be distinguished from the public preview. A `dirty` suffix means the APK was built with local tracked changes; `unknown` means source revision information was unavailable.

The top display carries a spacious library/task workspace. The lower display carries the conversation, contextual actions and a reachable composer. The browser preview presents them side by side on desktop and vertically on smaller screens. Native layouts must respect actual display bounds, insets and controller focus.

Use one small star motif, gentle surfaces and quiet motion. No game artwork or branding is bundled. Clear progress and honest uncertainty outrank decoration. Keyboard focus, reduced-motion settings, readable text and mobile layouts are part of the baseline.

The next design pass explores a compact **summon → ask/act → dismiss** interaction. See [Summoning the copilot](copilot-summon.md) for display ownership, motion constraints, Android feasibility, and the distinction between a visual concept and shipped functionality.
