# Cocoon launcher widget prototype

The Android source includes a standard `AppWidgetProvider` and responsive `RemoteViews` entry point. This is a launcher shortcut widget, not a background agent. It never fetches a server, creates requests, or changes emulator settings.

Tap the Thorpilot icon to open Workspace, **Chat / Find a game** to open the conversation, or **Requests** to open the read-only request page. Each action uses an explicit immutable `PendingIntent` addressed to Thorpilot. Only those three destinations are accepted. Existing activity navigation is reused where Android permits it.

## Add it in Cocoon

After installing a build containing the provider, open Cocoon **Start → New → New Widget → Android widget**, choose **Thorpilot companion**, and approve Android’s one-time **Create widget** prompt. You do not need to enable “Always allow.” Resize through Cocoon's grid editor. The widget is a single 64dp-high strip with 48dp touch targets and a 220dp minimum width. It requests a three-column, one-row default; the launcher controls the actual allocation. Extra host space stays transparent rather than stretching the panel. Cocoon documents support for installed Android app widgets in its [widget guide](https://cocoon-shell.com/wiki/widgets/).

In the recorded host check, a widget placed on the lower screen opened Thorpilot on that same screen. Cocoon app display preferences may not override an Android widget’s PendingIntent launch; that override has not been verified. Widget hosting does not grant Thorpilot control over Cocoon's internal library or display settings.

## Verification status

Verified on a physical AYN Thor with Cocoon **3.06-1** on **2026-10-07**: provider appears in Cocoon’s Android widget picker, one-time permission creates the hosted widget, and actual taps on the heading, Find a game, and Requests open Workspace, Chat, and Requests respectively. Returning Home preserves the widget. These checks exercised the launcher-hosted PendingIntents, not ADB activity launch substitutes.

The physical-device instrumentation suite also passed provider registration, RemoteViews inflation at narrow/wide/short allocations, and destination allowlisting; Android lint reported no issues. The compact layout was installed and checked on the same Thor: Chat and Requests both opened from actual widget taps, and Cocoon resizing reduced the host allocation from three rows to two. This host did not shrink it to one row in the check; the remaining margin is transparent. Physical hardware-controller focus still needs a separate check. The Requests route check established navigation only, not a connected-server fetch.

For development, Android `KEYCODE_MENU` opens Cocoon’s Start menu. Resolve the current logical display ID before sending ADB input: it can change, and it is not the same as the screenshot index.

The provider refreshes after package replacement. Cocoon cached the old rendered layout during an upgrade in this check; restarting Cocoon displayed the new strip. No periodic refresh is configured. Widget content contains no credentials, private library titles, or stale claims of connection health. The app checks current state after opening.
