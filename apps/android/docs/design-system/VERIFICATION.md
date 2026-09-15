# Issue #2 Android 검증과 화면 증거

2026-09-14 실행. 기준 `main f963401`, Android 구현 `7b0ea6d` → `c691ddb` → `e94764c`. 이전 #1 검증 기록을 재사용한 결과가 아니다.

## 실행 환경과 결과

| 항목 | 이번 실행 결과 |
| --- | --- |
| 도구체인 | Android Studio JBR 25.0.2, Gradle 9.3.1, AGP 9.1.1, Kotlin 2.2.10, Compose BOM 2026.02.01 / resolved UI 1.10.4. SDK/의존성 변경 없음. |
| APK 호환성 | aapt2로 minSdk 31 / targetSdk 36 / application ID `io.fixabley.dearby` 확인. API31 실기기 실행은 하지 않음. |
| 구조 | FSD 66 Kotlin 파일, self-test24(금지18/허용6), diff whitespace 검사 통과. |
| JVM | 39 tests, failures/errors/skips 0. favorites·VM·storage codec/cache·Session cancellation·calendar period/URL·좌표 포함. |
| 빌드 | Debug, 계측 APK, Release(R8/resource shrink) 성공. Release 배포 서명/설치는 범위 밖. |
| Lint | 오류0, 기존 경고12: 업데이트 권고5, KTX3, version catalog4. 불필요 업그레이드하지 않음. |
| 계측 | 전용5556에서 전체39 tests, failures/errors/skips 0. 기존35 + 신규4. Room 실제 DB5, 즐겨찾기/페이징/상세 back·조직 투영·지도/캘린더 전달값과 신규 디자인 접근성 회귀 포함. |
| 새 회귀 | label/value 단일 접근성 노드, disabled 클릭 차단, native touch bounds 최소48dp, 2배 글자 카드 본문과 액션 비겹침, loading→error polite announcement/독립 retry 콜백. |

```sh
cd apps/android
python3 scripts/check-fsd.py --self-test
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug
ANDROID_SERIAL=emulator-5556 JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```

로컬 상세 로그: `apps/android/build/design-system-final-build.log`, `design-system-instrumentation.log`, `design-system-capture.log`; 표준 보고서는 `app/build/test-results/testDebugUnitTest`, `app/build/outputs/androidTest-results/connected/debug`, `app/build/reports/lint-results-debug.html`에 있다. 빌드 산출물은 Git에 올리지 않고 이 검증 요약과 PNG 증거를 보관한다.

## 화면 비교

전용 `Dearby_Issue2_Test` AVD(API36.1 arm64 Pixel9, 1080×2424, density420)에서 adb screencap으로 캡처했다. 초기 main APK를 실행한 before는 light 발견/상세/빈 화면과 dark 2배 글자 발견이다. after는 동일 AVD의 기본 wallpaper dynamic color를 사용한다. 아래 이미지는 실제 앱이며 mockup이 아니다.

| 화면 | Before | After light | After dark | After light 2× | After dark 2× |
| --- | --- | --- | --- | --- | --- |
| 발견 | [light](evidence/before-light-discovery.png) / [dark 2×](evidence/before-dark-large-discovery.png) | [화면](evidence/after-light-discovery.png) | [화면](evidence/after-dark-discovery.png) | [화면](evidence/after-light-large-discovery.png) | [화면](evidence/after-dark-large-discovery.png) |
| 저장됨 | — | [화면](evidence/after-light-saved.png) | [화면](evidence/after-dark-saved.png) | [화면](evidence/after-light-large-saved.png) | [화면](evidence/after-dark-large-saved.png) |
| 빈 즐겨찾기 | [light](evidence/before-light-empty.png) | [화면](evidence/after-light-empty.png) | [화면](evidence/after-dark-empty.png) | [화면](evidence/after-light-large-empty.png) | [화면](evidence/after-dark-large-empty.png) |
| 즐겨찾기 | — | [화면](evidence/after-light-favorites.png) | [화면](evidence/after-dark-favorites.png) | [화면](evidence/after-light-large-favorites.png) | [화면](evidence/after-dark-large-favorites.png) |
| 상세 상단 | [light](evidence/before-light-detail.png) | [화면](evidence/after-light-detail.png) | [화면](evidence/after-dark-detail.png) | [화면](evidence/after-light-large-detail.png) | [화면](evidence/after-dark-large-detail.png) |
| 상세 스크롤 | — | [화면](evidence/after-light-detail-scroll.png) | [화면](evidence/after-dark-detail-scroll.png) | [화면](evidence/after-light-large-detail-scroll.png) | [화면](evidence/after-dark-large-detail-scroll.png) |

baseline dark 2×에서 참여 대상/마감 본문이 저장 버튼 뒤로 겹쳤다. 높이/fontScale 기반 compact 카드가 버튼 위에 제목·분류·확인 안내를 유지하고 전체 정보는 상세에서 제공한다. 상세의 긴 정보는 제한 없이 줄바꿈·스크롤하며 native 버튼의 높이를 고정하지 않는다. 큰 글자에서 카드 요약은 의도적으로 줄 수를 제한하되 원문 정보는 상세에서 유지한다.

## 보존과 한계

사용자 `emulator-5554`는 조작하지 않았다. 새 전용 AVD 5556만 사용했고 초기화·사용자 기기 종료·실제 calendar save를 하지 않았다. 기존 계측은 prefs를 backup/restore하며 screenshot 작업도 테스트 fixture의 원래 prefs 파일과 font_scale/night 설정을 finally에서 복원한다. 사용자가 검토할 수 있도록 전용 AVD는 유지한다.

NoticeModel/OrganizationModel, Room off-main/cancellation/transaction, L1→disk→mock, favorites 파일/키/IDs/single owner, 지도/캘린더 adapter와 URL memo 규칙은 이번 diff에서 변경하지 않았다. canonical asset SHA256 `c649b0a1d898497adf9bd4e2363c5753a1eecf996a7467e604dadaaee4a9e95f` 유지.

계측은 전달값·앱 콜백·사용자 흐름을 검증하며 외부 지도 앱 렌더링, Calendar 앱 내부 편집/저장, TalkBack 음성 전체 순회, 다른 OEM wallpaper 팔레트·API31 실기기·가로/태블릿 전체 조합을 검증했다고 주장하지 않는다. 접근성 증거는 Compose semantics/터치 bounds와 화면 검토이며 완전한 접근성 감사는 아니다. Preview는 Android Studio에서 ThemePreview/InformationPreview/StatusPreview의 light/dark/2×를 열 수 있고, 해당 구성요소는 실제 기기 계측/앱 화면에서도 렌더링했다.

화면 QA 완료: 28개 PNG 링크 확인, light/dark 기본·2×의 발견/상세/즐겨찾기와 큰글자 빈 상태·저장됨·스크롤 화면을 시각 검토했다. 캡처 후 5556의 `font_scale=1.0`, `night=no`, favorites `<map />` 복원을 읽기 확인했다.

## PR9 후속 — 아이콘 액션과 일정 위계 (2026-09-14)

아래는 f4784a1 이후 사용자 요청을 반영한 **새 실행 결과**다. 이전 표의 JVM39/계측39·after 이미지는 이번 후속 변경 전 기준으로 보존한다.

- 구현: `3cd742b` 카드 icon/metadata, `77295a4` 이름 중심 일정/신청 구획, `95735c4` 충돌하는 날짜 필드의 전체 접근성 보존.
- 최종 검증: FSD68/self-test24, JVM46(기존39+신규7), 전체 계측39, Debug/Release/계측 APK, Lint 오류0/기존12 모두 통과. 이번 계측에서 저장 이름/selected/48dp touch bounds를 확인하고, 예선·발표·결선 3개 calendar icon의 이름·날짜·장소·원본URL 매칭과 불명/invalid 날짜의 액션 부재를 확인했다.
- JVM 새 회귀: 같은 날 시각 축약, 연도 경계, 날짜만 있는 경우, invalid/충돌/역전/미확인, 자정 마감, 명시 timezone, 온라인 이름 중복/URL 보존, 복수 장소/주소/호실, 충돌한 timestamp/date 필드 동시 접근성 보존.
- 최종 로그: `build/icons-schedule-final-build.log`, `build/icons-schedule-final-instrumentation.log`, `build/icons-schedule-capture.log`. 최종 APK minSdk31/target36 및 기존 의존성 유지.
- 초기 icon 계측의 1건은 카드에 새로 표시한 관심 조직 label과 시트 label을 전역으로 찾던 기존 테스트 selector 충돌이었다. 상세 ancestor로 범위를 좁혔으며 최종 전체 실행39는 failures/errors/skips 0이다.

### 최신 화면

기존 `after-*`가 이번 before(f4784a1)다. 아래 `latest-*`는 최종 후속 앱으로 전용5556에서 새로 캡처한 실제 화면이다. 신청과 행사, 온라인 예선·발표·결선의 이름을 아이콘으로 대체하지 않고 titleLarge/bold로 읽을 수 있으며 날짜·장소는 bodyMedium, 보조 안내는 bodySmall/labelSmall이다. compact 카드에서는 날짜/장소도 1줄 메타데이터로 유지하고 전체 값은 접근성/상세에 남는다.

| 화면 | light | dark | light 2× | dark 2× |
| --- | --- | --- | --- | --- |
| 발견 | [화면](evidence/latest-light-discovery.png) | [화면](evidence/latest-dark-discovery.png) | [화면](evidence/latest-light-large-discovery.png) | [화면](evidence/latest-dark-large-discovery.png) |
| 저장됨 | [화면](evidence/latest-light-saved.png) | [화면](evidence/latest-dark-saved.png) | [화면](evidence/latest-light-large-saved.png) | [화면](evidence/latest-dark-large-saved.png) |
| 즐겨찾기 | [화면](evidence/latest-light-favorites.png) | [화면](evidence/latest-dark-favorites.png) | [화면](evidence/latest-light-large-favorites.png) | [화면](evidence/latest-dark-large-favorites.png) |
| 빈 화면 | [화면](evidence/latest-light-empty.png) | [화면](evidence/latest-dark-empty.png) | [화면](evidence/latest-light-large-empty.png) | [화면](evidence/latest-dark-large-empty.png) |
| 상세 상단 | [화면](evidence/latest-light-detail.png) | [화면](evidence/latest-dark-detail.png) | [화면](evidence/latest-light-large-detail.png) | [화면](evidence/latest-dark-large-detail.png) |
| 신청 | [화면](evidence/latest-light-application.png) | [화면](evidence/latest-dark-application.png) | [화면](evidence/latest-light-large-application.png) | [화면](evidence/latest-dark-large-application.png) |
| 행사 | [화면](evidence/latest-light-event.png) | [화면](evidence/latest-dark-event.png) | [화면](evidence/latest-light-large-event.png) | [화면](evidence/latest-dark-large-event.png) |
| 온라인 예선·발표 | [화면](evidence/latest-light-contest.png) | [화면](evidence/latest-dark-contest.png) | [화면](evidence/latest-light-large-contest.png) | [화면](evidence/latest-dark-large-contest.png) |
| 결선 | [화면](evidence/latest-light-final.png) | [화면](evidence/latest-dark-final.png) | [화면](evidence/latest-light-large-final.png) | [화면](evidence/latest-dark-large-final.png) |

이전과 같은 전용5556에서 캡처하며 실제 calendar save/외부 링크 실행 없이 UI와 전달값만 검증한다. 캡처의 초기 빠른 스크롤이 목표 구획을 지나쳐 드라이버를 느린 스크롤로 수정했다. 앱 코드/기기 데이터 초기화로 우회하지 않았다. TalkBack 전체 음성 순회, API31 실기기, 모든 OEM·가로·태블릿 조합과 외부 Calendar/지도 앱 내부는 여전히 미검증이다.

최신 화면 QA 완료: 새 PNG36개와 모든 증거 링크를 확인했고 light/dark 기본·2×의 카드, 즐겨찾기, 신청·행사와 온라인 예선·발표·결선을 시각 검토했다. 일정 이름·날짜·장소·액션의 겹침 없이 줄바꿈/스크롤되며 compact 카드의 말줄임은 상세와 전체 접근성 설명으로 보완한다. 캡처 종료 후 전용5556의 `font_scale=1.0`, `night=no`, favorites `<map />` 복원을 읽기 확인했다.

## 캘린더 상세 표현 후속 (2026-09-15)

기준 `ff8ccaa` 이후의 새 실행이다. `7cee7af`는 날짜/시간 위계, `0721fe5`는 장소 상세와 지도 인접 배치, `9820ca8`는 초·소수초와 미확인 경계 보존이다. 카드·모델·cache·favorites·Calendar exporter·공통 JSON/의존성은 변경하지 않았다.

[Google Calendar Android 공식 도움말](https://support.google.com/calendar/answer/72143?co=GENIE.Platform%3DAndroid&hl=en)의 제목·장소·종일 옵션 구분을 참고했다. 전용5556 설치 Calendar에 INSERT 화면 열기를 시도했으나 계정 설정 화면만 표시됐다. 계정 설정·일정 저장은 진행하지 않았으며 Calendar 일정 상세를 직접 관찰했다는 주장은 하지 않는다. 날짜/시간 줄 분리와 장소 위계는 사용자 요구와 공식 문서에 기반한 설계 해석이다. 날짜만 있는 source는 종일로 단정하지 않고 시간 미확인으로 표시한다.

이번 최종 실행: JVM52(이전46+새 날짜4/장소2), 관련 계측7(ApplicationCalendarFlowTest2/PhaseCalendarFlowTest2/VenueMapFlowTest3), Debug 앱·계측 APK 빌드, Lint 오류0/기존12, FSD71/self-test24 모두 통과했다. 테스트 failures/errors/skips는 모두0이다. 전체 계측39/Release는 직전 후속 결과이며 이번에는 재실행하지 않았다.

새 회귀는 같은날 날짜1회/시간범위, 여러날 시작·종료/연도경계, 날짜만/시간미확인, timezone 변환, 초·소수초, mixed precision·시작누락, invalid·역전·충돌 모든 raw 필드, 중복 summary와 24:00/불확실 원문 보존, 호실/주소 분리·중요조건 보존을 확인한다. 계측은 새 배치의 지도 버튼이 해당 원본 venue를 전달하는지와 신청/3phase calendar draft의 이름·날짜·장소·원본URL 매칭을 확인했다. 실제 Calendar 저장은 하지 않았다.

로컬 로그: `build/calendar-detail-final-build.log`, `build/calendar-detail-instrumentation.log`, `build/calendar-detail-capture.log`. JVM XML/계측 XML과 Lint 보고서는 표준 app/build 경로다. 공개 API/사용처/native mapping/설계 해석은 README.md의 캘린더 상세 절에 있다.

### 상세 대표 화면

이전 `latest-*` PNG는 ff8ccaa 기준 before로 그대로 보존한다. 이번에는 바뀐 상세 대표8장만 캡처한다.

| 대표 | Before | 이번 화면 |
| --- | --- | --- |
| light 신청 | [이전](evidence/latest-light-application.png) | [현재](evidence/calendar-light-application.png) |
| light 행사 | [이전](evidence/latest-light-event.png) | [현재](evidence/calendar-light-event.png) |
| dark 신청 | [이전](evidence/latest-dark-application.png) | [현재](evidence/calendar-dark-application.png) |
| dark 행사 | [이전](evidence/latest-dark-event.png) | [현재](evidence/calendar-dark-event.png) |
| light 2× 행사 | [이전](evidence/latest-light-large-event.png) | [현재](evidence/calendar-light-large-event.png) |
| light 2× 예선 | [이전](evidence/latest-light-large-contest.png) | [현재](evidence/calendar-light-large-contest.png) |
| dark 2× 행사 | [이전](evidence/latest-dark-large-event.png) | [현재](evidence/calendar-dark-large-event.png) |
| dark 2× 결선 | [이전](evidence/latest-dark-large-final.png) | [현재](evidence/calendar-dark-large-final.png) |

사용자5554는 조작하지 않았다. TalkBack 전체 음성 순회·API31 실기기·OEM/가로/태블릿 전체조합과 외부 지도/Calendar 내부는 미검증이다. 원문에만 있는 장소 변경/시간불확실/24:00 같은 의미는 남기므로 모든 원문 중복을 기계적으로 제거하지 않는다.

큰 글자 캡처 중 고정 횟수 swipe가 마지막 카드에 도달하지 못해 첫 드라이버가 중단됐다. finally로 설정을 복원한 후 실제 페이지 번호 4/4를 확인하는 드라이버로 남은 큰글자 화면만 재개했으며 앱 코드/데이터 변경으로 우회하지 않았다.

최종 화면 QA: 새 대표8장과 모든 evidence 링크 확인 완료. light/dark 신청·행사와 2× 행사·예선·결선에서 일정 제목/날짜/시간/장소/호실/개별 액션의 겹침 없이 줄바꿈·스크롤되는 것을 시각 검토했다. 촬영 종료 후 전용5556의 font_scale=1.0, night=no, 원래 favorites `<map />` 복원을 읽기 확인했다.

## 사용자 이미지 기반 기간·링크·일간 미리보기 (2026-09-15)

기준 `5dbb8c5` 이후 새 구현/실행이다. `66d0ea4`는 한국어 기간 문장/안전한 실제 URL 링크 카드, `08408b7`는 날짜 선택/시간그리드/일간 clipping과 관련 회귀다.

사용자 제공 [참고1](evidence/user-calendar-reference-1.png)·[참고2](evidence/user-calendar-reference-2.png)를 view_image로 직접 확인했다. 굵은 일정명, 오전/오후 …부터/…까지, 도메인 카드, 시간 gutter와 가로선, accent block을 Android M3로 구현했다. 이전 단계의 Google Calendar 설치앱 계정 설정 화면과 달리 이번 참고는 사용자가 제공한 실제 Calendar 캡처다. 앱이 생성한 화면이나 Android 화면이라고 주장하지 않는다.

이번 최종 검증은 JVM58(이전52+link/time2+timeline4), 관련계측10(신규timeline/link3+신청/phase/지도7), Debug/계측 APK build, Lint 오류0/기존12, FSD80/self-test24 통과다. tests failures/errors/skips0. 이번에는 전체suite/Release/이전36장을 반복하지 않았다. 날짜 picker는 숫자28/description selector가 아닌 native semantics의 전체 날짜 Text를 사용하도록 테스트를 수정한 뒤 최종10을 다시 실행했다. 최초 FSD에서 feature 내부 safeURL 함수 직접 의존을 발견해 동일 검증 정책을 상위 표시 projection에 독립 적용했으며 최종 구조검사는 통과했다.

- clipping: 시작일9시~자정/중간날0~자정/마지막날0~13시, 범위밖 제한, 배타자정·연도경계, sourceZone DST23/25h·반복시간offset, 사라진 localDate의 인접날 이동을 검증했다. 모든날 배열 없이 선택일 tick만 계산한다.
- 불명: deadline-only/날짜만/mixed precision/invalid/역전/충돌에는 block 없음. 기존 Calendar export가 날짜만을 처리하는 방식과 분리된 표시정책이다.
- 접근성: 원문URL 전체 목적지, 링크 callback-only, nativepicker 날짜변경, 경계버튼 disabled, 2× dark 짧은구간의 정확한 초·소수초/확대표시 semantics를 검증했다. block 말줄임과 최소 시각높이는 전체 caption/정확한 구간 설명과 확대 안내로 보완한다.
- 보호: 카드·모델·cache·favorites·Calendar exporter·map·원본source/JSON·SDK 불변. source/calendar save/URL share/외부열기를 실제 실행하지 않았고 모든 action 계측은 callback을 캡처한다. 기기 Calendar 조회/권한/provider/busy-block은 코디네이터 지시에 따라 별도 후속으로 남겨두었다.

로컬 로그 `build/timeline-final-build.log`, `build/timeline-instrumentation.log`, `build/timeline-capture.log`; 표준 JVM/계측 XML 및 Lint 보고서. 공개 API·native mapping·custom 이유와 [Material3 DatePickerState](https://developer.android.com/reference/kotlin/androidx/compose/material3/DatePickerState)의 UTC 날짜 처리 근거는 README.md의 사용자 이미지 후속 절을 참고한다.

### 이번 상세 대표 화면

기준 before는 직전 calendar-light-application/event 등이며, 아래 timeline-*는 이번 최종 앱의 새 실제 캡처다.

| 대표 | 증거 |
| --- | --- |
| light 기간문장·실제 URL 카드 | [화면](evidence/timeline-light-period-link.png) |
| 시작일 오전9시 시작 | [화면](evidence/timeline-light-first-day.png) |
| 다음 날짜로 이동한 중간날 | [화면](evidence/timeline-light-middle-day.png) |
| native 날짜선택기 | [화면](evidence/timeline-native-date-picker.png) |
| 선택기로 이동한 마감일 0~13시 | [화면](evidence/timeline-light-last-day.png) |
| 320dp viewport 안 시간대 스크롤 | [화면](evidence/timeline-light-scroll.png) |
| dark 시작일 | [화면](evidence/timeline-dark-first-day.png) |
| dark 2× 시작일 | [화면](evidence/timeline-dark-large-first-day.png) |

첫 캡처 드라이버는 nested scroll 내부를 움직이고 날짜 버튼의 자식Text를 놓쳤다. finally 복원 후 outer 여백 swipe와 childText 중심tap으로 수정해 새 화면을 다시 수집했다. 앱 데이터 변경으로 우회하지 않았고 캡처는 외부 열기/저장/공유 동작을 누르지 않는다. TalkBack 전체 음성·API31 실기기/OEM/가로/태블릿 전체조합·외부 앱 내부 검증은 하지 않았다.

최종 QA: 새 실제대표8장·사용자참고2장 링크 확인, 기간/link·시작일/중간일/마감일·nativepicker·내부스크롤·dark/2×를 시각 검토했다. 사용자5554는 조작하지 않았고 전용5556의 font_scale=1.0/night=no/favorites `<map />` 복원을 읽기 확인했다. 기기 일정 busy-block/native switch/권한은 별도 #10 Task에 남겨두며 현재 구현에는 포함하지 않는다.
