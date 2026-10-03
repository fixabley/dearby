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

Selected current-flow checkpoint: final build/unit 6/lint/AndroidTest APK passed; UI 3 tests passed in 16.21 seconds. Photos/cards, detail top/agenda/bottom and applied banner captured under `evidence/selected-current-flow`; the five-tab shell is the next card/profile checkpoint. Shared buttons now follow the cross-platform 50dp/radius11 rule; palette remains the existing Android tokens. Screenshot capture waits for platform dialog animations so dismissed dialogs do not ghost into evidence.


## 2026-10-04 five-tab prototype final verification

The final scope is discovery/saved/QR/received cards/profile and selected detail/application/calendar/card editor/shared/send screens. Everything uses fixed fixtures and session memory; no service/auth/storage/camera/calendar implementation returned. New card creation and existing same-ID edit are distinct.

- `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest`: BUILD SUCCESSFUL. JVM12 tests (6 catalog/calendar, 6 card/profile/state), zero failures/errors/skips.
- Lint: zero errors, six existing available-version notices. Configuration sizing uses LocalWindowInfo; Modifier API ordering warning was fixed. No suppressed/disabled tests or lint baseline.
- FSD:25 production files and25 positive/negative boundary tests passed, including cross-page restrictions and reusable card widgets.
- Existing Android16 AVD: default font full UI9 passed in51.335 seconds. This includes filters/bookmarks, detail schedule/bottom/application, zero/nonzero overlaps, profile session edit, QR show/share/new/preview/edit, sample scan/public card/save/send, wallet search/reciprocity and sending the final card in a shrinking group.
- Font scale1.3: LargeTextFlowTest exercises QR tile reachability, editor/history scroll and fixed bottom CTA, wallet detail/send and send-picker CTA. Native captures show text scaling and scrollable content. QR tile height was changed to a minimum height after its scaled description clipping was found; targeted default/large-text results accompany the final evidence. Emulator font scale restored to1.0 afterward.
- `git diff --check` passed. Merged debug manifest has only AndroidX internal signature receiver permission, no INTERNET/READ_CALENDAR/CAMERA. No former service/persistence symbols are present in production code. No previous data read/delete/migration, physical phone interaction, new AVD or root/shared changes.
- Complexity review with ponytail-review: Lean already. Ship. Separate correctness review handled same-ID edit, idempotent save, pager bounds and contact checkbox/slash semantics.

Evidence is in `evidence/selected-card-flows`: native PNGs mapped to references in README, Gradle log, unit XML, lint XML, FSD output, full UI runner log and targeted font-scale logs. Direct visual inspection confirmed the default QR tiles and three send-picker history rows fit, agenda capture includes actual time rows plus overlap CTA, and hidden phone has a slash. Profile and card forms intentionally scroll.

Limits: one Android16 emulator, default and1.3 font scales; no physical device, other OS/display sizes, screen-reader traversal or external browser network-content verification. No live service behavior exists to validate. Root owns integration/push/PR and physical phone verification. Untracked `apps/android/.idea/` was preserved and excluded.

Final targeted rerun after QR tile minimum-height adjustment: default QR flow1 passed in11.301 seconds; font-scale1.3 flow1 passed in6.158 seconds. Final large editor capture scrolls to the full last history/disclaimer; every fixed CTA remains visible. Font scale returned to1.0.
