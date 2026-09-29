# Discovery-only and calendar verification — 2026-09-29

Main checkout implementation, explicitly authorized after Orca worker startup failure. Dedicated `Dearby_Calendar_Verify` / emulator-5556, API36.1. Existing emulator-5554 was not modified.

- MainActivity mounts DiscoveryApp without accessing the legacy DearbyViewModel. Old DearbyApp, screens, repositories, bookmarks and account data remain. Only discovery/detail/application/report/calendar are reachable; bookmarks are hidden through explicit composition parameters.
- READ_CALENDAR only; no production calendar write permission. Instances expands recurrence, excludes free/cancelled/declined entries, projects only anonymous intervals, and translates UTC all-day date boundaries into device-local dates (DST aware). Queries run on Dispatchers.IO and cursors close. Calendar selections and results are sheet-scoped, cleared on stop/dispose, with generation checks against late results.
- A calendar must be selected. Missing/invalid activity times, no visible calendars, denied permission, query failure, partial schedule coverage and a verified empty intersection have different states. Long intervals are queried in 366-day windows and repeated occurrences are deduplicated. Unknown availability is conservatively busy. Personal event titles/locations are never in the projection, storage or network.

## Executed

- JVM: 32 tests, failures/errors/skips 0, including touching-boundary/invalid-time/offset/DST all-day regressions.
- FSD: 49 sources, 28 checker self-tests.
- Debug build and lint: success; 0 errors and 17 existing warnings (no new warning).
- CalendarDeviceTest: real Calendar Provider temporary local calendar, recurring occurrence expansion, free exclusion, comparison result and clear. The temporary calendar is removed in finally. Requires explicit `-e calendarFixture true` and a dedicated emulator; never runs writes on a normal CI/user device by default.
- DiscoveryOnlyTest: no legacy tabs/save buttons, fixture discovery → detail → calendar empty state → close → application WebView → report → later without marking applied. Passed with the actual app entrypoint. Screenshots here come from this run.

UI catalog is an explicit localhost fixture (`tests/fixtures/calendar-flow-server.py`), not real current recruitment or a production API validation. The real provider test is distinct from this HTTP fixture. Real cloud accounts, OEM devices and full TalkBack are not verified.

Run the fixture server, use adb reverse tcp:58763 tcp:58763 on the dedicated emulator, build Debug with `-PdearbyApiUrl=http://127.0.0.1:58763`, install app/test APKs and run the two instrumentation classes. Production source has no fixture default.
