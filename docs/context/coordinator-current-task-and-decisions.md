# 조율 — 2026-09-27

현재 정본은 [네이티브 재착수](native-restart-2026-09-27.md), [제품 명세](../product/native-spec-2026-09.md), [명함 API 계약](../../shared/contracts/native-v1.md), [활동 API 계약](../../shared/contracts/catalog-v1.md)이다.

## 완료와 현재 범위

첫 명함 구현 #37/#38/#39 통합·로컬 검증·양 플랫폼 실제 HTTP 교환 검증 완료. 82ef32a 원격 CI push 36273588483 및 PR 36273592329의 API/iOS/Android 전부 성공을 2026-09-27 재확인했다. PR40 Draft·main 미병합. 첫 작업의 세 하위 세션은 retained다.

현재 두 번째 구현은 #45 공식 활동 카탈로그/API, #46 iOS 발견·저장·상세·신청 자기기록, #47 Android 동일 범위다. 공통 계약 dcb590f를 기준으로 각자 새 브랜치에서 구현한다. 홈은 확인 유효기간 내 실제 모집 중인 활동만, 저장 목록은 모집 종료 항목도 유지한다. 사용자 확인 신청 기록과 주최 측 접수 확인을 구분한다. 자동 입력·캘린더·푸시는 이번 완료 범위가 아니다.

## 실행 세션 (검증 시점과 별개)

Run run_2817352ae397 / coordinator term_1cbabda3-9f12-4f38-9655-ef8a0a3548fd.

| 담당 | Task / Dispatch | 터미널 | 브랜치 |
| --- | --- | --- | --- |
| API | task_8cc6572cb14f / ctx_deaf5c8ca5dd | term_8f4dc959-3adb-4109-ba29-8321fc911357 | feat/api-activities |
| iOS | task_12d3471fcfb1 / ctx_a33d870a32de | term_a08d43fc-ce31-4077-9bac-a2446ed0b9a6 | feat/ios-activities |
| Android | task_e3bf1ed7f722 / ctx_e1187d1fd488 | term_a4ea575a-6b18-4abd-87ea-a0cc5503b02a | feat/android-activities |

세 작업 입력 수락과 turn_started를 확인했다. 기존 retained 터미널 재사용은 readiness timeout으로 실패했고 코드 실행 전에 실패한 것을 확인했다. #48로 추적하고 같은 하위 checkout에 새 담당 세션을 열었다. 기존 세션은 삭제하지 않았다. 재개 시 실제 상태를 다시 확인하고 완료 세션도 retain한다.

## 다음 행동 / 미완료

각 담당 변경 리뷰·선택적 통합, 실제 API 격리 서버를 이용한 양 앱 검증, 조율 checkout 검사, PR40 갱신 및 원격 CI 확인. API 73b2082를 d72a3eb로 통합했고 조율 build/typecheck/HTTP17 통과. Lint 경고 1개는 담당에게 정리 요청했다. 격리 서버 session72494, http://127.0.0.1:52777 (Android 10.0.2.2:52777), 테스트 자료 경로 /var/folders/_c/7hpdsqq9491gzj5z2wh2kf2m0000gn/T/dearby-native-integration-KhUBun. 실제 공식 갱신 2026-09-27T03:15:13Z, 30공고/28프로그램/26조직/모집중1. 양 앱 검증 뒤 정확한 이 서버만 종료하고 임시자료 삭제를 확인한다. 재개 시 서버가 살아 있다고 가정하지 않는다. .github/workflows/native.yml은 PR 및 main push만 실행하도록 중복 feature push 트리거를 제거한다.

전체 제품 #41, 외부 폼 #36, 운영 조건 #42/#43/#44, 접근성 #14를 계속 추적한다. 이슈 작성은 해결 완료가 아니다. 차단 사항은 이슈에 남기고 독립 작업을 계속한다.
