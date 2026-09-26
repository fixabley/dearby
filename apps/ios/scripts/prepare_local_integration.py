#!/usr/bin/env python3
"""Opt-in coordinator-owned local API/mail sink. Never logs OTPs or tokens."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import urllib.parse
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('--origin', required=True)
parser.add_argument('--inbox', required=True)
parser.add_argument('--simulator', required=True)
args = parser.parse_args()
url = urllib.parse.urlparse(args.origin)
if url.scheme != 'http' or url.hostname not in ('localhost', '127.0.0.1'):
    raise SystemExit('Only explicit loopback test API origins are accepted')
request = urllib.request.Request(args.origin + '/v1/auth/challenges',
    data=json.dumps({'email': 'ios@example.test'}).encode(),
    headers={'Content-Type': 'application/json'}, method='POST')
with urllib.request.urlopen(request) as response:
    challenge = json.load(response)
mail = json.loads(Path(args.inbox).read_text())
container = subprocess.check_output(['xcrun', 'simctl', 'get_app_container', args.simulator,
    'com.dearby.dearby', 'data'], text=True).strip()
output = Path(container) / 'Documents/dearby-integration.json'
output.parent.mkdir(parents=True, exist_ok=True)
with os.fdopen(os.open(output, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600), 'w') as file:
    json.dump({'origin': args.origin, 'challengeId': challenge['challengeId'], 'code': mail['code']}, file)
print('Private Simulator integration fixture prepared; no codes or tokens printed.')
