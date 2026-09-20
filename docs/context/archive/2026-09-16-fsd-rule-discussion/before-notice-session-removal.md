# 현재 작업과 결정

2026-09-16: **iOS 하위 두 레이어 제한·ContentView 책임 분리 구현과 코드 CI 검증 완료. PR27/28에서 사용자 리뷰 대기.** main은 이전 병합 `45a7c96`이며 이번 변경은 main에 병합하지 않았다.

## 승인된 규칙과 결과

App→Pages/Widgets, Pages→Widgets/Features, Widgets→Features/Entities, Features→Entities/Shared, Entities→Shared만 직접 참조한다. app/providers의 생성·수명 관리·주입만 모든 하위 레이어 조립을 허용한다. routes/entrypoint는 예외가 아니다. 공개 API·형제 슬라이스·순수 표현 규칙 유지, 타입 별칭/재노출 및 Provider UI 우회 금지. iOS widgets/<slice>/ui|model과 lowerCamelCase 유지. Android/API 이번 범위 아님.

ContentView는 123→28줄로 줄고 시작 상태·탭 진입만 연결한다. AppSession은 저장소/세션 로딩과 재시도, App routes는 탭/목적지, 상세 Widget은 도메인 조합, Features는 지도/캘린더/저장 행동, Entities는 Shared 디자인을 조립하는 순수 UI를 맡는다. 카드 제목은 본문 안에 유지했고 디자인 토큰 복제와 루트 강제 favorites 관찰은 제거했다.

## PR과 검증

- [PR27](https://github.com/fixabley/dearby/pull/27), `refactor/ios-app-composition`, 코드 head3c3d4d9: 초기화/라우팅 455ab7f, 상세/캘린더 c7bfde5, 카드3c3d4d9의 기능 커밋. [CI35001839918](https://github.com/fixabley/dearby/actions/runs/35001839918) 두 job SUCCESS.
- [PR28](https://github.com/fixabley/dearby/pull/28), `test/ios-two-layer-boundaries`, base=PR27 branch. 두 레이어/조립 예외/실제 위반 gate cc8541c 및 전역 test suite 직렬화3c0d12c. **코드 head3c0d12c [CI35004063640](https://github.com/fixabley/dearby/actions/runs/35004063640) 두 job SUCCESS**: SwiftLint153/0, 구조12 tests/4 suites, production 위반 주입·복원, standalone/cache/startup/Observation/busy/detail 회귀, Simulator build. 이후 인계 문서만 갱신한다. 최신 docs commit의 자동 CI 상태는 GitHub에서 확인한다.
- 기존 main 검사9개에서도 새 앱 전체가 통과하여 두 PR의 독립 검증 경계를 확인했다. 기능 커밋 사이 표시 계약 의존 때문에 각 PR 전체가 검증 단위다.
- root에서도 통합 코드 구조12/lint152(직렬화 부모 파일 추가 전) 및 strict pool12 검증, 마지막 worker의153/0·12tests와 앱 파일 트리 동일성 확인. 이전 결과와 새 코드 CI 결과를 구분한다.

원격 Swift6.1에서는 빌드/링크 완료 후 테스트 결과가 없는 대기가 두 번 있었다. 공유 Harmonize cache를 사용하는 모든 suite를 하나의 serialized 부모 아래에 둔 뒤 같은 환경 CI가 통과했다. cache starvation은 가설이며 stack으로 확정하지 않았다. 검사 본문·36개 레이어 조합·위반 조건은 유지했다.

## 검증 한계

[앱 증거](../../apps/ios/docs/evidence/two-layer-composition/README.md)와 [iOS 역할 인계](ios-implementation-and-handoff.md)를 따른다. 첫 화면 screenshot/AX를 확인했지만 Xcode27 업데이트 후 Simulator 입력 도구가 화면을 전환하지 못해 실제 탭·paging·doubletap·동의·OS editor/Maps UI 동작은 이번 실행에서 미검증이다. 원격 build 성공을 UI 조작 성공으로 해석하지 않는다. 앱·기기·worktree는 보존한다.

## 재개와 역할

Root는 test/ios-two-layer-boundaries에서 공통 규칙·CI·통합을 담당한다. iOS checkout dearby-ios-architecture-tests의 feat/ios-two-layer-composition 최종751da86. Run run_58f7d03f4deb의 Task task_adac1e244b57/Dispatch ctx_3ac914267e01 및 후속 task_2b86ed171b8c/ctx_4bd675d73f70 모두 succeeded 보고·ack·retained 완료, reclaimable0. 후속 담당 후보 terminal term_381a9f0e-d446-4fc4-903a-fb16686b7d52는 런타임 재확인 후 새 Task/Dispatch로 사용한다. 완료 ID를 재사용하지 않는다.

다음 행동: 사용자 구조 피드백을 받고 관련 기능 커밋/회귀로 수정. UI 입력 환경 복구 시 미검증 흐름 확인. GitHub main 병합은 별도 사용자 지시 시 수행한다. 상세 작업/CI 조사는 [보존 문서](archive/2026-09-16-fsd-rule-discussion/two-layer-implementation-and-ci-investigation.md)에 있다.
