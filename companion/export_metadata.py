#!/usr/bin/env python3
"""Read-only RomM metadata bridge. Credentials stay in the operator environment."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile
from urllib.parse import urlencode, urlsplit
from urllib.request import Request, build_opener, HTTPRedirectHandler

from gateway import ALIASES, Library, fingerprint, fallback_metadata, METADATA_MAX_BYTES, METADATA_MAX_ITEMS

MAX_RESPONSE = 16 * 1024 * 1024
MAX_FETCH_BYTES = 32 * 1024 * 1024
MAX_FETCH_ROWS = 50000


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError("RomM redirects are not allowed")


def fetch_rows(base_url, token, page_size=100, max_pages=100, opener=None):
    """Bounded, authenticated read-only listing. Incomplete exports fail closed."""
    parsed = urlsplit(base_url)
    if (parsed.scheme != 'https' or not parsed.hostname or parsed.username
            or parsed.password or parsed.query or parsed.fragment):
        raise ValueError('RomM URL must be credential-free HTTPS')
    if not token or not 1 <= page_size <= 500 or not 1 <= max_pages <= 1000:
        raise ValueError('Invalid token or pagination bounds')
    opener = opener or build_opener(NoRedirect())
    rows, seen = [], set()
    total_bytes = 0
    for page in range(max_pages):
        query = urlencode({'limit': page_size, 'offset': page * page_size,
                           'order_by': 'id', 'with_char_index': 'false', 'with_filter_values': 'false'})
        req = Request(base_url.rstrip('/') + '/api/roms?' + query,
                      headers={'Authorization': 'Bearer ' + token, 'Accept': 'application/json'})
        with opener.open(req, timeout=20) as response:
            raw = response.read(MAX_RESPONSE + 1)
        if len(raw) > MAX_RESPONSE:
            raise ValueError('RomM response exceeds size limit')
        total_bytes += len(raw)
        if total_bytes > MAX_FETCH_BYTES:
            raise ValueError('RomM aggregate response exceeds size limit')
        payload = json.loads(raw)
        items = payload.get('items') if isinstance(payload, dict) else payload
        if not isinstance(items, list) or len(items) > page_size:
            raise ValueError('Invalid RomM listing')
        signature = hashlib.sha256(raw).digest()
        if items and signature in seen:
            raise ValueError('RomM pagination did not advance')
        seen.add(signature)
        if any(not isinstance(item, dict) for item in items):
            raise ValueError('Invalid RomM row')
        if len(rows) + len(items) > MAX_FETCH_ROWS:
            raise ValueError('RomM row limit reached')
        rows.extend(items)
        total = payload.get('total') if isinstance(payload, dict) else None
        if isinstance(total, int) and not isinstance(total, bool) and total >= 0:
            if len(rows) >= total:
                return rows
        if len(items) < page_size:
            if isinstance(total, int) and len(rows) < total:
                raise ValueError('RomM listing ended before its reported total')
            return rows
    raise ValueError('RomM page limit reached; previous export retained')


def platform(row):
    value = str(row.get('platform_slug') or '')
    return ALIASES.get(value, value)


def candidate(row):
    title = row.get('name')
    if not isinstance(title, str) or not title.strip() or len(title) > 256 or any(ord(c) < 32 or 127 <= ord(c) <= 159 for c in title):
        return None
    ids = {}
    for provider in ('igdb', 'screenscraper', 'launchbox'):
        value = row.get(provider + '_id')
        if isinstance(value, int) and not isinstance(value, bool) and 0 < value <= 2147483647:
            ids[provider] = value
        elif isinstance(value, str) and re.fullmatch(r'[1-9][0-9]{0,17}', value):

            if int(value) <= 2147483647:
                ids[provider] = int(value)
    result = {'canonical_title': title.strip(), 'provider_ids': ids}
    cover = row.get('url_cover') or ''
    if isinstance(cover, str):
        if cover.startswith('//'):
            cover = 'https:' + cover
        parsed = urlsplit(cover)
        if (len(cover) <= 2048 and parsed.scheme == 'https' and parsed.netloc == 'images.igdb.com'
                and parsed.path.startswith('/igdb/image/upload/')
                and not parsed.query and not parsed.fragment and not any(c.isspace() for c in cover)):
            result['cover_url'] = cover
    regions = row.get('regions')
    if isinstance(regions, list) and regions and all(isinstance(v, str) for v in regions):
        region = ', '.join(dict.fromkeys(v.strip() for v in regions if v.strip()))
        if region and len(region) <= 64 and not any(ord(c) < 32 or 127 <= ord(c) <= 159 for c in region):
            result['region'] = region
    revision = row.get('revision')
    if (isinstance(revision, str) and revision.strip() and len(revision) <= 64
            and not any(ord(c) < 32 or 127 <= ord(c) <= 159 for c in revision)):
        result['revision'] = revision.strip()
    return result


def build_index(root, rows):
    library = Library(root)
    output = []
    for item in library.scan():
        identity = item['id']
        try:
            _, stream = library.open_entry(identity)
            with stream:
                before = fingerprint(os.fstat(stream.fileno()))
                sha1, sha256 = hashlib.sha1(), hashlib.sha256()
                for block in iter(lambda: stream.read(1024 * 1024), b''):
                    sha1.update(block)
                    sha256.update(block)
                if (fingerprint(os.fstat(stream.fileno())) != before
                        or sha256.hexdigest() != item['sha256']):
                    continue
        except OSError:
            continue
        exact, uncertain = [], []
        for row in rows:
            if platform(row) != item['platform'] or row.get('missing_from_fs'):
                continue
            value = candidate(row)
            if not value:
                continue
            row_sha256 = str(row.get('sha256_hash') or '').lower()
            row_sha1 = str(row.get('sha1_hash') or '').lower()
            hash_matches = (row_sha256 == sha256.hexdigest() or
                            (not row_sha256 and row_sha1 == sha1.hexdigest()))
            if hash_matches:
                if value not in exact:
                    exact.append(value)
            elif row.get('fs_name') == item['file_name']:
                if value not in uncertain:
                    uncertain.append(value)
        result = fallback_metadata(item['platform'], item['sha256'], Path(item['file_name']).stem)
        if len(exact) == 1 and exact[0]['provider_ids']:
            result.update(exact[0], match_status='matched')
        elif exact or uncertain:
            result.update(match_status='needs_review', candidates=(exact or uncertain)[:8])
        output.append(result)
    unique = {(item['platform'], item['sha256']): item for item in output}
    result = {'version': 1, 'items': list(unique.values())}
    if len(unique) > METADATA_MAX_ITEMS or len(json.dumps(result).encode()) > METADATA_MAX_BYTES:
        raise ValueError('Metadata index exceeds gateway bounds')
    return result


def write_atomic(path, body):
    path = Path(path)
    fd, temporary = tempfile.mkstemp(prefix='.' + path.name, dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as stream:
            json.dump(body, stream, ensure_ascii=False, separators=(',', ':'))
            stream.write('\n')
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', required=True)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument('--romm-url')
    source.add_argument('--romm-json', help='Previously exported RomM listing (items array)')
    parser.add_argument('--output', required=True)
    parser.add_argument('--max-pages', type=int, default=100)
    args = parser.parse_args()
    try:
        if args.romm_json:
            with Path(args.romm_json).open("rb") as source:
                raw = source.read(MAX_RESPONSE + 1)
            if len(raw) > MAX_RESPONSE:
                raise ValueError('Input exceeds size limit')
            payload = json.loads(raw)
            rows = payload.get('items') if isinstance(payload, dict) else payload
            if not isinstance(rows, list) or any(not isinstance(row, dict) for row in rows):
                raise ValueError('Invalid RomM rows')
        else:
            rows = fetch_rows(args.romm_url, os.environ.get('THORPILOT_ROMM_TOKEN', ''),
                              max_pages=args.max_pages)
        index = build_index(args.root, rows)
        write_atomic(args.output, index)
    except Exception as exc:
        # Never print upstream bodies, URLs or exception strings containing credentials.
        parser.exit(1, 'Metadata export failed (' + type(exc).__name__ + '); existing output retained.\n')
    print('Metadata export complete:', len(index['items']), 'files')


if __name__ == '__main__':
    main()
