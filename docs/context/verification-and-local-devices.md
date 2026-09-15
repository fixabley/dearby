# 검증 — 마지막 실행 결과와 재현 위치

갱신: 2026-09-16 KST. 최신 대상은 iOS FSD 리팩터링이다. 아래 과거 UI 작업과 실행 시점을 구별한다.

## 병합 후 main lint (2026-09-16 01:29 KST)

사용자 승인 후 PR19–25가 main `fb1214d`에 병합됐다. Root에서 해당 main으로 `bash apps/ios/tests/run_swiftlint.sh`를 새로 실행하여 127파일 위반0건/exit0을 확인했다. 로그 `/tmp/dearby-main-swiftlint.log`, 추가 수정 없음. main 파일 트리는 이전 최종 검증 head e7e682b와 동일하다. [병합 전 최종 CI](https://github.com/fixabley/dearby/actions/runs/34994511738)는 lint·Harmonize·전체 회귀·Simulator 빌드 성공이며 이번 로컬 lint 실행과 구별한다.

## 폴더명·SwiftLint 후속

소문자 시작 폴더 변경은 [PR24 검증 기록](../../apps/ios/docs/evidence/lowercase-folders/README.md)이 정본이다. Root에서도 architecture 9 tests 및 전체 113개 제품/asset 파일의 바이트 보존을 확인했다. [run34993653054](https://github.com/fixabley/dearby/actions/runs/34993653054)에서 hosted 구조·전체 회귀·Simulator 빌드가 성공했다. SwiftLint는 이후 ee9c4e0(root 051c45e)에서 별도로 검증했다. [SwiftLint 실행 정본](../../apps/ios/docs/evidence/swiftlint/README.md): 0.65.1/명시61규칙, 앱109+테스트18파일 strict 위반0건, tool missing/version mismatch/앱·테스트 위반 실패와 복원, 최종 architecture9/full standalone/busy/detail 및 Xcode27 Simulator 빌드·첫 화면 통과. Root가 같은 소스에서 checksum 설치·lint·runner fixture·Harmonize를 직접 실행하여 통과했고 첫 화면 PNG를 확인했다. 기존 전체 UI 조작과 이번 첫 화면 확인을 구별한다. 최신 hosted 결과는 [PR25 checks](https://github.com/fixabley/dearby/pull/25/checks)에서 확인한다.

## iOS Harmonize FSD 검증

정본은 [FSD-MIGRATION.md](../../apps/ios/docs/FSD-MIGRATION.md), 현재 PR은 [#23 checks](https://github.com/fixabley/dearby/pull/23/checks)다. 최종 제품 소스 a214cda의 [hosted 검증](https://github.com/fixabley/dearby/actions/runs/34990938224)에서 Harmonize 9 tests/109파일, 전체 standalone·busy-calendar·detail-presentation 회귀와 실제 Simulator 빌드가 통과했다. Root가 두 job의 성공과 상세 PASS 로그를 확인했다.

로컬은 Xcode26.6/Swift6.3.3에서 기능별 검증 후 도구 체인이 27.0으로 외부 교체됐다. 사용자가 라이선스에 동의한 뒤 Xcode27.0(27A266a)/Swift6.4에서 root 구조 검사와 담당 전체 회귀·빌드가 다시 통과했다. 전용 Simulator는 Dearby-FSD-Verify, iOS26.5, `B04DEBB6-53B1-4CB1-858C-8C290846D4AB`이며 글자 크기 large로 복원했다. 새 Xcode의 화면 프런트엔드는 Device Hub이다. 기기/PID/window는 다음 작업 시 다시 확인한다.

새 Xcode27 최종 UI에서 설정 전환·상세 14–16시 타임라인·즐겨찾기 삭제/재저장 동기화·재실행 복원·DB 상세 진입을 확인했다. Root는 최종 timeline/favorites/failure PNG를 직접 검토했다. 재조회 버튼의 UI 클릭 완료는 미확인이며 failed→refresh→ready 자동회귀와 구별한다. 실제 임시 위반 검사와 시점별 UI 증거는 정본에 기록한다. 테스트는 `--busy-calendar-fixture=empty`로 실행했으며 실제 개인 캘린더 접근·저장·전송을 하지 않았다. 전체 VoiceOver/모든 기기 검증은 포함하지 않는다. Android/API는 이번에 다시 빌드하거나 수정하지 않았다.

## 이전 검증 기록

## 기간·장소 줄 컴포넌트 검증 (2026-09-15 17시 KST)

시작과 종료는 원본 경계에서 별도 State 원소로 만들고 각각 Text로 표시한다. 장소는 원본 필드와 공백 단위로 배치하며, 열보다 긴 단일 단어만 내부 줄바꿈을 허용한다. Root가 양쪽 실제 화면과 변경 코드를 검토했다.

- [iOS 이번 검증](../../apps/ios/docs/evidence/card-lines/README.md): 관련 State 두 sample, FSD102/guard, Simulator 빌드·실행 통과. 첫 카드·다중/누락 일정·AX5 화면 확인. Simulator 서비스 오류는 데이터 삭제 없이 복구했다. worker release 뒤 root가 앱을 재실행하여 PID52933을 확인했다.
- [Android 이번 검증](../../apps/android/docs/card-schedules/CARD-LINES.md): 관련 JVM6·FSD101/self-test24·Debug/계측APK 빌드 통과. 최종 카드 계측 로그 `build/card-lines/instrumentation-layout.log`: OK (2 tests), 31.179초. Root가 `screenshots-final/card-schedule-canonical-map.png`에서 시작/종료 별도 줄, 장소 단어 줄배치, host·네이티브 아이콘을 직접 확인했다. 통합 소스 동일성 및 worker release 뒤 MainActivity 복귀 Status ok/PID8284를 확인했다.

이번 변경에서 전체 지도·캘린더 회귀나 실제 스크린리더 낭독을 다시 실행하지 않았다. 아래 검증은 이전 구현 시점의 결과다.

## 이전 카드 일정 표시 검증 (2026-09-15 16:24 KST)

최종 요구: 일정명/구분선/캘린더 아이콘+기간/위치 아이콘+위치, 유효 좌표 지도 아이콘 버튼, URL은 host만 표시.

16:25 KST: 플랫폼 최종 소스가 main 통합 결과와 동일한 것을 git diff로 확인했다. worker 정리 후 iOS 앱을 다시 열어 PID45082, Android 기존 MainActivity로 복귀 Status ok를 확인했다. 앱과 기기는 실행 유지. 두 플랫폼의 최종 실제 PNG를 root가 직접 확인했다. 플랫폼별 검증 정본:

- [iOS 카드 일정](../../apps/ios/docs/evidence/card-schedules/README.md), [최종 도메인·아이콘](../../apps/ios/docs/evidence/card-url-host/README.md): 전체 standalone 이후 관련 State 두 sample·URL fixture·FSD101/guard 및 최종 Simulator 빌드 통과. 실제 지도 목적지 핀·paging·다중 일정·AX5/상세 확인. 마지막 도메인/아이콘 수정은 관련 검사와 첫 카드·다중 일정 화면으로 검증했다.
- [Android](../../apps/android/docs/card-schedules/VERIFICATION.md): JVM79, FSD100/self-test24, Debug/계측APK, lint 오류0/기존경고13. 일정/정렬 구현의 관련 계측14 통과 후 URL/아이콘 변경마다 카드 계측2 통과. 최종 화면 도메인·아이콘 확인. 지도 좌표/Google Maps 핀·저장·paging 검증은 일정 구현 시점 결과다.

한계: iOS 실제 더블탭은 도구 포커스 오류로 입력 전 중단; 코드 보존과 공유 상태 테스트 결과와 구별한다. 전체 실기기/스크린리더/OS캘린더 테스트는 범위 밖. Android 최종 증거는 해당 checkout의 Git 제외 build/card-schedule-verification/app-icons-final.png, iOS는 위 최종 보고서의 first-card.png. 이전 이모지/긴 URL PNG를 최종 화면으로 해석하지 않는다.

## 이번 빌드·실행 (2026-09-15 13:03 KST)

main `9eeb383`을 플랫폼별 checkout에서 Debug 빌드·설치·실행했다. 기능 코드 변경 없음. Root는 두 빌드 성공 로그와 실제 화면 PNG를 읽어 확인했다.

| 플랫폼 | 결과 | 실행 기기 |
| --- | --- | --- |
| iOS | Xcode26.6 BUILD SUCCEEDED, 앱 PID21606 및 공고 카드 화면 확인 | iPhone17/iOS26.5, Dearby-Run-20260915, A617D464-41FC-4C33-A3AC-A109D5C9F054 |
| Android | assembleDebug 성공(2m38s, 37 tasks), 설치 Success, launch Status ok, PID4177/MainActivity resumed 및 최초 안내 화면 확인 | Pixel7/API36 ARM64, Dearby_Android_Run, emulator-5556 |

두 앱/기기를 실행 상태로 남겼다. 세션 정리 후 iOS 기존 PID가 종료된 것을 확인하여 메인에서 같은 설치 앱을 `xcrun simctl launch`로 재실행했고 최종 PID는 24087이다. Android PID4177은 계속 실행 중이다. 기존 전용 기기가 현재 목록에 없어 새로 생성했으며 사용자 기기/데이터 초기화 없음. Android는 JBR21.0.10이고 Gradle이 Platform36 revision2를 설치했다. 이번에는 전체 회귀·캘린더 권한·접근성 테스트를 실행하지 않았다.

증거 정본:

- [iOS 실행 보고서](/Users/jominjun/orca/workspaces/dearby/dearby-ios-run/apps/ios/docs/evidence/run-20260915/README.md)
- [Android 실행 보고서](/Users/jominjun/orca/workspaces/dearby/dearby-android-run/docs/context/android-implementation-and-handoff.md)

## 과거 #2 검증

| 대상 | 실제 결과 | 범위 |
| --- | --- | --- |
| iOS PR8 | Xcode26.6 build 성공, 전체 standalone 회귀 exit0, FSD69/negative fixtures | Shared UI·State 날짜 변환·기존 favorites/캐시/SwiftData/지도/캘린더 |
| iOS UI | 실제 미저장 카드 doubletap 저장, swipe·상세·목록·삭제, 신청/행사 EventKit 진입/취소, Maps 실행, light/dark·AX5 | 새 전용 Simulator, 실제 캘린더 저장 없음 |
| Android PR9 | JVM46·기기계측39 failures/errors/skips0, FSD68/self-test24, Debug/Release(R8)/계측APK, lint 오류0/기존12 | 날짜 변환·phase calendar 매칭·상태·Room 포함 |
| Android UI | light/dark 기본·2배 PNG36개, 일정별 제목/기간/장소/버튼 및 카드·목록 검토 | 전용5556; 외부 앱 전달값 검증 |
| 공통 | 문서 diff 검토 | 이번 #2에서 root 데이터/Node 테스트 재실행하지 않음 |
| API | 미실행 | #2 범위 밖 |

Root는 플랫폼 최종 XML/로그/원격 PR 범위와 주요 실제 PNG를 읽어 확인했다. 플랫폼별 보고서 위치는 [iOS 인계](ios-implementation-and-handoff.md), [Android 인계](android-implementation-and-handoff.md)를 따른다. 이전 #1 main 통합의 Node17·samples/FSD66·60 결과는 과거 결과이며 archive/통합 기록에 남아 있다.

## 한계

실제 Calendar Save·동기화, VoiceOver/TalkBack 전체 음성, 모든 기종/OEM·회전·태블릿·API31 실기기는 미검증. iOS 최신 Maps 앱 실행과 이전 c1045f1 pin 증거를 구별한다. Android는 지도/Calendar Intent 전달을 검증했으며 외부 앱 내부 화면은 미검증. iOS 다단계 모든 editor를 실제 열지 않았고 State 순서/mapper 회귀와 소스 연결을 검증했다.

## 재현

현재 플랫폼 worktree의 README 및 검증 문서를 따른다. iOS `bash apps/ios/tests/run_standalone.sh`, Android apps/android에서 `./gradlew testDebugUnitTest assembleDebug lintDebug`; 계측은 전용 기기 지정 후 실행한다. FSD는 각 플랫폼 scripts/tests 경로다. 루트 npm test/samples:check와 API 테스트는 별개다.

## 과거 기기 기록 (이번 실행 기기는 위 표)

- iOS Xcode26.6/SDK26.6, Simulator26.5. 최신 전용 `4156AE92-5308-4050-93DC-C9E241918DCB` (Dearby-Issue2-Schedule-Verify), light/large 복원, 리뷰용 유지. 기존 A434는 원인 미상의 UI 전환 후 조작 중단, 사용자 C38E 보존. UI 도구가 sheet 배경 AX target을 반환할 수 있어 screenshot과 실제 결과로 판정한다.
- Android 새 AVD Dearby_Issue2_Test, emulator5556, API36.1. JBR25/bytecode17, SDK /Users/jominjun/Library/Android/sdk. font_scale1.0/night=no/favorites 복원 후 리뷰용 유지. 사용자5554 미조작.
- 기기 부팅/UDID/window/도구 기본값은 재개 시 다시 확인한다. 임의 초기화·실제 일정 저장·사용자 데이터 삭제 금지. #1의 삭제된 checkout은 백업에서만 참조한다.
