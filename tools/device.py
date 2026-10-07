#!/usr/bin/env python3
"""Small, repeatable device loop. Local captures stay in ignored artifacts/."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['inspect', 'capture', 'install', 'test'])
    parser.add_argument('--serial', help='Select a device when more than one is connected')
    args = parser.parse_args()
    sdk = Path(os.environ.get('ANDROID_HOME', Path.home() / 'Library/Android/sdk'))
    executable = shutil.which('adb') or str(sdk / 'platform-tools/adb')
    adb = [executable] + (['-s', args.serial] if args.serial else [])

    def run(*parts):
        return subprocess.run(adb + list(parts), check=True, capture_output=True, text=True).stdout.strip()

    run('get-state')  # Refuse ambiguous, offline or unauthorized device selection.
    if args.action == 'inspect':
        print(json.dumps({
            'model': run('shell', 'getprop', 'ro.product.model'),
            'android': run('shell', 'getprop', 'ro.build.version.release'),
            'physical_displays': run('shell', 'dumpsys', 'SurfaceFlinger', '--display-id').splitlines(),
        }, indent=2))
    elif args.action == 'capture':
        output = ROOT / 'artifacts/device'
        output.mkdir(parents=True, exist_ok=True)
        physical = run('shell', 'dumpsys', 'SurfaceFlinger', '--display-id')
        ids = re.findall(r'^Display (\d+) ', physical, re.MULTILINE)
        if not ids:
            raise SystemExit('No physical displays reported; screenshot capture is unavailable.')
        for index, display in enumerate(ids):
            target = output / f'display-{index}.png'
            with target.open('wb') as image:
                subprocess.run(adb + ['exec-out', 'screencap', '-p', '-d', display], stdout=image, check=True)
            print(target)
    else:
        apk = ROOT / 'android/app/build/outputs/apk'
        print(run('install', '-r', str(apk / 'debug/app-debug.apk')))
        if args.action == 'install':
            print(run('shell', 'am', 'start', '-n', 'dev.thorpilot/.MainActivity'))
        else:
            print(run('install', '-r', str(apk / 'androidTest/debug/app-debug-androidTest.apk')))
            result = run('shell', 'am', 'instrument', '-w', 'dev.thorpilot.test/dev.thorpilot.DeviceChecks')
            print(result)
            if 'PASS:' not in result or 'FAIL:' in result:
                raise SystemExit('Device checks did not pass.')


if __name__ == '__main__':
    main()
