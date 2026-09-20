# iOS two-layer / root composition verification

Executed 2026-09-16 KST in `/Users/jominjun/Documents/dearby/dearby-ios-architecture-tests`, branch `feat/ios-two-layer-composition`, freshly based on fetched `origin/main 45a7c96`. The previous branch/history was preserved. No push, merge, shared/root policy or other-checkout changes.

## Verified

| Check | Result and evidence |
| --- | --- |
| Harmonize/SwiftSyntax | 12 tests, 3 suites PASS; all 36 source/target layer pairs for symbols and imports, exact provider path/public API boundaries, aliases/reexports, sibling slices, physical inventory and existing purity rules. [Log](architecture.txt) |
| PR1 baseline compatibility | Final refactored production + final public-api passed origin/main checker (9 tests) from /tmp; original checker files restored afterward and new 12-test gate rerun PASS. [Log](baseline-gate.txt) |
| Real production probes | Route NoticeModel and Shared import rejected; same NoticeModel in provider accepted; provider internal NoticeRecord and provider View rejected; restored production passes. [Log](negative-gate.txt) |
| SwiftLint 0.65.1 strict | 0 violations in 152 Swift files. No new exclusions or disabled rules. [Log](swiftlint.txt) |
| Standalone + disk/cache | Existing favorites, repositories, card/date/place, sourceURL/calendar drafts, maps, SwiftData L1/L2/source, corruption/missing, snapshot transaction rollback PASS. Intentional invalid-store fixtures emit CoreData errors before PASS. [Log](standalone.txt) |
| Card route projection | Added exact name/coordinate equality against each physical phase/venue on both JSON samples; full VM regression recompiled/reran PASS. [Log](card-routes.txt) |
| Startup / Observation | Storage construction failure, source-read failure with retained store, retry, ready-session idempotence; independent discovery/favorites/open-detail subscriptions notified on save and remove; invalid map indices PASS. Included in standalone log. |
| Busy calendar | Consent/first-use/later/relaunch; OFF/background/revocation, request continuation, failure/retry, stale queries and overlap/DST PASS. [Log](busy_calendar.txt) |
| Detail/time | Date precision, reversed/invalid periods, explicit place fields/URL validation, timeline clipping/selection/DST PASS. [Log](detail_presentations.txt) |
| Simulator build/run | Final build succeeded, PID22874, iOS26.5 `Dearby-FSD-Verify`, UDID `B04DEBB6-53B1-4CB1-858C-8C290846D4AB`; checkout-local derived data `apps/ios/build/two-layer-derived`. [Build log](simulator-build.txt) |
| Source preservation | CalendarDraftMapper, CalendarEditorRequest, BusyCalendarSession, EventKitBusyProvider, SwiftDataSnapshotStore byte-identical to base; calendar preferences/protocol identical moves. [Comparison](behavior-preservation.txt) |

Architecture/lint/busy/detail final runners also executed with cwd `/tmp` using absolute script paths. Standalone final exit0 was observed. Core algorithms/Shared design controls remain unchanged; Entity/Feature composition preserves their use. Unit subscriptions verify runtime Observation propagation without a forced root read; they do not prove lazy SwiftUI tab rendering.

## UI evidence and limitation

The final installed app's screenshot/AX show the first notice, card schedule/map action, saved status, tabs and first-use calendar consent. [Device screenshot](first-prompt.png). The save status was already present in the verification simulator; it is **not** proof of a new save interaction.

MCP tap/touch returned success receipts, but subsequent screenshot/AX remained on the first-use alert. Orca emulator attach failed because its helper expected the old Xcode SimulatorKit path; the running Simulator window was black and its executable path no longer existed after the Xcode27 update. Coordinator confirmed this local environment limitation and directed completion using the automatic regressions/build/source evidence. No Xcode/helper patching, device reset or claimed UI success.

**Not verified this run:** actual first-use consent click, discovered-tab doubletap/paging, lazy tabs discovery→save→favorites→remove→discovery, detail UI while favorites change, OS editor/Maps interaction, large-text gestures. These require a working Simulator input environment. Existing prior UI evidence is historical and is not claimed as this run's result. The app/device are preserved.

## Review / integration

Component commits: checker `d18a27f`, startup/routing `8de3af7`, detail/calendar `d454e18`, then card/native composition and the final public API/evidence (see branch log). The component refactors share Page/Entity contracts; only their full cumulative state is validated, not individual intermediate commits.

**Two PRs are supported by the baseline-gate experiment:** PR1 contains all three refactor commits plus final public-api and related docs/tests; PR2 adds checker `d18a27f` and root's common policy/CI documentation. Cherry-pick the refactor commits before the checker when building these branches (current worker history records checker first). PR1's final products passed the original main architecture suite; PR2's final state passed the new suite and real probes. Do not publish the partial root/detail/card intermediate states separately. No pushes/merges were performed.

Move/update policy-only documentation as needed when constructing the two PR descriptions; root owns common docs and integration. A later UI evidence follow-up still requires a working input environment. No new API/async cancellation policy or real-calendar testing is claimed.
