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
