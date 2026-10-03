# Android prototype structure

`app` owns Activity, navigation and two in-memory ViewModels. CatalogViewModel maps three fixed activities to catalog State and owns filters, sample application reports and bookmarks. DemoViewModel owns the fictional profile, own cards and received-card exchange groups. `DemoFixtures.kt` is plain data, not a provider or repository.

`pages/catalog`, `pages/profile`, `pages/qr` and `pages/wallet` render State and callbacks. They neither import each other nor call OS services. The reusable `widgets/card/cardContent` slice holds card display State, the mint card and stacked-card rendering. `shared/ui` holds theme, logo, buttons, ScreenHeader, ExampleBadge, InfoRow, PersonHeader, ContactSymbol, TimelineEntry, FormColumn and Field. Generic components accept strings and callbacks rather than domain models.

`features/application` handles an explicitly tapped safe external URL and local application demo. `features/calendar` compares fixed schedules and renders the timetable sheet. `entities/catalog/model` holds the three immutable activities and schedules. No repositories, providers, network clients, persistence or empty replacement layers exist.

ViewModels survive Activity recreation but reset on process restart. Navigation, forms and sheets use Compose remember; no SavedStateHandle or rememberSaveable writes are used. Card creation snapshots selected contacts/history. Editing changes the existing card ID without duplicating it. Received-card saves are idempotent; sample sending only marks the recipient reciprocal. Previous installed files are never opened or removed.

`scripts/check-fsd.py --self-test` checks package/path matching, downward dependencies, sibling-slice isolation, explicit exports and callback-only Page/Widget UI. Its 25 positive/negative cases cover the remaining slices, including shared card widgets; this lexical guard is not a compiler proof. Unit tests cover fixtures, filters, reset, interval boundaries, card snapshots/identity, deduplication, search and reciprocity. Instrumentation checks actual click flows and zero/nonzero overlap results; key card controls also run with Android font scale 1.3.
