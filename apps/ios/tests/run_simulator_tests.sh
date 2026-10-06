#!/bin/bash
# Unit + UI tests on a simulator dedicated to this checkout (reused between runs).
# CI does not boot simulators; run this before pushing iOS changes (the pre-push hook calls it).
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f Dearby.xcodeproj/project.pbxproj ] || ruby scripts/generate_project.rb
# Runtime: DEARBY_SIM_RUNTIME (e.g. iOS-27-0) or the newest installed iOS 26.x. The iOS 27 runtime keeps
# respawning lock-screen poster extensions under load, adding ~300 s between UI tests (2026-10-06).
want=${DEARBY_SIM_RUNTIME:-iOS-26-}
runtime=$(xcrun simctl list runtimes available -j | python3 -c "import json,sys; r=[x['identifier'] for x in json.load(sys.stdin)['runtimes'] if x['platform']=='iOS']; m=[x for x in r if '$want' in x]; print((m or r)[-1])")
name="Dearby-PrePush-$(printf '%s' "$PWD" | shasum | cut -c1-8)-${runtime##*.}"
sim=$(xcrun simctl list devices available -j | python3 -c "import json,sys; print(next((x['udid'] for v in json.load(sys.stdin)['devices'].values() for x in v if x['name']=='$name'), ''))")
if [ -z "$sim" ]; then
  devtype=$(xcrun simctl list devicetypes -j | python3 -c "import json,sys; print(next(d['identifier'] for d in json.load(sys.stdin)['devicetypes'] if d['name'].startswith('iPhone')))")
  sim=$(xcrun simctl create "$name" "$devtype" "$runtime")
fi
mkdir -p .build/prepush
log=.build/prepush/test.log
# One simulator test run at a time on this Mac: parallel runs from several checkouts starve each other.
lock=${TMPDIR:-/tmp}/dearby-simulator-tests.lock
waited=0
until mkdir "$lock" 2>/dev/null; do
  owner=$(cat "$lock/pid" 2>/dev/null || true)
  if [ -n "$owner" ] && ! kill -0 "$owner" 2>/dev/null; then rm -rf "$lock"; continue; fi
  [ $((waited % 30)) -eq 0 ] && echo "waiting for another simulator test run (pid ${owner:-?}) ... ${waited}s"
  sleep 5; waited=$((waited + 5))
done
echo $$ > "$lock/pid"
trap 'rm -rf "$lock"' EXIT
# Large-text test paused by user request (2026-10-06) until asked again.
status=0
xcodebuild -project Dearby.xcodeproj -scheme Dearby -destination "platform=iOS Simulator,id=$sim" \
  -derivedDataPath .build/prepush/DerivedData -parallel-testing-enabled NO test \
  -skip-testing:DearbyUITests/DiscoveryNavigationTests/testAccessibilityTextCalendarAndWalletRemainUsable \
  > "$log" 2>&1 || status=$?
grep -E "Executed [0-9]+ tests|error:|TEST (SUCCEEDED|FAILED)" "$log" | tail -5
[ "$status" -eq 0 ] || echo "full log: $PWD/$log"
exit "$status"
