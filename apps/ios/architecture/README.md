# iOS FSD policy

2026-09-16 approved domain FSD design. Run `bash apps/ios/tests/run_architecture.sh`.

- App → Pages → Widgets → Features → Entities → Shared: every lower layer is allowed.
- Same slice can use its UI/Model/API; another slice in the same layer is forbidden.
- App: Entrypoint/Routes/Providers. Shared: purpose segments. Others: Layer/Slice/UI|Model|API|Lib|Config.
- Entities/Shared UI are pure. Entity UI may receive its own Model. No repository, storage/network/OS effects.
- Widget/Page UI may use its own VM and lower Feature/Entity exported contracts.
- `public-api.json` explicitly names cross-slice contracts independently of Swift access modifiers. Records/codecs are hidden; App coordinates Entity cache storage facades, which do not commit independently.
- Harmonize models discover declarations and imports; SwiftSyntax supplies identifier references, actors/typealiases/functions and parse errors. Unknown/duplicate exports and ambiguous top-level declarations fail.
- This is a syntax graph, not compiler name resolution: member/local name collisions, implicit types, aliases, macro expansion, conditional compilation and dynamic dispatch need review. All conditional source branches are checked. Nested types are reached through their exported outer declaration.
- Python retains calendar permission/editor-write/alarm restrictions, domain SwiftData independence and nonempty layer inventory. UI blanket raw Model/VM and flat Widget policies were superseded; graph/path/pure UI protection now lives in AST rules with paired fixtures.

## Temporary migration

`migration-paths.json` enumerates each existing file's destination; the tests inspect all files under canonical paths, never skip old files. It is removed after feature migrations. This shared checker/cache-facade prerequisite enables small component commits without disabling CI.
