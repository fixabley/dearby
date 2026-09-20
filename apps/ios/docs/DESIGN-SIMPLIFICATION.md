# iOS design simplification

## Display projections and internal exports

Removed the unused card applicationSummary/locationSummary/applicationPeriod and detail descriptionProvenance/applicationSummary/scheduleSummaries/applicationPeriod fields. Views still consume the existing application and schedule projections. NoticeModel, storage codecs, source/evidence fields, primary source URL and all cache/restore semantics are unchanged. NoticeViewModelTests retains actual application row and timeline assertions rather than testing a duplicate unused summary.

NoticeClassificationView, CalendarEventEditor and SettingsView remain in use inside their owning slices but no longer appear in public-api.json. Their public callers are NoticeCardBody/NoticePreviewLabel, CalendarExportPresentation and SettingsPresentation respectively. This removes only unnecessary cross-slice permission, not runtime components.

Validation results are recorded in the iOS role handoff; earlier test runs are not new evidence.

## Notice card detail action

NoticeDetailsButton is local to widgets/noticeCard/ui. It retains its separate descriptive file, the native SecondaryButton style, full-width icon label, accessibility identifier and the Page-owned route callback. No Feature owns state or policy for this simple action. The type is explicitly registered as pure UI; its former Feature public API entry is removed.

Validation: production architecture and strict lint are run for the move; the final Simulator smoke must verify details opening and unchanged save behavior.

## Favorites page presentation

SavedOrganizationList is local to pages/favorites/ui and explicitly registered as pure UI. FavoriteListView still passes its existing empty/error condition and content; the native empty view and on-device storage disclosure are unchanged. FavoriteOrganizations and FavoriteOrganizationListViewModel keep their existing mutation, observation, retry and lifetime responsibilities.

Validation: production architecture and strict lint cover the placement; final standalone tests retain favorites failure preservation and single-source observation coverage.

## Detail identity and route composition

NoticeIdentityView and its NoticeIdentityState/NoticeInstitutionState belong to widgets/noticeDetail. Their implementation and source/evidence/organization resolution remain unchanged, but saveOrganization no longer exports detail-only presentation types. The identity View is explicitly registered as pure UI.

NoticeDestinationView constructs NoticeDetailPage directly, passing the app-owned CalendarPreferences. The forwarding AppState.detailPage method is removed. The exact route/type exception in the architecture checker does not expose other Features, repositories, APIs or OS access; inferred member behavior still needs code review. The route keeps its screen-local state and task keyed by notice ID and snapshot generation, and no View body repository lookup is added.

Validation: architecture and strict lint cover the move; final standalone AppStateLifecycle, busy calendar, detail presentation and Simulator build/smoke verify the retained behavior.

## Verification — 2026-09-20, this implementation

- `run_standalone.sh`, `run_busy_calendar.sh`, `run_detail_presentations.sh`: exit 0. These cover original/cache round-trips, missing vs failure, save-before-publish and snapshot rollback, single favorite state, lazy detail/generation replacement, weak list subscription lifetime, permission/background/cancellation and stale-result suppression.
- `run_architecture.sh`: 16 tests / 5 suites pass, including all 36 default layer pairs/imports, narrow Shared UI and exact detail composition exceptions, private/upward/sibling/API rejection, State helpers and class/struct VM ownership, explicit pure contract and rename/stale/duplicate sensitivity.
- `test_layer_distance_gate.sh`: exit 0. Real production probes reject distant references, route OS access, non-design Shared lib, pure effects after file rename, provider private/UI access, empty/stale pure contracts; allowed Shared UI/provider references and restored production pass.
- `setup_swiftlint.sh`: SwiftLint 0.65.1 archive checksum/version verified; final strict lint: 161 files, 0 violations. No baseline or disable added. `git diff --check` passes.
- XcodeBuildMCP `build_run_sim`: succeeded on Debug Dearby / iOS 26.5 / Dearby-FSD-Verify (`B04DEBB6-53B1-4CB1-858C-8C290846D4AB`), using this checkout's `build/design-simplification-derived`. Build log: `/Users/jominjun/Library/Developer/XcodeBuildMCP/workspaces/dearby-ios-architecture-tests-2e6f4371f452/logs/build_run_sim_2026-09-20T11-37-28-459Z_pid54829_2076d14d.log`.

Logs: `/tmp/dearby-simplification-final-{standalone,busy,detail,architecture,gate,lint}.log`. Gate ran before the final VM inventory-count-only correction; the final complete architecture suite/lint ran after that correction. Production code was unchanged after the successful Simulator build. Expected CoreData errors from the invalid-store fixture are followed by passing preservation assertions.

UI evidence and limits: app installation/launch and the Discovery runtime accessibility snapshot succeeded. The snapshot contained 한국농어촌공사 채용설명회, its application/activity dates, restored saved state, and `details.cieat-NCR000000007344` with label 공고 정보 · 출처 보기. A detail tap returned a success receipt but the subsequent snapshot retained the same screen hash (`0torrnh`), so detail entry was not verified. A favorites tap also returned a receipt, then snapshot/screenshot failed with simulator-not-found; a fresh simulator inventory no longer contained that UDID and `simctl list devices booted` was empty. No cause is attributed and no simulator was erased or recreated by this worker. Button navigation, favorites UI mutation, large text/VoiceOver traversal and real OS permission/calendar interaction remain unverified in this run. No screenshot was captured successfully. Model regressions do not substitute for those UI checks.

Ponytail review of the implementation diff found no further deletion justified within the approved scope: the explicit purity list has five real presentation owners, component moves retain meaningful files, native button wrappers preserve shared style, and repositories/session owners retain tested behavior. No speculative line/performance savings are claimed.
