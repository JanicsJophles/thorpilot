import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from gateway import read_metadata_index
from export_metadata import build_index, candidate, fetch_rows, write_atomic


class ExportTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / '3ds').mkdir()
        self.file = self.root / '3ds' / 'Example.cia'
        self.file.write_bytes(b'actual game bytes')
        self.row = {'platform_slug': '3ds', 'fs_name': 'Example.cia',
                    'name': 'Example Game', 'igdb_id': 42,
                    'sha1_hash': hashlib.sha1(self.file.read_bytes()).hexdigest(),
                    'url_cover': '//images.igdb.com/igdb/image/upload/t_cover_big/test.jpg'}

    def entry(self, rows):
        return build_index(self.root, rows)['items'][0]

    def test_exact_hash_and_platform_bind_to_local_sha256(self):
        result = self.entry([dict(self.row, fs_name='Renamed.cia')])
        self.assertEqual(result['match_status'], 'matched')
        self.assertEqual(result['canonical_title'], 'Example Game')
        self.assertEqual(result['sha256'], hashlib.sha256(self.file.read_bytes()).hexdigest())
        self.assertEqual(result['provider_ids'], {'igdb': 42})
        self.assertTrue(result['cover_url'].startswith('https://images.igdb.com/'))

    def test_filename_is_only_a_candidate_even_when_hash_disagrees(self):
        result = self.entry([dict(self.row, sha1_hash='f' * 40)])
        self.assertEqual(result['match_status'], 'needs_review')
        self.assertEqual(result['canonical_title'], 'Example')
        self.assertNotIn('cover_url', result)
        self.assertEqual(len(result['candidates']), 1)

    def test_wrong_platform_and_absent_files_never_bind(self):
        self.assertEqual(self.entry([dict(self.row, platform_slug='nds')])['match_status'], 'unmatched')
        self.assertEqual(self.entry([dict(self.row, missing_from_fs=True)])['match_status'], 'unmatched')

    def test_conflicting_exact_metadata_requires_review(self):
        result = self.entry([self.row, dict(self.row, name='Different Game', igdb_id=99)])
        self.assertEqual(result['match_status'], 'needs_review')
        self.assertEqual(len(result['candidates']), 2)

    def test_sha256_disagreement_cannot_be_overridden_by_sha1(self):
        self.assertEqual(self.entry([dict(self.row, sha256_hash='f' * 64)])['match_status'], 'needs_review')

    def test_unknown_and_symlinked_files_are_omitted(self):
        (self.root / '3ds' / 'copy.cia').symlink_to(self.file)
        (self.root / '3ds' / 'save.sav').write_bytes(b'save')
        self.assertEqual(len(build_index(self.root, [])['items']), 1)

    def test_secrets_and_private_artwork_urls_never_exported(self):
        for url in ['https://romm.local/assets/cover.png', 'https://images.igdb.com.evil/a',
                    'https://images.igdb.com/igdb/image/upload/a?token=secret']:
            value = candidate(dict(self.row, url_cover=url, api_key='secret'))
            self.assertNotIn('cover_url', value)
            self.assertNotIn('secret', json.dumps(value))

    def test_export_is_accepted_by_gateway_for_matches_and_candidates(self):
        for row in [self.row, dict(self.row, sha1_hash=''), dict(self.row, name='')]:
            path = self.root / 'metadata.json'
            write_atomic(path, build_index(self.root, [row]))
            self.assertEqual(len(read_metadata_index(path)), 1)

    def test_region_revision_are_bounded_source_metadata_not_filename_guesses(self):
        row = dict(self.row, regions=['USA', 'Europe', 'USA'], revision='Rev 1')
        result = self.entry([row])
        self.assertEqual(result['region'], 'USA, Europe')
        self.assertEqual(result['revision'], 'Rev 1')
        result = self.entry([dict(self.row, regions=['x' * 65], revision='bad\nvalue')])
        self.assertNotIn('region', result)
        self.assertNotIn('revision', result)
        path = self.root / 'metadata.json'
        write_atomic(path, build_index(self.root, [row]))
        self.assertEqual(next(iter(read_metadata_index(path).values()))['revision'], 'Rev 1')

    def test_atomic_write(self):
        target = self.root / 'index.json'
        write_atomic(target, {'version': 1, 'items': []})
        self.assertEqual(json.loads(target.read_text())['version'], 1)
        self.assertEqual(target.stat().st_mode & 0o777, 0o600)

    def test_aggregate_limits_apply_across_individually_valid_pages(self):
        class Opener:
            def __init__(self):
                self.count = 0
            def open(self, req, timeout):
                self.count += 1
                return io.BytesIO(json.dumps({'items': [{'id': self.count}], 'total': 3}).encode())
        with patch('export_metadata.MAX_FETCH_BYTES', 60):
            opener = Opener()
            with self.assertRaisesRegex(ValueError, 'aggregate response'):
                fetch_rows('https://romm.example', 'private', 1, 3, opener)
            self.assertEqual(opener.count, 2)
        with patch('export_metadata.MAX_FETCH_ROWS', 1):
            with self.assertRaisesRegex(ValueError, 'row limit'):
                fetch_rows('https://romm.example', 'private', 1, 3, Opener())

    def test_pagination_uses_raw_rows_and_fails_on_repeated_page(self):
        class Opener:
            def __init__(self, pages):
                self.pages = iter(pages)
                self.requests = []
            def open(self, req, timeout):
                self.requests.append(req)
                return io.BytesIO(json.dumps(next(self.pages)).encode())
        opener = Opener([{'items': [self.row], 'total': 2}, {'items': [dict(self.row, id=2)], 'total': 2}])
        self.assertEqual(len(fetch_rows('https://romm.example', 'private', 1, 2, opener)), 2)
        self.assertIn('offset=1', opener.requests[1].full_url)
        repeated = Opener([{'items': [self.row], 'total': 2}] * 2)
        with self.assertRaises(ValueError):
            fetch_rows('https://romm.example', 'private', 1, 2, repeated)
        with self.assertRaises(ValueError):
            fetch_rows('http://romm.example', 'private')


if __name__ == '__main__':
    unittest.main()
