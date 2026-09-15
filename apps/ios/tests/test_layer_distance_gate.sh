#!/usr/bin/env bash
set -euo pipefail
IOS_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
ROUTE_PROBE="$IOS_ROOT/Dearby/app/routes/DistanceGateProbe.swift"
PROVIDER_PROBE="$IOS_ROOT/Dearby/app/providers/DistanceGateProbe.swift"
LOG_FILE="$(mktemp)"
[[ ! -e "$ROUTE_PROBE" && ! -e "$PROVIDER_PROBE" ]] || { echo 'Probe path already exists'; exit 1; }
trap 'rm -f "$ROUTE_PROBE" "$PROVIDER_PROBE" "$LOG_FILE"' EXIT
check_failure() {
    if swift test --package-path "$IOS_ROOT/tests/ArchitectureTests" --filter production > "$LOG_FILE" 2>&1; then
        echo "FAIL: violation unexpectedly accepted ($1)"; exit 1
    fi
    grep -q "\[$1\]" "$LOG_FILE" || { cat "$LOG_FILE"; exit 1; }
    echo "PASS: actual production probe rejected [$1]"
}
printf '%s\n' 'struct DistanceGateProbe { let notice: NoticeModel }' > "$ROUTE_PROBE"
check_failure fsd-distant
printf '%s\n' 'import Shared' 'struct DistanceGateProbe {}' > "$ROUTE_PROBE"
check_failure fsd-distant
rm "$ROUTE_PROBE"
printf '%s\n' 'struct DistanceGateProbe { let notice: NoticeModel }' > "$PROVIDER_PROBE"
swift test --package-path "$IOS_ROOT/tests/ArchitectureTests" --filter production > "$LOG_FILE" 2>&1
echo 'PASS: provider construction reference accepted'
printf '%s\n' 'struct DistanceGateProbe { let record: NoticeRecord }' > "$PROVIDER_PROBE"
check_failure fsd-public-api
printf '%s\n' 'import SwiftUI' 'struct DistanceGateProbe: View { var body: some View { Text("bad") } }' > "$PROVIDER_PROBE"
check_failure provider-ui
rm "$PROVIDER_PROBE"
swift test --package-path "$IOS_ROOT/tests/ArchitectureTests" --filter production > "$LOG_FILE" 2>&1
echo 'PASS: restored production accepted'
