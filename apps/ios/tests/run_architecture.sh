#!/usr/bin/env bash
set -euo pipefail

TESTS_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# Mandatory calendar/cache safety fixtures, followed by the full AST architecture gate.
python3 -I "$TESTS_DIR/check_fsd_boundaries.py"
# The package has no dependency on the app target or iOS SDK.
swift test --package-path "$TESTS_DIR/ArchitectureTests" --force-resolved-versions
