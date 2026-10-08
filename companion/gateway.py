#!/usr/bin/env python3
"""Optional read-only, authenticated Thorpilot library gateway (Python 3.10+)."""
import argparse
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import stat
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import quote, urlsplit


# Only self-contained files: cue/bin, playlists and multi-file disc sets need a
# future bundle protocol, not a deceptively successful partial installation.
FORMATS = {
    "n3ds": {"cia"}, "nds": {"nds"}, "psx": {"chd", "pbp", "iso"},
    "psp": {"iso", "cso", "pbp"}, "gba": {"gba"}, "gb": {"gb"},
    "gbc": {"gbc"}, "nes": {"nes"}, "snes": {"sfc", "smc"},
    "n64": {"n64", "z64", "v64"}, "gamecube": {"iso", "gcm", "rvz"},
    "wii": {"iso", "rvz", "wbfs"}, "switch": {"nsp", "xci"},
    "ps2": {"iso", "chd", "cso"}, "dreamcast": {"chd", "cdi"},
    "genesis": {"md", "gen", "bin", "smd"}, "saturn": {"chd", "iso"},
}
ALIASES = {"3ds": "n3ds", "ds": "nds", "ps1": "psx", "gc": "gamecube", "megadrive": "genesis"}
RESERVED = {"CON", "PRN", "AUX", "NUL"} | {f"{prefix}{n}" for prefix in ("COM", "LPT") for n in range(1, 10)}


def fingerprint(s):
    return (s.st_dev, s.st_ino, s.st_size, s.st_mtime_ns, s.st_ctime_ns)


def safe_name(name):
    return (bool(name) and not name.startswith(".") and not name.endswith((".", " "))
            and name.split(".")[0].upper() not in RESERVED
            and not any(ord(c) < 32 or 127 <= ord(c) <= 159 or c in '/\\:*?"<>|' for c in name))


METADATA_MAX_BYTES = 4 * 1024 * 1024
METADATA_MAX_ITEMS = 10000
PROVIDERS = {"igdb", "screenscraper", "launchbox"}


def metadata_text(value, limit):
    if not isinstance(value, str) or not value.strip() or len(value) > limit:
        raise ValueError("Invalid metadata text")
    if any(ord(c) < 32 or 127 <= ord(c) <= 159 for c in value):
        raise ValueError("Invalid metadata text")
    return value.strip()


def metadata_candidate(raw):
    if not isinstance(raw, dict):
        raise ValueError("Invalid candidate")
    result = {"canonical_title": metadata_text(raw.get("canonical_title"), 256)}
    ids = raw.get("provider_ids", {})
    if not isinstance(ids, dict):
        raise ValueError("Invalid provider IDs")
    result["provider_ids"] = {}
    for provider in PROVIDERS & ids.keys():
        value = ids[provider]
        if type(value) is not int or not 0 < value <= 2147483647:
            raise ValueError("Invalid provider ID")
        result["provider_ids"][provider] = value
    if "cover_url" in raw:
        cover = metadata_text(raw["cover_url"], 2048)
        url = urlsplit(cover)
        if (url.scheme != "https" or url.netloc != "images.igdb.com"
                or not url.path.startswith("/igdb/image/upload/")
                or url.query or url.fragment or any(c.isspace() for c in cover)):
            raise ValueError("Invalid cover URL")
        result["cover_url"] = cover
    return result


def read_metadata_index(path):
    """Optional, bounded metadata. Invalid input never prevents file transfers."""
    if path is None:
        return {}
    try:
        with open(path, "rb") as stream:
            payload = stream.read(METADATA_MAX_BYTES + 1)
        if len(payload) > METADATA_MAX_BYTES:
            return {}
        document = json.loads(payload)
        if (not isinstance(document, dict) or type(document.get("version")) is not int
                or document["version"] != 1 or not isinstance(document.get("items"), list)
                or len(document["items"]) > METADATA_MAX_ITEMS):
            return {}
        index, duplicates = {}, set()
        for raw in document["items"]:
            try:
                if not isinstance(raw, dict) or type(raw.get("version")) is not int or raw["version"] != 1:
                    continue
                platform, digest = raw.get("platform"), raw.get("sha256")
                if not isinstance(platform, str) or platform not in FORMATS:
                    continue
                if not isinstance(digest, str) or not re.fullmatch(r"[a-f0-9]{64}", digest):
                    continue
                key = (platform, digest)
                if key in index or key in duplicates:
                    index.pop(key, None)
                    duplicates.add(key)
                    continue
                record = metadata_candidate(raw)
                status = raw.get("match_status")
                if status not in ("matched", "needs_review", "unmatched"):
                    continue
                if status == "matched" and not record["provider_ids"]:
                    continue
                candidates = raw.get("candidates", [])
                if not isinstance(candidates, list) or len(candidates) > 8:
                    continue
                record.update(version=1, platform=platform, sha256=digest,
                              match_status=status, candidates=[metadata_candidate(c) for c in candidates])
                for field in ("region", "revision"):
                    if field in raw:
                        record[field] = metadata_text(raw[field], 64)
                index[key] = record
            except (ValueError, TypeError):
                continue
        return index
    except (OSError, ValueError, TypeError, RecursionError):
        return {}


def fallback_metadata(platform, digest, stem):
    title = stem
    if platform == "n3ds" and re.match(r"^00040000[0-9a-fA-F]{8} +\S", title):
        title = re.sub(r"^00040000[0-9a-fA-F]{8} +", "", title)
        title = re.sub(r"\.(?:piratelegit|legit)$", "", title, flags=re.I)
        # Known packaging tags only; never infer a provider identity from a name.
        title = re.sub(r"(?: +\((?:CTR-[^()]+|v[0-9.]+|W|USA|Europe|Japan|World)\))+$", "", title)
    return {"version": 1, "platform": platform, "sha256": digest,
            "canonical_title": title[:256], "match_status": "unmatched",
            "provider_ids": {}, "candidates": []}


class Library:
    def __init__(self, root, metadata_file=None):
        self.root = Path(root).resolve(strict=True)
        if not self.root.is_dir():
            raise ValueError("Library root must be a directory")
        self.lock = threading.RLock()
        self.cache = {}
        self.entries = {}
        self.metadata_file = metadata_file

    def open_file(self, relative):
        """Traverse beneath an open root; never follow symlinks, even after scan."""
        parts = relative.split("/")
        if len(parts) != 2 or any(not safe_name(p) for p in parts):
            raise FileNotFoundError()
        directory = os.open(self.root, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
        try:
            child = os.open(parts[0], os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=directory)
            try:
                fd = os.open(parts[1], os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=child)
            finally:
                os.close(child)
        finally:
            os.close(directory)
        stream = os.fdopen(fd, "rb")
        if not stat.S_ISREG(os.fstat(stream.fileno()).st_mode):
            stream.close()
            raise FileNotFoundError()
        return stream

    def scan(self):
        with self.lock:
            entries, active_cache = {}, {}
            metadata = read_metadata_index(self.metadata_file)
            try:
                directories = sorted(self.root.iterdir())
            except OSError:
                directories = []
            for directory in directories:
                platform = ALIASES.get(directory.name, directory.name)
                if platform not in FORMATS or directory.is_symlink() or not directory.is_dir():
                    continue
                try:
                    paths = sorted(directory.iterdir())
                except OSError:
                    continue
                for path in paths:
                    if not safe_name(path.name) or path.suffix.lower().lstrip(".") not in FORMATS[platform]:
                        continue
                    relative = directory.name + "/" + path.name
                    try:
                        with self.open_file(relative) as stream:
                            before = fingerprint(os.fstat(stream.fileno()))
                            if before[2] <= 0:
                                continue
                            cached = self.cache.get(relative)
                            if cached and cached[0] == before:
                                digest = cached[1]
                            else:
                                checksum = hashlib.sha256()
                                for block in iter(lambda: stream.read(1024 * 1024), b""):
                                    checksum.update(block)
                                if fingerprint(os.fstat(stream.fileno())) != before:
                                    continue
                                digest = checksum.hexdigest()
                            active_cache[relative] = (before, digest)
                            identity = hashlib.sha256((relative + "\0" + digest).encode()).hexdigest()
                            item = dict(id=identity, title=path.stem, platform=platform,
                                        file_name=path.name, size_bytes=before[2], sha256=digest)
                            item["metadata"] = metadata.get((platform, digest), fallback_metadata(platform, digest, path.stem))
                            entries[identity] = (item, relative, before)
                    except OSError:
                        continue
            self.entries, self.cache = entries, active_cache
            return [entry[0] for entry in entries.values()]

    def open_entry(self, identity):
        with self.lock:
            entry = self.entries.get(identity)
            if entry is None:
                raise FileNotFoundError()
            item, relative, expected = entry
            stream = self.open_file(relative)
            if fingerprint(os.fstat(stream.fileno())) != expected:
                stream.close()
                raise FileNotFoundError()
            return item, stream


def byte_range(value, size):
    if not value:
        return 0, size - 1
    match = re.fullmatch(r"bytes=(\d*)-(\d*)", value)
    if not match or not any(match.groups()):
        raise ValueError()
    first, last = match.groups()
    if not first:
        length = int(last)
        if length <= 0:
            raise ValueError()
        return max(0, size - length), size - 1
    start, end = int(first), min(int(last), size - 1) if last else size - 1
    if start >= size or end < start:
        raise ValueError()
    return start, end


def make_handler(library, token):
    expected_auth = ("Bearer " + token).encode()

    class Handler(BaseHTTPRequestHandler):
        # HTTP/1.0 deliberately closes connections; no unread request body reuse.
        server_version = "ThorpilotGateway/1"

        def setup(self):
            super().setup()
            self.connection.settimeout(30)

        def log_message(self, format, *args):
            pass  # Never log auth, paths, query strings or library titles.

        def respond(self, code, data=None, **headers):
            body = json.dumps(data).encode() if data is not None else b""
            self.send_response(code)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Content-Type", "application/json")
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            for key, value in headers.items():
                self.send_header(key, value)
            self.end_headers()
            if self.command != "HEAD":
                self.wfile.write(body)

        def do_HEAD(self):
            self.do_GET()

        def do_GET(self):
            supplied = self.headers.get("Authorization", "").encode()
            if not hmac.compare_digest(supplied, expected_auth):
                return self.respond(401, {"error": "Authentication required"}, **{"WWW-Authenticate": "Bearer"})
            url = urlsplit(self.path)
            if url.query or url.fragment:
                return self.respond(400, {"error": "Unexpected query"})
            if url.path == "/api/downloads":
                items = library.scan()
                return self.respond(200, {"items": items, "total": len(items)})
            match = re.fullmatch(r"/api/downloads/([a-f0-9]{64})/content", url.path)
            if not match:
                return self.respond(404, {"error": "Not found"})
            try:
                item, stream = library.open_entry(match[1])
            except OSError:
                return self.respond(404, {"error": "File changed or unavailable; refresh library"})
            with stream:
                etag = '"' + item["sha256"] + '"'
                if self.headers.get("If-Match") not in (None, "*", etag):
                    return self.respond(412, {"error": "File identity changed"})
                size = item["size_bytes"]
                requested = self.headers.get("Range")
                if self.headers.get("If-Range") not in (None, etag):
                    requested = None
                try:
                    start, end = byte_range(requested, size)
                except ValueError:
                    return self.respond(416, {"error": "Range not satisfiable"}, **{"Content-Range": f"bytes */{size}"})
                self.send_response(206 if requested else 200)
                self.send_header("Content-Type", "application/octet-stream")
                self.send_header("Content-Length", str(end - start + 1))
                self.send_header("Content-Disposition", "attachment; filename*=UTF-8''" + quote(item["file_name"], safe=""))
                self.send_header("Accept-Ranges", "bytes")
                self.send_header("ETag", etag)
                self.send_header("Cache-Control", "private, no-store")
                self.send_header("X-Content-Type-Options", "nosniff")
                if requested:
                    self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
                self.end_headers()
                if self.command == "HEAD":
                    return
                stream.seek(start)
                remaining = end - start + 1
                try:
                    while remaining:
                        block = stream.read(min(1024 * 1024, remaining))
                        if not block:
                            break
                        self.wfile.write(block)
                        remaining -= len(block)
                except (BrokenPipeError, ConnectionResetError, TimeoutError):
                    pass

    return Handler


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=os.environ.get("THORPILOT_LIBRARY_ROOT"), required=not os.environ.get("THORPILOT_LIBRARY_ROOT"))
    parser.add_argument("--bind", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8792)
    parser.add_argument("--metadata-file", help="Optional version 1 metadata index; reloaded on catalog reads")
    args = parser.parse_args()
    token = os.environ.get("THORPILOT_DOWNLOAD_TOKEN", "")
    if len(token) < 32 or not token.isascii() or any(c.isspace() for c in token):
        parser.error("THORPILOT_DOWNLOAD_TOKEN must contain at least 32 non-whitespace ASCII characters")
    library = Library(args.root, args.metadata_file)
    library.scan()  # Warm manifest before accepting clients.
    server = ThreadingHTTPServer((args.bind, args.port), make_handler(library, token))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
