# Android prototype verification — 2026-10-03

Code: `a61bfa2` + `cee8225`. Host JAVA_HOME `/Applications/Android Studio.app/Contents/jbr/Contents/Home`; ANDROID_HOME `/Users/jominjun/Library/Android/sdk`.

- `./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest`: BUILD SUCCESSFUL, final title change included.
- JVM: 5 tests, 0 failures/errors/skips. Fixed fixtures/URLs/statuses, type filters, per-activity memory reports and fresh-session reset, conference one-hour overlap/camp+meetup zero overlap, touching/empty intervals, safe link schemes.
- Lint: 0 errors, 7 warnings (6 available-version notices, 1 preserved GitHub image asset). No baselines/suppressions or disabled tests.
- `python3 scripts/check-fsd.py --self-test`: 13 Kotlin production files, 19 positive/negative boundary cases passed.
- `git diff --check`: passed.
- Merged debug manifest: no INTERNET/READ_CALENDAR/CAMERA permission or card deep link; AndroidX internal signature receiver permission remains.
- Original drawable files unchanged; previous app data is never read, deleted or migrated. No physical device install/interaction performed by this worker.

UI validation uses the existing `Dearby_Calendar_Verify` Android 16 AVD, `emulator-5554`. Initial host-GPU launch stalled at app startup (am start reported timeout; no instrumentation verdict), so the same AVD was restarted without wiping data, with `-no-window -no-snapshot -gpu swiftshader_indirect`. No new AVD was created. Final result: `OK (2 tests)` in 8.095 seconds; all five screenshots were inspected.

The software-GPU attempt exposed an Android 16 test dependency issue: removing the direct Espresso 3.7 declaration selected an older transitive version, which failed at `InputManager.getInstance`. Espresso 3.7 was restored. Both functional UI tests then passed, including filter preservation, demo application recording, one-hour conference overlap and zero-overlap meetup. The screenshot helper was subsequently changed to Android UiAutomation screen capture to include the foreground dialog reliably (Compose root selection also included its background Activity). Final screenshots and runner output are in `evidence/ui-prototype`.

Scope: one existing Android 16 emulator at default font scale. External browser network content, physical phones, other OS versions, larger text scales and screen-reader traversal were not verified by this worker. Existing local assets were kept intentionally; there is no real service integration to validate.

## 2026-10-04 calendar reference follow-up

The newly approved timetable reference is implemented as a native bottom sheet: rounded top/handle, count/close, date, 60-minute overlap summary, 30-minute two-column grid, teal/blue blocks, orange interval/dashes and continued-activity edge. Selection stays in its previous screen and is absent from the result. Zero overlap has a compact summary; confirmation closes the sheet. No calendar permission or data access was added.

Build/unit/lint/AndroidTest APK passed again; FSD 14 files/19 self-tests passed. Existing emulator UI tests: 2 passed in 10.423 seconds. Captures in `evidence/calendar-timetable` were compared with the supplied reference. Fixture-specific values intentionally differ: October 24, 13–17 activity, 14–15 busy time, 60 minutes and 1/1. This is a checkpoint before the newly requested five-tab/card/profile UI expansion.

The timetable visible range was extended to 15:30 after review so “이후에도 계속” is below the 15:00 overlap end. Current-flow selected references are now native photo cards, detail hero, summary/info rows, agenda timeline, bottom information and a memory-only application banner. Fixed fixture titles/dates remain the authorized three examples. Root-provided local conference/camp/meetup images are used directly; no remote image loader.
