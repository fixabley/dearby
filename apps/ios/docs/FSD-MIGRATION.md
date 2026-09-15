# FSD migration — 2026-09-16

Base main f8f648c. Local Xcode26.6/Swift6.3.3. Current role: own apps/ios only.

## Common checker and storage composition prerequisite

The shared checker must precede file migrations so every intermediate component commit can pass. Exact old→new path mapping applies all rules without exclusions; it is removed after the last move. notice/Organization cache facades hide Record/Codec while preserving caller-owned snapshot commit/rollback and canonical fingerprint bytes.

Executed: run_architecture.sh (9 tests, positive/violating fixtures), full run_standalone.sh (favorites, organization, VM with both samples, calendar/map, notice/Organization disk caches, snapshot reopen/invalidation/rollback with both samples) passed. The injected invalid-directory tests print expected CoreData errors and finish PASS. This is a new execution, not copied prior evidence.

## Notice card

widgets/noticeCard/UI owns the connected NoticeCard and local display components; Model owns VM/State. Discovery passes the VM and receives save results for its existing feedback/haptic count. The widget reads its own VM and calls save; visual content, doubletap, schedule/map callbacks and AX paging remain the existing implementation. No Entity extraction was forced for single-use card content.

Card validation: architecture 9 tests and full standalone regression passed (104 files before UI-only split). Simulator build/run succeeded on dedicated Dearby-FSD-Verify B04DEBB6-53B1-4CB1-858C-8C290846D4AB, empty busy fixture, first feed and consent visible. CardContent/ScheduleList were split into separate files; Content suffix is explicitly protected as pure UI. The original title Text stays inside content.

## Favorite organization card and saved-ID entity

Widget ui/Model now form FavoriteOrganizationCard slice. The connected View reads its VM and delegates explicit remove; the pure Content stays local. entities/Favorite owns one observable ID set and existing UserDefaults contract/key, independent of Organization. features/SaveOrganization validates/resolves save meaning and returns the existing result; every VM still shares the same FavoriteOrganizations facade/store.

Favorite validation: full architecture and standalone (including shared Observation consumers, legacy UserDefaults reload, duplicate save, explicit remove, unresolved no-write and both snapshot rollback samples) passed after renaming the stored field to favoriteStore to disambiguate the retained calendar store.remove lexical guard. Simulator build/run passed. No old storage key or cache schema changed.

## Detail calendar/map actions and overlap feature

Calendar request/editor/delegate now belong to features/addToCalendar/API; CalendarAddButton is its UI entry. VenueMapLink/Launcher and VenueMapButton belong to features/OpenLocation api/UI. ReadCalendarBusy is renamed CheckCalendarOverlap with its unchanged provider/session cancellation/privacy behavior. Detail still owns display-only Page State/UI; App route owns presentation/lifetime and uses exported behavior contracts. OS bridges are confined to Feature API, never pure UI. No calendar data is accessed in verification: mock providers only.

Detail/action validation: architecture, full standalone (both calendar source-URL/map samples and disk regressions), run_busy_calendar.sh (consent, settings persistence, cancellation/stale generations, clipping/intersection and privacy-safe provider fixtures), run_detail_presentations.sh (presentation/timezone/timeline) all passed. Simulator build/run passed with empty mock busy provider; OS calendar access was not used.

## App entrypoint, routes and providers

DearbyApp belongs to Entrypoint; ContentView/NoticeDestinationView/NoticeDetailDestination remain Routes; snapshot/container/session/preferences/factory ownership lives in Providers. State/error/lifetime code is retained. Removed the unused App-level save relay now that the widget delegates to its own VM. Updated all executable scripts and source-inventory sentinel with the moved entrypoint. No URL scheme or external DI/state system added.

App validation: architecture/full standalone/busy/detail scripts passed at the moved paths; simulator build/run passed (no warnings/errors). Dedicated fixture-only simulator showed consent, feed and favorites; explicit remove immediately produced empty favorites. Full UI proof and final sensitivity tests follow below.

## Physical policy and actual UI verification

Removed migration-paths.json and all alias consumption; the test forbids reintroducing it. Final physical inventory: 108 Swift files. Architecture 9 tests passed. Four exclusive temporary production probes were run through the absolute runner from /tmp: upward reference, sibling Widget VM, Entity pure UI repository and private NoticeRecord; each returned exit1 with its exact rule. Every probe was removed in finally; final runner returned0 and all 108 original SHA256 hashes matched.

Dedicated Simulator B04DEBB6-53B1-4CB1-858C-8C290846D4AB used only --busy-calendar-fixture=empty. Verified: consent Later, favorites explicit remove→empty, feed save button→saved, first-card detail sheet and dismissal, physical upward swipe→second card, native Simulator double-click on DB title→saved button/feedback, favorites list contains KRC+DB, DB row→matching detail. See evidence/fsd/feed-doubletap.png, favorites-synchronized.png and favorite-detail.png. AX5 next control advanced to 3/4, with inner content scroll and separate navigation controls visible (ax5-next.png).

Verification environment changed externally at ~2026-09-16 00:41 KST: Xcode26.6 was replaced by Xcode27.0 (27A266a); xcrun/swift began failing exit69 until license acceptance. No license was accepted by this worker. All build/Swift passes above happened on26.6 before that change. Subsequent AX capture used Orca's existing Simulator window. The user subsequently accepted the license; final Xcode27 verification and font restoration are recorded below.

## Calendar connection and retry UI follow-up

Moved CalendarConnectionControl/State and BusyTimeStatusView to CheckCalendarOverlap. CalendarOverlapTimeline injects the unchanged query status/retry view through EventDayTimeline's generic ViewBuilder slot. Shared keeps date selection, interval geometry, anonymous busy data and pure block/summary rendering. The consent/status/retry wording and callbacks are unchanged. Local Python safety/inventory/fixtures passed on109 files; all four runner scripts passed bash syntax checks and git diff --check passed. New Swift fixtures and generic UI compilation subsequently passed hosted CI and local Xcode27 verification below. Root confirmed PR23 head a214cda has identical apps/ios source to local57aba2a and started run34990938224 (Xcode16.4 architecture/standalone/busy/detail plus Xcode26.6 Simulator build).

Further existing-app UI verification after the license change used Orca native accessibility only: Settings toggle OFF→ON→connected explanatory state→OFF, with the empty mock busy provider. This verifies the c703de3 running binary, not the later moved CalendarConnection UI. Ownership audit found75 changed paths, all apps/ios or the two permitted iOS role documents.

Root reported hosted app build success for the exact final source: [PR23 run34990938224, job104455103242](https://github.com/fixabley/dearby/actions/runs/34990938224/job/104455103242), macOS26/Xcode26.6. This covers the generic timeline slot and moved Feature UI; it is CI evidence, not a local27.0 run.

## Final verification after user license acceptance — 2026-09-16 01:03 KST

Local Xcode27.0 (27A266a), Swift6.4 (swiftlang-6.4.0.34.1): all four scripts completed successfully on the final109 production files. Architecture ran9 Swift Testing tests/2 suites plus Python fixtures. Standalone includes both sample datasets, cache/snapshot reopen/rollback, favorites/Observation and original calendar URL/map checks; busy includes failed→refresh→ready and cancellation/privacy boundaries; detail includes timeline/date precision/DST. Logs: /tmp/dearby-fsd-xcode27-{architecture,standalone,busy,detail}.log. Expected injected CoreData invalid-path diagnostics are followed by PASS.

Local build_run_sim succeeded (39.1s, PID20997) with the final Calendar Feature UI and generic timeline. Build log: ~/Library/Developer/XcodeBuildMCP/workspaces/dearby-ios-architecture-tests-2e6f4371f452/logs/build_run_sim_2026-09-15T15-53-36-532Z_pid21361_2a2d919c.log. Root separately reported the full hosted run34990938224 successful: Xcode16.4 architecture/standalone/busy/detail plus Xcode26.6 Simulator build, exact source a214cda matching57aba2a.

The old Simulator frontend lost input after the Xcode replacement. Xcode27 ships DeviceHub.app under Contents/Applications; opening Device Hub restored native AX interaction with the same dedicated simulator. The first content-size reset had failed while the simulator was shutdown; after boot, setting large and querying large both succeeded. Final device is B04DEBB6-53B1-4CB1-858C-8C290846D4AB, iOS26.5, large, fixture-only; no personal calendar or physical device was used.

Final running-build UI checks: consent Continue; Settings ON→OFF→ON with expected status; first notice detail and generic application timeline; activity CalendarOverlapTimeline showing exact14:00–16:00 block and no-overlap explanatory status; restored KRC+DB favorites; removing KRC immediately updates feed to unsaved; save restores saved state/feedback; terminate/relaunch restores both favorites; DB favorite row opens matching DB detail. Empty fixture final relaunch PID31785 is left running. Memory calendar preferences intentionally reset per fixture launch; production boolean persistence is verified by regression tests.

Failure fixture displayed the safe error text and retry button (xcode27-calendar-failure.png), never treating an error as no events. Retry-click completion is **not** claimed: Device Hub AX indexes changed during scroll and the click attempts were rejected; failed→refresh→ready is covered by the passing busy regression. Physical doubletap and AX5 paging were verified on the earlier26.6 build; the final follow-up changes only calendar UI ownership, and final27 save-button synchronization was rerun. See xcode27-calendar-timeline.png and xcode27-favorites-restored.png for final-build visual evidence.

No outstanding implementation blocker remains. No compiler-level FSD isolation is claimed: the AST graph is syntactic, with documented identifier/alias/macro/dynamic-resolution limits. Personal EventKit reads/writes and external map/calendar handoff were not exercised; pure mappers/providers and source URL notes were regression-tested. Root owns final PR review/integration; no merge was performed by this worker.
