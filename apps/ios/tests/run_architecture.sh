#!/usr/bin/env bash
set -euo pipefail
TESTS_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# Historical calendar/cache fixtures refer to deleted product code. The complete
# AST rule fixtures and real production dependency graph run against Sources.
swift test --package-path "$TESTS_DIR/ArchitectureTests" --force-resolved-versions
