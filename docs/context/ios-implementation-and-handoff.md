# iOS 구현 인계

## 현재 — 승인된 설계 단순화 구현 중

2026-09-20 KST: `75ca705`에서 `feat/ios-design-simplification` 생성, 기존 미커밋 검토 문서 보존. 새 task `task_5680a3ed1580`, dispatch `ctx_77f89952ed32`, terminal `term_13ba3b4a-9172-4a99-bff8-485358963ea0`. 자기 apps/ios와 역할 문서만 수정하며 commit 승인, push/PR/merge는 Root 담당이다. 새 승인 좁은 예외가 오래된 모든 하위 허용 문구보다 우선한다.

공통 선행 변경은 공개 Shared UI의 Pages/Widgets 직접참조, 명시적 pure UI 선언 계약, State 보조타입 접미사 강제 완화다. 컴포넌트 이동에 필요한 검사 기반이므로 별도 선행 커밋으로 묶는다. 현재 실제 VM은 공유 참조/Observation/구독 수명이 있어 class를 유지하되 선언 검사는 class/struct를 허용한다. 주타입/파일명/model 위치는 검사하며 enum/actor를 새로 허용하지 않는다. 선행 표시필드/export 커밋 `920b6bf`는 standalone 전체 exit0으로 Root에 전달했다. 정책 최종 architecture16 tests/5 suites와 확장 production gate(exit0)를 실행했다. strict lint는 VM 소유형식 조정 전 1차 exit0이며 최종 재실행 예정이다. 정책 로그 `/tmp/dearby-simplification-policy-{architecture,gate,lint}.log`; empty/stale 계약, rename 후 pure 위반, Shared 비표시/route OS/provider 내부·UI 위반 거절 및 원상복구를 확인했다. 최종 빌드/UI는 아직 미실행이며 이전 PASS는 재사용하지 않는다.

## 이전 검토 — 구현 승인 전 기록

2026-09-20 KST, 자기 checkout `dearby-ios-architecture-tests`, branch `feat/ios-state-lifecycles`, HEAD `75ca7057daea85ae3225cf94b05be5635aaca7c0`에서 검토했다. 시작 시 Git 변경 없음, Orca runtime ready와 현재 worktree 연결을 확인했다. 이번 terminal `term_13ba3b4a-9172-4a99-bff8-485358963ea0`, task `task_ba39e8fd4fba`, dispatch `ctx_41ab5059821a`이며 재개 시 다시 확인한다. 이전 단락의 terminal/Dispatch와 검증은 과거 기록이다.

사용자 승인 범위는 기존 설계 수단까지 근거로 재검토하는 보고 작업이다. `ponytail-review`를 적용했으며 단일 구현체·파일 크기만으로 삭제를 제안하지 않았다. 앱/테스트 코드·공통 정책·다른 checkout을 수정하지 않고 이 문서와 `docs/workstreams/ios.md`만 갱신했다. 커밋/push/PR 없음. 아래 경로는 별도 표시가 없으면 `apps/ios/` 기준이며 line은 검토 HEAD 기준이다. 후보는 승인·적용된 변경이 아니다.

### 규칙별 유지 이유와 비용

| 검토 대상 | 결론과 현 코드 근거 | 비용·완화 조건 |
| --- | --- | --- |
| 하위 두 레이어 제한 | 완화 권고. `FSDBoundaries.swift:90,104–105`와 `LayerDistanceTests.swift:16–20`는 실제로 거리 2를 강제한다. `app/providers/AppComposition.swift:10–12`는 이 제한을 만족하려고 Page 생성만 전달한다. | 상향 참조 금지와 저장/OS 소유권을 유지하면서 직접 하위 참조 또는 우선 Shared 표시 UI/토큰 접근을 허용할 가치가 있다. 전달된 AGENTS의 모든 하위 참조 허용과 이 checkout의 ARCHITECTURE/실행 규칙이 불일치하므로 Root가 정본을 조율해야 한다. 이번에 테스트를 약화하지 않았다. |
| 형제 슬라이스 금지 | 현 사례에서는 유지. `NoticeCardViewModel.swift:13–19`, `NoticeDetailViewModel.swift:8–18`이 Notice/Organization을 상위에서 조합하고, NoticeRepository는 조직을 조회하지 않는다. | 독립적인 공고/조직 조회·캐시 실패 경계를 유지한다. 단순 표시 조합까지 Feature로 밀어내는 비용은 후보 4처럼 소유 위치를 바로잡아 줄인다. 이름만 다른 작은 슬라이스를 반드시 추가하라는 뜻은 아니다. |
| State/VM 파일 규칙 | 표시 State와 원본 Model 구분·UI 밖 모델 조립은 유지. `ArchitectureRules.swift:47–70`은 위치·struct/class·파일명·동일 파일 보조타입 접미사까지 강제한다. | `:58–60` 보조타입 전부 State 강제는 순수성/수명을 증명하지 못하므로 독립 의무로 유지할 이유가 약하다. 값 enum도 무조건 거절할 이유는 없다. 다만 현재 이 규칙 때문에 불필요한 파일이 생성됐다는 증거는 없으므로 파일 통합/VM 삭제는 제안하지 않는다. `SettingsViewModel:6–11`은 실제 sheet 상태, `SettingsPresentation:16–21`과 `AppTabs:13,20`은 실제 caller다. bool 하나라는 이유로 없애지 않는다. |
| public-api manifest | 명시적 내부 경계는 유지하고 불필요한 export만 축소(후보 3). `FSDBoundaries:57–63,111–112`는 존재·중복·비공개 참조를 검사한다. | 단일 Swift 모듈의 internal은 슬라이스 접근 제한이 아니므로 역할이 있다. 반면 이름 목록 수동 관리 및 타입 추론/멤버 접근 미해석 비용이 있다. 모든 무문자열 참조를 미사용으로 판정하지 않는다. Records/Codec을 공개해 manifest를 무력화하지 않는다. |
| 순수 UI 판별 | 부수효과 금지는 유지, `Content.swift`라는 이름에 순수성을 결합하는 방식은 완화 검토. `FSDBoundaries:19,97–115`는 파일 접미사·토큰·api 위치로 추정한다. | `NoticeCardContent:13–19`는 callback 기반 표시이고 `NoticeDetailSections:9,35`는 Session을 연결한다. 둘의 차이는 파일명이 아닌 계약이다. 명시적 순수 컴포넌트 지정과 같은 positive/negative fixtures를 먼저 마련해야 하며 suffix 삭제만으로 보호를 없애지 않는다. 문법 검사는 타입 검사/실제 부수효과 증명이 아니다. |
| Repository/캐시 | 역할 유지. `NoticeRepository:6–10`의 L1과 `SwiftDataNoticeSource:14–33`의 L2/외부 조회·명시 save·rollback은 서로 다른 책임이다. `AppSnapshotComposition:7–17`이 두 repository를 공유 조립한다. | 별도 파일과 주입 계약 비용이 있지만 missing과 오류, save 실패와 성공을 구분하는 실제 요구다. Notice/Organization 범용 repository로 합치거나 SwiftData 자동저장으로 대체하지 않는다. `SwiftDataSnapshotStore:48–71,76–78` transaction은 후보 생성 실패 시 기존 디스크/화면을 보존한다. |
| Session/앱/화면 수명 | 이름이 아니라 실제 책임 유지. `CalendarPreferences:38–65,76–108`은 앱 권한/설정·attach, `BusyCalendarSession:29–80`은 화면 선택/개인 결과·취소 generation을 소유한다. `NoticeDetailRouteState:12–15`는 화면별 ID/generation 중복 로딩을 막는다. | 하나로 합치면 앱 권한과 상세 개인 결과의 수명이 섞인다. `AppState:48–54`의 commit 이후 구독·화면 게시와 `FavoriteOrganizationListViewModel:30–45`의 구독/실패 보존도 전달만 하는 계층이 아니다. 특정 클래스명을 영구 규칙으로 고정할 필요는 없지만 이 책임을 잃는 삭제 근거는 없다. |

ArchitectureRules/FSDBoundaries/LayerDistanceTests는 `tests/ArchitectureTests/Tests/DearbyArchitectureTests/` 아래다. 표의 짧은 앱 파일명은 아래 후보 및 실제 소스 경로로 찾을 수 있다.

### 구체 후보 — 우선순위 순, 최대 5개

1. `Dearby/widgets/noticeCard/model/NoticeCardState.swift:L7–8,L13; Dearby/widgets/noticeDetail/model/NoticeDetailState.swift:L7,L16–18: delete: 표시에서 읽지 않는 State 투영 필드. 원본 Model과 실제 표시용 application/schedules만 유지.`
   - 카드 `applicationSummary`, `locationSummary`, `applicationPeriod`; 상세 `descriptionProvenance`, `applicationSummary`, `scheduleSummaries`, `applicationPeriod`가 대상이다. 실제 생성자는 `NoticeCardViewModel:21–26`, `NoticeDetailViewModel:15–26`이며 카드 Preview도 `NoticeCardContent:26–28`에서 더 이상 읽지 않는 인자를 전달한다.
   - 실제 표시 caller `NoticeCardContent:13–19`는 summary/schedules/saved를, `NoticeDetailSections:27–42`는 application/schedules/location을 읽는다. 전체 production·tests Swift 참조 검색에서 위 필드의 production 읽기를 찾지 못했다. 카드 applicationSummary는 `tests/NoticeViewModelTests.swift:38`에서만 비교한다. 이 투영 assertion은 실제 신청 행 검증(`:44–48`)으로 맞추되 source 데이터/저장 복원 검증은 유지한다.
   - `organizationLinks`, `sources`, `evidence`, `sourceURL` 및 원본 Model 필드는 이번 삭제 대상에서 제외한다. 원본 보존·출처 계약과 `NoticeViewModelTests:42–43,194–196`, `AppStateLifecycleTests:127`의 상세 조회 실패 fixture가 있어 단순 화면 미참조만으로 함께 지우면 의미가 바뀐다.
   - 적용 검증: standalone(NoticeViewModel/CalendarDraft/SwiftData round-trip), detail, strict lint, architecture, Simulator 빌드와 카드·상세의 신청/활동/원문 표시 확인. 빌드 통과만으로 표시 동일성을 주장하지 않는다.

2. `Dearby/app/providers/AppComposition.swift:L10–12: shrink: 상세 Page를 그대로 생성해 반환하는 detailPage 전달 메서드. 거리 규칙 완화 후 route에서 NoticeDetailPage(viewModel:preferences:)를 직접 조립.`
   - 실제 단일 caller는 `app/routes/NoticeDestinationView.swift:16`. 숫자가 아니라 함수에 조회·변환·수명·오류 처리 없이 동일 인자를 전달한다는 점이 근거다. `AppState.calendarPreferences:9`는 이미 존재한다.
   - `AppComposition`의 기본 의존 생성 init(:4–7), route의 `@State detail`(:7), `.task(id:key)`(:21), `makeDetailViewModel`은 그대로 유지한다. Shared만 예외 처리하는 완화로는 App→Feature preferences 참조를 해결하지 못하므로 이 후보는 더 넓은 하위 참조 정책 승인에 종속된다.
   - 적용 검증: LayerDistanceTests 전체 layer-pair/import expectation과 production distance gate를 새 정책에 맞게 갱신하고 상향/형제/private API 거절은 유지한다. standalone AppStateLifecycle의 최초 진입·재진입·generation 교체·상세 실패 격리 및 Simulator route 빌드를 확인한다.

3. `architecture/public-api.json:L3,L42,L91: delete: 다른 슬라이스가 직접 사용하지 않는 NoticeClassificationView/CalendarEventEditor/SettingsView export. 선언과 같은 슬라이스 내부 caller는 유지.`
   - NoticeClassificationView의 caller는 `entities/notice/ui/NoticeCardBody.swift:22`, `NoticePreviewLabel.swift:11`; CalendarEventEditor는 `features/addToCalendar/api/CalendarExportPresentation.swift:11`; SettingsView는 `pages/settings/ui/SettingsPresentation.swift:18`이다. 각 export를 없애도 같은 슬라이스 접근 정책은 허용된다.
   - manifest 전부를 없애거나 타입 선언을 지우는 제안이 아니다. raw 검색에서 외부 문자열이 안 보이는 다른 타입은 반환 타입 추론 등에 노출될 수 있으므로 이번 구체 후보에 포함하지 않는다.
   - 적용 검증: architecture production graph + 잘못된 manifest/private reference fixture, strict lint/앱 빌드, detail의 캘린더 편집기·설정 진입 smoke. public-api는 런타임 코드가 아니지만 검증을 새로 실행하기 전 통과로 기록하지 않는다.

4. `Dearby/features/saveOrganization/ui/SavedOrganizationList.swift:L8–19; NoticeIdentityView.swift:L7–25: shrink: 저장 행동과 무관한 표시 조합을 Feature에 두는 배치. SavedOrganizationList는 pages/favorites/ui, NoticeIdentityView와 전용 표시 State는 widgets/noticeDetail로 귀속.`
   - 실제 caller는 `pages/favorites/ui/FavoriteListView.swift:13`, `widgets/noticeDetail/ui/NoticeDetailSections.swift:19–21`. 두 View 모두 저장소 조회·mutation·권한 없이 native/Shared UI를 조립한다. NoticeIdentityView의 :3 주석도 이미 NoticeDetail 소유라고 한다.
   - Shared UI/토큰 직접 접근 완화가 선행되어야 한다. NoticeIdentityState/NoticeInstitutionState를 옮길 때 `NoticeDetailViewModel:11–18`의 조직 합성 및 cross-slice export를 함께 정리한다. 목록 empty/error 조건, 기기 저장 안내, 조직/분류/회차 문구는 보존한다. Feature 전체 또는 FavoriteOrganizations 소유권을 삭제하는 제안은 아니다.
   - 적용 검증: architecture의 Shared 접근 positive 및 sibling/upward negative fixtures, standalone 즐겨찾기 empty/실패/재시도·관찰, 앱 빌드, 큰 글자·VoiceOver 순서·empty 메시지/하단 안내 시각 확인. 이동만으로 줄 수나 성능이 개선된다고 주장하지 않는다.

5. `Dearby/features/openNoticeDetails/ui/NoticeDetailsButton.swift:L4–13: native: 자체 상태/정책 없이 callback 버튼 하나만 둔 Feature. caller NoticeCardContent 안에 기존 SecondaryButton+Label+accessibilityIdentifier를 배치하고 Feature entry/manifest를 제거.`
   - 실제 caller는 `widgets/noticeCard/ui/NoticeCardContent.swift:19`; callback은 `NoticeCard:15–17` → `DiscoveryCardPage:16,23` → `DiscoveryView:34`를 통해 Page의 detail 선택으로 이어진다. Feature가 라우팅을 결정하지 않고 `noticeID`도 AX identifier에만 사용한다는 점이 근거다.
   - Shared 표시 직접 접근 허용 후 적용 가능하다. `SecondaryButton`은 이미 SwiftUI Button의 native bordered/large/fixedSize를 제공하므로 유지한다. 앱의 재사용 디자인 버튼을 전체 제거할 이유는 없다. label “공고 정보 · 출처 보기”, info.circle, iconOnly, full width, `details.<id>`를 그대로 옮긴다.
   - 적용 검증: architecture/strict lint/Simulator build, 발견 버튼 탭으로 같은 상세가 한 번 열림, 카드 doubletap 저장 동작 유지, AX label/identifier와 큰 글자 hit area 확인. 모델 테스트만으로 버튼/VoiceOver 동등성을 확정하지 않는다.

### 이번 실행과 다음 행동

이번에는 파일/호출 참조 읽기, `rg` 전체 iOS production·test Swift 조회, public-api 외부 참조 후보를 찾는 읽기 전용 Python, Git/Orca 실시간 조회, 문서 diff 검사를 수행했다. **앱 빌드·SwiftLint·Harmonize·회귀·Simulator UI·권한·접근성 검증은 실행하지 않았다.** 아래 2026-09-16 PASS 기록은 과거 구현의 증거이며 이번 재검토의 새 PASS가 아니다. 성능/삭제 줄 수는 측정하지 않아 추정치를 제시하지 않는다.

Root에 Shared 표시 참조 비용, Content 접미사 한계, State 보조타입 강제의 약한 근거 및 카드 미사용 필드를 CLI로 전달했다. Root는 공통 AGENTS/정책검토 문서를 담당한다. 다음 작업은 Root가 승인 대상을 확정한 뒤 기능별 코드·검증을 함께 배정하는 것이다. 이번 worker는 완료 보고 후 세션을 유지하며 새 지시를 기다린다.

## 이전 구현 기록 — 이번 검토에서 재실행하지 않음

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
