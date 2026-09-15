# iOS architecture gate

From the repository root:

```sh
bash apps/ios/tests/run_architecture.sh
```

The script also works by absolute path from another working directory. It runs **all** existing Python FSD rules and their fixtures, followed by the macOS Swift Testing package. Either failure returns a nonzero exit status. Do not pass `--slice`, filter tests, or allow failure in CI. The first run downloads and builds dependencies; subsequent runs reuse this checkout's `.build` directory.

## Toolchain and dependency boundary

- Host: macOS 13+, Swift 6.1+ (Xcode 16.4 baseline). Swift Testing comes with the toolchain.
- Local validation host: Xcode 26.6 / Apple Swift 6.3.3. Xcode 16.4 compatibility must also be verified by the coordinator's CI job; it is not locally installed.
- [Harmonize 1.2.1](https://github.com/perrystreetsoftware/Harmonize/tree/1.2.1), exact dependency, revision `e0122b62af0da04e95b4284c940c3e37dedb82f8`.
- Its inspected [manifest](https://github.com/perrystreetsoftware/Harmonize/blob/1.2.1/Package.swift) requires macOS 13 and SwiftSyntax from 601.0.1. We additionally pin SwiftSyntax to 601.0.1 to keep the Swift 6.1 parser baseline. `Package.resolved` locks Yams 5.4.0 too; the runner uses `--force-resolved-versions`.
- This is a test-only package with no product or app-target dependency. The app project, runtime packages, iOS SDK, UI and production source are untouched. No app build or simulator is needed for this gate.

## What Harmonize actually checks

Rules use the public `SwiftSourceCode(source:)` API and its `imports`, `structs`, `classes`, `enums`, `extensions`, `name`, `modifiers`, and `inheritanceTypesNames` models. Swift Testing `#expect` records failures from the shared rule evaluator, so fixtures exercise the exact production predicates. These are current Dearby conventions, not universal Swift requirements:

| Rule ID | Enforced convention |
| --- | --- |
| `domain-import` | Entities/*/Model must not import SwiftData, including selective imports. |
| `ui-import` | UI folders and widget declarations directly conforming to View/SwiftUI.View must not import SwiftData, EventKit, EventKitUI, MapKit, CoreLocation or UIKit. |
| `state-struct` | Top-level Pages/Widgets declarations ending in State are value structs. |
| `state-location` | Such State declarations live in Pages/Slice/Model or flat Widgets/Domain/Widget. File renaming cannot evade this check. |
| `state-name` | A presentation *State.swift file declares its matching struct; its other top-level struct/class/enum names also end in State. Nested helper types are not subject to this naming rule. |
| `viewmodel-class` | Top-level Pages/Widgets declarations ending in ViewModel are classes. |
| `viewmodel-location` | These ViewModels live in Pages/Slice/Model or flat Widgets/Domain/Widget. |
| `viewmodel-name` | A presentation *ViewModel.swift file declares its matching class. |
| `shared-access` | Top-level Shared/UI structs/classes/enums do not expose public/open/package access; internal/private/fileprivate remain allowed. This is not a compiler-enforced slice boundary. |

Every rule has a valid and violating fixture. Fixtures include qualified/generic View declarations, conditional/selective imports, comments, and the distinction between a generic View constraint and direct conformance. Production assertions also require real State/ViewModel/Shared UI/domain populations; an empty scan cannot pass.

## Existing guard mapping — every rule retained

`../check_fsd_boundaries.py` remains unchanged, including its full `self_test()` suite. No equivalence or deletion is claimed.

| Existing Python guard | Harmonize coverage / retained responsibility |
| --- | --- |
| Known layers, Layer/Slice/UI\|Model\|API and flat Widgets/Domain/Widget paths | State/VM declaration-location checks supplement this; Python retains all general path checks. |
| Upward references and same-layer cross-slice references, widget domain+widget identity | Python retains the complete lexical declaration/reference graph. Harmonize does not resolve the app's cross-file references. |
| Independent Notice/Organization entities; Feature must not use Page State | Retained Python graph checks. |
| Rendering UI excludes raw models/repositories/ViewModels and API/non-UI Feature references | Python retains name/reference checks and the Page-only SaveOrganizationResult exception. Import rules add syntax checks but are not equivalent. |
| Domain Model excludes SwiftData/ModelContext/ModelContainer | Harmonize handles imports; Python retains identifiers/type usage. |
| UI excludes storage/resources/OS APIs including UserDefaults/Bundle/URLSession/openURL | Harmonize handles listed imports; Python retains all token checks, including APIs from otherwise allowed Foundation/SwiftUI. |
| Calendar permission/editor/save/remove/alarm constraints and exact read-adapter exception | Entirely retained in Python. |

Neither tool proves Swift compiler-level dependencies, alias resolution, macro expansion, dynamic dispatch, inferred dependencies, or API runtime behavior. Harmonize recognizes direct syntactic View conformance; aliases/inherited conformance are not resolved. The Python guard is lexical and can have false positives/negatives with complex Swift syntax. Existing guard fixtures still cover its multiline/generic rendering detection, allowed state/callback inputs, graph restrictions and permission exceptions. Review remains necessary for unsupported semantics.

## Source scope and sensitivity

The source inventory is anchored to the compiled test's `#filePath`, resolving exactly this checkout's `apps/ios/Dearby`. It never asks Git for the common repository directory and never searches a monorepo/worktree parent. Consequently `apps/ios/tests` fixtures, `.build/checkouts`, sibling worktrees and other platform sources are outside the scan. A fixture builds a miniature checkout with these decoys and checks exact inventory. Missing roots, missing/empty FSD layers, empty populations and symlinks fail closed. New production Swift files are included automatically.

We intentionally use Harmonize's explicit source API instead of `Harmonize.productionCode()` and its `.harmonize.yaml` root discovery. No global exclude configuration can accidentally hide a production file, and parsing fixture strings cannot broaden production scope.

For an end-to-end check, temporarily add `public struct ArchitectureSensitivityProbe {}` under `Dearby/Shared/UI/ArchitectureSensitivityProbe.swift`, run the entry script and require `shared-access` failure; remove it even on interruption and rerun clean. This violation passes the older Python guard but must fail Harmonize. Do not commit the probe. A Python-only upward-reference probe can separately verify retention of semantic guard coverage.

Actual local results and reversible violation checks: [VERIFICATION](VERIFICATION.md).
