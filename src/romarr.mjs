export function normalizeRequest(row) {
  return {
    id: String(row.id || ""),
    title: String(row.game || "Untitled request"),
    platform: String(row.platform || ""),
    state: String(row.status || "unknown"),
    detail: String(row.detail || ""),
    progress: Number.isFinite(row.progress)
      ? Math.max(0, Math.min(100, row.progress))
      : null,
    client: String(row.client || ""),
    peers: Number.isFinite(row.peers) ? row.peers : null,
  };
}
export async function listRequests(base, key, fetcher = fetch) {
  const url = new URL("/api/v1/game-requests", base);
  if (!["https:", "http:"].includes(url.protocol))
    throw Error("Unsupported server protocol");
  const response = await fetcher(url, {
    headers: { "X-Api-Key": key },
    signal: AbortSignal.timeout(15000),
    redirect: "error",
  });
  if (!response.ok) throw Error("Request service unavailable");
  const data = await response.json();
  if (!Array.isArray(data.items)) throw Error("Unsupported request response");
  return data.items.map(normalizeRequest);
}
