# iOS domain FSD architecture

Updated 2026-09-16 after the approved domain FSD migration. This replaces the former flat Widget and blanket rendering model/VM rules. The app remains one SwiftUI/Observation module; folder boundaries are enforced by tests and review, not the Swift compiler.

All custom folders below `Dearby/` use lowerCamelCase, including `ui`, `api`, and `resources`. Swift filenames/types and the Xcode target/project root retain their names. Only the root `Assets.xcassets` tool-managed structure keeps Xcode asset naming. Both Python and Swift physical inventories reject invalid directories, including empty/non-Swift directories; there is no FSD source exclusion.

## Production layout (Dearby/)

```text
app/
  entrypoint/        DearbyApp
  routes/            ContentView, AppTabs, NoticeDestinationView
  providers/         snapshot/container/session, calendar preferences, injected provider factories
pages/
  discovery/ui/      feed paging, save-result feedback, navigation callbacks
  favorites/ui/      saved organization list
  noticeDetail/
    ui/              NoticeDetailPage: List, navigation title and presentation
  settings/ui/       connection preference presentation
widgets/
  noticeCard/
    ui/              connected NoticeCard, pure NoticeCardContent and local schedule/save views
    model/           NoticeCardViewModel, NoticeCardState, schedule/place states
  noticeDetail/
    ui/              detail sections and feature composition
    model/           NoticeDetailViewModel/State, organization/notice assembly
  favoriteOrganizationCard/
    ui/              connected card, pure FavoriteOrganizationCardContent
    model/           FavoriteOrganizationCardViewModel/State, saved-list ViewModel/index/subscription
features/
  saveOrganization/model|ui/       save/remove facade, saved identity, controls and storage disclosure
  openNoticeDetails/ui/           notice-specific detail action with route callback
  addToCalendar/model|api|ui/      draft/date policy, OS editor bridge, add button
  openLocation/api|ui/            exact venue map URL/launcher and button
  checkCalendarOverlap/model|api|ui/ ephemeral busy query/session, authorization/retry UI, EventKit read adapter
entities/
  notice/model|api|ui/             independent notice values, repository/cache/source, notice-only display projections and pure content
  organization/model|api|ui/      organization values, repository/cache/path resolution, summary section
  favorite/model|api/             single observable ID set and persistent repository
shared/
  ui/                            native controls, rows, generic timeline presentation
  lib/                           domain-free date/time/display values and interval calculations
```

Empty segments are not created. One-use card content stays in its Widget. Independent UI types added by this migration use separate files; title Text is not extracted into a wrapper.

## Direction, segments and public API

By default only the nearest **two** lower layers may be directly referenced: app→pages/widgets, pages→widgets/features, widgets→features/entities, features→entities/shared, entities→shared. Only `app/providers/` is the composition root exception for constructing, retaining and injecting any lower-layer dependency. `app/routes/` and `app/entrypoint/` obey the two-layer rule, except the exact detail route may pass the exported CalendarPreferences instance into its Page. Routes still reject direct storage/network/OS work and API declarations. Pages/Widgets may use exported Shared UI design components/tokens directly; Shared api/lib/model and blanket imports remain outside that exception. Same-slice UI→Model→API remains allowed; sibling slices remain forbidden. Framework imports such as SwiftUI/Foundation are not FSD layer references. Existing framework safety checks remain.

Provider exceptions never permit unexported internals or UI implementation. AppComposition constructs a Page destination from the shared snapshot and calendar preference owner; the route receives that Page, not inferred access to distant domain operations. Cross-slice typealias/re-export facades are prohibited. Inferred/member/macro/dynamic dependencies still require review.

The executable [public-api.json](architecture/public-api.json) names cross-slice contracts. Swift `internal` does not imply permission to reach another slice's internals; `public` is not required to be an FSD entrypoint. NoticeRecord, OrganizationRecord and NoticeStorageCodec are internal. App uses NoticeCacheStorage/OrganizationCacheStorage schema/deletion/fingerprint contracts instead. The checker rejects unknown/duplicate manifest entries and ambiguous declarations.

Entity UI may receive its own Model. shared/Entity UI and presentation declarations registered in `architecture/pure-ui.json` are pure: values/callbacks only, no repository/storage/network/OS work. Widget/Page connected UI may access its own VM and lower-layer public contracts. Shared has no upper-domain dependencies. Shared controls/tokens remain below meaningful Entity/Feature UI: NoticeCardBody, OrganizationSummary, NoticePreviewLabel, NoticeSourceSection, save controls and NoticeDetailsButton. Widget State retains cross-domain composition and saved values; pure entity UI receives notice-only display values and slots/callbacks. Page canvas uses the native SwiftUI system background. The concrete policy and checker limits are in [architecture/README](architecture/README.md).

## State and lifetime

- NoticeModel carries notice values and organization IDs/roles, never OrganizationModel or repository lookup. Organization stays independent; VMs combine them into screen State.
- Connected NoticeCard reads its VM and delegates save; Discovery retains paging, save feedback/haptic trigger and detail route callback. Connected FavoriteOrganizationCard reads its VM and delegates explicit remove.
- entities/favorite/FavoriteOrganizationStore is the sole observable saved-ID owner. The shared features/saveOrganization/FavoriteOrganizations facade validates organization targets and exposes that state without a copy. Existing UserDefaults key/array restoration, idempotent insert, explicit delete and unresolved no-write semantics remain. The facade sends synchronous post-mutation changes through weak subscriptions; it does not copy IDs. FavoriteOrganizationListViewModel owns only visible saved cards, a single organization-to-feed index and explicit load failure/retry. Unsaved organizations are not preassembled. AppState switches list subscriptions only after snapshot commit. See [saved list composition](docs/SAVED-ORGANIZATION-LIST.md).
- Observable `AppState` owns startup/retry and the current cards/favorite cards, snapshot generation and shared dependencies. `AppSnapshotComposition` in app/providers constructs the independent repositories and candidate display models. `NoticeSession` is removed; no replacement Notice Store is introduced. ContentView selects loading/failure/ready; AppTabs owns tab/navigation roots and SettingsPresentation owns prompt/settings lifecycle.
- `SwiftDataSnapshotStore` owns disk snapshot metadata and transactions only. L1→SwiftData L2→bundled mock is retained per entity. Ordinary external reads persist before L1 promotion; during snapshot replacement, private candidate L1 entries and display models remain unpublished until the combined metadata/L2 save succeeds. Composition/save failure preserves the existing manifest, both L2 slices, published repositories and visible models. Success replaces both repositories and all cards/favorite cards, then advances the routing generation. See [snapshot transactions](docs/SNAPSHOT-TRANSACTIONS.md).
- Detail ViewModels are created on detail entry, never during feed startup or View body evaluation. A value `NoticeDetailRouteState` in app/routes owns one VM and the loaded `(notice ID, generation)` key for that screen lifetime; it is routing state, distinct from the Widget's pure `NoticeDetailState`. A task loads each key once, body only assembles the page, and a changed snapshot replaces open-detail state without displaying the old generation. Detail-only failure is isolated from cards. The single favorites owner remains shared across every model. See [AppState verification](docs/APP-STATE.md).
- NoticeDetailPage owns the List/presentation; its Widget combines notice/organization State with calendar/location Features. CalendarExportPresentation/VenueMapPresentation own editor/map actions and failure presentation, and BusyCalendarLifecycleModifier owns detail attach/detach. CalendarPreferencesLifecycleModifier owns the single app scene/EventKit bridge; CalendarPreferences gates background work and resumes attached queries once. Card Widgets resolve exact phase/venue indices and delegate maps to the same feature. Routes only select destinations. No new deep-link scheme, API client, DI library or state framework is introduced.

## Privacy and platform behavior

CheckCalendarOverlap retains actor-confined EventKit read access. The shared features/checkCalendarOverlap CalendarPreferences owns first-use consent, permission requests, two boolean preferences and foreground gating; detail BusyCalendarSession owns selected-day queries, cancellation generations and ephemeral intervals, without its own consent/request flow. See [calendar lifetime](docs/CALENDAR-LIFECYCLE.md). OFF, close, background, revocation and stale generations clear/cancel as before. Event metadata is neither displayed nor persisted/sent to a server. Tests use mock busy providers, never personal calendar data.

Calendar export keeps the original source-only HTTP(S) notes, exact phase/date/timezone and existing event URL semantics. The system editor handles edits; app code never saves/removes events directly or requests write-only permission. Maps use exact valid phase/venue coordinates and existing failure feedback. The display retains field-boundary schedule/place rows, native icons, URL host labels, paging/inner AX scroll, doubletap and native controls.

## Verification

```sh
bash apps/ios/tests/run_architecture.sh
bash apps/ios/tests/test_layer_distance_gate.sh
bash apps/ios/tests/run_swiftlint.sh
bash apps/ios/tests/run_standalone.sh
bash apps/ios/tests/run_busy_calendar.sh
bash apps/ios/tests/run_detail_presentations.sh
```

The macOS architecture package pins Harmonize and SwiftSyntax and scans only this checkout's production source. All physical paths use the final layout; no migration mapping remains. [Current execution evidence](docs/evidence/two-layer-composition/README.md) distinguishes verified model/cache/startup/Observation checks and Simulator build/screenshots from UI input regressions blocked by the local Xcode27 Simulator environment. Prior feature evidence remains under docs/evidence/issue-02, issue-10 and card-lines; those older runs are not new validation.

CalendarConnectionControl/State and BusyTimeStatusView belong to CheckCalendarOverlap. CalendarOverlapTimeline injects query status/retry into Shared EventDayTimeline through a ViewBuilder slot. Generic timeline/date/anonymous interval geometry remains Shared; it neither requests permission nor decides retry behavior.

The pure UI contract follows declaration identity, not the Content filename suffix. Renaming a file preserves enforcement; stale/duplicate/missing declaration entries fail. Review must register newly introduced pure presentation components and check inferred effects. State files retain their main struct/name/location contract while supporting types may use meaningful names without a State suffix.
