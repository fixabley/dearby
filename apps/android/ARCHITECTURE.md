# Android ownership

One Gradle app module; folders do not provide compiler-enforced FSD isolation.

- `app`: Activity dependency construction, session-scoped ViewModel and route composition. A single active mutation gate serializes account changes and dependent requests. Per-screen temporary state stays in its page.
- `pages/profile`, `pages/login`, `pages/qr`, `pages/wallet`: screen UI and editor/picker state, callbacks to app orchestration. No database/network construction in UI.
- `widgets/card`: pure reusable CardState projection and CardContent; name/job header stays outside expanding details.
- `features/account`: authentication transport and aggregate session State; `features/wallet`: wallet/import/delivery transport; `features/guest`: device ID ownership and success-only import removal; `features/qr`: QR encode/decode/link adapter.
- `entities/profile`: full editable profile and source/cache repository. `entities/card`: published public snapshot models and public/owned card lookup repository. Card contact/history snapshot values are deliberately independent from private profile values, avoiding entity-to-entity ownership or hidden-field leakage.
- `shared/api`: generic HTTP/JSON only; `shared/storage`: Room records/DAO and Keystore; `shared/ui`: native reusable controls/theme.

Repositories are concrete and injected, with no empty interfaces/DI container. The app ViewModel coordinates the bounded first card-exchange flow; it does not own future catalog, calendar, push, or web-form logic. Shared profile/contact and card snapshot models are not renamed screen State; UI projections use CardState, WalletEntryState, ImportEntryState and AccountState. Public entry points are those types, page functions and feature/repository operations. Current imports are reviewed manually; no inherited Swift/FSD scanner is claimed installed. The old two-layer distance guideline is not yet automatically enforced, and pages still use typed profile/card input models for their editing boundaries; this is documented rather than claimed strict migration complete.
