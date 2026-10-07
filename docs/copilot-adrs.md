# Copilot architecture decisions

These are proposed decisions for the next stages; they do not describe a finished agent runtime.

## ADR 1: Cocoon companion, independent Android application

Build an Android app with an optional standard app widget. Keep Cocoon as the launcher. Do not require a custom launcher fork or undocumented Pod interface. A full app owns conversation and previews; the widget is a small entry point with a last-updated state. Widget design follows [Android's model](https://developer.android.com/develop/ui/views/appwidgets/overview); Cocoon host behavior must be tested.

Consequence: some launcher tasks remain guided user actions until maintainers provide a supported integration. This is preferable to silently editing a changing private database.

## ADR 2: Planner and executor are separate

The model proposes structured actions. A local executor independently validates schema, adapter version, permissions, resource identity, freshness, and user approval. Backend adapters receive only the credentials and fields they need. Model output cannot select arbitrary packages, filesystem paths, URLs, or shell commands.

Proposed tool contract:

```text
action: library.plan_transfer
arguments: {libraryItemId, grantedDestinationId}
result: {planId, expiresAt, bytes, conflicts, expectedHashes}

execution: library.apply_transfer
arguments: {planId, approvalToken, idempotencyKey}
result: {jobId, state, verifiedBytes, recoveryAction}
```

Opaque identifiers map to local granted resources. Revalidate the plan before execution. Bind approval to its digest and scope; a changed destination or file list requires a new preview. Treat tool results and metadata as data, never instructions.

## ADR 3: Explicit context, minimal transmission

Build a local context index of granted library metadata, supported emulator versions, display capabilities, service capabilities, and user preferences. Send only the context needed for the current question. Show whether a request stays local or reaches the configured server/model provider. Credentials, save contents, screenshots and full filesystem inventories are not default model context.

Keep a redacted local action journal. Export is a user action with a preview. Do not log signed download URLs or nested provider responses without sanitization.

## ADR 4: State survives screens and interrupted work

Conversation, plans, jobs and rendering state are separate. Display changes do not start jobs. Each job has an identity, last-confirmed state, cancelability, and restart reconciliation. Future durable local jobs should use a transactional store; do not stretch chat preferences into a job engine.

Track `requested → downloading → imported → transferring → verified → ready` as different stages owned by different systems. Emulator installation may be an additional stage. Unknown state is not success. A network outage cannot establish a bad release.

## ADR 5: Reversibility before automation

Start with read-only inventory and launch previews. For configuration writes, require an allowlisted setting schema and supported app version, close the emulator, snapshot the original, compare its hash before writing, then validate the result. If another process changed the file, stop with a conflict. Keep the backup until the user accepts the result.

SAF providers differ in write/rename capabilities. Do not promise atomic file replacement everywhere. Use staged copies and verified completion; a failed provider operation must leave a recoverable journal. Restore operations preserve both conflicting versions rather than silently picking one.

## ADR 6: Visible and bounded execution

Read-only operations within a standing grant may run on request. New grants, transfers, restore/overwrite, configuration changes, and screen capture require explicit scoped approval. An opt-in recurring rule is a visible saved authorization, not unlimited future permission. Users can pause jobs and revoke grants without asking the agent.

Accessibility is an optional last-mile adapter, not the foundation. ADB is development tooling, not a shipping dependency. Neither route bypasses Android permission boundaries or supplies root access.

## ADR 7: Battery and performance are product constraints

Use event-driven state and foreground-only refresh where possible. Suspend decorative animation and polling when not visible. Transfers need resumability, network/charging preferences, and cancellation. Do not hold indefinite wake locks or run continuous screen capture by default.

Treat remote inference as the first implementation path. Local inference remains an experiment whose latency, memory, heat, and battery cost must be measured on the target hardware. An NPU specification is not evidence that a model/runtime fits the product budget.
