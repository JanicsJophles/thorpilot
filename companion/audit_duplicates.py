#!/usr/bin/env python3
"""Read-only evidence report for duplicate files and possible game variants."""
import argparse
from collections import defaultdict
import json
from pathlib import Path
import sys

from gateway import Library


def audit(roots, metadata_file=None):
    """Hash eligible files and group evidence; never select or delete a copy."""
    files = []
    # Resolving and deduplicating roots prevents a repeated argument from
    # manufacturing duplicate copies. Report paths stay relative to each root.
    unique_roots = sorted({str(Path(root).resolve(strict=True)) for root in roots})
    for root_number, root in enumerate(unique_roots):
        library = Library(root, metadata_file)
        library.scan()
        for item, relative, _ in library.entries.values():
            metadata = item['metadata']
            file = {key: item[key] for key in ('platform', 'sha256', 'file_name', 'size_bytes')}
            file.update(root_index=root_number, relative_path=relative,
                        canonical_title=metadata['canonical_title'],
                        match_status=metadata['match_status'],
                        provider_ids=metadata['provider_ids'])
            for field in ('region', 'revision'):
                if field in metadata:
                    file[field] = metadata[field]
            files.append(file)
    files.sort(key=lambda file: (file['platform'], file['root_index'], file['relative_path']))
    exact, variants = defaultdict(list), defaultdict(list)
    for file in files:
        exact[(file['platform'], file['sha256'])].append(file)
        if file['match_status'] == 'matched':
            for provider, identity in sorted(file['provider_ids'].items()):
                variants[(file['platform'], provider, identity)].append(file)
    return {
        'version': 1,
        'read_only': True,
        'roots': unique_roots,
        'files_scanned': len(files),
        'files_with_matched_metadata': sum(file['match_status'] == 'matched' for file in files),
        'exact_copies': [
            {'platform': platform, 'evidence': {'sha256': digest}, 'files': group}
            for (platform, digest), group in sorted(exact.items()) if len(group) > 1
        ],
        'possible_variants': [
            {'platform': platform, 'evidence': {'provider': provider, 'title_id': identity},
             'files': group}
            for (platform, provider, identity), group in sorted(variants.items())
            if len({file['sha256'] for file in group}) > 1
        ],
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', required=True, action='append',
                        help='ROM library root; repeat to compare storage locations')
    parser.add_argument('--metadata-file', help='Optional validated gateway metadata index')
    args = parser.parse_args(argv)
    try:
        report = audit(args.root, args.metadata_file)
    except (OSError, ValueError):
        print('Cannot inspect a library root. Check its path and permissions.', file=sys.stderr)
        return 1
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
