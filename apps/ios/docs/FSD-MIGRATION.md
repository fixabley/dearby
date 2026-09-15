# FSD migration — 2026-09-16

Base main f8f648c. Local Xcode26.6/Swift6.3.3. Current role: own apps/ios only.

## Common checker and storage composition prerequisite

The shared checker must precede file migrations so every intermediate component commit can pass. Exact old→new path mapping applies all rules without exclusions; it is removed after the last move. Notice/Organization cache facades hide Record/Codec while preserving caller-owned snapshot commit/rollback and canonical fingerprint bytes.

Executed: run_architecture.sh (9 tests, positive/violating fixtures), full run_standalone.sh (favorites, organization, VM with both samples, calendar/map, Notice/Organization disk caches, snapshot reopen/invalidation/rollback with both samples) passed. The injected invalid-directory tests print expected CoreData errors and finish PASS. This is a new execution, not copied prior evidence.
