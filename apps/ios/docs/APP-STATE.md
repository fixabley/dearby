# App state and detail lifetime

`AppState` replaces AppSession and NoticeSession. It owns startup/retry, current card/favorite-card models, snapshot date/generation and the shared favorites/preferences. app/providers creates and injects NoticeRepository/OrganizationRepository; the entities remain independent. No NoticeStore/NoticeFeedStore or global UI repository lookup is introduced.

`AppSnapshotComposition` prepares candidate repositories and display models inside the [snapshot disk transaction](SNAPSHOT-TRANSACTIONS.md). Its synchronous MainActor closure cannot suspend; the candidate stays private until the final save. A failed source read, model composition or save rolls back metadata and both L2 slices and leaves the old published repositories/models untouched. A successful replacement publishes fresh L1 repositories/cards/favorite cards together and increments generation. Existing Widget ViewModel/State rendering and the single favorites owner are preserved.

`NoticeDestinationView` owns a small value `NoticeDetailRouteState` with its loaded `(notice ID, generation)` key and one detail VM. This is screen-local routing state, not the Widget's pure rendering State and not a repository cache. `.task(id:)` performs creation once per key; body only reads state and assembles the Page. Failure is cached for that screen/key; a different key or a new screen lifetime can try again. A changed generation hides the old detail until the new task loads it. AppState does not retain a dictionary of detail models, and initial feed composition never resolves detail-only organization links.

## Regression commands

- `bash apps/ios/tests/run_standalone.sh`: startup/storage/source retry, idempotent loading, independent discovery/favorites/open-detail Observation, route reuse/ID and generation changes/new lifetime, lazy detail/failure isolation, snapshot read/save failure preservation, successful cache/model replacement and deletion; existing source/cache/disk/calendar/map suites.
- `bash apps/ios/tests/run_architecture.sh`: full 12 tests, all 36 layer pairs, provider/public API checks and lexical safety fixtures.
- `bash apps/ios/tests/test_layer_distance_gate.sh`: unchanged actual-production violation probes.
- `bash apps/ios/tests/run_swiftlint.sh`, `run_busy_calendar.sh`, `run_detail_presentations.sh`.

The route lifecycle tests exercise the production value-state loader and repeated presentation reads; they do not claim to drive SwiftUI's renderer or actual taps. Simulator compilation and actual UI interaction are reported separately in the iOS handoff.
