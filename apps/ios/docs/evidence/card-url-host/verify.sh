#!/bin/bash
set -euo pipefail
notice=(apps/ios/Dearby/shared/lib/*.swift apps/ios/Dearby/entities/notice/model/*.swift apps/ios/Dearby/entities/notice/api/*.swift)
organization=(apps/ios/Dearby/entities/organization/model/*.swift apps/ios/Dearby/entities/organization/api/*.swift)
favorites=(apps/ios/Dearby/features/FavoriteOrganization/model/*.swift apps/ios/Dearby/features/FavoriteOrganization/api/*.swift)
snapshot=(apps/ios/Dearby/app/BundleSnapshot.swift apps/ios/Dearby/app/SnapshotManifest.swift)
swiftc -swift-version 6 -parse-as-library "${notice[@]}" "${organization[@]}" "${favorites[@]}" "${snapshot[@]}" apps/ios/Dearby/widgets/notice/noticeCard/*State.swift apps/ios/Dearby/widgets/notice/noticeCard/*ViewModel.swift apps/ios/Dearby/widgets/organization/favoriteOrganizationCard/*State.swift apps/ios/Dearby/widgets/organization/favoriteOrganizationCard/*ViewModel.swift apps/ios/Dearby/pages/noticeDetail/model/*.swift apps/ios/Dearby/app/NoticeSession.swift apps/ios/tests/NoticeViewModelTests.swift -o apps/ios/build/dearby-viewmodel-tests
apps/ios/build/dearby-viewmodel-tests apps/ios/Dearby/resources/activity-samples.json
apps/ios/build/dearby-viewmodel-tests shared/contracts/activities/sample.json
python3 apps/ios/tests/check_fsd_boundaries.py
