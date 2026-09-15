# FSD migration — 2026-09-16

Base main f8f648c. Local Xcode26.6/Swift6.3.3. Current role: own apps/ios only.

## Common checker and storage composition prerequisite

The shared checker must precede file migrations so every intermediate component commit can pass. Exact old→new path mapping applies all rules without exclusions; it is removed after the last move. Notice/Organization cache facades hide Record/Codec while preserving caller-owned snapshot commit/rollback and canonical fingerprint bytes.

Executed: run_architecture.sh (9 tests, positive/violating fixtures), full run_standalone.sh (favorites, organization, VM with both samples, calendar/map, Notice/Organization disk caches, snapshot reopen/invalidation/rollback with both samples) passed. The injected invalid-directory tests print expected CoreData errors and finish PASS. This is a new execution, not copied prior evidence.

## Notice card

Widgets/NoticeCard/UI owns the connected NoticeCard and local display components; Model owns VM/State. Discovery passes the VM and receives save results for its existing feedback/haptic count. The widget reads its own VM and calls save; visual content, doubletap, schedule/map callbacks and AX paging remain the existing implementation. No Entity extraction was forced for single-use card content.

Card validation: architecture 9 tests and full standalone regression passed (104 files before UI-only split). Simulator build/run succeeded on dedicated Dearby-FSD-Verify B04DEBB6-53B1-4CB1-858C-8C290846D4AB, empty busy fixture, first feed and consent visible. CardContent/ScheduleList were split into separate files; Content suffix is explicitly protected as pure UI. The original title Text stays inside content.
