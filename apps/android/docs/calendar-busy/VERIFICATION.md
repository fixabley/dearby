# #10 Android 최신 시각 검증 · 2026-09-15

최종 형태는 **시간축 본문의 Primary 활동 + 반투명 tertiary 배색 내 일정 + 실제 교집합 구간만 점선**이다. 경고 아이콘과 ON/OFF/동의를 유지한다. 시간 gutter/우측 lane/두열 디자인의 PNG는 최신 증거에서 교체했다. 상세 날짜·요일 / 다음 줄 시간 구조를 유지하며 한국 시간 중복 라벨만 제거했다.

## 이번 후속 작업에서 실행

- `TimelineIntersectionTest` JVM **3개 통과**: 부분교집합/포함/불포함/끝-시작 접점/나노초 양의 교집합/자정 clip 및 union. 점선 계산에 최소 시각 높이를 넣지 않는다.
- 전용5556 계측 **8개 통과**: BusyTimelineDisplayTest 4(본문 동일 x/폭, 08:45–09:45 busy 중 09:00–09:45만 점선임을 위치/높이로 검증, 접점/short 무경고·무점선, 이전 window 숨김, 긴 활동 제목과 busy 문구의 세로영역 비중첩), BusyCalendarFlowTest 3(OFF/동의취소/fake거절, light 선택일/OFF, 실제2배 dark 선택일/OFF), DetailTimezoneTest 1(한국 라벨 없음/원본 시각 AX/Europe-London 표시 유지).
- Debug APK 및 AndroidTest APK compile/build 성공. FSD **94파일 / self-test24 통과**, git diff --check 통과. 이번 요청은 표시 변경이므로 provider/권한 전체 suite와 lint 전체를 재실행하지 않았다.
- 이번 변경은 Shared 표시/관련 Page의 라벨/테스트/문서만 포함한다. feature provider/session, permission, cache, favorites, source, export, map 코드는 수정하지 않았다.

명령의 환경은 JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home, ANDROID_HOME=/Users/jominjun/Library/Android/sdk, ANDROID_SERIAL=emulator-5556이다.

```sh
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests '*TimelineIntersectionTest' :app:assembleDebug
./apps/android/gradlew -p apps/android :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.fixabley.dearby.BusyTimelineDisplayTest,io.fixabley.dearby.BusyCalendarFlowTest,io.fixabley.dearby.DetailTimezoneTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
python3 apps/android/scripts/check-fsd.py --self-test
```

계측은 관련 클래스/메서드를 나눠 실행했다. BusyCalendarFlowTest의 dark 메서드는 실제 system font_scale=2.0으로 실행하고 1.0으로 복원했다. ModalBottomSheet에서 LocalDensity만 바꾼 캡처를 큰글자 증거로 사용하지 않았다.

## 최신 실제 렌더링 PNG · 모두 fake provider

| 상태 | 증거 |
|---|---|
| 연결 전 OFF | [off-light](screenshots/busy-evidence/off-light.png) |
| 목적 안내 / 동의 취소 / fake 거절 | [consent](screenshots/busy-evidence/consent-light.png), [cancel](screenshots/busy-evidence/cancel-light.png), [denied](screenshots/busy-evidence/denied-light.png) |
| 본문 배색 + 점선 | [busy-light](screenshots/busy-evidence/busy-light.png) |
| 선택일 다음날 | [next-day-light](screenshots/busy-evidence/next-day-light.png) |
| dark 실제2배 | [busy-dark-2x](screenshots/busy-evidence/busy-dark-2x.png) |
| dark 실제2배 다음날 | [next-day-dark-2x](screenshots/busy-evidence/next-day-dark-2x.png) |
| 부분교집합/접점/비겹침 | [boundaries-light](screenshots/busy-evidence/boundaries-light.png) |
| OFF 후 블록·점선·경고 제거 | [hidden-light](screenshots/busy-evidence/hidden-light.png), [hidden-dark-2x](screenshots/busy-evidence/hidden-dark-2x.png) |

총11장이다. 스크롤 가능한 320dp 시간창에서는 화면 밖 시간 구간을 세로 스크롤로 읽는다. 큰글자에서 긴 활동 제목은 부모 상세의 전체 제목 및 전체 AX로 보존되며 그래프 내부는 최대2줄 표시다. 짧아 문구가 들어가지 않는 busy는 아래 한국어 요약으로 확인한다.

## 이전 기능 검증과 한계

이전 head1cd2ae6 작업의 provider/session 포함 JVM13·계측12·lint 오류0/기존경고12는 그 시점 결과이며 이번 시각 변경에서 새로 실행한 결과로 주장하지 않는다. Instances recurrence/ALL_DAY/SQL/cancellation 계약은 [설계 문서](README.md)에 유지되어 있다.

사용자5554에 접근하지 않았고 전용5556 font_scale1.0 / night=no 및 favorites prefs `<map />` 보존·복원을 확인했다. 실제 OS 권한 허용·개인 CalendarProvider 조회·Calendar Save·계정설정은 하지 않았다. 계측의 거절은 fake callback UI이며 실제 OS 권한 대화상자 검증이 아니다. 실제 반복예외/OEM/관리형기기 및 개인 캘린더 실기 확인은 계속 미검증이다. 날짜/시간의 sourcezone·precision/불확실성 및 Calendar export는 바꾸지 않았다.
