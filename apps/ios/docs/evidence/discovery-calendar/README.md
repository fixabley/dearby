# Discovery-only and calendar verification — 2026-09-29

Main checkout implementation explicitly authorized after Orca worker startup failure. Dedicated Simulator: `Dearby-Calendar-Verify`, DFC0F35B-6385-4D94-92B2-93CD56849E27, iOS26.5. Existing user Simulators were not modified.

DiscoveryState constructs only catalog persistence/HTTP. DearbyApp mounts DiscoveryRootView; the full RootView, AppState, all screens, bookmark data and card storage remain. Default app has no legacy tabs/save affordances/auth/import/deeplink routing. The full flow can be restored at the entrypoint; no general feature-flag framework was added.

EventKit is isolated in DeviceCalendarStore. OS full access is necessary to read; the app only reads. Returned values contain calendar choices and anonymous busy intervals. Free/cancelled events are excluded, unsupported availability conservatively counts as busy. EventKit expands recurring/all-day events; the adapter resets its object cache after reads. Sheet state clears on dismiss/inactive and rejects late asynchronous results. Calendar choices, missing/partial activity times, denied access, missing calendars and failures are distinct from no overlap. Long intervals are queried in bounded 366-day windows; no calendar API truncation is treated as a complete comparison.

## Executed

- Final native unit suite: 40 tests, 33 passed and 7 explicit skips (6 existing local-API fixture conditions + denied-permission condition). Real EventKit recurrence passed with full access. The denied-permission test was separately run after revocation and passed (1/1); permission restored afterward.
- Real EventKit test creates a separate temporary local calendar, checks recurring occurrence times and clears state, then removes that calendar. Local calendars here do not support free/busy availability: free filtering is covered by the predicate regression; free fixture insertion is conditional on actual calendar capability.
- Architecture16 and strict SwiftLint64 files passed. Native Debug build passed. Initial unsigned-test Keychain error was resolved by running normally signed Simulator tests; app session storage code was not changed.
- DiscoveryNavigationTests passed 2/2 with no skips and tests actual default entrypoint with explicit localhost UI fixture. Legacy UI test files are retained and explicitly skipped because they assert tabs now deliberately hidden. Underlying domain/storage/auth tests still run.

HTTP fixture screenshots are not official current activities or actual application submissions. iOS uses SFSafariViewController. Source and application intent remain distinct; only closing an application attempt asks for a self-report. Physical calendars/cloud sync, full VoiceOver and silent internal EventKit failures are not verified. EventKit's nonthrowing events(matching:) API cannot expose every OS-internal query failure separately from an empty array; permission and unavailable selections are explicitly checked.

References: [Apple EventKit access](https://developer.apple.com/documentation/eventkit/accessing-the-event-store), [Android Calendar Provider](https://developer.android.com/identity/providers/calendar-provider).
