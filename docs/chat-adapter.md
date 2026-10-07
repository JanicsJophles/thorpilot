# Optional game chat adapter

The Android companion can send a conversation to an operator-configured HTTPS ROMarr server. This is an **optional custom API**, not a capability promised by stock ROMarr. No server address, provider key, indexer, or content source ships in Thorpilot.

`ChatClient.send(baseUrl, apiKey, messages)` runs synchronously and must be called on a worker thread. The UI supplies the existing saved connection. The only network action is `POST {baseUrl}/api/v1/game-chat`, authenticated with the `X-Api-Key` header. Redirects are rejected to avoid forwarding a key to a different endpoint. Standard Android TLS validation remains enabled.

Request JSON:

```json
{"messages":[{"role":"user","content":"Suggest a short puzzle game"}]}
```

Only `user` and `assistant` roles are sent. The last message must be a user question. The most recent 20 messages are sent, each at most 3,000 characters; older assistant responses are truncated to that limit. The serialized request must fit the server's 64,000-byte limit. Recommendation cards are local UI metadata and are not sent back as conversation content.

Successful response:

```json
{
  "reply": "Here is a suggestion.",
  "games": [{
    "id": 123,
    "title": "Example title",
    "platform": "nds",
    "platform_name": "Nintendo DS",
    "reason": "Short sessions and a relaxed pace.",
    "cover": "",
    "owned": false
  }],
  "unverified": 0
}
```

The adapter returns catalogue-checked suggestions. It does not imply availability, emulator compatibility, ownership rights, or download completion. `owned` is the server's library match. `unverified` counts suggestions the backend could not verify. Chat never submits a request, starts a download, launches an emulator, or changes device settings.

The client bounds response bodies to 128 KiB, reply text to 4,000 characters, cards to five, and card fields to fixed lengths. Non-HTTPS cover addresses are discarded. Covers are metadata only; ChatClient does not download them. Connect timeout is 15 seconds; read timeout is 90 seconds to allow provider generation and catalogue verification. HTTP failures and malformed data produce `ChatException` with a safe user-facing message. Upstream bodies, raw exceptions, and credentials are never surfaced in error messages or logs. A JSON `error` response, including HTTP 200 responses, is treated as failure.

## Local state

`ChatHistory(context).load()` returns `ChatSnapshot(messages, draft)`. `save(messages, draft)`, `saveDraft(draft)`, and `clear()` manage a bounded conversation in app-private preferences. The last 20 messages and their cards survive activity/process recreation. The draft is limited to 3,000 characters. Android backup and device transfer are disabled by the app; API keys belong exclusively in the separate Keystore-backed connection store.

History is local plaintext inside the Android application sandbox, not end-to-end encrypted chat storage. Sending a message shares its recent conversation with the configured server and whichever AI provider that server uses. Clearing local chat does not erase any server/provider records. A failed send should leave the user message available to retry and must not invent an assistant response. UI lifecycle cancellation should prevent stale worker results from replacing a newer or cleared conversation.
