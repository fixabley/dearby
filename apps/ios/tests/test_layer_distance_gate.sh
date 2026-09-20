#!/usr/bin/env bash
set -euo pipefail
IOS_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
ROUTE_PROBE="$IOS_ROOT/Dearby/app/routes/DistanceGateProbe.swift"
PROVIDER_PROBE="$IOS_ROOT/Dearby/app/providers/DistanceGateProbe.swift"
WIDGET_PROBE="$IOS_ROOT/Dearby/widgets/noticeCard/ui/DistanceGateProbe.swift"
PURE_SOURCE="$IOS_ROOT/Dearby/widgets/noticeCard/ui/NoticeCardContent.swift"
PURE_RENAMED="$IOS_ROOT/Dearby/widgets/noticeCard/ui/RenamedPureProbe.swift"
PURE_BACKUP="$(mktemp)"
PURE_MANIFEST="$IOS_ROOT/architecture/pure-ui.json"
MANIFEST_BACKUP="$(mktemp)"
LOG_FILE="$(mktemp)"
[[ ! -e "$ROUTE_PROBE" && ! -e "$PROVIDER_PROBE" && ! -e "$WIDGET_PROBE" && ! -e "$PURE_RENAMED" ]] || { echo 'Probe path already exists'; exit 1; }
cp "$PURE_SOURCE" "$PURE_BACKUP"
cp "$PURE_MANIFEST" "$MANIFEST_BACKUP"
restore() {
    cp "$PURE_BACKUP" "$PURE_SOURCE"
    cp "$MANIFEST_BACKUP" "$PURE_MANIFEST"
    rm -f "$ROUTE_PROBE" "$PROVIDER_PROBE" "$WIDGET_PROBE" "$PURE_RENAMED" "$PURE_BACKUP" "$MANIFEST_BACKUP" "$LOG_FILE"
}
trap restore EXIT
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
printf '%s\n' 'struct DistanceGateProbe { let defaults = UserDefaults.standard }' > "$ROUTE_PROBE"
check_failure route-effect
rm "$ROUTE_PROBE"
printf '%s\n' 'struct DistanceGateProbe { let token = NativeSpacing.related }' > "$WIDGET_PROBE"
swift test --package-path "$IOS_ROOT/tests/ArchitectureTests" --filter production > "$LOG_FILE" 2>&1
echo 'PASS: widget exported Shared design reference accepted'
printf '%s\n' 'struct DistanceGateProbe { let interval: BusyTimeInterval }' > "$WIDGET_PROBE"
check_failure fsd-distant
rm "$WIDGET_PROBE"
mv "$PURE_SOURCE" "$PURE_RENAMED"
printf '%s\n' 'extension NoticeCardContent { var invalidStorage: UserDefaults { .standard } }' >> "$PURE_RENAMED"
check_failure pure-ui-effect
rm "$PURE_RENAMED"
cp "$PURE_BACKUP" "$PURE_SOURCE"
printf '%s\n' 'struct DistanceGateProbe { let notice: NoticeModel }' > "$PROVIDER_PROBE"
swift test --package-path "$IOS_ROOT/tests/ArchitectureTests" --filter production > "$LOG_FILE" 2>&1
echo 'PASS: provider construction reference accepted'
printf '%s\n' 'struct DistanceGateProbe { let record: NoticeRecord }' > "$PROVIDER_PROBE"
check_failure fsd-public-api
printf '%s\n' 'import SwiftUI' 'struct DistanceGateProbe: View { var body: some View { Text("bad") } }' > "$PROVIDER_PROBE"
check_failure provider-ui
rm "$PROVIDER_PROBE"
printf '%s\n' '[]' > "$PURE_MANIFEST"
check_failure pure-ui-contract
printf '%s\n' '["MissingPureView"]' > "$PURE_MANIFEST"
check_failure pure-ui-contract
cp "$MANIFEST_BACKUP" "$PURE_MANIFEST"
swift test --package-path "$IOS_ROOT/tests/ArchitectureTests" --filter production > "$LOG_FILE" 2>&1
echo 'PASS: restored production accepted'
