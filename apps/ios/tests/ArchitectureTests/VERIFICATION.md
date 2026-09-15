# Architecture verification — 2026-09-15

Base: `origin/main b0c76a10c2f6946d493cf25f313050e76ce22de7` (PR17 included). All runs below were executed in the worker's own `dearby-ios-architecture-tests` checkout. This report covers architecture tests, not app build/runtime tests.

## Actual local results

Toolchain: Xcode 26.6 (17F113), Apple Swift 6.3.3, arm64 macOS. Exact dependencies: Harmonize 1.2.1, SwiftSyntax 601.0.1, Yams 5.4.0; resolution is committed.

`bash apps/ios/tests/run_architecture.sh` returned 0:

```text
PASS: FSD lexical boundaries (102 Swift files; scope: all), guard fixtures
Harmonize production: 102 Swift files, 10 State structs, 3 ViewModel classes
Test supportedRuleSensitivity(fixture:) with 12 test cases passed
Test alternateWidgetViewDeclarations(declaration:) with 3 test cases passed
Test commentsAndStringsAreNotDeclarationsOrImports() passed
Test genericConstraintAloneDoesNotMakeWidgetAView() passed
Test inventoryIsCheckoutLocalAndFailsClosed() passed
Test run with 6 tests in 1 suite passed
```

The initial XCTest compatibility banner reports zero XCTest cases; the subsequent Swift Testing run above is the real suite. Do not interpret the banner as the test count.

The inventory fixture initially exposed Foundation's `/private/var` versus `/var` temporary path normalization difference. Standardizing enumerated file URLs fixed it; the full suite then passed. Missing production layers, invalid roots and source symlinks now fail. Decoy fixtures/dependency/other-worktree Swift files remain outside the selected checkout's production scope.

## Reversible production violation checks

Both probes used the untracked file `apps/ios/Dearby/shared/ui/ArchitectureSensitivityProbe.swift`, created exclusively and removed in a Python `finally` block. Every run invoked the **absolute path** to `run_architecture.sh` with working directory `/tmp`.

| Temporary source | Expected / observed |
| --- | --- |
| `public struct ArchitectureSensitivityProbe {}` | Python passed for 103 files; Harmonize reported `[shared-access] ArchitectureSensitivityProbe must remain internal to the app module`; script exit 1. |
| `struct ArchitectureSensitivityProbe { let session: NoticeSession }` | Python reported `upward reference to NoticeSession (app/NoticeSession.swift)`; script exit 1 before Swift tests. |
| Probe removed | Same absolute runner from `/tmp` returned 0; all 6 Swift Testing tests and the legacy guard passed. |

SHA-256 hashes of every original production Swift file were compared before probes, after each probe removal, and after the final passing run: all **102 files unchanged**. No probe remains.

## Scope and limitations

- `git diff origin/main -- apps/ios/Dearby apps/ios/Dearby.xcodeproj apps/ios/tests/check_fsd_boundaries.py` was empty.
- `git diff --check` passed. No app target/source change, no legacy rule removal and no application runtime dependency.
- Xcode 16.4 / Swift 6.1 is the manifest/parser baseline, but is not installed locally. The coordinator owns that hosted CI verification, workflow and combined PR. Do not claim it passed from this report.
- Rule coverage and known syntax/lexical limitations are in [README](README.md). Unsupported FSD semantic dependencies remain protected by the unchanged Python guard and review.
