# 현재 작업과 결정

2026-09-16: 사용자 승인으로 **직접 하위 두 레이어 제한과 ContentView 책임 분리**를 iOS에 구현·로컬 검증을 완료했고 PR/CI를 정리 중이다. 이전 PR19–26은 main에 병합 완료(`45a7c96`), 당시 main SwiftLint127파일 위반0건. 이 결과를 이번 변경의 검증으로 재사용하지 않는다.

## 승인된 규칙

App→Pages/Widgets, Pages→Widgets/Features, Widgets→Features/Entities, Features→Entities/Shared, Entities→Shared. app/providers의 의존성 생성·수명 관리·주입만 전체 하위 레이어 조립을 허용한다. routes/entrypoint는 예외가 아니다. 동일 슬라이스 허용·형제 슬라이스/상향 금지·공개 계약·순수 표현 규칙 유지. 타입 별칭/재노출·단순 전달 Widget·providers로 기능 UI 이동으로 우회하지 않는다.

ContentView는 시작 상태와 루트 라우팅만 맡는다. 저장소 생성·로딩·재시도는 관찰 가능한 AppSession으로, 탭/목적지 연결은 별도 App routes로, 공고/지도/캘린더의 행동·도메인 조회 조립은 Features/Widgets/Pages의 의미 있는 책임으로 분리한다. 루트 강제 favorites 관찰은 실제 소비 지점의 Observation으로 대체하되 lazy tab 상태 전파를 검증한다.

## 소유권과 진행

Root branch `test/ios-two-layer-boundaries` (선행 `refactor/ios-app-composition`): AGENTS와 docs/architecture 공통 규칙 및 Git/CI 통합 담당. iOS 담당은 기존 checkout에서 최신 origin/main 기반 `feat/ios-two-layer-composition`을 만들고 apps/ios 및 자기 역할 문서만 수정했다. Android/API 이번 구현 범위 아님.

Orca Run `run_58f7d03f4deb`, Task `task_adac1e244b57`, 완료 Dispatch `ctx_3ac914267e01`, terminal `term_dd600aaf-7cbe-4288-a233-2ba46a78d35b`. 기존 terminal 재사용 시도 `ctx_865c17828a0b`는 준비 timeout으로 입력 전달 없이 실패했다. 같은 Task의 새 terminal 배정은 input_accepted와 turn_started 확인. 상세 배정은 orca-sessions-and-worktrees.md.

## 완료 기준과 다음 행동

- Harmonize에서 정상/위반 fixture와 provider 예외 범위 검증, 전체 소스에 최종 규칙 적용.
- SwiftLint, 기존 standalone/cache/busy/detail, Simulator build 및 변경된 사용자 흐름 회귀.
- 기능별 작은 커밋에 관련 테스트/문서 포함; 필요에 맞춰 PR 분리. 앱/기능 3커밋은 PR27, 경계 검사와 공통 정책은 후속 PR로 분리. 기존 검사9/새 검사12 모두 통과하여 분리 호환성을 확인했다.
- Worker 질문에 답하고 완료 증거 검토 후 root에 통합. 실행하지 않은 결과를 성공으로 기록하지 않는다.

이전 병합 기록은 [보존 문서](archive/2026-09-16-fsd-rule-discussion/before-two-layer-limit.md)에 있다. 루트 main 및 역할 연결은 재개 때 Git/Orca에서 재확인한다.

## 이번 검증과 통합

Worker는 succeeded 보고를 수신·확인했고 사용자 요청에 따라 retained로 유지했다. d18a27f(검사), 8de3af7(초기화/라우팅), d454e18(상세/캘린더), 91d5b72(카드/공통 디자인; root에 먼저 반영한 4a3590e에서 로그 공백만 정리)를 검토했다. Root는 앱3커밋을 455ab7f/c7bfde5/3c3d4d9로 먼저 통합해 PR27을 게시하고, 검사 변경을 후속 branch에 통합하고 공통 정책·CI와 같은 커밋으로 묶었다. 검사 적용 후 root와 worker의 추적 파일 트리가 동일함을 확인했다. 기능 커밋은 서로 표시 계약에 의존하므로 개별 중간 커밋이 아닌 각 PR 전체가 검증 단위다.

- 로컬 전체 경계12 tests, SwiftLint152파일 위반0, standalone/cache/snapshot rollback, 시작 재시도/독립 Observation 저장·삭제, busy/calendar/detail 회귀, Simulator build 통과. 상세 증거는 apps/ios/docs/evidence/two-layer-composition/README.md.
- Root가 최종 첫 화면 screenshot을 직접 확인했다. 실제 탭·페이징·더블탭·OS 편집기 UI 동작은 이번 검증에서 완료하지 못했다. MCP tap 성공 응답에도 화면 전환이 없고 Orca helper가 Xcode27에서 변경된 SimulatorKit 경로를 찾지 못하는 환경 문제를 기록했다. 시스템 Xcode/helper를 수정하거나 기기를 초기화하지 않았다.
- PR27: https://github.com/fixabley/dearby/pull/27. 후속 경계 PR/원격 CI는 GitHub에서 재확인한다. main에는 이번 변경을 병합하지 않았다.

Root 통합 checkout에서도 architecture12 exit0, strict lint152/0을 새로 실행했다. 실제 source gate probe를 CI에 추가하며 기존 필수 job 이름/보호 규칙은 유지한다.
