# iOS 구현 인계

## 완료 — 아키텍처 테스트 전체 직렬화 (2026-09-16 02:53 KST)

자기 checkout/branch: dearby-ios-architecture-tests / feat/ios-two-layer-composition, 시작 head91d5b72. 이번 런타임 확인: term_381a9f0e-d446-4fc4-903a-fb16686b7d52 / task_2b86ed171b8c / ctx_4bd675d73f70. 재개 시 Orca 상태 재확인.

세 suite를 `ArchitectureTestSuite`의 extension 안에 중첩하고 부모 하나에 `.serialized` 적용. 모든 suite/parameterized case의 겹침을 막으며 실행 순서 자체는 보장하지 않는다. 기존 테스트 본문·fixture는 공백과 suite 포장을 제외하고 HEAD와 동일함을 비교 확인했다. 12 tests/36 layer pairs, exact provider/public API/probes 유지. 앱·Shared·외부 패키지·공통 정책·runner 수정 없음.

이번 Swift6.4 실행:

- 전체 `run_architecture.sh` exit0: Python guard fixtures/132 Swift sources, Swift Testing12 tests / 3하위+1부모 suite, 1.624초. 모든 suite start/pass와 비중첩 실행 확인. 로그 `/tmp/dearby-serialized-architecture.log`.
- `LIBDISPATCH_COOPERATIVE_POOL_STRICT=1 swift test --package-path apps/ios/tests/ArchitectureTests --skip-build` 30초 subprocess timeout 설정, exit0/12tests/1.616초. 로그 `/tmp/dearby-serialized-strict-pool.log`.
- `test_layer_distance_gate.sh` exit0: 실제 route identifier/import 위반, provider private API/UI 위반 거절, provider 허용·원상복구 성공. 로그 `/tmp/dearby-serialized-probes.log`.
- `run_swiftlint.sh` strict exit0, 153파일/위반0. 로그 `/tmp/dearby-serialized-lint.log`; `git diff --check` 통과.

원인 한계: CI run35002215465 attempt1은 Swift6.1 빌드 완료 뒤 무출력 정지했고 CI stack sample은 없다. Harmonize의 sync read/async barrier write에 의한 starvation은 가설이며 root와 worker의 로컬 strict pool 실행은 재현되지 않았다. [테스트 README](../../apps/ios/tests/ArchitectureTests/README.md)에 실행 원칙·증거·한계를 기록했다.

남은 일: root가 이 테스트 실행 수정 커밋을 cherry-pick해 PR28 push/동일 macOS15·Xcode16.4 CI 재검증. worker push·타 checkout 수정 없음. 아래 기능/UI 검증 기록은 이전 작업 결과이며 이번에 다시 실행하지 않았다.

## 이전 구현·검증 (이번 실행 아님) — 하위 두 레이어 / AppSession (2026-09-16 KST)

자기 checkout dearby-ios-architecture-tests, branch feat/ios-two-layer-composition, origin/main 45a7c96에서 clean 확인/fetch 후 신규 분기. 기존 브랜치와 history 보존, push/merge/타 checkout 변경 없음. Orca terminal term_dd600aaf-7cbe-4288-a233-2ba46a78d35b / task task_adac1e244b57 / dispatch ctx_3ac914267e01. 다음 작업 시 런타임 재확인.

구현: app→pages/widgets, pages→widgets/features, widgets→features/entities, features→entities/shared, entities→shared. 정확한 app/providers만 생성·수명·주입 예외이며 내부 API/UI 구현은 허용하지 않음. source/import 거리·형제·alias/re-export 검사와 실제 production probes 추가. Widget은 coordinator 정정대로 최신 main의 widgets/<slice>/ui|model 유지.

AppSession 시작/저장소 유지/재시도, 최소 ContentView와 AppTabs, SettingsPresentation 수명 분리. Detail Page는 List/라우팅, Widget은 원본 모델/조직/즐겨찾기 State 조합, Feature는 캘린더/지도/동의·수명/실패 UI. Entity NoticeCardBody/OrganizationSummary 및 Feature 행동 UI가 기존 Shared 스타일을 조립. root 강제 favorites Observation read 제거; 독립 발견/즐겨찾기/열린 상세 구독 save/remove 자동 테스트 통과.

검증 정본은 [실행 보고서](../../apps/ios/docs/evidence/two-layer-composition/README.md). 최종 strict lint152/0, architecture12, 기존 standalone/cache/busy/detail 및 새 startup retry/Observation PASS. 최종 제품+기존 main checker9 PASS로 두 PR 분리 가능성을 검증했고 새 checker 복원 후12 PASS. Simulator build_run_sim PASS, PID22874, iOS26.5 B04DEBB6-53B1-4CB1-858C-8C290846D4AB, 자기 checkout build/two-layer-derived.

실제 UI 한계: screenshot/AX 첫 화면·최초동의 표시 확인. MCP tap/touch 응답은 성공이지만 화면 미전환, Orca helper SimulatorKit 경로 오류/Simulator 검정 창. Xcode27 업데이트 로컬 환경 문제를 root와 확인했고 시스템 패치/기기 초기화 없이 보존. 실제 탭 저장/삭제·더블탭·페이지 전환·동의 클릭·OS 에디터/지도는 이번 실행 미검증이며 과거 증거를 새 검증으로 주장하지 않음.

커밋: checker d18a27f, root/startup 8de3af7, detail/calendar d454e18, card/native 최종 커밋은 branch log 참조. 세 기능 커밋은 Page/Entity 표시 계약에 상호 의존하므로 전체 묶음으로 통합. 권장 PR1=기능3개+final public-api/docs/tests(기존 main checker 통과 입증), PR2=checker+root 공통정책. worker history는 checker 먼저이므로 root가 기능 커밋부터 선택해 통합한다. 개별 중간 커밋 검증은 주장하지 않는다.

남은 일: root의 최종 리뷰/공통 문서/CI/PR 구성·통합, 정상 Simulator 입력 환경에서 UI 회귀. 현재 dispatch 보고 후 추가 작업 없이 대기.
