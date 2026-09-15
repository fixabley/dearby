#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
mkdir -p apps/ios/build
notice=(apps/ios/Dearby/shared/lib/*.swift apps/ios/Dearby/entities/notice/model/*.swift apps/ios/Dearby/entities/notice/api/*.swift)
organization=(apps/ios/Dearby/entities/organization/model/*.swift apps/ios/Dearby/entities/organization/api/*.swift)
favorites=(apps/ios/Dearby/features/saveOrganization/model/*.swift apps/ios/Dearby/entities/favorite/model/*.swift apps/ios/Dearby/entities/favorite/api/*.swift)
snapshot=(apps/ios/Dearby/app/providers/BundleSnapshot.swift apps/ios/Dearby/app/providers/SnapshotManifest.swift)
swiftc -swift-version 6 -parse-as-library "${notice[@]}" "${organization[@]}" "${favorites[@]}" "${snapshot[@]}" apps/ios/tests/FavoritesStoreTests.swift -o apps/ios/build/dearby-favorites-tests
swiftc -swift-version 6 -parse-as-library "${organization[@]}" apps/ios/tests/OrganizationRepositoryTests.swift -o apps/ios/build/dearby-organization-tests
swiftc -swift-version 6 -parse-as-library "${notice[@]}" "${organization[@]}" "${favorites[@]}" "${snapshot[@]}" apps/ios/Dearby/widgets/noticeCard/model/*State.swift apps/ios/Dearby/widgets/noticeCard/model/*ViewModel.swift apps/ios/Dearby/widgets/favoriteOrganizationCard/model/*State.swift apps/ios/Dearby/widgets/favoriteOrganizationCard/model/*ViewModel.swift apps/ios/Dearby/widgets/noticeDetail/model/*State.swift apps/ios/Dearby/widgets/noticeDetail/model/*ViewModel.swift apps/ios/Dearby/app/providers/NoticeSession.swift apps/ios/tests/NoticeViewModelTests.swift -o apps/ios/build/dearby-viewmodel-tests
swiftc -swift-version 6 -parse-as-library "${notice[@]}" "${organization[@]}" "${snapshot[@]}" apps/ios/Dearby/features/addToCalendar/model/*.swift apps/ios/Dearby/features/addToCalendar/api/CalendarEditorRequest.swift apps/ios/tests/CalendarDraftTests.swift -o apps/ios/build/dearby-calendar-tests
swiftc -swift-version 6 -parse-as-library "${notice[@]}" apps/ios/Dearby/features/openLocation/api/VenueMapLink.swift apps/ios/Dearby/features/openLocation/api/VenueMapLauncher.swift apps/ios/tests/VenueMapTests.swift -o apps/ios/build/dearby-map-tests
for sample in apps/ios/Dearby/resources/activity-samples.json shared/contracts/activities/sample.json; do
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
for sample in apps/ios/Dearby/resources/activity-samples.json shared/contracts/activities/sample.json; do
  apps/ios/build/dearby-notice-disk-tests "$sample"
done

swiftc -swift-version 6 -parse-as-library "${notice[@]}" "${organization[@]}" "${favorites[@]}" "${snapshot[@]}" apps/ios/Dearby/widgets/noticeCard/model/*State.swift apps/ios/Dearby/widgets/noticeCard/model/*ViewModel.swift apps/ios/Dearby/widgets/favoriteOrganizationCard/model/*State.swift apps/ios/Dearby/widgets/favoriteOrganizationCard/model/*ViewModel.swift apps/ios/Dearby/widgets/noticeDetail/model/*State.swift apps/ios/Dearby/widgets/noticeDetail/model/*ViewModel.swift apps/ios/Dearby/app/providers/NoticeSession.swift apps/ios/Dearby/app/providers/SwiftDataSnapshotStore.swift apps/ios/tests/SwiftDataSnapshotTests.swift -o apps/ios/build/dearby-snapshot-disk-tests
for sample in apps/ios/Dearby/resources/activity-samples.json shared/contracts/activities/sample.json; do
  apps/ios/build/dearby-snapshot-disk-tests "$sample"
done

swiftc -D DEBUG -swift-version 6 -parse-as-library "${notice[@]}" "${organization[@]}" "${favorites[@]}" "${snapshot[@]}" \
  apps/ios/Dearby/widgets/noticeCard/model/*.swift \
  apps/ios/Dearby/widgets/favoriteOrganizationCard/model/*.swift \
  apps/ios/Dearby/widgets/noticeDetail/model/*.swift \
  apps/ios/Dearby/features/checkCalendarOverlap/api/BusyCalendarProvider.swift \
  apps/ios/Dearby/features/checkCalendarOverlap/model/*.swift \
  apps/ios/Dearby/pages/settings/model/SettingsViewModel.swift \
  apps/ios/Dearby/app/providers/NoticeSession.swift \
  apps/ios/Dearby/app/providers/SwiftDataSnapshotStore.swift \
  apps/ios/Dearby/app/providers/PreviewBusyCalendarProvider.swift \
  apps/ios/Dearby/app/providers/AppSession.swift \
  apps/ios/tests/AppSessionTests.swift -o apps/ios/build/dearby-startup-tests
apps/ios/build/dearby-startup-tests apps/ios/Dearby/resources/activity-samples.json
