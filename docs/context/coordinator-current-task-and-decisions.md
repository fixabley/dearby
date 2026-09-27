# 조율 — 2026-09-27

현재 정본은 [네이티브 재착수](native-restart-2026-09-27.md), [제품 명세](../product/native-spec-2026-09.md), [명함 API 계약](../../shared/contracts/native-v1.md), [활동 API 계약](../../shared/contracts/catalog-v1.md)이다.

## 완료와 현재 범위

첫 명함 구현 #37/#38/#39 통합·로컬 검증·양 플랫폼 실제 HTTP 교환 검증 완료. 82ef32a 원격 CI push 36273588483 및 PR 36273592329의 API/iOS/Android 전부 성공을 2026-09-27 재확인했다. PR40 Draft·main 미병합. 첫 작업의 세 하위 세션은 retained다.

두 번째 구현 #45 공식 활동 카탈로그/API, #46 iOS 발견·저장·상세·신청 자기기록, #47 Android 동일 범위를 통합·로컬 검증했다. 공통 계약 dcb590f를 기준으로 각자 새 브랜치에서 구현했다. 홈은 확인 유효기간 내 실제 모집 중인 활동만, 저장 목록은 모집 종료 항목도 유지한다. 사용자 확인 신청 기록과 주최 측 접수 확인을 구분한다. 자동 입력·캘린더·푸시는 이번 완료 범위가 아니다.

## 실행 세션 (검증 시점과 별개)

Run run_2817352ae397 / coordinator term_1cbabda3-9f12-4f38-9655-ef8a0a3548fd.

| 담당 | Task / Dispatch | 터미널 | 브랜치 |
| --- | --- | --- | --- |
| API | task_8cc6572cb14f / ctx_deaf5c8ca5dd | term_8f4dc959-3adb-4109-ba29-8321fc911357 | feat/api-activities |
| iOS | task_12d3471fcfb1 / ctx_a33d870a32de | term_a08d43fc-ce31-4077-9bac-a2446ed0b9a6 | feat/ios-activities |
| Android | task_e3bf1ed7f722 / ctx_e1187d1fd488 | term_a4ea575a-6b18-4abd-87ea-a0cc5503b02a | feat/android-activities |

세 담당 모두 succeeded/retained다. API 652c0ac, Android c8cf82a, iOS 10cd5db까지 소유 커밋을 순서대로 통합했다. 조율 checkout API HTTP20/typecheck/lint/build, Android JVM28/Debug/Lint/FSD43, iOS 단위·실제 catalog HTTP28(기존 인증 준비 테스트2 skip)/Simulator test build/구조16/strict SwiftLint54파일 위반0 통과. 담당의 실제 브라우저·화면·재시작 증거와 별도로 기록한다. 세 작업 입력 수락과 turn_started를 확인했다. 기존 retained 터미널 재사용은 readiness timeout으로 실패했고 코드 실행 전에 실패한 것을 확인했다. #48로 추적하고 같은 하위 checkout에 새 담당 세션을 열었다. 기존 세션은 삭제하지 않았다. 재개 시 실제 상태를 다시 확인하고 완료 세션도 retain한다.

## 다음 행동 / 미완료

PR40 갱신 및 최신 head의 원격 CI 확인을 진행한다. 변경 후 Ponytail 검토에서 추가 삭제 후보는 없고 정확성·저장·접근성 범위는 검증 문서를 따른다. .github/workflows/native.yml은 PR 및 main push만 실행해 중복 feature push 검증을 제거했다.

격리 통합 서버는 양 플랫폼 검증 후 PID27463의 명령을 확인하고 SIGTERM으로 정상 종료했다. session72494 종료0, 임시 DB/메일 디렉터리 삭제를 확인했다. 과거 포트52777은 이제 실행 중이 아니다. 조율 Simulator4712C750-BF32-42A8-8FBA-9AD2BA339EFC도 Shutdown 상태를 확인했다. 다른 기기나 retained 세션은 종료하지 않았다. 실제 공식 수집 마지막 확인은 2026-09-27T03:21:49Z, 당시 30공고/28프로그램/26조직/모집중1이었다. 운영 최신성을 의미하지 않는다.

주요 증거: [검증 매트릭스](../implementation/verification-matrix.md), [iOS 실제 화면](../../apps/ios/docs/evidence/catalog/README.md), [Android 검증](../../apps/android/docs/VERIFICATION.md).

전체 제품 #41, 외부 폼 #36, 운영 조건 #42/#43/#44, 접근성 #14, 운영 수집/경보 #49를 계속 추적한다. 이슈 작성은 해결 완료가 아니다. 차단 사항은 이슈에 남기고 독립 작업을 계속한다.
