#!/usr/bin/env python3
"""Copy the reviewed sample contract into offline client resources. No network calls."""
import argparse
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = root / 'shared/contracts/activities/sample.json'
targets = [
    root / 'apps/ios/Dearby/Resources/activity-samples.json',
    root / 'apps/android/app/src/main/assets/activity-samples.json',
]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--check', action='store_true')
args = parser.parse_args()
for target in targets:
    if args.check:
        if not target.exists() or target.read_bytes() != source.read_bytes():
            raise SystemExit(f'Outdated sample: {target.relative_to(root)}')
    else:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(source.read_bytes())
print('Activity samples are in sync.' if args.check else 'Synced iOS and Android samples.')
