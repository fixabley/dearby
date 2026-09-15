# 카드 일정·장소 줄 컴포넌트 검증

2026-09-15, Android 전용 checkout `dearby-android-run`, 기준 `8547adb`. 카드 표시만 변경했다.

## 구현

- `CardScheduleState.dateLines`는 원본 시작/종료를 각각 포맷한 배열이며 Column의 별도 Text로 표시한다. 완성된 기간 문자열 split·newline 주입은 없다. 날짜-only, 시작/종료 누락, 역전·충돌·잘못된 날짜 원문, 초·소수초, 외국 시간대 의미를 유지한다.
- `CardPlaceState.lines`는 venue.name/address, 온라인 설명/host를 각각 보존한다. CardPlaceLine은 공백 토큰을 FlowRow에 배치해 열 안에 들어가는 `2관`, `세미나실(5층)` 등은 중간에서 나누지 않는다. 지명 추론·하드코딩 없이 원본 필드 전체를 접근성 Text로 제공한다.
- [Compose 공식 문서](https://developer.android.com/develop/ui/compose/text/style-paragraph#cjk-considerations)의 Strict/Phrase는 언어별 결과가 달라 보조 설정으로 사용한다. 열 자체보다 긴 토큰은 Text soft-wrap으로 넘침을 방지한다. 360dp/fontScale2에서는 `세미나실(5층)`도 열보다 길어 괄호 안 줄바꿈이 생기며 글자는 모두 보존된다.
- FlowRow의 긴 토큰이 기존 IntrinsicSize.Min 행 높이에 잘리는 것을 캡처로 발견했다. 행의 강제 intrinsic 높이를 없애고 내용 높이의 구분선을 그려 전체 본문이 스크롤되도록 수정했다. State-only UI, native icon, host 정책, exact phase/좌표 callback은 유지한다.

## 이번 실행 결과

- 관련 JVM **6개**: CardScheduleStateTest 4 + NoticeCardViewModelTest 2, 실패0. 원본 필드 분리·URL 보존·날짜 경계 의미 검사.
- FSD **101파일**, boundary self-test **24개** 통과.
- Debug 및 AndroidTest APK 빌드 성공, 5556에 설치 성공.
- CardScheduleFlowTest **2개** 최종 통과(31.179초): 번들 첫 카드 원본 venue→App geo adapter, fontScale2/360dp 다중 일정·날짜-only·경계 누락·온라인·긴 장소·하단 버튼 스크롤 및 callback. 긴 URL 마지막 토큰의 실제 unmerged Text 노드 표시도 검사한다.
- 첫 계측은 317.398초/2개 통과였지만 캡처에서 긴 토큰 누락을 발견했으므로 최종 증거로 삼지 않는다. 수정 후 위 31.179초 실행과 최종 PNG를 기준으로 한다.
- PNG를 직접 열어 첫 카드의 시작/종료 두 줄, `충북대학교 중앙도서관 2관` / `세미나실(5층)`, 큰 글자 누락/다중 일정, 장소명/주소 분리, 마지막 `complete` 표시를 확인했다.

## 로컬 증거·재현

`apps/android/build/card-lines/`(Git 제외): `build.log`, `build-layout.log`, `fsd.log`, `instrumentation-layout.log`, `screenshots-final/card-schedule-{canonical-map,large-0-date,large-1-date,large-2-date,large-3-date,large-long-location,large-location-end}.png`, `app-final.png`, `launch-final.log`, `activity-final.txt`. 다른 checkout에 자동 전달되지 않는다. `preexisting-docs.patch`와 문서 원본 복사본에 작업 전 미커밋 실행 기록을 보존했다.

```sh
cd apps/android
ANDROID_HOME=/Users/jominjun/Library/Android/sdk JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest --tests '*CardScheduleStateTest' --tests '*NoticeCardViewModelTest' :app:assembleDebug :app:assembleDebugAndroidTest
python3 scripts/check-fsd.py --self-test
/Users/jominjun/Library/Android/sdk/platform-tools/adb -s emulator-5556 shell am instrument -w -e class io.fixabley.dearby.widgets.notice.noticecard.CardScheduleFlowTest io.fixabley.dearby.test/androidx.test.runner.AndroidJUnitRunner
```

전체 지도·전체 회귀·Lint·실제 OS 캘린더·TalkBack 검증은 이번에 재실행하지 않았다. 상세/API/공통/iOS는 변경하지 않았으며 전용5556과 Dearby 앱을 실행 상태로 유지한다. push/PR 없이 메인 검토·통합을 기다린다.
