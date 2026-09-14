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
