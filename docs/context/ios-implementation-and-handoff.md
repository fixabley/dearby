# iOS 구현 인계

## 완료 — 상태 수명·즐겨찾기 목록·발견 관찰 분리

2026-09-16 KST: 자기 `dearby-ios-architecture-tests`의 `7f3e3c3`에서 `feat/ios-state-lifecycles` 생성. 기존 미커밋 검토 기록은 이번 승인 구현으로 갱신한다. terminal `term_e6d174b0-3d53-4fae-ac33-1f4b0d2ac42d`, task `task_6bf555ab4911`, dispatch `ctx_2fb2d4ca0526`; 재개 시 실시간 상태 재확인. 자기 apps/ios·역할 문서만 수정하며 root Xcode 설정/다른 checkout/공통 규칙은 건드리지 않는다.

### 캘린더 수명 — 구현 완료

기존 소스 검토의 background 재개 문제를 먼저 회귀로 재현했다: `/tmp/dearby-lifecycles-calendar-red.log`에서 background 알림 후 개인 결과 비우기 assertion 실패(exit133). CalendarPreferences의 활성 guard와 한 번의 Feature api OS lifecycle adapter로 scene/EventKit 알림을 조율했다. 상세 modifier는 attach/detach만 담당하고 중복 resume/refresh를 없앴다. production 호출 없는 상세 동의 API와 consent enum/resume도 제거했다. SettingsPresentation의 첫 scene 보고로 초기 background를 반영하고 inactive는 권한 창을 취소하지 않는다.

이번 busy 회귀 통과: background 알림, 늦은 authorization/query/grant, inactive 권한 dialog, 초기 background, 알림/재개당 조회1회, 취소된 쿼리 수, detach. 로그 `/tmp/dearby-lifecycles-calendar.log`. standalone도 exit0(`/tmp/dearby-lifecycles-calendar-standalone.log`). architecture12 tests·production gate·detail exit0, strict SwiftLint157파일 위반0. 로그 `/tmp/dearby-lifecycles-calendar-{architecture,gate,detail,lint}.log`. Simulator build는 gate 원상복구 뒤 자기 project/scheme·iOS26.5·derivedData `apps/ios/build/state-lifecycles-derived`에서 재확인 성공. 실제 UI 입력은 미검증. 기술 문서는 [캘린더 수명](../../apps/ios/docs/CALENDAR-LIFECYCLE.md).

### 즐겨찾기 목록 — 구현·검증 완료

FavoriteOrganizationListViewModel은 스냅샷당 조직→feed ID 인덱스를 한 번 만들고 저장된 조직만 조립한다. Feature facade의 weak token 동기 post-mutation 구독으로 추가/삭제를 즉시 반영하고 중복 저장은 재조립하지 않는다. body/getter I/O와 ID 복제본은 없다. 기존 card 인스턴스는 유지하고 새 조직만 조회한다. 새 저장 경로 실패는 기존 카드를 보존하며 Page에 실패/재시도를 명시한다. 스냅샷 후보 실패는 기존 목록/구독을 보존하고 성공 직후에만 이전 구독을 끊는다. 앱 전체를 감싸는 Session/Store를 추가하지 않았다. [목록 조립 문서](../../apps/ios/docs/SAVED-ORGANIZATION-LIST.md).

캘린더 커밋 `9e95709`는 root가 `361b668`로 통합하고 PR30으로 분리했다고 알려왔다(런타임 메시지, 원격 독립 조회는 하지 않음). 즐겨찾기 목록은 `18699c6`, 발견 부모 관찰은 별도 후속 기능 커밋이다.

### 발견 부모 관찰 — 구현·검증 완료

NoticeCardViewModel.isDisplayable은 초기 콘텐츠 존재만 읽고 favorites에 접근하지 않는다. DiscoveryView는 init에서 한 번 필터해 count/paging/AX 컨트롤이 같은 배열을 사용한다. 카드 Widget만 State.saved를 관찰하며 detail 여는 콜백에서만 필요 State를 읽는다. AppStateTests는 부모 membership 알림0과 카드/즐겨찾기/열린상세 save/remove 알림을 동시에 검증하고 missing card·표시 ID/count를 확인한다. SwiftUI 렌더러를 실행한 측정으로 주장하지 않는다.

### 최종 검증·다음 행동

이번 최종 production에서 `run_standalone.sh`, `run_architecture.sh`(12 tests/4 suites·전체36 layer pairs), `test_layer_distance_gate.sh`, `run_swiftlint.sh`(160파일 위반0), `run_busy_calendar.sh`, `run_detail_presentations.sh` 모두 exit0. 로그 `/tmp/dearby-lifecycles-final-{standalone,architecture,gate,lint,busy,detail}.log`. `git diff --check` 통과. 기존 invalid-store fixture의 CoreData 오류는 의도된 실패 경로이고 뒤의 검사들이 통과했다.

Simulator build 성공(2026-09-16 16:24 KST): 자기 Dearby.xcodeproj/scheme Dearby, Debug, iOS26.5 B04DEBB6-53B1-4CB1-858C-8C290846D4AB, derivedData `apps/ios/build/state-lifecycles-derived`. 로그 `/Users/jominjun/Library/Developer/XcodeBuildMCP/workspaces/dearby-ios-architecture-tests-2e6f4371f452/logs/build_sim_2026-09-16T07-24-53-381Z_pid4555_43c4bca3.log`. 실제 UI 입력·렌더러·OS 권한 창/개인 일정은 이번에 구동하지 않았다.

기능별 커밋은 캘린더 `9e95709`, 즐겨찾기 `18699c6`, 발견 관찰 `b6c3d81` 순서다. 이후 캘린더 소유권 주석/문서 정정만 별도 커밋하며 실행 코드 변경은 없다(최종 SHA는 완료 보고와 log). root가 자기 checkout에서 통합·공통 문서·PR을 마감한다. worker push/PR 없음, 타 checkout 접근/사용자 Xcode 설정 수정 없음. 완료 보고 후 새 지시를 기다린다.

### Session/유사 객체 책임 재검토

실제 Session 선언은 BusyCalendarSession 하나이며 상세 개인 결과/취소/선택 날짜 수명 때문에 유지한다. CalendarPreferences는 앱 권한/boolean 수명, SettingsViewModel은 Page sheet 표시, FavoriteOrganizations는 저장 행동 검증/피드백, FavoriteOrganizationStore는 단일 observable ID 원본, SwiftDataSnapshotStore는 디스크 transaction, entity repositories는 독립 cache-aside를 맡는다. 이름이나 짧은 메서드만으로 합치지 않는다. AppState는 앱 준비/스냅샷 게시, NoticeDetailRouteState는 화면 key별 lazy detail 수명을 유지한다. 전달전용 Session/Store는 추가하지 않는다. NoticeCard/NoticeDetail/FavoriteOrganizationCard ViewModel은 각 원본 조합과 표시 State를 제공하므로 유지하고, 새 FavoriteOrganizationListViewModel은 목록/인덱스/구독 수명·명시적 오류를 실제로 소유한다. UserDefaultsCalendarPreferenceStore는 두 boolean 영속 어댑터이며 MemoryCalendarPreferenceStore는 테스트/preview 경계로 유지한다. FavoriteOrganizationsObservation은 weak 변경 구독의 해제 수명을 표현하는 토큰이며 상태 저장소가 아니다.

정리/삭제 대상은 BusyCalendarSession의 중복 동의/권한 요청 및 resume API와 미사용 consent enum, 상세별 scene/EventKit 관찰, 모든 조직 카드 선조립, 발견 부모의 saved 기반 필터였다. BusyCalendarSession을 이름만 바꾸거나 제거하지 않았다. 최종 주석도 opt-in owner라는 과거 표현을 query owner로 바로잡았다.

아래는 이번 실행이 아닌 이전 구현 Dispatch의 보존 기록이다.

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
