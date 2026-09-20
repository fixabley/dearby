# iOS FSD policy

2026-09-16 approved domain FSD design. Run `bash apps/ios/tests/run_architecture.sh`.

- Default: only the nearest two lower layers: app→pages/widgets, pages→widgets/features, widgets→features/entities, features→entities/shared, entities→shared.
- Only the exact app/providers segment may directly reference any lower layer for construction, lifetime and injection. routes, entrypoint, routes/providers and providersExtra are not exceptions. The exception does not relax public-api/internal visibility or purity rules; provider View/ViewModifier declarations are rejected. Review still checks that providers implement no feature UI or OS action behavior.
- Layer-named imports follow the same distance/direction policy; Apple framework imports are unaffected by distance. Existing UI/SwiftData import safety rules still apply.
- Cross-slice aliases to production declarations and @_exported imports are rejected; alias/re-export facades are not public API escape hatches.
- Same slice can use its ui/model/API; another slice in the same layer is forbidden.
- App: entrypoint/routes/Providers. Shared: purpose segments. Others: Layer/Slice/UI|Model|API|Lib|Config.
- Pages/Widgets may additionally reference exported declarations physically in `shared/ui/` (native design components/tokens). Shared api/lib/model and blanket layer imports receive no exception. Shared UI remains pure.
- Only `app/routes/NoticeDestinationView.swift` may additionally reference exported `CalendarPreferences` from its exact Feature model file, to pass the app-owned instance into the detail Page. Routes reject direct storage/network/OS identifiers and API declarations; they do not acquire provider privileges. Syntax checks cannot resolve inferred member operations, so review verifies that preferences are only passed, not used to request permissions.
- Entity/Shared UI are pure by default. `pure-ui.json` explicitly lists additional Page/Widget pure UI declaration names; their whole source files receive the same effect checks. File renames do not remove protection; renamed/deleted declarations leave a failing stale contract entry. Empty production lists and duplicate/missing/non-presentation entries fail. New value/callback-only components must be registered in the same change: their omission cannot be inferred from Swift syntax and must be reviewed. Editing this list is a policy change, not a way to silence a failure. `Content.swift` alone no longer imposes a contract. Entity UI may receive its own Model. No repository, storage/network/OS effects.
- Widget/Page UI may use its own VM and lower Feature/Entity exported contracts.
- `public-api.json` explicitly names cross-slice contracts independently of Swift access modifiers. Records/codecs are hidden; App coordinates Entity cache storage facades, which do not commit independently.
- Harmonize models discover declarations and imports; SwiftSyntax supplies identifier references, actors/typealiases/functions and parse errors. Unknown/duplicate exports and ambiguous top-level declarations fail.
- This is a syntax graph, not compiler name resolution: member/local name collisions, implicit types, inferred aliases, macro expansion, conditional compilation and dynamic dispatch need review. All conditional source branches are checked. Nested types are reached through their exported outer declaration.
- Python retains calendar permission/editor-write/alarm restrictions, domain SwiftData independence and nonempty layer inventory. UI blanket raw model/VM and flat Widget policies were superseded; graph/path/pure UI protection now lives in AST rules with paired fixtures.

## Completed migration

All source paths now use the new physical layout. No path aliases or exclusions remain; a test rejects restoration of migration-paths.json. The distance checker and component refactors are one integration unit. Component commits aid review; the full branch is the validated unit.

## Presentation declarations

The main State keeps its matching filename, struct value semantics and model location. Supporting types in that file need not all end in State; names describe their purpose. Existing ViewModels remain classes because they retain shared dependencies or observable/subscription state; this change introduces no speculative value-VM abstraction. VMs may be classes or structs, with matching main declaration/filename and model location; enum/actor VMs are not enabled. Observation correctness remains a compiler and runtime regression concern rather than a filename-based ownership claim.
