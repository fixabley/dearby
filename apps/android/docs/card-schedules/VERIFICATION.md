# 공고 카드 일정·위치 (2026-09-15)

## 변경 범위

- `NoticeCardViewModel`이 `CardScheduleState`를 조립한다. 신청 기간 다음에 원본 순서의 모든 schedule을 표시한다.
- 왼쪽 일정명, 1dp 세로 구분선, 오른쪽 📅 날짜와 📍 위치를 배치한다. 이모지와 본문을 별도 열로 두어 긴 URL도 본문 시작점에서 줄바꿈한다.
- 시작·종료가 있으면 `부터 …까지`, 한쪽만 있으면 미확인 경계를 표시한다. date-only는 시간을 만들지 않는다. timestamp는 원본 시간대로 표시하며 한국 시간 반복 라벨은 생략한다. 기본 분 단위이며 원본의 0이 아닌 초·소수초는 보존한다. 충돌·역전·잘못된 날짜는 원본과 `날짜 확인 필요`를 표시한다.
- 신청 위치는 submissionLocations 및 application.url만 사용한다. 활동 위치는 online mode/URL과 기존 `venuesFor`의 exact phase join을 사용한다. 온라인 단계에 물리적 venue를 연결하지 않으며 여러 장소를 모두 보존한다. 미확인 위치를 다른 단계나 sourceURL로 채우지 않는다.
- 유효 좌표에만 48dp 이상 🗺️ IconButton과 일정명·위치를 포함한 접근성 이름을 제공한다. State의 선택 venue를 콜백으로 Discovery → App의 기존 geo 어댑터에 전달한다. View는 모델/저장소 조회와 OS 호출을 하지 않는다.
- compact에서도 제목·분류·참여 대상·모든 일정을 삭제/말줄임하지 않는다. 본문이 넘치면 카드 내부에서 스크롤하고 저장·상세 액션은 하단에 유지한다. 본문 경계에서 기존 세로 pager로 넘긴다.
- 상세 UI, 모델/캐시, 공통·iOS 파일은 수정하지 않았다.

## 이번 검증

전용 `Dearby_Android_Run` / `emulator-5556`, Android 16/API36 ARM64, JBR21, SDK `/Users/jominjun/Library/Android/sdk`에서 실행했다. 사용자 5554는 조작하지 않았다.

- JVM 78건, 실패 0. 신규 projection 3건은 date-only/누락/시간대/소수초/충돌·역전·잘못된 날짜, 신청 위치, 온라인 및 복수 venue, 유효·무효 좌표를 다룬다.
- FSD 100 Kotlin 파일, self-test 24건 통과.
- Debug APK·계측 APK 빌드, Lint 통과. 오류 0·경고 13은 변경하지 않은 의존성/기존 KTX·TOML 제안이다.
- 관련 계측: CardScheduleFlowTest 2, NoticeCardTest 1, DiscoveryFlowTest 5, VenueMapFlowTest 3, VenueMapIntentTest 3. 첫 실행 14건 통과, 마지막 줄 정렬 변경 후 최종 재실행도 14건 통과(153.698초).
- 캡처 시 화면 합성 완료를 기다리고 본문 끝 캡처를 추가한 계측 APK 빌드 및 CardScheduleFlowTest 재실행 2건도 통과(35.991초).
- 신규 계측은 실제 카드 지도 클릭 → App 어댑터 → 정확한 좌표의 geo Intent를 확인한다. 360dp×600dp / fontScale 2에서 신청·온라인·누락·결선 4개 행, 긴 주소/URL 끝, 지도·저장·상세 콜백을 확인하고 화면을 캡처한다. fixture는 계측 전용이며 번들 정본을 변경하지 않는다.
- 기존 계측은 저장/더블탭/페이지 전환/상세 복귀/즐겨찾기 유지 및 기존 상세 지도 분리를 확인한다. 즐겨찾기 테스트는 원래 ID를 백업·복원한다.

## 로컬 증거와 재현

Git 제외 `apps/android/build/card-schedule-verification/`:

- `build-final.log`, `fsd.log`, `instrumentation-final.log`, `evidence-build.log`, `evidence-instrumentation.log`.
- `app-final.png`, `launch-final.log`, `return-final.log`, `activity-final.txt`: 최종 설치 앱 실행·복귀와 MainActivity resumed.
- `map-activity.txt`, `map-result.png`, `map-result-activity.txt`: 카드 실제 탭으로 전달된 geo 좌표/라벨과 Google Maps 핀 렌더링.
- `screenshots-final/card-schedule-canonical-map.png`: 실제 번들 공고의 두 행과 지도 버튼.
- `screenshots-final/card-schedule-large-{0,1,2,3}-date.png`: 큰 글자의 date-only·온라인·누락·다중 일정.
- `screenshots-final/card-schedule-large-long-location.png`: 긴 주소와 지도 버튼.
- `screenshots-final/card-schedule-large-location-end.png`: 본문 끝으로 스크롤한 URL 마지막 문자열과 하단 액션.
- `preexisting-docs.patch`: 작업 전 존재했던 실행 기록 두 문서의 diff 보존본.

```sh
cd apps/android
ANDROID_HOME=/Users/jominjun/Library/Android/sdk JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
python3 scripts/check-fsd.py --self-test
/Users/jominjun/Library/Android/sdk/platform-tools/adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
/Users/jominjun/Library/Android/sdk/platform-tools/adb -s emulator-5556 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
/Users/jominjun/Library/Android/sdk/platform-tools/adb -s emulator-5556 shell am instrument -w -e class io.fixabley.dearby.widgets.notice.noticecard.CardScheduleFlowTest,io.fixabley.dearby.widgets.notice.noticecard.NoticeCardTest,io.fixabley.dearby.DiscoveryFlowTest,io.fixabley.dearby.VenueMapFlowTest,io.fixabley.dearby.app.VenueMapIntentTest io.fixabley.dearby.test/androidx.test.runner.AndroidJUnitRunner
```

## 실제 지도와 최종 상태

설치된 앱의 카드 지도 버튼을 adb tap으로 눌러 Google Maps로 넘어가는 것을 확인했다. 첫 로그인 안내는 Skip으로 건너뛰었으며 계정 로그인이나 위치 권한 변경은 하지 않았다. Google Maps에서 `충북대학교 중앙도서관 2관 세미나실(5층)` 라벨 및 `36.628196, 127.457875` 좌표와 핀 표시를 직접 캡처·확인했다. Activity dump의 원본 geo 좌표는 `36.62819644470018,127.45787581357385`로 기존 어댑터 값과 일치한다.

확인 후 Dearby MainActivity로 복귀했고 `topResumedActivity`와 PID6228을 확인했다(2026-09-15 16:16 KST). 앱·전용 에뮬레이터를 실행 상태로 유지한다. 재개 시 PID·연결은 다시 확인한다.

## 한계

전체 계측/실제 캘린더/TalkBack 전체 탐색/모든 화면 크기를 검증했다고 주장하지 않는다. 외부 지도는 위 한 장소의 핀 렌더링만 확인했으며 경로 안내·지도 앱 저장은 검증하지 않았다. Orca emulator attach/ax는 runtime 응답 연결 오류가 있어 기기 지정 adb로 검증했다. 로그·PNG·APK는 로컬 산출물이며 다른 checkout으로 자동 전달되지 않는다.
