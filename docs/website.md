# Public website

The static project website lives in `site/`, with documentation generated from an explicit list of `docs/` pages by `tools/build-site.mjs`. It is separate from the local app prototype and has no server credentials, request access, or live chat connection.

## Build and preview

```sh
npm ci
npm run build:site
python3 -m http.server 8793 --bind 127.0.0.1 --directory artifacts/site
```

The interactive handheld on the landing page is a scripted concept. Its game title and progress are fictional. Available and planned capabilities are labelled separately. No screenshots containing private requests, connection settings, or device identifiers are published.

## Design

Night `#080b14`, moonlight `#f0f4ff`, mint `#8ef4cf`, violet `#b3a1ff`, coral `#ffb899`, and muted blue `#9daac2` mirror the native companion. Manrope carries the rounded display lettering; DM Sans handles reading. The defining element is an interactive, original CSS dual-screen handheld. Everything around it stays comparatively quiet, with open sections rather than a grid of dashboard cards.

Desktop places the promise beside the handheld; mobile stacks them without hiding the interactive preview. The landing page does not imply official AYN or Cocoon affiliation. Keyboard focus, reduced motion, responsive layouts, and accurate feature claims are part of visual verification.

## Hosting

The production site is served as static files on a dedicated unprivileged Atlas container through a separate Cloudflare Tunnel. Only the static site is exposed. Tunnel tokens stay outside this repository. Builds and link validation run on the project's Atlas self-hosted CI runner.

A restricted service on the site container checks protected `main` every five minutes, builds the site with Node 22, validates internal links, and switches to a commit-addressed release directory. It uses public read-only Git access, so CI has no deployment credential or access to the site container. Main requires PRs and passing checks. The publisher runs as a dedicated non-root user, can write only its site/build directory, and cannot read the tunnel token. Previous releases remain available for rollback. Initial infrastructure provisioning is separate from this update loop.

Cloudflare references: [tunnel setup](https://developers.cloudflare.com/tunnel/get-started/), [tunnel token handling](https://developers.cloudflare.com/tunnel/reference/tunnel-tokens/).
