#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
mkdir -p apps/ios/build
swiftc -swift-version 6 -parse-as-library \
  apps/ios/Dearby/shared/lib/BusyTime*.swift \
  apps/ios/Dearby/features/checkCalendarOverlap/api/BusyCalendarProvider.swift \
  apps/ios/Dearby/features/checkCalendarOverlap/model/*.swift \
  apps/ios/Dearby/app/providers/CalendarPreference*.swift \
  apps/ios/Dearby/app/providers/UserDefaultsCalendarPreferenceStore.swift \
  apps/ios/tests/CalendarPreferenceTests.swift \
  apps/ios/tests/BusyCalendarTests.swift -o apps/ios/build/dearby-busy-calendar-tests
apps/ios/build/dearby-busy-calendar-tests
python3 apps/ios/tests/check_fsd_boundaries.py
