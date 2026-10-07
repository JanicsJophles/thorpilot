const scenarios = {
  discover: `<div class="preview-eyebrow">A mood, not a search box.</div><h2>What feels like<br>your kind of game?</h2><div class="chat-bubble">Something cozy. Just twenty minutes.</div><div class="preview-reply"><span>✦</span><p>A small adventure, at your pace.<br><strong>Let’s find your next favorite.</strong></p></div>`,
  requests: `<div class="preview-eyebrow">Know where things stand.</div><h2>From your library<br>to your next session.</h2><div class="demo-request"><span class="demo-cover">☾</span><div><strong>Moonlight Garden</strong><small>Fictional demo game</small><div class="progress"><i></i></div></div><span>68%</span></div><div class="preview-reply"><span>↓</span><p>Downloading is one step.<br><strong>On your device is another.</strong></p></div>`,
  care: `<div class="preview-eyebrow">A future we’re exploring.</div><h2>Make a change.<br>Keep a way back.</h2><div class="care-steps"><span>Understand</span><b>→</b><span>Preview</span><b>→</b><span>Measure</span></div><div class="preview-reply"><span>⌘</span><p>Emulator-aware suggestions.<br><strong>Your say. A backup. A clear result.</strong></p></div>`
};
const content = document.querySelector('#preview-content');
document.querySelectorAll('[data-mode]').forEach(button => button.addEventListener('click', () => {
  document.querySelectorAll('[data-mode]').forEach(item => item.setAttribute('aria-pressed', String(item === button)));
  content.innerHTML = scenarios[button.dataset.mode];
}));
if (content) content.innerHTML = scenarios.discover;
