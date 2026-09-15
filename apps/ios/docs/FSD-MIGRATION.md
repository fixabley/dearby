# FSD migration — 2026-09-16

Base main f8f648c. Local Xcode26.6/Swift6.3.3. Current role: own apps/ios only.

## Common checker and storage composition prerequisite

The shared checker must precede file migrations so every intermediate component commit can pass. Exact old→new path mapping applies all rules without exclusions; it is removed after the last move. Notice/Organization cache facades hide Record/Codec while preserving caller-owned snapshot commit/rollback and canonical fingerprint bytes.

Executed: run_architecture.sh (9 tests, positive/violating fixtures), full run_standalone.sh (favorites, organization, VM with both samples, calendar/map, Notice/Organization disk caches, snapshot reopen/invalidation/rollback with both samples) passed. The injected invalid-directory tests print expected CoreData errors and finish PASS. This is a new execution, not copied prior evidence.

## Notice card

Widgets/NoticeCard/UI owns the connected NoticeCard and local display components; Model owns VM/State. Discovery passes the VM and receives save results for its existing feedback/haptic count. The widget reads its own VM and calls save; visual content, doubletap, schedule/map callbacks and AX paging remain the existing implementation. No Entity extraction was forced for single-use card content.

Card validation: architecture 9 tests and full standalone regression passed (104 files before UI-only split). Simulator build/run succeeded on dedicated Dearby-FSD-Verify B04DEBB6-53B1-4CB1-858C-8C290846D4AB, empty busy fixture, first feed and consent visible. CardContent/ScheduleList were split into separate files; Content suffix is explicitly protected as pure UI. The original title Text stays inside content.

## Favorite organization card and saved-ID entity

Widget UI/Model now form FavoriteOrganizationCard slice. The connected View reads its VM and delegates explicit remove; the pure Content stays local. Entities/Favorite owns one observable ID set and existing UserDefaults contract/key, independent of Organization. Features/SaveOrganization validates/resolves save meaning and returns the existing result; every VM still shares the same FavoriteOrganizations facade/store.

Favorite validation: full architecture and standalone (including shared Observation consumers, legacy UserDefaults reload, duplicate save, explicit remove, unresolved no-write and both snapshot rollback samples) passed after renaming the stored field to favoriteStore to disambiguate the retained calendar store.remove lexical guard. Simulator build/run passed. No old storage key or cache schema changed.

## Detail calendar/map actions and overlap feature

Calendar request/editor/delegate now belong to Features/AddToCalendar/API; CalendarAddButton is its UI entry. VenueMapLink/Launcher and VenueMapButton belong to Features/OpenLocation API/UI. ReadCalendarBusy is renamed CheckCalendarOverlap with its unchanged provider/session cancellation/privacy behavior. Detail still owns display-only Page State/UI; App route owns presentation/lifetime and uses exported behavior contracts. OS bridges are confined to Feature API, never pure UI. No calendar data is accessed in verification: mock providers only.

Detail/action validation: architecture, full standalone (both calendar source-URL/map samples and disk regressions), run_busy_calendar.sh (consent, settings persistence, cancellation/stale generations, clipping/intersection and privacy-safe provider fixtures), run_detail_presentations.sh (presentation/timezone/timeline) all passed. Simulator build/run passed with empty mock busy provider; OS calendar access was not used.

## App entrypoint, routes and providers

DearbyApp belongs to Entrypoint; ContentView/NoticeDestinationView/NoticeDetailDestination remain Routes; snapshot/container/session/preferences/factory ownership lives in Providers. State/error/lifetime code is retained. Removed the unused App-level save relay now that the widget delegates to its own VM. Updated all executable scripts and source-inventory sentinel with the moved entrypoint. No URL scheme or external DI/state system added.

App validation: architecture/full standalone/busy/detail scripts passed at the moved paths; simulator build/run passed (no warnings/errors). Dedicated fixture-only simulator showed consent, feed and favorites; explicit remove immediately produced empty favorites. Full UI proof and final sensitivity tests follow below.

## Physical policy and actual UI verification

Removed migration-paths.json and all alias consumption; the test forbids reintroducing it. Final physical inventory: 108 Swift files. Architecture 9 tests passed. Four exclusive temporary production probes were run through the absolute runner from /tmp: upward reference, sibling Widget VM, Entity pure UI repository and private NoticeRecord; each returned exit1 with its exact rule. Every probe was removed in finally; final runner returned0 and all 108 original SHA256 hashes matched.

Dedicated Simulator B04DEBB6-53B1-4CB1-858C-8C290846D4AB used only --busy-calendar-fixture=empty. Verified: consent Later, favorites explicit remove→empty, feed save button→saved, first-card detail sheet and dismissal, physical upward swipe→second card, native Simulator double-click on DB title→saved button/feedback, favorites list contains KRC+DB, DB row→matching detail. See evidence/fsd/feed-doubletap.png, favorites-synchronized.png and favorite-detail.png. AX5 next control advanced to 3/4, with inner content scroll and separate navigation controls visible (ax5-next.png).

Verification environment changed externally at ~2026-09-16 00:41 KST: Xcode26.6 was replaced by Xcode27.0 (27A266a); xcrun/swift began failing exit69 until license acceptance. No license was accepted by this worker. All build/Swift passes above happened on26.6 before that change. Subsequent AX capture used Orca's existing Simulator window. Font restoration/relaunch and new follow-up compilation are pending license resolution, not claimed passed.
