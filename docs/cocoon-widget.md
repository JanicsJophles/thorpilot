# Cocoon launcher widget prototype

The Android source includes a standard `AppWidgetProvider` and responsive `RemoteViews` entry point. This is a launcher shortcut widget, not a background agent. It never fetches a server, creates requests, or changes emulator settings.

Tap the heading to open Workspace, **Chat / Find a game** to open the conversation, or **Requests** to open the read-only request page. Each action uses an explicit immutable `PendingIntent` addressed to Thorpilot. Only those three destinations are accepted. Existing activity navigation is reused where Android permits it.

## Add it in Cocoon

After installing a build containing the provider, open Cocoon **Start → New → New Widget → Android widget**, choose **Thorpilot companion**, and approve Android’s one-time **Create widget** prompt. You do not need to enable “Always allow.” Resize through Cocoon's grid editor. The hint hides on smaller allocations; controls remain 48dp high. Cocoon documents support for installed Android app widgets in its [widget guide](https://cocoon-shell.com/wiki/widgets/).

In the recorded host check, a widget placed on the lower screen opened Thorpilot on that same screen. Cocoon app display preferences may not override an Android widget’s PendingIntent launch; that override has not been verified. Widget hosting does not grant Thorpilot control over Cocoon's internal library or display settings.

## Verification status

Verified on a physical AYN Thor with Cocoon **3.06-1** on **2026-10-07**: provider appears in Cocoon’s Android widget picker, one-time permission creates the hosted widget, and actual taps on the heading, Find a game, and Requests open Workspace, Chat, and Requests respectively. Returning Home preserves the widget. These checks exercised the launcher-hosted PendingIntents, not ADB activity launch substitutes.

The physical-device instrumentation suite also passed provider registration, RemoteViews inflation at narrow/wide/short allocations, and destination allowlisting; Android lint reported no issues. Cocoon grid resizing and hardware-controller focus still need separate host checks. The Requests route check established navigation only, not a connected-server fetch.

For development, Android `KEYCODE_MENU` opens Cocoon’s Start menu. Resolve the current logical display ID before sending ADB input: it can change, and it is not the same as the screenshot index.

No periodic refresh is configured. Widget content contains no credentials, private library titles, or stale claims of connection health. The app checks current state after opening.
