#!/usr/bin/env bash
# App Store screenshots (2026-10-22 user allowed simulators for this only): builds the Debug app for the simulator against the
# production API, opens each `-DearbyScreenshot` scene (Sources/shared/config/ScreenshotScene.swift) on an iPhone 6.9" and an
# iPad 13" simulator, and saves PNGs into fastlane/screenshots/ko for deliver (it sorts by name and tells devices apart by size).
# 맞춤 대기열(queue)·즐겨찾기 캘린더(favorites)·명함·설정·내 활동(로그인 시) need an account: sign in once on each simulator first; the session stays in its keychain.
# Usage: bash apps/ios/scripts/capture_screenshots.sh   (WAIT=<seconds per scene>, SCENES="discover detail" to narrow)
set -euo pipefail
cd "$(dirname "$0")/.."
WAIT="${WAIT:-8}"
SCENES="${SCENES:-discover discover-expanded queue detail mine favorites cards settings}"
OUT=fastlane/screenshots/ko
DERIVED="${TMPDIR:-/tmp}/dearby-screenshots"
BUNDLE=io.wid.dearby

# The newest available simulator whose name starts with $1 (exact names such as "iPad Pro 13-inch (M5)" vary by Xcode).
udid() {
  xcrun simctl list devices available -j | python3 -c '
import json, sys
prefix = sys.argv[1]
found = [d for runtime, ds in json.load(sys.stdin)["devices"].items() if "iOS" in runtime for d in ds if d["name"].startswith(prefix)]
print(found[-1]["udid"] if found else "")' "$1"
}

gem list -i xcodeproj >/dev/null || gem install --user-install --no-document xcodeproj
ruby scripts/generate_project.rb
xcodebuild -project Dearby.xcodeproj -scheme Dearby -configuration Debug -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath "$DERIVED" DEARBY_API_ORIGIN=https://api.dearby.wid.io.kr build
APP="$DERIVED/Build/Products/Debug-iphonesimulator/Dearby.app"
mkdir -p "$OUT"

for device in "iPhone 17 Pro Max:iPhone69" "iPad Pro 13-inch:iPad13"; do
  name="${device%%:*}" prefix="${device##*:}"
  id=$(udid "$name")
  [ -n "$id" ] || { echo "No available simulator named '${name}…'; create one in Xcode > Devices and Simulators." >&2; exit 1; }
  xcrun simctl boot "$id" 2>/dev/null || true
  xcrun simctl bootstatus "$id" -b >/dev/null
  xcrun simctl ui "$id" appearance light
  xcrun simctl status_bar "$id" override --time 9:41 --batteryState charged --batteryLevel 100 --cellularBars 4 --wifiBars 3
  xcrun simctl install "$id" "$APP"
  n=1
  for scene in $SCENES; do
    xcrun simctl launch --terminate-running-process "$id" "$BUNDLE" -DearbyScreenshot "$scene" >/dev/null
    sleep "$WAIT"
    file="$OUT/${prefix}_$(printf %02d "$n")_${scene}.png"
    xcrun simctl io "$id" screenshot "$file" >/dev/null
    echo "saved $file"
    n=$((n + 1))
  done
  xcrun simctl terminate "$id" "$BUNDLE" 2>/dev/null || true
  xcrun simctl status_bar "$id" clear
done
echo "Review the PNGs before running ios-appstore-submit.yml; deliver replaces every screenshot in App Store Connect with these."
