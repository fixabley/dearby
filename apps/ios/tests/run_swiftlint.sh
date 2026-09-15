#!/usr/bin/env bash
set -euo pipefail
IOS_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
SWIFTLINT_VERSION=0.65.1
SWIFTLINT="$IOS_ROOT/build/tools/swiftlint-$SWIFTLINT_VERSION/swiftlint"
if [[ ! -x "$SWIFTLINT" ]]; then
  echo "SwiftLint $SWIFTLINT_VERSION missing. Run bash apps/ios/scripts/setup_swiftlint.sh first." >&2
  exit 1
fi
ACTUAL_VERSION="$("$SWIFTLINT" version)"
if [[ "$ACTUAL_VERSION" != "$SWIFTLINT_VERSION" ]]; then
  echo "SwiftLint version mismatch: expected $SWIFTLINT_VERSION, got $ACTUAL_VERSION. Run setup_swiftlint.sh." >&2
  exit 1
fi
cd "$IOS_ROOT"
exec "$SWIFTLINT" lint --config "$IOS_ROOT/.swiftlint.yml" --strict --no-cache
