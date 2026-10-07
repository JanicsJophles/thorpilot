import { createHash } from 'node:crypto';
import { mkdir, readFile, readdir, copyFile, writeFile, rm, rename } from 'node:fs/promises';
import { resolve } from 'node:path';
import { marked } from 'marked';

const root = resolve(import.meta.dirname, '..');
const out = resolve(root, 'artifacts/site');
const pages = [
  ['service-integrations', 'Connect your services', 'Brain, personal assistants, libraries, and scoped capabilities.'],
  ['copilot-vision', 'The copilot vision', 'Useful help, natural handoffs, and room for play.'],
  ['integration-feasibility', 'Integration evidence', 'What Cocoon, Android, and emulators actually support.'],
  ['cocoon-widget', 'Cocoon widget', 'A lightweight launcher entry point and its validation status.'],
  ['copilot-adrs', 'Architecture decisions', 'Bounded tools, permissions, reversibility, and context.'],
  ['copilot-delivery', 'Build and test plan', 'Milestones with concrete physical-device acceptance checks.'],
  ['device-testing', 'Test on your handheld', 'Build, install, capture, and verify the native app.'],
  ['chat-adapter', 'Connect game discovery', 'The optional server-backed chat contract.'],
  ['architecture', 'Architecture overview', 'Current components and the path forward.'],
  ['roadmap', 'Roadmap', 'Shipped features and work still ahead.'],
  ['design', 'Design direction', 'A calm, colorful companion for two screens.'],
  ['website', 'Website development', 'Build the public site and understand its deployment.'],
];
const escape = value => value.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
const nav = pages.map(([slug,title])=>`<a href="/docs/${slug}.html">${escape(title)}</a>`).join('');
function shell(title, content) {
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${escape(title)} · Thorpilot</title><meta name="theme-color" content="#080b14"><link rel="icon" href="/mark.svg"><link rel="stylesheet" href="/style.css"></head><body><a class="skip" href="#main">Skip to content</a><header class="site-header"><a class="brand" href="/"><img src="/mark.svg" width="32" height="32" alt="">Thorpilot</a><nav aria-label="Main navigation"><a href="/">The experience</a><a href="/docs/">Docs</a><a class="source-link" href="https://github.com/JanicsJophles/thorpilot">GitHub ↗</a></nav></header><div class="docs-shell"><nav class="docs-nav" aria-label="Documentation">${nav}</nav><main class="docs-article" id="main">${content}</main></div><footer class="site-footer"><a class="brand" href="/">✦ Thorpilot</a><p>Independent. Open source. Still taking shape.</p></footer></body></html>`;
}
await rm(out, {recursive:true,force:true});
await mkdir(resolve(out,'docs'),{recursive:true});
for(const file of await readdir(resolve(root,'site'))) await copyFile(resolve(root,'site',file),resolve(out,file));
const slugs = new Set(pages.map(([slug])=>slug));
for(const [slug,title] of pages) {
  let source = await readFile(resolve(root,'docs',`${slug}.md`),'utf8');
  source = source.replace(/\]\(([^):]+)\.md(#[^)]*)?\)/g, (all,path,fragment='') => {
    const name = path.split('/').at(-1);
    return slugs.has(name) ? `](/docs/${name}.html${fragment})` : `](https://github.com/JanicsJophles/thorpilot/blob/main/${path.startsWith('../')?path.slice(3):'docs/'+path}.md${fragment})`;
  });
  await writeFile(resolve(out,'docs',`${slug}.html`),shell(title,marked.parse(source)));
}
await writeFile(resolve(out,'docs/index.html'),shell('Documentation',`<span class="section-kicker">Build with a clear picture</span><h1>The Thorpilot field guide.</h1><p>Start with the vision, check the evidence, then pick a useful milestone. These documents distinguish working features from proposed integrations.</p><div class="docs-index-list">${pages.map(([slug,title,description])=>`<a href="/docs/${slug}.html">${escape(title)}<span>${escape(description)}</span></a>`).join('')}</div>`));
await writeFile(resolve(out,'robots.txt'),'User-agent: *\nAllow: /\nSitemap: https://thorpilot.rackmind.ai/sitemap.xml\n');
const urls = ['/','/docs/',...pages.map(([slug])=>`/docs/${slug}.html`)];
await writeFile(resolve(out,'sitemap.xml'),`<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${urls.map(path=>`<url><loc>https://thorpilot.rackmind.ai${path}</loc></url>`).join('')}</urlset>`);
console.log(`Built ${urls.length} pages into artifacts/site`);

// Content-addressed assets prevent a cached stylesheet/script from crossing releases.
for (const asset of ['style.css', 'site.js', 'mark.svg']) {
  const digest = createHash('sha256').update(await readFile(resolve(out, asset))).digest('hex').slice(0, 12);
  const dot = asset.lastIndexOf('.');
  const versioned = `${asset.slice(0,dot)}.${digest}${asset.slice(dot)}`;
  await rename(resolve(out, asset), resolve(out, versioned));
  for (const page of ['index.html', 'docs/index.html', ...pages.map(([slug]) => `docs/${slug}.html`)]) {
    const path = resolve(out, page);
    await writeFile(path, (await readFile(path, 'utf8')).replaceAll(`/${asset}`, `/${versioned}`));
  }
}
