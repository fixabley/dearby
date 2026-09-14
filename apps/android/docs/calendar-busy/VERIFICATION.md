# #10 Android 검증 · 2026-09-15

최종 UI는 시간 눈금 뒤 내 일정 반투명 배경 + 전체 너비 활동 + 실제 교집합 경고다. PR9 이전 결과와 중간 두 열 디자인의 결과를 최신 증거로 사용하지 않는다.

## 이번 실행

- JVM **13개 통과**: BusySession 5, BusyInterval 4, 기존 DayTimeline 4를 이번 변경에서 재실행. 동의 취소/거절/제한/성공빈결과/조회실패/철회/백그라운드/화면종료/OFF/비협조적 지연 결과/선택일 stale/복수phase 단일세션, strict half-open overlap/merge/clip, floating 종일·서울·LA 23/25시간 DST·다른 source-zone 날짜·자정 종료를 확인했다.
- 전용 **emulator-5556 API36.1 계측 12개 통과**: AndroidBusyProvider 3, BusyCalendarFlow 3, BusyTimelineDisplay 3, 기존 DayTimeline 3. 최종 1배 run에서 11개, 실제 system font_scale=2.0의 dark detail run에서 1개를 실행했다. 이후 VISIBLE 필터 제거 관련 provider 3개를 별도 재실행했고 통과했다(중복 합산하지 않음).
- Provider 테스트는 in-memory SQLite 숫자 fixture/query hook만 사용한다. NULL/tentative/숨긴 캘린더 보존, free/canceled/self-declined 제외, projection 6개 숫자 필드, timed/floating 쿼리 범위, IO 스레드, null cursor 실패, CancellationSignal 전달을 검증했다.
- UI는 fake provider/permission callback만 사용한다. OFF→동의→취소→거절에서 query=0, 허용 fake ON→활동 선택일 변경/겹침→OFF에서 gutter 배경/경고 제거를 확인했다. 신청은 비교에 등록하지 않는다. 접점·비겹침에는 경고가 없고, 짧은 활동의 확대 높이가 가짜 경고를 만들지 않는다. 날짜를 바꾸면 이전 window의 결과를 표시하지 않는다.
- Debug APK/AndroidTest APK 빌드 성공. lintDebug **오류0 / 기존 경고12**. FSD **92파일 / self-test24 통과**. git diff --check 통과. minSdk31 및 toolchain/의존성 유지.

명령(자기 checkout):

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ANDROID_HOME=/Users/jominjun/Library/Android/sdk ./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests '*Busy*' --tests '*DayTimelineTest' :app:assembleDebug :app:lintDebug
ANDROID_SERIAL=emulator-5556 ./apps/android/gradlew -p apps/android :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<아래 클래스 또는 메서드> -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
python3 apps/android/scripts/check-fsd.py --self-test
```

클래스: `io.fixabley.dearby.features.calendarbusy.AndroidBusyProviderTest`, `io.fixabley.dearby.BusyTimelineDisplayTest`, `io.fixabley.dearby.DayTimelineTest`; `BusyCalendarFlowTest#offConsentCancelDeniedNeverQueries`, `#selectedActivityDayOverlapLight`를 1배에서 실행하고 `#selectedActivityDayOverlapDarkLarge`는 실제 font_scale2.0에서 실행했다. 각 실행의 JAVA_HOME/ANDROID_HOME은 첫 줄과 동일하다.

## 대표 PNG · 가짜 익명 일정

모두 전용5556에서 실제 렌더링을 캡처했다. 큰글자 시트는 실제 system font_scale2.0을 사용했다. Compose LocalDensity만 바꾼 중간 캡처는 최종 PNG로 남기지 않았다.

| 상태 | 증거 |
|---|---|
| 연결 전 OFF | [off-light](screenshots/busy-evidence/off-light.png) |
| 목적 안내 | [consent-light](screenshots/busy-evidence/consent-light.png) |
| 동의 취소 | [cancel-light](screenshots/busy-evidence/cancel-light.png) |
| fake 권한 거절 | [denied-light](screenshots/busy-evidence/denied-light.png) |
| ON 겹침/시간눈금 배경 | [busy-light](screenshots/busy-evidence/busy-light.png) |
| 선택일 다음날 | [next-day-light](screenshots/busy-evidence/next-day-light.png) |
| dark 실제2배 | [busy-dark-2x](screenshots/busy-evidence/busy-dark-2x.png) |
| dark 실제2배 다음날 | [next-day-dark-2x](screenshots/busy-evidence/next-day-dark-2x.png) |
| 겹침09:15–09:45 / 접점10–11 / 비겹침11:30–12 | [boundaries-light](screenshots/busy-evidence/boundaries-light.png) |
| OFF 후 활동: 배경/경고 제거 | [hidden-light](screenshots/busy-evidence/hidden-light.png) |

## 보존 및 한계

사용자5554에 접근하지 않았다. 5556 font_scale1.0 / night=no를 복원·확인했고 favorites prefs `<map />`가 그대로다. READ_CALENDAR는 granted=false를 확인했다. 실제 OS 권한 허용, 사용자 CalendarProvider 조회, 캘린더 일정 저장/수정/삭제, Calendar 계정 설정은 하지 않았다. 거절 화면은 fake callback의 결과이며 OS 권한 대화상자 실기 검증으로 주장하지 않는다.

반복 occurrence 전개는 공식 Instances 계약에 의존한다. 실제 계정의 반복/예외 일정, OEM CalendarProvider 차이, 관리형 기기의 정책 제한 UI는 미검증이다. 비교는 사용자가 선택한 활동 날짜만이며 전체 활동 참여 가능을 보장하지 않는다. 제목·장소·주최자·메모 문자열은 projection/화면/로그/디스크 캐시에 없다. OS permission popup 자체와 실제 개인 캘린더를 이용한 수동 확인은 승인된 별도 환경에서 남아 있다.
