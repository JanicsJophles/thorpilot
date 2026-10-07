# Optional service integrations

Thorpilot is an independent handheld companion. Operators can connect their own services; no private assistant, context library, infrastructure configuration, or account is included.

## Current and proposed adapters

| Integration | Role | Status |
| --- | --- | --- |
| ROMarr companion fork | Game discovery chat and request status | Optional native clients implemented. Chat itself does not request downloads. |
| RomM | Library browsing and file inventory | Direct Thorpilot adapter proposed. |
| Personal assistant | Help grounded in an operator's selected context and permitted tools | Generic adapter proposed; no bundled assistant service. |
| Context provider | Retrieve relevant setup notes and preferences with source dates | Generic read-only adapter proposed. |
| RackMind | Optional homelab service health and operations | Future adapter; separately configured and authorized. |

See [chat-adapter.md](chat-adapter.md) for the implemented ROMarr contract. The other integrations are design directions, not live connections shipped with the app.

## A useful first step

A context adapter could answer “What did I decide about my handheld setup?” using a small number of selected excerpts with citations and source dates. An assistant adapter could combine that evidence with live library or device state, then offer a specific action. Each integration needs a tested API, cancellation, timeouts, and honest unavailable states before the app presents it as connected.

## Connection boundaries

- Configure services independently with scoped credentials and revocation. Never bundle private deployment details.
- Keep retrieval read-only initially. Saving a preference or changing a setting is a separate named operation with a visible result.
- Treat retrieved text as evidence, not permission to run tools or expand access.
- Show which context is sent to a model provider, and support source exclusions.
- Preserve durable service IDs and timestamps. An assistant answer does not prove a request, transfer, install, or settings change succeeded.
- Test empty results, stale evidence, denied access, service outages, and cancellation using synthetic fixtures.

A self-hosted service can remain entirely personal. Connecting it to Thorpilot does not require publishing its source, memory, configuration, or architecture.

See [architecture.md](architecture.md) and [copilot-delivery.md](copilot-delivery.md) for the companion design and delivery gates.
