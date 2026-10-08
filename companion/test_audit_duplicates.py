from contextlib import redirect_stderr, redirect_stdout
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest

from audit_duplicates import audit, main


class DuplicateAuditTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.first = self.root / 'internal'
        self.second = self.root / 'sd'
        self.first.mkdir()
        self.second.mkdir()
        self.index = self.root / 'metadata.json'

    def game(self, root, platform, name, content):
        path = root / platform / name
        path.parent.mkdir(exist_ok=True)
        path.write_bytes(content)
        return path

    def record(self, content, **values):
        return dict(version=1, platform='n3ds', sha256=hashlib.sha256(content).hexdigest(),
                    canonical_title='Example', match_status='matched', provider_ids={'igdb': 42},
                    **values)

    def metadata(self, records):
        self.index.write_text(json.dumps({'version': 1, 'items': records}))

    def test_exact_copies_need_no_metadata_and_do_not_modify_files(self):
        paths = [self.game(self.first, 'n3ds', 'One.cia', b'same'),
                 self.game(self.second, '3ds', 'Two.cia', b'same')]
        before = [(path.read_bytes(), path.stat().st_mtime_ns) for path in paths]
        result = audit([self.second, self.first, self.first])
        self.assertEqual(2, result['files_scanned'])
        self.assertEqual(1, len(result['exact_copies']))
        self.assertEqual(['n3ds/One.cia', '3ds/Two.cia'],
                         [file['relative_path'] for file in result['exact_copies'][0]['files']])
        self.assertEqual([], result['possible_variants'])
        self.assertEqual(before, [(path.read_bytes(), path.stat().st_mtime_ns) for path in paths])

    def test_exact_copies_in_one_root_have_distinct_filenames(self):
        self.game(self.first, 'n3ds', 'Original.cia', b'same')
        self.game(self.first, 'n3ds', 'Other name.cia', b'same')
        report = audit([self.first])
        self.assertEqual(2, report['files_scanned'])
        self.assertEqual(1, len(report['exact_copies']))
        self.assertEqual({'Original.cia', 'Other name.cia'},
                         {file['file_name'] for file in report['exact_copies'][0]['files']})
        self.assertEqual([], report['possible_variants'])

    def test_file_or_missing_root_is_a_clear_cli_error(self):
        file = self.game(self.first, 'n3ds', 'One.cia', b'one')
        for root in (file, self.root / 'missing'):
            with self.subTest(root=root):
                out, error = io.StringIO(), io.StringIO()
                with redirect_stdout(out), redirect_stderr(error):
                    self.assertEqual(1, main(['--root', str(root)]))
                self.assertEqual('', out.getvalue())
                self.assertIn('Cannot inspect a library root', error.getvalue())

    def test_alias_and_overlapping_roots_do_not_repeat_a_path(self):
        self.game(self.first, 'n3ds', 'One.cia', b'one')
        alias = self.root / 'alias'
        alias.symlink_to(self.first, target_is_directory=True)
        # Parent scans are intentionally shallow. An overlapping parent cannot
        # recurse into this nested ROM library and count its file a second time.
        report = audit([self.root, self.first, alias, self.first / '.'])
        self.assertEqual(1, report['files_scanned'])
        self.assertEqual(2, len(report['roots']))
        self.assertEqual([], report['exact_copies'])

    def test_region_variants_are_separate_from_exact_copies(self):
        self.game(self.first, 'n3ds', 'USA.cia', b'us')
        self.game(self.second, 'n3ds', 'USA copy.cia', b'us')
        self.game(self.first, 'n3ds', 'Europe.cia', b'eu')
        self.metadata([self.record(b'us', region='USA', revision='1'),
                       self.record(b'eu', region='Europe', revision='2')])
        result = audit([self.first, self.second], self.index)
        self.assertEqual(1, len(result['exact_copies']))
        self.assertEqual(1, len(result['possible_variants']))
        group = result['possible_variants'][0]
        self.assertEqual({'provider': 'igdb', 'title_id': 42}, group['evidence'])
        self.assertEqual({'USA', 'Europe'}, {file['region'] for file in group['files']})
        self.assertEqual({'1', '2'}, {file['revision'] for file in group['files']})
        self.assertEqual(result, audit([self.second, self.first], self.index))

    def test_title_alone_and_uncertain_candidates_do_not_group(self):
        self.game(self.first, 'n3ds', 'One.cia', b'one')
        self.game(self.first, 'n3ds', 'Two.cia', b'two')
        one, two = self.record(b'one'), self.record(b'two')
        two['match_status'] = 'needs_review'
        two['candidates'] = [{'canonical_title': 'Example', 'provider_ids': {'igdb': 42}}]
        self.metadata([one, two])
        self.assertEqual([], audit([self.first], self.index)['possible_variants'])
        two['match_status'] = 'matched'
        two['provider_ids'] = {'igdb': 43}
        self.metadata([one, two])
        self.assertEqual([], audit([self.first], self.index)['possible_variants'])

    def test_platform_and_provider_namespaces_do_not_mix(self):
        self.game(self.first, 'n3ds', 'One.cia', b'one')
        self.game(self.first, 'switch', 'One.nsp', b'one')
        self.game(self.first, 'n3ds', 'Two.cia', b'two')
        other_platform = {**self.record(b'one'), 'platform': 'switch'}
        other_provider = {**self.record(b'two'), 'provider_ids': {'screenscraper': 42}}
        self.metadata([self.record(b'one'), other_platform, other_provider])
        report = audit([self.first], self.index)
        self.assertEqual([], report['exact_copies'])
        self.assertEqual([], report['possible_variants'])

    def test_invalid_metadata_and_excluded_files(self):
        path = self.game(self.first, 'n3ds', 'One.cia', b'one')
        self.game(self.first, 'n3ds', 'save.sav', b'one')
        self.game(self.first, 'n3ds', 'archive.zip', b'one')
        self.game(self.first, 'n3ds', '._One.cia', b'one')
        (path.parent / 'Link.cia').symlink_to(path)
        self.index.write_text('{broken')
        report = audit([self.first], self.index)
        self.assertEqual(1, report['files_scanned'])
        self.assertEqual(0, report['files_with_matched_metadata'])
        self.assertEqual([], report['exact_copies'])

    def test_duplicate_metadata_binding_is_not_trusted(self):
        self.game(self.first, 'n3ds', 'One.cia', b'one')
        self.game(self.first, 'n3ds', 'Two.cia', b'two')
        self.metadata([self.record(b'one'), self.record(b'one'), self.record(b'two')])
        self.assertEqual([], audit([self.first], self.index)['possible_variants'])


if __name__ == '__main__':
    unittest.main()
