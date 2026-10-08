import hashlib
import http.client
import json
from pathlib import Path
import tempfile
import unittest
import threading
from http.server import ThreadingHTTPServer

from gateway import Library, METADATA_MAX_BYTES, read_metadata_index, make_handler


class MetadataTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'n3ds').mkdir()
        self.name = '0004000000174600 Example (CTR-P-BPXE) (v0.0.0) (W).piratelegit.cia'
        (self.root / 'n3ds' / self.name).write_bytes(b'example')
        self.digest = hashlib.sha256(b'example').hexdigest()
        self.index = self.root / 'metadata.json'
        self.library = Library(self.root, self.index)
        self.record = dict(version=1, platform='n3ds', sha256=self.digest,
                           canonical_title='A verified title', match_status='matched',
                           provider_ids={'igdb': 123}, region='USA', revision='1',
                           cover_url='https://images.igdb.com/igdb/image/upload/t_cover_big/example.jpg',
                           candidates=[])

    def write(self, records):
        self.index.write_text(json.dumps(dict(version=1, items=records)))

    def metadata(self):
        return self.library.scan()[0]['metadata']

    def test_default_clean_title_is_not_a_match_and_identity_unchanged(self):
        before = self.library.scan()[0]
        self.assertEqual('Example', before['metadata']['canonical_title'])
        self.assertEqual('unmatched', before['metadata']['match_status'])
        self.assertEqual({}, before['metadata']['provider_ids'])
        self.write([self.record])
        after = self.library.scan()[0]
        self.assertEqual(self.record, after['metadata'])
        for field in ('id', 'file_name', 'sha256', 'size_bytes', 'title'):
            self.assertEqual(before[field], after[field])
        self.assertEqual(self.name, after['file_name'])

    def test_http_metadata_is_bound_and_does_not_expose_paths_or_mutate_rom(self):
        path = self.root / 'n3ds' / self.name
        before = path.stat()
        self.write([{**self.record, 'source_path': str(path), 'private_token': 'not-public'}])
        token = 'test-only-' + 'a' * 32
        server = ThreadingHTTPServer(('127.0.0.1', 0), make_handler(self.library, token))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            connection = http.client.HTTPConnection('127.0.0.1', server.server_port, timeout=2)
            connection.request('GET', '/api/downloads', headers={'Authorization': 'Bearer ' + token})
            response = connection.getresponse()
            body = response.read()
            connection.close()
            self.assertEqual(200, response.status)
            item = json.loads(body)['items'][0]
            self.assertEqual(item['sha256'], item['metadata']['sha256'])
            self.assertEqual(item['platform'], item['metadata']['platform'])
            self.assertEqual(self.record, item['metadata'])
            self.assertNotIn(str(self.root).encode(), body)
            self.assertNotIn(b'not-public', body)
            connection = http.client.HTTPConnection('127.0.0.1', server.server_port, timeout=2)
            connection.request('GET', '/api/downloads/' + item['id'] + '/content',
                               headers={'Authorization': 'Bearer ' + token})
            response = connection.getresponse()
            self.assertEqual(200, response.status)
            self.assertEqual(b'example', response.read())
            connection.close()
        finally:
            server.shutdown()
            server.server_close()
            thread.join()
        after = path.stat()
        self.assertEqual((before.st_ino, before.st_size, before.st_mtime_ns, before.st_ctime_ns),
                         (after.st_ino, after.st_size, after.st_mtime_ns, after.st_ctime_ns))
        self.assertEqual([path], list(path.parent.iterdir()))
        self.assertEqual(b'example', path.read_bytes())

    def test_optional_null_fields_reject_record_but_absence_is_valid(self):
        for field in ('cover_url', 'region', 'revision'):
            self.write([{**self.record, field: None}])
            self.assertEqual('unmatched', self.metadata()['match_status'])
            absent = {k: v for k, v in self.record.items() if k != field}
            self.write([absent])
            self.assertEqual('matched', self.metadata()['match_status'])

    def test_both_platform_and_hash_must_match(self):
        for field, value in [('platform', 'switch'), ('sha256', '0' * 64)]:
            self.write([{**self.record, field: value}])
            self.assertEqual('unmatched', self.metadata()['match_status'])

    def test_invalid_records_fail_closed(self):
        cases = [('canonical_title', 'x' * 257), ('canonical_title', 'bad\ntext'),
                 ('region', 'x' * 65), ('revision', []), ('version', True),
                 ('match_status', 'guessed'), ('provider_ids', {'igdb': True}),
                 ('provider_ids', {'igdb': 0}), ('provider_ids', {'igdb': 2147483648}),
                 ('provider_ids', {}), ('candidates', [self.record] * 9)]
        for field, value in cases:
            with self.subTest(field=field, value=value):
                self.write([{**self.record, field: value}])
                self.assertEqual('unmatched', self.metadata()['match_status'])

    def test_cover_url_restrictions(self):
        for url in ['http://images.igdb.com/igdb/image/upload/a.jpg',
                    'https://images.igdb.com.evil.test/igdb/image/upload/a.jpg',
                    'https://user@images.igdb.com/igdb/image/upload/a.jpg',
                    'https://images.igdb.com:443/igdb/image/upload/a.jpg',
                    'https://images.igdb.com/other/a.jpg',
                    self.record['cover_url'] + '?token=secret',
                    self.record['cover_url'] + '#fragment',
                    self.record['cover_url'] + 'x' * 2048]:
            self.write([{**self.record, 'cover_url': url}])
            self.assertEqual('unmatched', self.metadata()['match_status'], url)

    def test_candidates_are_bounded_and_sanitized(self):
        candidate = dict(canonical_title='Maybe', provider_ids={'igdb': 124, 'private': 'secret'},
                         cover_url=self.record['cover_url'], secret='not exposed')
        self.write([{**self.record, 'match_status': 'needs_review', 'candidates': [candidate]}])
        result = self.metadata()
        self.assertEqual('needs_review', result['match_status'])
        self.assertEqual({'igdb': 124}, result['candidates'][0]['provider_ids'])
        self.assertNotIn('secret', result['candidates'][0])

    def test_duplicate_binding_is_ambiguous(self):
        self.write([self.record, {**self.record, 'canonical_title': 'Other'}])
        self.assertEqual('unmatched', self.metadata()['match_status'])

    def test_bad_or_oversized_file_never_breaks_catalog(self):
        for data in [b'{', b'[]', b'{"version":2,"items":[]}', b'x' * (METADATA_MAX_BYTES + 1)]:
            self.index.write_bytes(data)
            self.assertEqual({}, read_metadata_index(self.index))
            self.assertEqual('unmatched', self.metadata()['match_status'])
        self.index.unlink()
        self.assertEqual('unmatched', self.metadata()['match_status'])


if __name__ == '__main__':
    unittest.main()
