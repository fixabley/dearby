# 검증 — 마지막 실행 결과와 재현 위치

갱신: 2026-09-15 KST. 아래는 #2 플랫폼 PR의 실제 실행 결과다. main에는 아직 통합하지 않았다.

## 최신 검증

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

## 기기 보존

- iOS Xcode26.6/SDK26.6, Simulator26.5. 최신 전용 `4156AE92-5308-4050-93DC-C9E241918DCB` (Dearby-Issue2-Schedule-Verify), light/large 복원, 리뷰용 유지. 기존 A434는 원인 미상의 UI 전환 후 조작 중단, 사용자 C38E 보존. UI 도구가 sheet 배경 AX target을 반환할 수 있어 screenshot과 실제 결과로 판정한다.
- Android 새 AVD Dearby_Issue2_Test, emulator5556, API36.1. JBR25/bytecode17, SDK /Users/jominjun/Library/Android/sdk. font_scale1.0/night=no/favorites 복원 후 리뷰용 유지. 사용자5554 미조작.
- 기기 부팅/UDID/window/도구 기본값은 재개 시 다시 확인한다. 임의 초기화·실제 일정 저장·사용자 데이터 삭제 금지. #1의 삭제된 checkout은 백업에서만 참조한다.
