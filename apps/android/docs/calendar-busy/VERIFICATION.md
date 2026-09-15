# Android 최신 검증 · 2026-09-15

최종 화면은 시간축 본문의 Primary 활동과 반투명 tertiary 내 일정 블록이다. 실제 양의 교집합만 점선으로 강조하며 경고와 요약도 해당 교집합 시각을 나타낸다. 예를 들어 활동14–16시와 busy15–17시는 `겹치는 시간 · 오후 3시부터 오후 4시까지`다. 이전 gutter 배경/두열/상세 스위치 증거는 교체했다.

## 이번 작업에서 실행한 검증

- JVM **8개**: TimelineIntersectionTest4(부분/포함/접점/short/자정 union 및 실제 교집합 한국어 문구), CalendarSettingsControllerTest4(최초 안내 처리·저장값 재생성·허용·거절·실패·철회·제한·늦은 callback).
- 전용5556 계측 **11개**: BusyTimelineDisplayTest4(본문 좌표·폭, 실제 교집합 점선 위치/높이, 접점·short 무충돌, 이전 window 숨김, 긴 제목과 busy 문구 비중첩), BusyCalendarFlowTest3(조회 도중 OFF와 지연 결과 폐기, light 선택일/OFF, 실제2배 dark 선택일/OFF), CalendarSettingsFlowTest3(나중에·설정 거절, 기존 허용·영속 ON/OFF, 실제 앱 gear 진입·피드 빠른 이동·저장·상세), DetailTimezoneTest1(한국 시간 라벨 생략/원본 AX/외국 시간대 유지).
- Debug 및 AndroidTest APK 빌드 성공. FSD **98파일 / self-test24** 통과. 관련 클래스를 여러 번 나눠 실행했으며 중복 실행을 개수에 합산하지 않았다.
- 피드 100ms 빠른 swipe에서 1→2번째 카드 이동, double-tap 관심 저장, 환경설정 진입, 상세 전체 제목 접근을 보통 글자와 실제 system font_scale2.0에서 확인했다. 피드/카드 코드는 변경하지 않았다.

환경: Android Studio bundled JBR, ANDROID_HOME=/Users/jominjun/Library/Android/sdk, ANDROID_SERIAL=emulator-5556. Gradle 명령은 `./apps/android/gradlew -p apps/android`에 `:app:testDebugUnitTest --tests '*TimelineIntersectionTest' --tests '*CalendarSettingsControllerTest'`, `:app:assembleDebug :app:assembleDebugAndroidTest`, 관련 클래스/메서드 지정 `:app:connectedDebugAndroidTest`를 사용했다. 구조 검증은 `python3 apps/android/scripts/check-fsd.py --self-test`다.

로컬 실행 로그: `/tmp/android-settings-final-light.log`, `/tmp/android-settings-ui.log`, `/tmp/android-settings-final-large.log`, `/tmp/android-settings-gear-final.log`, `/tmp/android-overlay-date-test.log`. 마지막 gear vector 수정 후 실제2배 앱 진입 테스트를 재실행했다. 큰 글자는 실제 시스템 설정을 사용했으며 종료 후 font_scale1.0으로 복원했다.

## 최신 대표 PNG · fake provider / fake permission callback

| 상태 | 증거 |
|---|---|
| 최초 안내 켜기/나중에 | [first-launch](screenshots/busy-evidence/first-launch.png) |
| 환경설정 OFF/거절/ON | [off](screenshots/busy-evidence/settings-off.png), [denied](screenshots/busy-evidence/settings-denied.png), [on](screenshots/busy-evidence/settings-on.png) |
| 실제2배 앱 gear와 환경설정 | [settings-entry](screenshots/busy-evidence/settings-entry-2x.png) |
| 실제2배 빠른 이동 후 2번째 카드/관심 저장 | [feed-page](screenshots/busy-evidence/feed-page-2x.png) |
| 조회 중 OFF 후 지연 결과 폐기 | [detail-off-after-loading](screenshots/busy-evidence/detail-off-after-loading.png) |
| 본문 배색/정확한 점선과 요약 | [light](screenshots/busy-evidence/busy-light.png), [dark-2x](screenshots/busy-evidence/busy-dark-2x.png) |
| 다음 선택일 | [light](screenshots/busy-evidence/next-day-light.png), [dark-2x](screenshots/busy-evidence/next-day-dark-2x.png) |
| 부분교집합/접점/비겹침 | [boundaries](screenshots/busy-evidence/boundaries-light.png) |
| OFF 후 블록·점선·경고 제거 | [light](screenshots/busy-evidence/hidden-light.png), [dark-2x](screenshots/busy-evidence/hidden-dark-2x.png) |

총14장이다. 최초 안내 및 설정 단독 테스트의 배경은 테스트 Host이며 앱 전체 진입은 settings-entry/feed-page 증거로 분리했다. 320dp 시간창은 세로 스크롤하며, 짧은 블록의 문구가 들어가지 않으면 아래 시간 요약과 전체 접근성 정보로 읽는다. 긴 활동 제목은 상세 및 전체 AX를 보존하고 그래프 내부 최대2줄이다. 큰 글자 카드의 기존 축약은 유지되며 상세에서 전체 내용을 확인한다.

## 범위와 한계

이번 후속 작업은 App 환경설정/최초 안내·2개 boolean 영속화, Page 조립, Shared 표시와 관련 테스트/문서다. provider/session, Room/cache, favorites, source, export 및 map 구현은 바꾸지 않았다. 기존 head1cd2ae6의 provider 포함 JVM13·계측12·lint 오류0/기존경고12는 과거 결과이며 이번에 전체 suite/lint를 재실행했다고 주장하지 않는다.

5554에 접근하지 않았다. 전용5556 font_scale1.0/night=no 및 기존 favorites prefs `<map />` 보존을 확인했다. 실제 OS 권한 허용·개인 CalendarProvider 조회·Calendar Save·계정설정은 하지 않았다. 거절은 fake callback 검증이고 실제 OEM/관리형기기 권한 UI 및 개인 반복 일정 예외는 미검증이다. 영속화는 fake store 재생성 테스트와 SharedPreferences boolean 어댑터 구현으로 검증했으며 개인 일정은 저장하지 않는다.
