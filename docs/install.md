# Install Thorpilot

Thorpilot is an **early Android preview**, designed for AYN Thor and usable on Android 11 or newer. You can install it directly on your handheld: no computer, ADB, root, or Thorpilot account is required. Local setup works without a server or API key.

## Download the preview

The first signed public APK is being prepared. The links below become available when release **v0.1.0-preview.1** is published; a missing download means publication is still pending.

- [Preview release and known limitations](https://github.com/JanicsJophles/thorpilot/releases/tag/v0.1.0-preview.1)
- [Download Thorpilot 0.1.0-preview.1 APK](https://github.com/JanicsJophles/thorpilot/releases/download/v0.1.0-preview.1/thorpilot-0.1.0-preview.1.apk)

GitHub sign-in is not required to download public release assets. Use the signed APK attached to the release, rather than a CI artifact. The CI artifact named `thorpilot-release-unsigned` is a build input for maintainers and cannot be installed as-is.

## Install on your handheld

1. Open this page in your handheld's browser and download the preview APK once available.
2. Open the download. If Android asks, allow that browser to install unknown apps, then return to the installer. This permission applies to the browser you used; you can turn it off again after installation.
3. Tap **Install**, then **Open**. Thorpilot also appears in your installed apps and can be launched from your frontend.
4. Follow the guided setup. Choose an existing ROM folder when you are ready, or skip that step and return later. Android's folder picker grants access only to the location you choose.

Setup does not require a library server, chat provider, or API key. Connections are optional extras. Keep your existing folder layout; choosing a folder does not require reorganizing or replacing its files. See [library storage and transfers](library-sync.md) for supported formats and separate internal-storage and SD-card locations.

## Cocoon and other frontends

Already using Cocoon? Keep your existing setup and open Thorpilot alongside it. If you are setting up a new handheld, use [Cocoon's official website](https://cocoon-shell.com/) for its download and installation instructions. Thorpilot's setup guide hands off to official resources; it does not silently install Cocoon, purchase anything, or change your default launcher.

After selecting game folders in Thorpilot, configure those folders in your frontend and emulator too. Cocoon's [emulator setup guide](https://cocoon-shell.com/wiki/emulator-setup/) explains its library configuration. Other frontends have their own folder-selection and rescan steps. You can also add the optional [Thorpilot companion widget](cocoon-widget.md) in Cocoon.

## Updating

Install newer signed public Thorpilot APKs over the existing public preview. Keep the app installed: an ordinary update signed with the same release key retains its app data. Check the release notes for compatibility changes before updating.

**Already using a development build?** The Gradle/ADB debug APK and the public release APK use different signing identities. Android will not install the release APK over a development-signed `dev.thorpilot` installation. Do not uninstall or clear app data to work around this: that removes private Thorpilot data, including local records and saved connections. Keep using development builds until a supported migration path is available. See [device testing](device-testing.md) for that workflow.

## What to expect

This is a work in progress. Local device tools and guided setup are available without a server; chat, request status, and library downloads require their respective optional services. Thorpilot does not include games, BIOS files, firmware, emulator keys, or download providers. It does not automatically configure every emulator, tune games, or provide an in-game overlay. The second-screen companion and explicit emulator handoff are distinct from a gameplay overlay.

If something is confusing or fails, [open an issue](https://github.com/JanicsJophles/thorpilot/issues) with your Thorpilot version, Android version, and the step that failed. Leave out credentials, personal server addresses, game files, and saves.
