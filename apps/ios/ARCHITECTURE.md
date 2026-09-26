# Native vertical slice (2026-09-27)

Xcode 27 (27A266a), Swift 6 language mode / compiler 6.4, SwiftUI + Observation + SwiftData; iOS 18 minimum. No third-party app runtime dependency. Apple current system-requirements page lists Xcode 27 as stable; 27.1/27.2 are beta: https://developer.apple.com/xcode/system-requirements (checked 2026-09-27).

## Ownership

- `app/entrypoint`: app launch and storage-load failure UI.
- `app/providers`: root container/session lifetime, account transitions and HTTP orchestration. Account-changing responses are rejected if the initiating token is no longer active.
- `app/routes`: composition of pages/widgets, sheets and URL routing; no direct storage/OS implementation.
- `pages/home`: five independent tab navigation stacks and connection banner, generic supplied content.
- `widgets`: profile, QR, wallet, card composition/selection and login presentation. Domain values are at most two layers below; actions arrive from providers or features.
- `features`: ProfileState owns account-scoped master profile; GuestLibraryState owns guest IDs and success-only removal; ExchangeState owns durable account-bound pending request; auth owns challenge requests; wallet owns card/context receipt composition; scanner owns VisionKit camera lifecycle.
- `entities/identity`: pure profile, public card, contact/history, guest ID/context and link values. Profile and published card are one identity domain slice with independent value types; there is no cross-entity lookup or repository coupling. CardView only renders the published snapshot.
- `shared`: URLSession HTTP, Keychain and SwiftData document primitives. No domain imports.

`architecture/public-api.json` enumerates exported declarations, `pure-ui.json` marks HomePage's effect-free contract. Restored Harmonize 1.2.1 + SwiftSyntax 601.0.1 checks declaration rules, explicit two-layer distance (only app/providers construction exception), same-layer slice isolation, exported APIs, effect-free UI and exact path conventions. Fixtures are retained from pre-web f1d9a63. Source inventory changed from deleted Dearby/ to Sources/; obsolete positive cardinality requirements for absent Notice ViewModels/shared design components were removed, while every declaration/boundary rule remains unchanged. Syntax references are not compiler type resolution; inferred dependencies and macros still need review.

## Persistence and transport

Models are value-semantic wire/domain values; feature State classes own save/rollback and lifecycle. UI never mutates SwiftData records. Profile editing has a separate draft and dismisses only after successful explicit save. Store creation/read errors block initial UI instead of silently falling back to empty memory. SwiftData read corruption preserves original disk bytes.

Profiles are keyed per account; guest drafts cannot silently upload after login. New account selection loads GET /profile. Existing account-local edits survive refresh until an explicit publishing action uploads the master profile using a request without id/updatedAt. Logged-out profile UI is a login invitation. Keychain sessions are namespaced by configured API URL and device-only when-unlocked accessibility. No tokens in preferences, logs or SwiftData. URLSession uses ephemeral storage. Authenticated 401 clears the active session/UI but preserves account drafts and guest IDs. Local logout works when remote revocation cannot be confirmed and says so.

Guest records contain cardId/context/savedAt, not private profiles or images. Public-card previews in import UI are transient. Import removes only selected IDs with a recognized success status and a valid receipt UUID. Transport, malformed response and local-save failure preserve original IDs. Idempotent server retry covers server success followed by local persistence failure.

Pending send payload/request ID persists before HTTP and is account-scoped. Same ambiguous send retains its ID across restart; confirmed delivery clears it. No tap changes reciprocal locally; server GET /wallet owns this state. Confirmed delivery and subsequent list-refresh failure have separate messages.

API config is an origin; /v1 is appended exactly once (also tolerates an existing /v1 prefix). Release URL validation requires HTTPS; Debug accepts loopback HTTP and only Debug plist allows local networking. Custom `dearby://card/<UUID>` links work between installed apps, validate query exclusivity and label length, and are not Universal Links. Context is unverified user input. HTTPS deployment/browser fallback is #43.

## Gates

`bash apps/ios/scripts/setup_swiftlint.sh` installs SHA256-verified SwiftLint 0.65.1. `bash apps/ios/tests/run_swiftlint.sh` runs the unchanged rule set with current source/test roots. `bash apps/ios/tests/run_architecture.sh` runs 16 AST/fixture tests including the real production graph. Unit tests exercise disk reopening, save rollback, account isolation, strict request shape, explicit JSON nulls, import retention, auth recovery, link validation and HTTP errors. The app's iOS SDK tests are separate from macOS architecture-package tests.
