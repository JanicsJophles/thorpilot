# Contributing to Thorpilot

Thorpilot is an early companion for Android handhelds. Small, focused improvements and clear bug reports are welcome. Open an issue before a large change so we can agree on the experience and scope.

## Work through a pull request

1. Fork the repository or create a feature branch if you are a maintainer.
2. Make a focused change with appropriate tests and documentation.
3. Open a pull request with verification results. Include screenshots for UI changes and name the devices/screens you tested.
4. Wait for review and required CI checks before merging. Do not push directly to `main` or bypass checks.

Follow the setup instructions in the README. Run `npm test` for the browser/server prototype. Android changes should build with `cd android && ./gradlew :app:assembleDebug :app:lintDebug`; device checks require an authorized test device. Report checks you could not run rather than implying they passed.

## Keep the companion trustworthy

- Use scoped, explicit device actions. Avoid arbitrary commands from model output.
- Preserve connection credentials in secure storage and keep them out of logs.
- Handle one-screen devices, disconnected displays, unavailable servers, and denied permissions.
- Include only code and assets you have permission to contribute. Do not submit game files, BIOS files, private providers, or personal infrastructure configuration.
- Keep private keys and server addresses out of screenshots, fixtures, and issue reports.

## CI and private test hardware

Public fork code must not execute automatically on private self-hosted runners. Untrusted contributions are reviewed first and tested in isolated, unprivileged environments. If private hardware testing is necessary, a maintainer reviews the complete change, then places the reviewed code on a same-repository branch for an explicitly approved run. Never expose secrets through `pull_request_target`, or fetch and execute unreviewed fork code in a privileged job. A same-repository branch is a staging step, not a substitute for review.

Report vulnerabilities through the process in [SECURITY.md](SECURITY.md), not a public issue containing exploit details or secrets.
