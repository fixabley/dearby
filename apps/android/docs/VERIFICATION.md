## 2026-10-22 내용 로딩 스켈레톤

- `python3 scripts/check-fsd.py --self-test`: 통과(26 cases, 96 files)
- `./gradlew :app:lintDebug :app:assembleDebug`: 통과
- ponytail-review: Lean already. Ship.
- 실행하지 않은 것: 기기 화면 흐름(`adb devices`에 연결된 기기 없음, 에뮬레이터는 띄우지 않음), TalkBack 읽기·동작 줄이기에서 맥박 멈춤의 실제 확인, 자동 테스트(새로 만들지 않음), 큰 글씨 테스트(2026-10-06 지시)

## 2026-10-17 프로필·명함 v2

- `python3 scripts/check-fsd.py --self-test`: 통과(71 files)
- `./gradlew lintDebug assembleDebug`: 통과
- 실행하지 않은 것: 에뮬레이터·기기 화면 흐름, 실제 서버 연결, 큰 글씨 테스트(2026-10-06 지시)

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

## 2026-10-10 패스키 로그인 전환

- 실행: `./gradlew :app:assembleDebug :app:lintDebug` BUILD SUCCESSFUL. lint 오류 0, 경고는 의존성·Gradle 새 버전 알림뿐이다(`PublicKeyCredential` API 28 경고는 버전 검사로 해소).
- 실행: `python3 scripts/check-fsd.py --self-test` 25 cases, 57 Kotlin files passed.
- 병합 debug manifest 권한: 기존 INTERNET·CAMERA·AndroidX 내부 receiver 권한에 `USE_BIOMETRIC`·`USE_FINGERPRINT`가 더해졌다. `androidx.credentials`가 끌어오는 `androidx.biometric`의 일반(normal) 권한이라 실행 중 요청 창은 없다.
- ponytail-review(복잡성): Lean already. Ship.
- 실행하지 않음: 자동 테스트는 2026-10-06 사용자 요청으로 지운 상태라 새로 만들거나 돌리지 않았다. `LargeTextFlowTest`(큰 글씨) 미실행. 에뮬레이터 화면 흐름 미확인.
- 확인 불가: 실제 패스키 생성·로그인·refresh·logout은 `wid.io.kr` assetlinks 배포와 Kotlin API 새 엔드포인트가 있어야 해서 확인하지 못했다.

## 2026-10-16 애플 로그인(웹 방식)

- 실행함: `python3 scripts/check-fsd.py --self-test`(25 cases, 63 files 통과), `./gradlew :app:compileDebugKotlin`, `./gradlew lintDebug assembleDebug`(CI와 같은 명령) BUILD SUCCESSFUL, lint 오류 0(기존 버전 알림·미사용 리소스 경고만). `git diff --check` 통과. ponytail-review: Lean already(`AppleBrowser`는 `Passkeys`와 같은 형태로 둔 한 메서드 클래스). Ship.
- 임시 JVM 확인(저장소 밖, 남기지 않음): `AppleVerifier.create()` 43자, `hash`가 RFC 7636 부록 B 값(`E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM`)과 일치.
- 같은 날 iOS에 맞춰 수정: 애플 연결 실패 시 패스키 토큰을 저장하지 않고 logout 시도, 400·401은 선택 화면을 닫고 안내. 다시 check-fsd·`lintDebug assembleDebug` 통과.
- 실행하지 않음: 단위 테스트 소스가 없어(2026-10-06 사용자 요청으로 삭제) 새로 만들지 않았다. 큰 글씨 테스트 미실행. 에뮬레이터·기기에서 Custom Tab, `dearby://auth/apple` 전달·`singleTask` 동작, 프로세스 종료 뒤 verifier 복원, 실제 애플 로그인(서버 Services ID 필요)은 확인하지 못했다.

## 2026-10-16 계정 삭제

- 실행: `python3 scripts/check-fsd.py --self-test` 25 cases, 63 Kotlin files passed.
- 실행: `./gradlew lintDebug assembleDebug` BUILD SUCCESSFUL. `lintDebug --rerun-tasks`로 다시 돌려 lint 오류 0, 경고 12개는 기존 의존성·Gradle 버전·미사용 리소스 알림뿐이다.
- 실행하지 않음: 이 checkout에는 단위·기기 테스트 폴더가 없어 새 테스트를 만들거나 돌리지 않았다. 큰 글씨 테스트 미실행. 에뮬레이터 화면 흐름과 운영 서버 `DELETE /v1/me` 호출은 확인하지 않았다(운영 계정을 지우게 되므로).

## 2026-10-18 명함 공유 삭제

위 기록의 QR·스캔·공유·활동별 묶음 검증은 당시 기록이며 해당 화면은 더 없다. 이번 변경 뒤 실제 실행: `python3 scripts/check-fsd.py --self-test`(25 cases, 66 Kotlin files 통과), `./gradlew lintDebug assembleDebug`(성공, lint 오류 0·경고 8: AGP/의존성 버전 안내와 UnusedResources). 기기·에뮬레이터 화면 흐름은 확인하지 않았다.
