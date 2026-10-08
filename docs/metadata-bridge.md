# Optional RomM metadata bridge

The bridge reuses the titles, provider identifiers and artwork already held by
RomM (including libraries managed through ROMarr). It does not scrape again,
request games or edit the source library. Run it on the library server where the
same finalized game files served by the download gateway are available.

Provide a read-only RomM bearer token through a protected service environment
file as `THORPILOT_ROMM_TOKEN`. Never put credentials in a command argument or
commit that environment file. Provider credentials stay in RomM.

```sh
python3 companion/export_metadata.py --root /srv/library/roms \
  --romm-url https://romm.example.com --output /srv/thorpilot/metadata.json
```

For an offline listing, replace `--romm-url` with
`--romm-json /srv/private/romm-listing.json`. That file must contain either the
RomM listing's `items` array or an array of RomM rows. Offline input must represent
a complete export; the tool cannot detect omitted pages in a manually saved file.
The output is the version 1 metadata index consumed by the optional gateway.
Configure the gateway's metadata index option with this output path.
Every atomic export creates an owner-readable/writable file (mode `0600`), even
when replacing an existing output. Run the exporter and gateway under the same
unprivileged service account so the gateway can read each refreshed index; an
initial manual permission change will not survive the next export.

## Identity and uncertainty

The exporter hashes local files with SHA-1 and SHA-256 in the same read, checks
filesystem identity before and after, and compares the resulting SHA-256 to the
gateway catalog. Only supported self-contained files are considered. Symlinks,
saves and archives are excluded by the gateway's existing rules.

An exact platform and RomM `sha256_hash` match identifies a file. On RomM versions
that expose `sha1_hash` instead, an exact SHA-1 match can identify it; the resulting
metadata record is always bound to the locally computed SHA-256. SHA-1 is used as
a compatibility lookup against a trusted personal library, not as evidence that
an adversarial file is authentic. An available but disagreeing SHA-256 cannot be
overridden by SHA-1. Automatic matches also require a provider ID. Hash agreement
proves the file correspondence, **not that RomM's scraped game identity is right**.

Conflicting metadata for an exact file requires review. Matching only the platform
and filename also produces a review candidate, never an automatic match. A file
without usable evidence stays unmatched. Provider identifiers and titles are
allowlisted and bounded. Artwork is restricted to public HTTPS IGDB image URLs;
private RomM asset URLs and credential-bearing URLs are never copied to devices.
Other artwork providers require explicit transport support in a future version.
Bounded region values from RomM `regions` and an explicit `revision`, when present,
are preserved on exact matches; revisions are never guessed from filenames.

The API reader caps responses at 16 MiB per page, 32 MiB across all pages and
50,000 rows, as well as bounding page count and timeouts. It rejects redirects,
and fails if pagination repeats or exceeds its configured bound. It does not
print upstream response bodies or credentials. Fetch failure preserves the prior
index. A successful export replaces the entire index atomically, so stale entries
can disappear. Run periodically after RomM scans, using a service timer if desired;
no background service or schedule is installed automatically by this script.

This index feeds Thorpilot's library preparation UI. It does not modify Cocoon's
private metadata database or export an undocumented frontend format. Existing
Cocoon metadata repairs remain an explicit operation.

## Verification

```sh
python3 -m unittest discover -s companion -p 'test_*.py' -v
```

Exporter fixtures exercise hashes, platform separation, uncertain filename
matches, conflicting metadata, private URL filtering, pagination and atomic writes.
