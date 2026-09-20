# iOS 구현 인계

## 완료 — NoticeSession 제거 문서 후속

2026-09-16 KST: 자기 checkout `dearby-ios-architecture-tests`의 clean `b6cbfe8`에서 문서 후속만 수행했다. Orca 런타임에서 현재 checkout을 확인했으며 terminal `term_e6d174b0-3d53-4fae-ac33-1f4b0d2ac42d`, task `task_2118e21be810`, dispatch `ctx_d22a8757002f`를 사용했다. 재개 시 런타임 상태를 다시 확인한다.

`ROOT-SESSION.md`를 최신 [APP-STATE.md](../../apps/ios/docs/APP-STATE.md) 연결 안내로 교체하고 `NOTICE-CARD-COMPOSITION.md`의 검사 참조를 `AppStateTests`로 갱신했다. 코드·테스트·과거 evidence 및 ArchitectureTests VERIFICATION 이력은 변경하지 않았다. 이번에는 지시대로 테스트·빌드를 새로 실행하지 않았으며 아래 검증은 이전 구현 Dispatch의 기록이다. 문서 커밋 SHA는 완료 보고와 브랜치 log를 따른다.

문서 후속은 완료했으며 root가 앱 통합·빌드·PR과 이 문서 커밋의 통합을 담당한다. 다른 checkout에는 접근하지 않았다.

## 이전 구현 완료 — NoticeSession 제거 / AppState·상세 지연 생성

2026-09-16 15:34 KST 확인. 자기 checkout `dearby-ios-architecture-tests`, `751da86`에서 clean 확인 후 `feat/ios-notice-state-composition` 생성. 이번 Orca terminal `term_381a9f0e-d446-4fc4-903a-fb16686b7d52`, task `task_8bb50e0f40a3`, dispatch `ctx_812a0cdd5cbe`; 재개 시 상태를 다시 확인한다.

### 승인 범위와 구현

- apps/ios와 자기 역할/워크스트림 문서만 수정했다. root checkout·사용자 Xcode27 project/scheme·Shared 디자인·공통 정책·Android/API는 수정하지 않았다. worker push/PR 없음.
- NoticeSession 삭제, AppSession→AppState. 새 NoticeStore/NoticeFeedStore 없음. AppState는 앱 준비/실패/재시도, 카드·즐겨찾기 모델과 snapshot generation을 소유한다. app/providers의 AppSnapshotComposition은 두 독립 repository와 후보 모델을 생성/공유/주입한다.
- SwiftDataSnapshotStore에서 makeSession·화면 VM·즐겨찾기 의존을 제거했다. coordinator 승인(이번 ask)대로 동기 MainActor candidate transaction을 사용한다. 후보 L1/화면은 save 성공 전 공개하지 않으며 실패 시 폐기한다. 일반 cache-aside는 기존대로 즉시 영속 저장 후 L1에 반영한다. snapshot transaction은 metadata/두 L2의 최종 save 후 공개 repository/카드들을 교체한다. pending 변경과 재진입을 거부한다.
- 상세는 초기 카드 조립에서 만들지 않는다. coordinator 승인(이번 ask)한 value NoticeDetailRouteState가 app/routes에서 화면별 VM/loaded(id,generation)만 소유한다. task에서 한 번 조회하고 body는 순수 Page 조립만 한다. 같은 키 재호출/화면 재진입/공고 ID·snapshot 변경/실패 격리를 검증했다. Widget의 원본 Model·표시 State 책임과 단일 즐겨찾기 Observation은 유지했다.
- 기능 커밋: `4a9734c` 저장소 snapshot transaction 지원·전용 검사·문서; 다음 `refactor(ios): replace notice session with app state and lazy detail` 커밋은 앱 상태/라우팅 적용·회귀·문서를 포함한다. 첫 커밋은 기존 makeSession caller를 일시 보존해 독립 검증 가능하며 두 번째에서 제거한다. 최종 SHA는 브랜치 log/worker_done 참조.

### 이전 구현 Dispatch에서 실행한 검증

- 첫 저장소 커밋의 staged 소스·기존 caller·추가 SnapshotTransactionChecks를 자기 build 폴더에 추출해 swiftc 컴파일, 두 sample fixture 실행 exit0. `/tmp/dearby-transaction-component.log`. intermediate 전체 앱 빌드까지 했다는 뜻은 아니다.
- 최종 `bash apps/ios/tests/run_standalone.sh` exit0. `/tmp/dearby-app-state-standalone.log`: 기존 favorites/source/cache/SwiftData disk/calendar/map, startup/storage/source retry·성공 후 idempotence, discovery/favorites/open-detail save/remove Observation, 새 lazy detail 및 수명, snapshot 조립/read/save 실패 시 manifest/L2/L1/화면 보존·성공 교체·삭제 통과. 의도된 invalid store 경로 fixture의 CoreData 오류 로그는 예상 결과다.
- `run_architecture.sh` exit0: lexical guard133 production Swift files, Swift Testing12 tests/4 suites(공통 부모 포함), 전체36 layer pairs 유지. `/tmp/dearby-app-state-architecture.log`.
- `test_layer_distance_gate.sh` exit0: 실제 route identifier/import 거리 위반, provider private API/UI 위반 거절, provider 허용·원상복구 통과. `/tmp/dearby-app-state-probes.log`.
- `run_swiftlint.sh` strict exit0: 156파일/위반0. `/tmp/dearby-app-state-lint.log`. `run_busy_calendar.sh`, `run_detail_presentations.sh` 각각 exit0: `/tmp/dearby-app-state-busy.log`, `/tmp/dearby-app-state-detail.log`. `git diff --check` 통과.
- XcodeBuildMCP build_sim 성공(12초): 자기 Dearby.xcodeproj, scheme Dearby, Debug, iOS26.5 simulator B04DEBB6-53B1-4CB1-858C-8C290846D4AB, derivedData `apps/ios/build/notice-state-derived`. 로그 `/Users/jominjun/Library/Developer/XcodeBuildMCP/workspaces/dearby-ios-architecture-tests-2e6f4371f452/logs/build_sim_2026-09-16T06-24-48-191Z_pid61537_03181c8f.log`. 최종 production 코드와 동일한 코드 빌드이며 이후 변경은 테스트·문서다.

### 한계와 다음 행동

실제 UI 탭/렌더러 자동화는 이번 실행 미검증. 수명 테스트는 실제 route value-state loader와 반복 표시값 읽기를 실행하며 SwiftUI body 자체를 구동했다고 주장하지 않는다. root 지시에 따라 이전 Simulator 입력 도구 복구로 범위를 넓히지 않았다. 기존 UI 증거는 [이전 구현 보고서](../../apps/ios/docs/evidence/two-layer-composition/README.md)의 역사 기록이다.

Root가 두 커밋을 순서대로 통합하고 사용자 Xcode 설정 보존을 확인한 뒤 공통 문서·CI·push/PR을 진행한다. 기술 정본은 [AppState](../../apps/ios/docs/APP-STATE.md), [snapshot transaction](../../apps/ios/docs/SNAPSHOT-TRANSACTIONS.md), [ARCHITECTURE](../../apps/ios/ARCHITECTURE.md)다. 완료 보고 후 새 지시 전까지 작업하지 않는다.
