#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
mkdir -p apps/ios/build
swiftc -swift-version 6 -parse-as-library \
  apps/ios/Dearby/shared/lib/Event*.swift \
  apps/ios/Dearby/entities/notice/model/NoticePlaceState.swift \
  apps/ios/tests/DetailPresentationTests.swift -o apps/ios/build/dearby-detail-presentation-tests
apps/ios/build/dearby-detail-presentation-tests
python3 apps/ios/tests/check_fsd_boundaries.py
swiftc -swift-version 6 -parse-as-library \
  apps/ios/Dearby/shared/lib/Event*.swift \
  apps/ios/tests/EventTimelineTests.swift -o apps/ios/build/dearby-event-timeline-tests
apps/ios/build/dearby-event-timeline-tests
