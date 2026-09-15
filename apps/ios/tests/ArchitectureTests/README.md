# iOS architecture gate

Run `bash apps/ios/tests/run_architecture.sh` from the checkout root, or invoke its absolute path from any cwd. Failures propagate with nonzero exit. The package is macOS test-only and has no app runtime dependency.

Policy, approved exceptions and public contracts: [iOS FSD policy](../../architecture/README.md). The latest design supersedes blanket rendering model/VM bans and flat Widget paths; their old fixture expectations have been replaced with explicit allowed/violating pairs.

Harmonize 1.2.1, SwiftSyntax 601.0.1 and Yams 5.4.0 are locked in Package.resolved. Swift 6.1+/macOS13+; local tests use Xcode26.6/Swift6.3.3. The runner requires resolved versions. No app SDK is required for architecture tests.

Harmonize `SwiftSourceCode` provides declaration/import/conformance/modifier models. `parent == nil` filters nested declaration names because includeNested:false alone still included collected nested names in 1.2.1. SwiftParser/SwiftSyntax supplement actor/typealias/function declarations, conditional declarations, parse errors and identifier tokens. Tests reject distant (more than two lower layers), upward, cross-slice, unexported references, pure UI repository/OS/storage/network use, invalid paths and invalid public manifests; every rule has allowed and violating fixtures. LayerDistanceTests covers all 36 layer pairs for identifiers and imports, exact provider paths/public-api boundaries, provider UI and alias/re-export facades. SourceInventory anchors to this checkout and fails on missing layers/symlinks; other worktrees and dependency fixtures are excluded by the fixed production root.

Python retains calendar permission/editor-save/remove/alarm and domain SwiftData guards. Standalone tests protect cache promotion/rollback/digest, shared favorites and data contracts; architecture tests do not prove runtime behavior. Identifier matching is not a compiler symbol resolver (member/local name collisions, inferred references, aliases, macros and dynamic calls remain review limitations). Neither this gate nor Swift internal visibility enforces module-level FSD isolation.

[Previous 2026-09-15 verification](VERIFICATION.md) is historical. Current two-layer/startup/composition evidence is in [the execution report](../../docs/evidence/two-layer-composition/README.md). Run `bash apps/ios/tests/test_layer_distance_gate.sh` for temporary real-source violation probes with cleanup.
