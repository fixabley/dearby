# Android 설계 단순화 · 2026-09-20

## 범위와 근거

기준은 origin/main PR32 `2362917`; 실제 Android caller와 `ARCHITECTURE.md`를 조사했다. iOS의 폴더/파일명 규칙을 일괄 이식하지 않았다. 플랫폼 코드, 앱 구조 검사·문서, Android 역할 인계만 변경하며 공통 규칙/루트 CI는 메인이 조율한다.

| 승인 항목 | Android 대응과 결과 |
| --- | --- |
| 미사용 표시 투영 | 카드 applicationSummary/locationSummary/applicationDateText 및 계산 제거. 상세 descriptionProvenance/categoryPath/relatedOrganizations/sources/evidence와 context의 중복 ID/role 제거. NoticeModel/codec/원문·출처는 보존하고 테스트를 원본/codec에 적용. |
| 공개 Shared 디자인 UI | 기존 직접 사용 유지. Pages/Widgets의 Shared 접근은 shared.ui로 한정하고 storage/network/OS 경로 금지 회귀 추가. |
| 표시 Feature/전달 wrapper | 표시만 하는 Feature/factory 없음. AddToCalendarButton/VenueMapButton/CardScheduleRow는 이미 Page/Widget 소유이며 의미 있는 UI 단위라 유지. Route의 읽기 전용 map에 대한 mutable 복사만 제거. |
| 형식적 이름 강제 | State 보조타입 접미사/VM class 강제 없음. ResolvedOrganizationRole 같은 이름을 유지하며 State/VM 수명 계약을 바꾸지 않음. |
| Content 파일명 검사 | 없음. @Composable Page/Widget의 값/콜백·raw Model/Repository/OS 금지 검사를 유지. |
| Route/세션 | NoticeSession은 공유 repository/snapshot/VM identity, BusySession은 상세 조회 취소/세대/foreground를 소유하므로 유지. SettingsController/Host와 중복되는 미호출 BusySession.confirm/permissionResult/retry 및 Consent/Requesting만 제거. |
| 내부 진입점 | FavoriteNoticeState는 Widget 내부에서만 직접 사용하므로 외부 import allowlist에서 제거. BusyResult는 Route에서 속성을 소비하고 FavoriteStore는 주입 계약이므로 유지. |

## 이번 실행

작업 checkout `dearby-android-design-simplification/apps/android`, JDK `/Applications/Android Studio.app/Contents/jbr/Contents/Home`, Android SDK `/Users/jominjun/Library/Android/sdk`. local.properties와 빌드 로그는 자기 checkout의 ignored 로컬 파일이다.

- 카드 커밋 e370c03: JVM79, Debug/계측 APK, lint 오류0/경고13, FSD101/자체회귀24 통과 (`build-design-simplification.log`).
- 상세 커밋 1bc9972: JVM79, 계측 APK, FSD101/자체회귀24 통과 (`build-design-detail.log`). 원본 모델과 codec roundtrip, canonical 출처/근거 검증을 유지했다.
- 캘린더 커밋 9d107e5: JVM80, 계측 APK, FSD101/자체회귀24 통과 (`build-design-busy.log`). 기존 Settings 권한/지연 callback 회귀는 유지하고 BusySession background/close의 비협조적 늦은 결과 회귀를 추가했다.
- 최종 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` 통과 (`build-design-final.log`): JVM80, 실패/오류/skip0, lint 오류0/경고13. 경고는 UseKtx4/UseTomlInstead4/AndroidGradlePluginVersion2/GradleDependency2/NewerVersionAvailable1이며 억제 규칙을 추가하지 않았다.
- 구조 검사 `python3 scripts/check-fsd.py --self-test`: 101 Kotlin 파일, 자체회귀30(금지22/허용8) 통과. 기존24를 삭제하지 않고 Shared 경로와 내부 진입점 6건을 추가했다.
- 전용 Dearby_Issue2_Test/API36 emulator-5556 전체 계측: **58개 중 57통과/1실패, 오류0/skip0** (`build-design-instrumentation.log`, 5분32초). pager 한 장/더블탭 저장/즐겨찾기·상세 동기화/Activity 복원, 일정·장소·큰 글자/AX, 설정·busy/Room rollback·손상 보존·지도/캘린더 Intent 등이 실행됐다. 실행 전 앱 최초 안내에서 ‘나중에’를 선택했다. 사용자 기기는 조작하지 않았다.
- 실패는 기존 CalendarEditorTest.kt:36의 READ_CALENDAR 미선언 assertion이었다. PR32의 원래 test/manifest에서도 모순임을 확인했다. 별도 opt-in busy read의 권한 선언과 exporter 계약을 구분해 Intent flags=0으로 교체하고 WRITE 미선언/정확한 extras/자동저장 없음/실패 안내 회귀는 유지했다. production/manifest는 바꾸지 않았다.
- 수정 후 `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.fixabley.dearby.app.CalendarEditorTest`: **2통과/실패·오류·skip0**, 31초 (`build-design-calendar-editor-recheck.log`). 최종 test 소스에 대해 lintDebug 재실행도 성공 (`build-design-lint-recheck.log`, 오류0/경고13). 전체58을 다시 실행했다고 기록하지 않는다.
- 최초 전체 XML/HTML은 덮어쓰지 않도록 `build/design-simplification/initial-connected-results`, `initial-connected-report`에 보존했다. 표준 connected 결과는 마지막 2개 재검증 결과다.

표준 산출물은 `app/build/test-results/testDebugUnitTest`, `app/build/reports/lint-results-debug.{html,xml,txt}`, `app/build/outputs/androidTest-results/connected`, `app/build/reports/androidTests/connected`에 있다. 과거 문서의 결과를 이번 통과로 계산하지 않는다.

## 변경 후 검토

Ponytail은 변경 diff와 실제 호출 경로에만 적용했다. 추가 삭제 후보 없음: **Lean already. Ship.** 저장소 독립성·공유 즐겨찾기·화면 수명·작은 의미 있는 UI 경계를 줄 수 절감을 위해 합치지 않았다. 실제 production Kotlin diff는 `git diff --numstat 2362917 -- app/src/main` 기준 순감 26줄이며 목표나 승인 근거가 아니다.

별도 정확성 검토에서 원본 모델/codec/Room transaction과 OS 어댑터를 변경하지 않았고, State.saved의 단일 즐겨찾기 관찰과 날짜/장소/AX 표시값을 유지했음을 확인했다. 단일 모듈 정규식 구조 검사는 Kotlin 의미론 전체를 보장하지 않는다.

## 실제 앱 수동 확인과 한계

전체/수정 계측 후 최종 APK를 다시 설치하고 최초 안내 ‘나중에’를 선택했다. 실제 카드에서 신청/행사 시작·종료/장소/관심 조직과 native 버튼을 확인했고, 카드 지도 버튼→Google Maps(로그인 Skip)→`충북대학교 중앙도서관 2관 세미나실(5층)` 및 좌표 `36.628196, 127.457875`의 지도/pin/장소 패널 표시→뒤로 두 번→같은 발견 첫 카드 복귀를 확인했다. 상세의 신청 캘린더 버튼은 Google Calendar/Google 계정 안내 화면으로 전환됐다. 로그인·계정 추가·저장은 하지 않았으며 Dearby로 돌아왔다. 스크린샷을 직접 열어 확인했고 로컬 `build/design-simplification/screenshots/{discovery,detail,map,calendar-account-required}.png`, 지도/복귀 AX JSON이 증거다.

실제 개인 계정 CalendarProvider/반복 일정, OS 캘린더 편집기 내부 편집·저장·동기화, TalkBack 전체 탐색은 미실행이다. 계측의 Intent/fixture/콜백 검증을 실제 OS 저장 성공으로 해석하지 않는다. 원본 번들/저장소/설정 계약과 #13/#14/#15 후속 범위는 보존했다. 이 기준 checkout의 GitHub workflow는 iOS용 하나뿐이고 Android CI는 없다. push/PR/merge는 메인 담당이다.

마무리 시 전용5556은 최종 앱을 실행한 채 유지하고 Orca emulator helper만 종료했다. worker terminal은 메인의 사용자 요청 retained 처리 대상으로 남기며 임의 종료하지 않는다. 최종 역할 인계는 docs/context/android-implementation-and-handoff.md다.
