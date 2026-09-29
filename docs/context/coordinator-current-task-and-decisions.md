# 조율 — 2026-09-29

## 현재 작업 — 탐색 전용 노출과 실제 일정 겹침 (#54)

PR40은 2026-09-28 사용자 요청으로 main 849b1fe에 병합됐고 필수 검사명 불일치 #53도 해결됐다. 2026-09-29 사용자는 기존 파일을 보존하면서 탐색/상세/신청/겹치는 시간 확인하기만 노출하도록 요청했고 실제 기기 캘린더 연동 추가 구현도 승인했다. 루트는 origin/main에서 feat/discovery-calendar를 생성했고 공통 명세·통합만 담당한다. API/공통 데이터 계약 변경은 필요하지 않다.

Run `run_8ba522965ee6`, coordinator `term_1cbabda3-9f12-4f38-9655-ef8a0a3548fd`. 기존 하위 checkout·터미널 목록을 확인했으나 iOS task_e2d4a2436ae4, Android task_f0066409b05f는 업무 실행 전에 시작 실패했다. 최초 두 건은 Codex 업데이트 안내에 입력되어 업무 실행 전 종료된 것을 실제 출력으로 확인하고 abandon/retain 처리했다. 이어진 신규 세션(ctx_4d6d78c277cf/ctx_3b0279ef9065)과 같은 세션 재사용(ctx_7db4243f0414/ctx_254163acf611)은 agent_readiness timeout으로 실패했다. 실패 6건 모두 retained이고 reclaimable은 0이다. #48에 시작 실패를 추적한다.

사용자가 이 작업에 한해 현재 브랜치에서 메인이 양 플랫폼을 직접 구현하도록 명시적으로 승인했다. 이후 워크트리 정리를 먼저 하도록 요청했다. 하위 4개 checkout의 커밋은 git cherry 기준 모두 origin/main에 동일 패치가 있으며 네이티브/API는 clean, web만 Next.js가 생성한 AGENTS.md 변경이 있다. 전체 하위 파일(ignored 검증 자료 포함)과 Git 이력을 별도 로컬 백업하고 세션 보존 선호와 제거 범위를 확인한 뒤 진행한다. 앱 구현은 아직 시작하지 않았으며 승인된 화면/캘린더 목표는 유지한다.

수락 조건: 파일·데이터 보존, 비노출 기능 딥링크 우회 및 자동 auth/import 차단, 탐색/상세/신청 왕복 유지, 캘린더 권한·선택·실제 바쁜 시간 비교, 빈 결과와 오류/미확인 구별, 반복·종일·시간대·경계 테스트, 개인정보 세션 수명, 두 플랫폼 빌드/검사 및 전용 기기 증거. 아직 구현·검증 진행 중이다.

## 2026-09-28 PR 병합 요청

사용자가 PR40 병합을 승인했다. 기존 head 4986572의 API/Android/iOS 원격 CI 성공과 충돌 없음을 확인하고 Draft를 해제했다. main 필수 검사명 `iOS architecture`와 현재 작업명 `ios`의 불일치로 정상 병합이 차단되어 #53에 증거와 해소 조건을 기록했다. 실제 구조·스타일·빌드·테스트를 실행하는 iOS 작업 표시명을 필수 검사명에 맞춘다. 보호 규칙·검사 내용은 유지하며 새 head의 CI 성공 후 정상 merge commit으로 병합한다. 최종 병합 상태와 SHA는 PR40을 확인한다. 기존 retained 세션과 하위 checkout은 변경하지 않는다.

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

f315825 원격 PR CI 36291919730의 API/iOS/Android가 모두 통과했고 PR40 댓글에 기록했다. 변경 후 Ponytail 검토에서 추가 삭제 후보는 없고 정확성·저장·접근성 범위는 검증 문서를 따른다. .github/workflows/native.yml은 PR 및 main push만 실행해 중복 feature push 검증을 제거했다.

격리 통합 서버는 양 플랫폼 검증 후 PID27463의 명령을 확인하고 SIGTERM으로 정상 종료했다. session72494 종료0, 임시 DB/메일 디렉터리 삭제를 확인했다. 과거 포트52777은 이제 실행 중이 아니다. 조율 Simulator4712C750-BF32-42A8-8FBA-9AD2BA339EFC도 Shutdown 상태를 확인했다. 다른 기기나 retained 세션은 종료하지 않았다. 실제 공식 수집 마지막 확인은 2026-09-27T03:21:49Z, 당시 30공고/28프로그램/26조직/모집중1이었다. 운영 최신성을 의미하지 않는다.

주요 증거: [검증 매트릭스](../implementation/verification-matrix.md), [iOS 실제 화면](../../apps/ios/docs/evidence/catalog/README.md), [Android 검증](../../apps/android/docs/VERIFICATION.md).

전체 제품 #41, 외부 폼 #36, 운영 조건 #42/#43/#44, 접근성 #14, 운영 수집/경보 #49를 계속 추적한다. 이슈 작성은 해결 완료가 아니다. 차단 사항은 이슈에 남기고 독립 작업을 계속한다.


## 2026-09-27 컨펌 시안 복구 진행 (#50)

사용자가 실제 화면과 렌더링 시안의 차이를 지적했고 수정 후 컨펌된 화면을 기준으로 지정했다. [시각 계약](../design/native-visual-contract.md)과 원본 18종/출처 manifest를 c86f809에 보존했다. 기본 native appearance를 우선하던 구 문서는 이 기준 아래에 둔다. 초기/반려 시안과 최신 명시적 수정의 우선순위를 표로 남겼다.

Run `run_2cda4f7a8687`, coordinator `term_1cbabda3-9f12-4f38-9655-ef8a0a3548fd`.

- iOS: task_517938ce17a9 / ctx_af275322501e / term_472cad7d-204b-4362-975d-4661f37b6aad / feat/ios-visual-fidelity.
- Android: task_872cf36fa029 / ctx_46936e11509f / term_892f9668-7754-4dae-812c-1c6298cd7caa / feat/android-visual-fidelity.

기존 worktree·retained terminal 상태 확인 후 같은 하위 checkout에 새 supervised 담당 세션을 시작했고 양쪽 input_accepted/turn_started를 확인했다. 과거 세션은 그대로 유지한다. 이번 변경 전 캡처 → 기준 시안 비교 → 실제 UI 교정 → 변경 후 캡처와 기능 회귀가 완료 조건이다. 실제 실행/통합은 아직 진행 중이며 시안 복구 완료로 표현하지 않는다.

Root 격리 API는 포트57937, session73883으로 실행 중이다. 공식 수집 checkedAt 2026-09-27T04:04:39Z, 30개 중 모집중1개. 테스트 이메일만 허용한 임시 데이터이며 production 기본값이 아니다. 양 담당 완료 후 root가 정상 종료한다.

### 컨펌 화면 교정 통합 — 2026-09-27 후속

- 승인 원본18장과 마지막 지시 우선 기준을 docs/design에 보존했다. #50 구현 대조, #51 실제 이미지/콘텐츠 분류 계약, #52 bodyless logout 오류를 추적한다.
- Android 담당 c1ec7c6까지 통합. 최종 UI26·실제 로그인 복귀·저장/재시작 및 조율 JVM28/FSD45/Debug/lint0errors 확인, ctx_46936e11509f succeeded/retained. 정상 동작의 사진/카메라/시스템 시트는 기본 플랫폼 기능이며 미구현 운영 기능을 꾸미지 않는다.
- iOS production source1c2de21까지 통합. 조율 단위27pass/6fixture skip, 구조16, lint57files0위반. 담당의 실제 API/로그아웃과 최대글자/게스트취소 UI 결과는 별도 증거로 기록했다. 캡처 이름만 믿지 않고 이미지를 직접 열어 잘못 매핑된 picker-top 파일을 교정 요청했다.
- 모든 원본/전/후 증거와 한계는 docs/design/visual-fidelity-review.md 및 플랫폼 gallery/report로 연결한다. 원격 CI와 마지막 세션 정리 상태는 다음 기록을 따른다.

### 컨펌 교정 완료 인계 — 2026-09-27 14:01 KST

- Android/iOS 본 작업과 iOS 카드 헤더 후속 작업을 통합하고 실제 최종 캡처를 직접 대조했다. iOS 후속02cd130/8b63646은 뒤쪽 헤더 잘림/투과만 교정했으며 조율 최종 Debug build/SwiftLint도 통과했다.
- run_2cda4f7a8687의 성공 담당들은 사용자 요청대로 retained, reclaimable0. 기존 iOS 터미널 재사용 시도 ctx_4cfa2eac646c는 readiness timeout으로 실패하여 #48에 재현을 남겼다. 새 후속 ctx_d9a62d0bc9d9는 시작 관찰이 불확실했지만 실제 terminal live를 확인해 중복 실행하지 않았고 succeeded/retained로 정리했다.
- 조율 소유 로컬 API PID87011/57937을 정상 종료하고 private mail/DB 임시 디렉터리 삭제를 확인했다. 조율 전용 Simulator는 Shutdown이며 다른 담당 기기/터미널은 임의 종료하지 않았다.
- 최신 참조/검증: docs/design/native-visual-contract.md, docs/design/visual-fidelity-review.md, docs/implementation/verification-matrix.md. 최종 원격 CI 결과는 PR40 checks 및 조율 댓글의 실행 URL을 확인한다. #51 등 운영/데이터 후속은 아직 미완료다.
