# SwiftLint policy and reproducible setup

## Run

```sh
bash apps/ios/scripts/setup_swiftlint.sh
bash apps/ios/tests/run_swiftlint.sh
python3 -I apps/ios/tests/check_swiftlint_runner.py
```

All commands resolve this checkout from their script location and work outside the repository cwd. Installation is explicit and separate from linting. The runner does not download, use Homebrew/PATH fallback, auto-correct source, or update a baseline. It requires the exact local version and runs `lint --strict --no-cache`; warnings fail the gate. Root owns CI wiring in the existing required iOS architecture job: setup → `python3 -I apps/ios/tests/check_swiftlint_runner.py` → strict lint → existing architecture/regression scripts.

## Pinned tool

[Official SwiftLint release 0.65.1](https://github.com/realm/SwiftLint/releases/tag/0.65.1), `portable_swiftlint.zip`, SHA256 `c1e429b0599cf1b516f369a2d9ec04eaf0e436f3c12b637df8851fa52ff694d0`.

The setup script verifies the archive before extracting/executing, verifies the binary version, and installs the executable/license in ignored `apps/ios/build/tools/swiftlint-0.65.1/`. Failure leaves an existing installation intact until successful validation. Upgrades must update the version/checksum and runner contract test together; never use a moving latest URL. macOS/Xcode is required; this task verified Xcode 27.0 (27A266a), Swift 6.4. Hosted older toolchains are a separate coordinator CI check.

## Scope and rules

The [configuration](../.swiftlint.yml) includes all 109 app Swift files plus 18 project-owned test/package Swift files, including the Harmonize/SwiftSyntax test implementations. Only dependency/generated SwiftPM `.build` and `.swiftpm` trees are excluded. Fixture declarations stored inside Swift strings remain strings; tests themselves are linted. There is no baseline, production slice exemption, or global disable command.

This is an explicit initial policy of 61 `only_rules`, rather than SwiftLint's complete default configuration. It checks language pitfalls (casts/try, duplicate declarations/conditions/imports, observers, unused values and control flow) and local readability (spacing, braces, statement placement, return syntax, trailing whitespace/newlines). FSD layer direction, same-layer slices, domain/state boundaries, physical names and public API are exclusively covered by the architecture gate.

Length/complexity/identifier thresholds and whole-file formatting conventions are not enabled in this initial adoption: SwiftUI builders and exhaustive fixtures need a separately agreed readability/refactor policy. The policy allows both explicit `nil` defaults and trailing collection commas, preserving current value/default declarations and small-diff lists. It does not claim those unselected rules pass. Future rule expansion should ship with focused fixes and its own review.

Two narrow source annotations preserve intentional behavior:

- `ContentView` keeps `let _ = favorites.ids`: a declaration reads shared Observation state before lazy ViewBuilder closures without adding a Void view expression. Only that line disables `redundant_discardable_let`.
- Existing standalone JSON mutation tests treat the bundled fixture schema as a precondition. Individual `as!` lines disable `force_cast`; production casts remain checked. Each affected test file explains this reason. `superfluous_disable_command` rejects stale/invalid suppressions.

The fixes are local whitespace/brace layout, a for/where equivalent in the architecture checker, and the above comments. No UI, cache, domain or data contract behavior changes. Do not run `--format` over the app as part of this gate.

## Validation

[Execution evidence](evidence/swiftlint/README.md) records actual strict lint, missing/wrong tool failures, deliberate app/test violations, and regression/build results. `check_swiftlint_runner.py` creates an isolated miniature checkout and verifies missing binary exit1, wrong version exit1, strict whitespace violations in both app and tests exit2, and clean restoration exit0 from a different cwd. It uses the same checked-in runner/config and installed binary.
