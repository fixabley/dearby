# Android prototype structure

`app` owns the Activity, session ViewModel and navigation. `pages/catalog` renders immutable screen State and callbacks. `features/application` opens an explicit external URL and renders the local application demo; `features/calendar` computes fixed example overlaps and renders the existing sheet. `entities/catalog/model` holds three immutable example activities and their schedules. `shared/ui` holds the existing theme, logo and buttons.

There are no repositories, providers, persistence, network clients or empty replacement layers. CatalogViewModel directly maps fixed models to screen State and owns filter/report memory. Navigation and sheet controls use Compose remember; no SavedStateHandle or rememberSaveable writes are used. ViewModel survives Activity recreation but process restart resets all demo state. Previous installed files are never opened or removed.

`scripts/check-fsd.py --self-test` checks package/path matching, downward dependencies, sibling slice isolation, explicit exports and callback-only Page UI. Its 19 positive/negative cases cover remaining slices; it is a lexical guard, not a compiler proof. Unit tests cover fixtures, filters, reset, unsafe links and interval boundaries; instrumentation tests cover actual click flows and zero/nonzero overlap results.
