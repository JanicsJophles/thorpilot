import test from "node:test";
import assert from "node:assert/strict";
import { createServer } from "../src/server.mjs";
import { normalizeRequest, listRequests } from "../src/romarr.mjs";
test("metadata is preserved, not upgraded to downloading", () => {
  assert.equal(normalizeRequest({ status: "metadata" }).state, "metadata");
});
test("adapter never returns credentials or internal URLs", async () => {
  const rows = await listRequests(
    "https://example.test",
    "SECRET",
    async (u, o) => {
      assert.equal(o.headers["X-Api-Key"], "SECRET");
      return {
        ok: true,
        json: async () => ({
          items: [
            {
              game: "Example",
              status: "failed",
              api_key: "SECRET",
              url: "http://private",
            },
          ],
        }),
      };
    },
  );
  assert.ok(!JSON.stringify(rows).includes("SECRET"));
  assert.ok(!JSON.stringify(rows).includes("private"));
});
test("prototype defaults to demo and exposes no mutation endpoints", async () => {
  const server = createServer({});
  await new Promise((r) => server.listen(0, "127.0.0.1", r));
  try {
    const base = "http://127.0.0.1:" + server.address().port;
    assert.equal(
      (await fetch(base + "/api/status").then((r) => r.json())).mode,
      "demo",
    );
    assert.equal(
      (await fetch(base + "/api/requests").then((r) => r.json())).demo,
      true,
    );
    assert.equal(
      (await fetch(base + "/api/requests", { method: "POST" })).status,
      405,
    );
    assert.equal((await fetch(base + "/.env")).status, 404);
  } finally {
    await new Promise((r) => server.close(r));
  }
});
