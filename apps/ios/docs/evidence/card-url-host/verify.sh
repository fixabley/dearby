#!/bin/bash
set -euo pipefail
notice=(apps/ios/Dearby/Shared/Lib/*.swift apps/ios/Dearby/Entities/Notice/Model/*.swift apps/ios/Dearby/Entities/Notice/API/*.swift)
organization=(apps/ios/Dearby/Entities/Organization/Model/*.swift apps/ios/Dearby/Entities/Organization/API/*.swift)
favorites=(apps/ios/Dearby/Features/FavoriteOrganization/Model/*.swift apps/ios/Dearby/Features/FavoriteOrganization/API/*.swift)
snapshot=(apps/ios/Dearby/App/BundleSnapshot.swift apps/ios/Dearby/App/SnapshotManifest.swift)
swiftc -swift-version 6 -parse-as-library "${notice[@]}" "${organization[@]}" "${favorites[@]}" "${snapshot[@]}" apps/ios/Dearby/Widgets/Notice/NoticeCard/*State.swift apps/ios/Dearby/Widgets/Notice/NoticeCard/*ViewModel.swift apps/ios/Dearby/Widgets/Organization/FavoriteOrganizationCard/*State.swift apps/ios/Dearby/Widgets/Organization/FavoriteOrganizationCard/*ViewModel.swift apps/ios/Dearby/Pages/NoticeDetail/Model/*.swift apps/ios/Dearby/App/NoticeSession.swift apps/ios/tests/NoticeViewModelTests.swift -o apps/ios/build/dearby-viewmodel-tests
apps/ios/build/dearby-viewmodel-tests apps/ios/Dearby/Resources/activity-samples.json
apps/ios/build/dearby-viewmodel-tests shared/contracts/activities/sample.json
python3 apps/ios/tests/check_fsd_boundaries.py
