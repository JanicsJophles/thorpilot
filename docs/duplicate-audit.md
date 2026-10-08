# Audit duplicate games

The optional read-only audit finds repeated files and possible regional or revision
variants before you decide what to keep. It does not delete, rename, transfer or
choose a preferred release. Saves and installed emulator data are outside its scope.

Run on the machine that can read the ROM library folders:

```sh
python3 companion/audit_duplicates.py \
  --root /srv/library/roms \
  --metadata-file /srv/thorpilot/metadata.json
```

Repeat `--root` to compare mounted storage locations. Repeated or symlink-alias
roots are counted once after path resolution. Missing roots and roots that are
files fail with exit status 1 and no report. The output is JSON on stdout;
redirect it to a separate report file if needed. It includes local root paths, so
review it before sharing. It needs no API keys or network access. The metadata file
is the optional index described in [the metadata bridge](metadata-bridge.md).

## Read the evidence

- `exact_copies`: two or more eligible files with the same normalized platform and
  SHA-256. These are byte-identical copies, including intentional internal/SD copies.
- `possible_variants`: files with different SHA-256 values on the same platform
  sharing a provider's title ID in validated, matched metadata. Filenames, titles,
  regions and revisions are included when available. This is evidence to review,
  not proof that either release is redundant; updates, editions or wrongly scraped
  metadata may share an identity.

Equal names never cause a match. Unmatched or uncertain metadata and review
candidates never cause variant grouping. Provider namespaces remain separate, and
matches are not joined transitively. A group can appear once per agreeing provider;
its exact file copies can also appear in `exact_copies`.

## Limits

The audit uses the gateway's supported self-contained formats, platform aliases,
metadata validation, safe path traversal and stable-file hashing. It scans only
files directly within platform folders. Archives, symlinks, hidden files, saves,
unsupported formats and unreadable/changing files are skipped. Counts describe the
eligible files actually read, not the entire directory tree. A first audit reads
all eligible ROM bytes and can take time on a large library.

Missing or invalid optional metadata leaves exact-copy detection available but
can hide variant matches. Check `files_with_matched_metadata` before interpreting
an empty variants list. This is a snapshot, not a lock on the library; verify the
current files and save locations before any separate cleanup. No absence of
findings guarantees the library has no duplicates.

## Test

```sh
python3 -m unittest discover -s companion -p 'test_audit_duplicates.py' -v
```
