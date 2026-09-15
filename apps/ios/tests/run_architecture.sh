#!/usr/bin/env bash
set -euo pipefail

TESTS_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# No --slice: every legacy rule and its positive/negative fixtures remain mandatory.
python3 "$TESTS_DIR/check_fsd_boundaries.py"
# The package has no dependency on the app target or iOS SDK.
swift test --package-path "$TESTS_DIR/ArchitectureTests" --force-resolved-versions
