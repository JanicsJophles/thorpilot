const $ = (s) => document.querySelector(s);
let messages = [],
  requests = [],
  tab = "requests",
  mode = "demo";
try {
  messages = JSON.parse(sessionStorage.getItem("thorpilot-chat") || "[]");
} catch {}
const escape = (s) =>
  String(s ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const labels = {
  metadata: "Waiting for peers",
  failed: "Failed",
  downloaded: "Awaiting import",
  imported: "In library",
  downloading: "Downloading",
  stalled: "Stalled",
  unknown: "Check connection",
  queued: "Queued",
  ready: "Ready (demo)",
};
function draw() {
  $("#count").textContent = requests.length;
  const content = $("#top-content");
  if (tab === "device") {
    content.innerHTML =
      '<div class="empty"><h3>Your handheld, understood.</h3><p>No device paired in this prototype. The planned Android companion will report emulator versions, storage access and game availability.</p><p>Device actions will be explicit, logged and reversible where possible. Cocoon remains your launcher.</p></div>';
    return;
  }
  if (tab === "roadmap") {
    content.innerHTML =
      '<div class="empty"><h3>One useful step at a time.</h3><p>Next: an Android companion with a Cocoon widget, real second-display placement and a paired device inventory.</p><p>Then: verified transfers, save backups and emulator-specific settings with measured before/after results.</p></div>';
    return;
  }
  content.innerHTML = requests.length
    ? requests
        .map(
          (r) =>
            `<article class="request"><div class="request-top"><div><h3>${escape(r.title)}</h3><span class="platform">${escape(r.platform)}${r.client ? " · " + escape(r.client) : ""}</span></div><span class="tag">${escape(labels[r.state] || r.state)}</span></div>${r.progress !== null && r.progress !== undefined ? `<progress aria-label="${escape(r.title)} progress" max="100" value="${r.progress}"></progress>` : ""}<p>${escape(r.detail)}${r.peers !== null && r.peers !== undefined ? " · " + r.peers + " connected peers" : ""}</p></article>`,
        )
        .join("")
    : '<div class="empty"><h3>Nothing waiting on you.</h3><p>Your requests will appear here when you connect a supported service.</p></div>';
}
function drawChat() {
  $("#conversation").innerHTML =
    '<div class="assistant"><span class="sigil">✦</span>Hey. Let’s make more room for playing.\n\nI’m a preview of your future handheld companion. Try checking requests or exploring how game tuning could work.</div>' +
    messages
      .map((m) => `<div class="${m.role}">${escape(m.text)}</div>`)
      .join("");
  $("#conversation").scrollTop = $("#conversation").scrollHeight;
}
function ask(text) {
  if (!text.trim()) return;
  messages.push({ role: "user", text: text.trim() });
  let reply;
  if (/request|download|queue/i.test(text)) {
    tab = "requests";
    setTab();
    reply =
      mode === "demo"
        ? "These are example requests so you can try the interface. Connect your own ROMarr server to read real status."
        : "Your requests are on the top display. These are live service records; a download is not the same as a verified game on your handheld.";
  } else if (/tun|optimi|stutter|performance/i.test(text)) {
    tab = "roadmap";
    setTab();
    reply =
      "For real tuning, I’ll need the game, emulator version and a baseline on your device. The plan is to back up the settings, test one change, and keep it only if the result improves.\n\nThis preview cannot inspect or change emulator settings yet.";
  } else
    reply =
      "That’s exactly the kind of conversation Thorpilot is being designed for. This prototype uses scripted replies; an AI provider and device tools are not connected yet.\n\nThe first real workflow will help you find a game, track its request and verify that it reached your handheld.";
  messages.push({ role: "assistant", text: reply });
  messages = messages.slice(-30);
  sessionStorage.setItem("thorpilot-chat", JSON.stringify(messages));
  drawChat();
}
function setTab() {
  document
    .querySelectorAll("[data-tab]")
    .forEach((b) => b.setAttribute("aria-pressed", b.dataset.tab === tab));
  draw();
}
async function refresh() {
  try {
    const s = await fetch("/api/status").then((r) => r.json());
    mode = s.mode;
    $("#connection").textContent =
      mode === "demo"
        ? "Demo · no device paired"
        : "ROMarr connected · no device paired";
    const response = await fetch("/api/requests");
    const d = await response.json();
    if (!response.ok) throw Error(d.error);
    requests = d.items;
    draw();
    $("#last-check").textContent =
      (d.demo ? "Example data · " : "Updated ") +
      new Date().toLocaleTimeString();
  } catch {
    $("#last-check").textContent =
      "Service unavailable · showing last known state";
  }
}
$("#composer").onsubmit = (e) => {
  e.preventDefault();
  ask($("#message").value);
  $("#message").value = "";
  sessionStorage.removeItem("thorpilot-draft");
};
$("#message").value = sessionStorage.getItem("thorpilot-draft") || "";
$("#message").oninput = (e) =>
  sessionStorage.setItem("thorpilot-draft", e.target.value);
$("#message").onkeydown = (e) => {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    $("#composer").requestSubmit();
  }
};
$("#new-chat").onclick = () => {
  messages = [];
  sessionStorage.removeItem("thorpilot-chat");
  drawChat();
};
document
  .querySelectorAll("[data-ask]")
  .forEach((b) => (b.onclick = () => ask(b.dataset.ask)));
document.querySelectorAll("[data-tab]").forEach(
  (b) =>
    (b.onclick = () => {
      tab = b.dataset.tab;
      setTab();
    }),
);
document.querySelectorAll("button[data-view]").forEach(
  (b) =>
    (b.onclick = () => {
      $(".workspace").dataset.view = b.dataset.view;
      document
        .querySelectorAll("button[data-view]")
        .forEach((x) => x.setAttribute("aria-pressed", x === b));
    }),
);
$("#theme").onclick = () => {
  document.body.classList.toggle("night");
  $("#theme").textContent = document.body.classList.contains("night")
    ? "Daylight"
    : "Nightfall";
};
$("#refresh").onclick = refresh;
drawChat();
refresh();
setInterval(refresh, 15000);
