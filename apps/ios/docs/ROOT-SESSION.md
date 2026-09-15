# Startup and root routing

AppSession owns the snapshot reader, favorites lifetime, retained SwiftDataSnapshotStore, ready NoticeSession and load failure. A failed store construction can retry; a failed source read reuses the existing store; ready startup is idempotent. AppComposition constructs the production dependencies and detail Page destinations. Providers contain no feature View implementation.

ContentView selects progress/failure/ready and starts loading. AppTabs owns the two lazy tabs and navigation roots. Each page/card reads live view models; the root no longer forces a favorites read. SettingsPresentation owns its sheet, first-use prompt and ScenePhase wiring. CalendarPreferences moved byte-for-byte into its feature; its persistent two-boolean adapter remains in providers.

`AppSessionTests` (included in run_standalone.sh) verifies store/source failures, retries, storage reuse, successful session identity and three independent Observation subscriptions (discovery, favorites and open detail) on save/remove. Existing calendar preference/busy tests cover first prompt/later/relaunch, permission continuations, OFF/background/revocation and stale publication. Actual lazy tab UI transitions remain unverified in this Xcode27 environment; see the [execution report](evidence/two-layer-composition/README.md).

The checker, root, detail and card commits form one integration unit: the new exported Page/Widget contracts cross those component boundaries. Only the complete branch is claimed as validated; do not publish intermediate red states as separate PRs.
