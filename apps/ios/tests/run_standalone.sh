#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
mkdir -p apps/ios/build
notice=(apps/ios/Dearby/Shared/Lib/*.swift apps/ios/Dearby/Entities/Notice/Model/*.swift apps/ios/Dearby/Entities/Notice/API/*.swift)
organization=(apps/ios/Dearby/Entities/Organization/Model/*.swift apps/ios/Dearby/Entities/Organization/API/*.swift)
favorites=(apps/ios/Dearby/Features/FavoriteOrganization/Model/*.swift apps/ios/Dearby/Features/FavoriteOrganization/API/*.swift)
snapshot=(apps/ios/Dearby/App/BundleSnapshot.swift apps/ios/Dearby/App/SnapshotManifest.swift)
swiftc -swift-version 6 -parse-as-library "${notice[@]}" "${organization[@]}" "${favorites[@]}" "${snapshot[@]}" apps/ios/tests/FavoritesStoreTests.swift -o apps/ios/build/dearby-favorites-tests
swiftc -swift-version 6 -parse-as-library "${organization[@]}" apps/ios/tests/OrganizationRepositoryTests.swift -o apps/ios/build/dearby-organization-tests
swiftc -swift-version 6 -parse-as-library "${notice[@]}" "${organization[@]}" "${favorites[@]}" "${snapshot[@]}" apps/ios/Dearby/Widgets/NoticeCard/Model/*State.swift apps/ios/Dearby/Widgets/NoticeCard/Model/*ViewModel.swift apps/ios/Dearby/Widgets/Organization/FavoriteOrganizationCard/*State.swift apps/ios/Dearby/Widgets/Organization/FavoriteOrganizationCard/*ViewModel.swift apps/ios/Dearby/Pages/NoticeDetail/Model/*.swift apps/ios/Dearby/App/NoticeSession.swift apps/ios/tests/NoticeViewModelTests.swift -o apps/ios/build/dearby-viewmodel-tests
swiftc -swift-version 6 -parse-as-library "${notice[@]}" "${organization[@]}" "${snapshot[@]}" apps/ios/Dearby/Features/AddToCalendar/Model/*.swift apps/ios/Dearby/App/CalendarEditorRequest.swift apps/ios/tests/CalendarDraftTests.swift -o apps/ios/build/dearby-calendar-tests
swiftc -swift-version 6 -parse-as-library "${notice[@]}" apps/ios/Dearby/App/VenueMapLink.swift apps/ios/Dearby/App/VenueMapLauncher.swift apps/ios/tests/VenueMapTests.swift -o apps/ios/build/dearby-map-tests
for sample in apps/ios/Dearby/Resources/activity-samples.json shared/contracts/activities/sample.json; do
  apps/ios/build/dearby-favorites-tests "$sample"
  apps/ios/build/dearby-viewmodel-tests "$sample"
  apps/ios/build/dearby-calendar-tests "$sample"
done
apps/ios/build/dearby-organization-tests
apps/ios/build/dearby-map-tests
python3 apps/ios/tests/check_fsd_boundaries.py

swiftc -swift-version 6 -parse-as-library "${organization[@]}" apps/ios/tests/SwiftDataOrganizationTests.swift -o apps/ios/build/dearby-organization-disk-tests
apps/ios/build/dearby-organization-disk-tests

swiftc -swift-version 6 -parse-as-library "${notice[@]}" "${organization[@]}" "${snapshot[@]}" apps/ios/tests/SwiftDataNoticeTests.swift -o apps/ios/build/dearby-notice-disk-tests
for sample in apps/ios/Dearby/Resources/activity-samples.json shared/contracts/activities/sample.json; do
  apps/ios/build/dearby-notice-disk-tests "$sample"
done

swiftc -swift-version 6 -parse-as-library "${notice[@]}" "${organization[@]}" "${favorites[@]}" "${snapshot[@]}" apps/ios/Dearby/Widgets/NoticeCard/Model/*State.swift apps/ios/Dearby/Widgets/NoticeCard/Model/*ViewModel.swift apps/ios/Dearby/Widgets/Organization/FavoriteOrganizationCard/*State.swift apps/ios/Dearby/Widgets/Organization/FavoriteOrganizationCard/*ViewModel.swift apps/ios/Dearby/Pages/NoticeDetail/Model/*.swift apps/ios/Dearby/App/NoticeSession.swift apps/ios/Dearby/App/SwiftDataSnapshotStore.swift apps/ios/tests/SwiftDataSnapshotTests.swift -o apps/ios/build/dearby-snapshot-disk-tests
for sample in apps/ios/Dearby/Resources/activity-samples.json shared/contracts/activities/sample.json; do
  apps/ios/build/dearby-snapshot-disk-tests "$sample"
done
