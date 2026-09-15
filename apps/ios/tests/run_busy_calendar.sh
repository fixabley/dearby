#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
mkdir -p apps/ios/build
swiftc -swift-version 6 -parse-as-library \
  apps/ios/Dearby/Shared/Lib/BusyTime*.swift \
  apps/ios/Dearby/Shared/Lib/CalendarConnectionState.swift \
  apps/ios/Dearby/Features/CheckCalendarOverlap/API/BusyCalendarProvider.swift \
  apps/ios/Dearby/Features/CheckCalendarOverlap/Model/*.swift \
  apps/ios/Dearby/App/Providers/CalendarPreference*.swift \
  apps/ios/Dearby/App/Providers/UserDefaultsCalendarPreferenceStore.swift \
  apps/ios/tests/CalendarPreferenceTests.swift \
  apps/ios/tests/BusyCalendarTests.swift -o apps/ios/build/dearby-busy-calendar-tests
apps/ios/build/dearby-busy-calendar-tests
python3 apps/ios/tests/check_fsd_boundaries.py
