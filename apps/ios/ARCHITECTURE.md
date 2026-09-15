# iOS domain FSD architecture

Updated 2026-09-16 after the approved domain FSD migration. This replaces the former flat Widget and blanket rendering model/VM rules. The app remains one SwiftUI/Observation module; folder boundaries are enforced by tests and review, not the Swift compiler.

All custom folders below `Dearby/` use lowerCamelCase, including `ui`, `api`, and `resources`. Swift filenames/types and the Xcode target/project root retain their names. Only the root `Assets.xcassets` tool-managed structure keeps Xcode asset naming. Both Python and Swift physical inventories reject invalid directories, including empty/non-Swift directories; there is no FSD source exclusion.

## Production layout (Dearby/)

```text
app/
  entrypoint/        DearbyApp
  routes/            ContentView, NoticeDestinationView, NoticeDetailDestination
  providers/         snapshot/container/session, calendar preferences, injected provider factories
pages/
  discovery/ui/      feed paging, save-result feedback, navigation callbacks
  favorites/ui/      saved organization list
  noticeDetail/
    ui/              full detail and page-local sections
    model/           NoticeDetailViewModel/State, place presentation
  settings/ui/       connection preference presentation
widgets/
  noticeCard/
    ui/              connected NoticeCard, pure NoticeCardContent and local schedule/save views
    model/           NoticeCardViewModel, NoticeCardState, schedule/place states
  favoriteOrganizationCard/
    ui/              connected card, pure FavoriteOrganizationCardContent
    model/           FavoriteOrganizationCardViewModel/State
features/
  saveOrganization/model/          validated save/remove action facade and result
  addToCalendar/model|api|ui/      draft/date policy, OS editor bridge, add button
  openLocation/api|ui/            exact venue map URL/launcher and button
  checkCalendarOverlap/model|api|ui/ ephemeral busy query/session, authorization/retry UI, EventKit read adapter
entities/
  notice/model|api|ui/             independent notice values, repository/cache/source, pure classification
  organization/model|api/         organization values, repository/cache/path resolution
  favorite/model|api/             single observable ID set and persistent repository
shared/
  ui/                            native controls, rows, generic timeline presentation
  lib/                           domain-free date/time/display values and interval calculations
```

Empty segments are not created. One-use card content stays in its Widget. Independent UI types added by this migration use separate files; title Text is not extracted into a wrapper.

## Direction, segments and public API

Every lower layer is allowed: App→pages/widgets/features/entities/Shared, Pages→widgets/features/entities/Shared, Widgets→features/entities/Shared, Features→entities/Shared, Entities→Shared. Same-slice UI→Model→API is allowed; same-layer different-slice references are forbidden. App and Shared use purpose segments without domain slices.

The executable [public-api.json](architecture/public-api.json) names cross-slice contracts. Swift `internal` does not imply permission to reach another slice's internals; `public` is not required to be an FSD entrypoint. NoticeRecord, OrganizationRecord and NoticeStorageCodec are internal. App uses NoticeCacheStorage/OrganizationCacheStorage schema/deletion/fingerprint contracts instead. The checker rejects unknown/duplicate manifest entries and ambiguous declarations.

Entity UI may receive its own Model. shared/Entity UI and explicit `*Content.swift` presentation components are pure: values/callbacks only, no repository/storage/network/OS work. Widget/Page connected UI may access its own VM and lower-layer public contracts. Shared has no upper-domain dependencies. The concrete policy and checker limits are in [architecture/README](architecture/README.md).

## State and lifetime

- NoticeModel carries notice values and organization IDs/roles, never OrganizationModel or repository lookup. Organization stays independent; VMs combine them into screen State.
- Connected NoticeCard reads its VM and delegates save; Discovery retains paging, save feedback/haptic trigger and detail route callback. Connected FavoriteOrganizationCard reads its VM and delegates explicit remove.
- entities/favorite/FavoriteOrganizationStore is the sole observable saved-ID owner. The shared features/saveOrganization/FavoriteOrganizations facade validates organization targets and exposes that state without a copy. Existing UserDefaults key/array restoration, idempotent insert, explicit delete and unresolved no-write semantics remain.
- app/providers creates/shares repositories and sessions. notice/Organization each retain L1→SwiftData L2→bundled mock source. External success is explicitly persisted before L1 promotion. Missing versus error, snapshot digest/schema and coordinated rollback remain unchanged. Entity cache facades never save independently; App commits/rolls back the snapshot transaction and replaces sessions after success.
- app/routes owns destinations and lifecycle wiring. Calendar editor and map behavior adapters live in Feature/API; routes supply callbacks and present resulting UI. No new deep-link scheme, API client, DI library or state framework is introduced.

## Privacy and platform behavior

CheckCalendarOverlap retains actor-confined EventKit read access. Global CalendarPreferences owns first-use consent and two boolean preferences; detail BusyCalendarSession owns ephemeral intervals. OFF, close, background, revocation and stale generations clear/cancel as before. Event metadata is neither displayed nor persisted/sent to a server. Tests use mock busy providers, never personal calendar data.

Calendar export keeps the original source-only HTTP(S) notes, exact phase/date/timezone and existing event URL semantics. The system editor handles edits; app code never saves/removes events directly or requests write-only permission. Maps use exact valid phase/venue coordinates and existing failure feedback. The display retains field-boundary schedule/place rows, native icons, URL host labels, paging/inner AX scroll, doubletap and native controls.

## Verification

```sh
bash apps/ios/tests/run_architecture.sh
bash apps/ios/tests/run_standalone.sh
bash apps/ios/tests/run_busy_calendar.sh
bash apps/ios/tests/run_detail_presentations.sh
```

The macOS architecture package pins Harmonize and SwiftSyntax and scans only this checkout's production source. All physical paths use the final layout; no migration mapping remains. [Current execution evidence](docs/FSD-MIGRATION.md) includes simulator/violation sensitivity and limitations. Prior feature evidence remains under docs/evidence/issue-02, issue-10 and card-lines; those older runs are not new validation.

CalendarConnectionControl/State and BusyTimeStatusView belong to CheckCalendarOverlap. CalendarOverlapTimeline injects query status/retry into Shared EventDayTimeline through a ViewBuilder slot. Generic timeline/date/anonymous interval geometry remains Shared; it neither requests permission nor decides retry behavior.
