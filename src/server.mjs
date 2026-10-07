import http from "node:http";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { listRequests } from "./romarr.mjs";
const web = new URL("../web/", import.meta.url);
export function createServer(env = process.env) {
  return http.createServer(async (req, res) => {
    const send = (status, body, type = "application/json") => {
      res.writeHead(status, {
        "Content-Type": type,
        "Cache-Control": "no-store",
        "X-Content-Type-Options": "nosniff",
        "Content-Security-Policy":
          "default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'",
      });
      res.end(
        typeof body === "string" || Buffer.isBuffer(body)
          ? body
          : JSON.stringify(body),
      );
    };
    if (req.method !== "GET")
      return send(405, { error: "This prototype is read-only." });
    const path = new URL(req.url, "http://localhost").pathname;
    if (path === "/api/status")
      return send(200, {
        mode: env.ROMARR_URL && env.ROMARR_API_KEY ? "connected" : "demo",
        deviceConnected: false,
        adapter: "romarr-custom-requests-v1",
      });
    if (path === "/api/requests") {
      if (!env.ROMARR_URL || !env.ROMARR_API_KEY)
        return send(200, {
          demo: true,
          items: [
            {
              id: "demo-one",
              title: "Weekend racing collection",
              platform: "PSP",
              state: "ready",
              progress: 100,
              detail: "Example only — no real transfer or device connection.",
            },
            {
              id: "demo-two",
              title: "A cozy game for the train",
              platform: "Nintendo DS",
              state: "queued",
              progress: 0,
              detail: "Example request — waiting for your choice.",
            },
          ],
        });
      try {
        return send(200, {
          demo: false,
          items: await listRequests(env.ROMARR_URL, env.ROMARR_API_KEY),
        });
      } catch {
        return send(502, {
          error:
            "Cannot reach the request service. Last known state may be out of date.",
        });
      }
    }
    const files = {
      "/": ["index.html", "text/html; charset=utf-8"],
      "/app.js": ["app.js", "text/javascript"],
      "/style.css": ["style.css", "text/css"],
    };
    if (!files[path]) return send(404, { error: "Not found" });
    const [name, type] = files[path];
    return send(200, await readFile(new URL(name, web)), type);
  });
}
if (process.argv[1] === fileURLToPath(import.meta.url))
  createServer().listen(
    Number(process.env.PORT || 8791),
    process.env.HOST || "127.0.0.1",
    () =>
      console.log(
        "Thorpilot preview: http://localhost:" + (process.env.PORT || 8791),
      ),
  );
