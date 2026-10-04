# Android prototype verification

현재 증거: [네이티브 화면 및 검사 기록](../evidence/selected-card-flows/README.md). 대체된 중간 증거/보고서는 [정리 기록](cleanup-2026-10-04.md)에 따라 제거했습니다. 과거 서비스 텍스트 기록은 [archive](archive)에 그대로 보존하며 해당 문서의 이전 evidence 경로는 당시 커밋의 기록입니다.

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
