#!/bin/bash
# Unit + UI tests on a simulator dedicated to this checkout (reused between runs).
# CI does not boot simulators; run this before pushing iOS changes (the pre-push hook calls it).
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f Dearby.xcodeproj/project.pbxproj ] || ruby scripts/generate_project.rb
name="Dearby-PrePush-$(printf '%s' "$PWD" | shasum | cut -c1-8)"
sim=$(xcrun simctl list devices available -j | python3 -c "import json,sys; print(next((x['udid'] for v in json.load(sys.stdin)['devices'].values() for x in v if x['name']=='$name'), ''))")
if [ -z "$sim" ]; then
  runtime=$(xcrun simctl list runtimes available -j | python3 -c "import json,sys; print([r['identifier'] for r in json.load(sys.stdin)['runtimes'] if r['platform']=='iOS'][-1])")
  devtype=$(xcrun simctl list devicetypes -j | python3 -c "import json,sys; print(next(d['identifier'] for d in json.load(sys.stdin)['devicetypes'] if d['name'].startswith('iPhone')))")
  sim=$(xcrun simctl create "$name" "$devtype" "$runtime")
fi
mkdir -p .build/prepush
log=.build/prepush/test.log
# Large-text test paused by user request (2026-10-06) until asked again.
status=0
xcodebuild -project Dearby.xcodeproj -scheme Dearby -destination "platform=iOS Simulator,id=$sim" \
  -derivedDataPath .build/prepush/DerivedData -parallel-testing-enabled NO test \
  -skip-testing:DearbyUITests/DiscoveryNavigationTests/testAccessibilityTextCalendarAndWalletRemainUsable \
  > "$log" 2>&1 || status=$?
grep -E "Executed [0-9]+ tests|error:|TEST (SUCCEEDED|FAILED)" "$log" | tail -5
[ "$status" -eq 0 ] || echo "full log: $PWD/$log"
exit "$status"
