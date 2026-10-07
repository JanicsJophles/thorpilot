# Optional library download gateway

A small Python 3.10+ standard-library server for transferring games **already in
your library** to Thorpilot. It neither finds releases nor downloads games from
providers. ROMarr and RomM are optional, independent services. Configure the root
to the directory containing ES-DE platform folders, such as `switch/` and `nds/`.

## Run

Provide a randomly generated token of at least 32 characters through your service
manager's protected environment file (`THORPILOT_DOWNLOAD_TOKEN`). Do not commit
that file or put tokens in URLs. Example command with a placeholder library path:

```sh
python3 companion/gateway.py --root /srv/library/roms
```

Default listener: `127.0.0.1:8792`. `THORPILOT_LIBRARY_ROOT` can replace `--root`.
Run as an unprivileged account with read-only filesystem access. The gateway
never writes, renames or deletes library content. Keep the listener private and
put an authenticated **HTTPS reverse proxy** in front of it; the Android client
requires HTTPS. Do not forward this plain HTTP port to the public Internet.
An explicitly configured LAN HTTPS endpoint can use this same service, token and
manifest, without sending local game bytes through a remote proxy. Local TLS must
still have a valid certificate; do not disable certificate checking.

Only top-level, self-contained supported files are listed. Archives, saves,
hidden files, symlinks, empty files, partial extensions and unrecognized formats
are excluded. Nintendo 3DS accepts `.cia` only. Disc descriptors, playlists and
multi-file sets are omitted until a bundle protocol can preserve their required
companions. A listed format is not an emulator-compatibility guarantee.

## API contract

Every request requires `Authorization: Bearer <token>`. Tokens are never accepted
in query strings. There are no CORS headers, mutation endpoints or redirects.

`GET /api/downloads` (also `HEAD`):

```json
{
  "items": [{
    "id": "<64 lowercase hex characters>",
    "title": "Example",
    "platform": "switch",
    "file_name": "Example.nsp",
    "size_bytes": 12345,
    "sha256": "<actual SHA-256 of file bytes>"
  }],
  "total": 1
}
```

`platform` is a canonical destination folder (for example `n3ds`, `nds`, `psx`,
`psp`, `gamecube`). Titles are filename stems, not online metadata matches.
IDs depend on the source-relative path and SHA-256. They are stable across server
restarts and change when file bytes change. No absolute filesystem paths appear
in responses. Clients must validate destination filenames themselves as well.

`GET /api/downloads/{id}/content` (also `HEAD`) streams the raw file with:

- `Content-Length`, `Accept-Ranges: bytes`, and `ETag: "<sha256>"`.
- One byte range per request (`bytes=offset-`, bounded or suffix). Successful
  ranges return `206` and `Content-Range`; invalid ranges return `416`.
- `If-Match` supports a matching quoted SHA-256 or `*`; mismatch returns `412`.
- `If-Range` must match the quoted SHA-256. Otherwise the whole file returns
  `200`. **A resuming client must never append a `200` response to partial data.**
- A missing or changed file returns `404`; refresh the manifest before retrying.

Use an app-owned temporary file for transfer, verify its complete SHA-256 and
length, then publish it to the selected folder without replacing existing files.
The final checksum is essential: a source modified in place during streaming can
produce mixed bytes even after the gateway's initial identity check. Resume only
against the same expected size/hash and validate `Content-Range` before appending.

The gateway hashes real file bytes on startup and when identities change. Its
in-memory cache includes device, inode, size, nanosecond modification time **and
change time**, preventing stale reuse when size/mtime alone are unchanged.
Catalog reads rescan the folder; initial hashing of a large collection can take
time, and startup warms the cache before accepting clients. A restart rehashes
the library. Source content should be finalized by atomic rename, not edited in
place while being served. This gateway is intended for a personal library,
not an untrusted multi-tenant upload service.

## Verify

```sh
python3 -m unittest discover -s companion -p 'test_*.py' -v
```

Tests exercise authenticated HTTP, actual byte checksums, range resume, stale
identity handling, forbidden formats, path traversal and symlink substitution.
