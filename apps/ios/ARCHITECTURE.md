# Native vertical slice (2026-09-27)

Xcode 27 (27A266a), Swift 6 language mode / compiler 6.4, SwiftUI + Observation + SwiftData; iOS 18 minimum. No third-party runtime dependency. Apple current system-requirements page lists Xcode 27 as stable; 27.1/27.2 are beta: https://developer.apple.com/xcode/system-requirements (checked 2026-09-27).

- `app`: root lifetime, navigation, account/API orchestration. One local container and one session owner.
- `pages`: profile, QR and wallet screens, view-local editing/selection state.
- `widgets/card`: reusable displayed public card with stable name/job header.
- `features`: ProfileState owns account-scoped saved master profile; GuestLibraryState owns guest IDs and success-only import removal; ExchangeState owns durable account-bound pending request. Auth and card composer own transient form state.
- `entities`: pure profile, card and guest domain values, Codable API wire names. Receipt composition is in features/wallet (card plus exchange context).
- `shared`: URLSession HTTP, Keychain, SwiftData document storage, common profile snapshot fields.

Models are immutable/value-semantic wire/domain values; State classes own save/rollback and lifecycle. UI never mutates SwiftData objects. Profile edits use a separate value draft and dismiss only after save. Persistence errors never fall back silently to an empty in-memory store. SwiftData store read corruption displays a blocking storage error and preserves original disk bytes.

Account profiles are separately keyed. Guest profile drafts are not silently uploaded after login; a newly selected account loads GET /profile. Existing account-local edits remain local until the explicit publishing action uploads the master profile using the strict PUT request shape. Keychain sessions are namespaced by configured API URL and device-only when-unlocked accessibility. No tokens are written to preferences, logs or SwiftData.

SwiftData document keys store typed Codable values with explicit save/rollback. Guest identity records contain only cardId/context/savedAt; temporary public-card display cache lives only in import view state. A successful import removes only selected IDs with a server receipt; network or local save failure preserves IDs. Idempotent server retry handles success followed by local save failure.

Pending send payload and request ID are persisted before HTTP and scoped by account. The same ambiguous send retains its ID across restart; confirmed success clears it. No local tap changes reciprocal state. Remote list refresh after confirmed delivery is independently reported.

The historical source tree, SwiftLint installation/configuration, Harmonize checker and architecture tests were deleted before this task; none are claimed restored. This slice uses the FSD semantic layers above, but direct page-to-domain value dependencies remain and the historical two-layer distance rule is not mechanically enforced. Reintroducing structural tooling is tracked in the handoff; adding pass-through wrappers solely to satisfy folder distance is deliberately deferred to coordinator review.
