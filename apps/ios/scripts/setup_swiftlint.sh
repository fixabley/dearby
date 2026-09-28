#!/usr/bin/env bash
set -euo pipefail
IOS_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
SWIFTLINT_VERSION=0.65.1
SWIFTLINT_SHA256=c1e429b0599cf1b516f369a2d9ec04eaf0e436f3c12b637df8851fa52ff694d0
TOOLS_DIR="$IOS_ROOT/build/tools"
DESTINATION="$TOOLS_DIR/swiftlint-$SWIFTLINT_VERSION"
mkdir -p "$TOOLS_DIR"
STAGING="$(mktemp -d "$TOOLS_DIR/swiftlint-download.XXXXXX")"
trap 'rm -rf "$STAGING"' EXIT
curl --fail --location --silent --show-error --retry 3 \
  "https://github.com/realm/SwiftLint/releases/download/$SWIFTLINT_VERSION/portable_swiftlint.zip" \
  --output "$STAGING/portable_swiftlint.zip"
ACTUAL_SHA256="$(shasum -a 256 "$STAGING/portable_swiftlint.zip" | awk '{print $1}')"
if [[ "$ACTUAL_SHA256" != "$SWIFTLINT_SHA256" ]]; then
  echo "SwiftLint archive checksum mismatch: expected $SWIFTLINT_SHA256, got $ACTUAL_SHA256" >&2
  exit 1
fi
unzip -q "$STAGING/portable_swiftlint.zip" -d "$STAGING/extracted"
if [[ "$("$STAGING/extracted/swiftlint" version)" != "$SWIFTLINT_VERSION" ]]; then
  echo "SwiftLint archive executable version mismatch" >&2
  exit 1
fi
mkdir -p "$DESTINATION"
install -m 755 "$STAGING/extracted/swiftlint" "$DESTINATION/swiftlint"
install -m 644 "$STAGING/extracted/LICENSE" "$DESTINATION/LICENSE"
echo "Installed SwiftLint $SWIFTLINT_VERSION (SHA256 verified) at $DESTINATION/swiftlint"
