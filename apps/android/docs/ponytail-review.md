# Prototype ponytail review — 2026-10-03

Reviewed the conversion diff and remaining callers, separately from correctness/UI validation.

- `pages/catalog/CatalogPage.kt`: shrink: title-only Row left after removing the hidden bookmark. Replace with a full-width Text; applied without changing card padding.
- `app/build.gradle.kts`: delete: unused preview and ViewModel Compose dependencies, plus Android resource unit-test setup. Use the plain lifecycle ViewModel dependency and Compose test dependencies; applied.
- Service repositories, cache models, clocks/timers, error/retry machinery, login/card/profile/QR UI and unused common UI functions were removed rather than mocked.

Final review: Lean already. Ship.

Tests and boundary checks are retained for the remaining behavior. This review does not substitute for build, lint, unit, emulator or visual checks; see VERIFICATION.md.

Lint cleanup uses the existing AndroidX `String.toUri()` extension. The intentionally preserved unused GitHub image asset and dependency upgrade notices remain warnings; no suppression or test disabling was added.

Runtime correction: the explicit Espresso 3.7 dependency is necessary on Android 16. Removing it selected an older transitive version and both UI tests failed with `InputManager.getInstance` reflection errors; restored it instead of suppressing tests.

Calendar reference follow-up: the timetable is one feature-owned visual component using the existing overlap values; it adds no calendar framework or data layer. Lean already. Ship.
