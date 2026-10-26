#!/usr/bin/env bash
# Play screenshots on the connected REAL device (adb; AGENTS.md: no emulators). Installs the Debug build against the production
# API, opens each `dearbyScreenshot` scene (app/ScreenshotScene.kt) and saves PNGs where fastlane supply reads them.
# 맞춤 대기열(queue)·즐겨찾기 캘린더(favorites)·명함·설정·내 활동(로그인 시) need an account: sign in once in this Debug install first; reinstalling keeps the app data.
# Usage: bash apps/android/scripts/capture_screenshots.sh   (WAIT=<seconds per scene>, SCENES="discover detail" to narrow,
#        SIZE=<w>x<h> logical display size while capturing, default 1080x2160 because Play refuses a long side over twice the short)
set -euo pipefail
cd "$(dirname "$0")/.."
WAIT="${WAIT:-8}"
SCENES="${SCENES:-discover discover-expanded queue detail mine favorites cards settings}"
SIZE="${SIZE:-1080x2160}"
OUT=fastlane/metadata/android/ko-KR/images/phoneScreenshots
PACKAGE=io.wid.dearby

adb get-state >/dev/null || { echo "No adb device connected." >&2; exit 1; }
DEARBY_API_ORIGIN=https://api.dearby.wid.io.kr ./gradlew installDebug
mkdir -p "$OUT"

# Demo mode: fixed clock, full battery and signal, no notification icons; undone on exit together with the display size.
restore() {
  adb shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null || true
  [ -z "$SIZE" ] || adb shell wm size reset || true
}
trap restore EXIT
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter
demo clock -e hhmm 0941
demo battery -e level 100 -e plugged false
demo network -e mobile show -e level 4 -e datatype none
demo network -e wifi show -e level 4
demo notifications -e visible false
[ -z "$SIZE" ] || adb shell wm size "$SIZE"

n=1
for scene in $SCENES; do
  # singleTask would hand a second start to onNewIntent, which ignores the extra; start fresh each time.
  adb shell am force-stop "$PACKAGE"
  adb shell am start -n "$PACKAGE/com.dearby.nativeapp.app.MainActivity" --es dearbyScreenshot "$scene" >/dev/null
  sleep "$WAIT"
  file="$OUT/$(printf %02d "$n")_${scene}.png"
  adb exec-out screencap -p > "$file"
  echo "saved $file ($(sips -g pixelWidth -g pixelHeight "$file" 2>/dev/null | awk '/pixel/ {printf "%s ", $2}'))"
  n=$((n + 1))
done
adb shell am force-stop "$PACKAGE"
echo "Review the PNGs (Play: each side 320–3840 px, long side at most twice the short) before android-play-release.yml uploads them."
