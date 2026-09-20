# 현재 작업과 결정

2026-09-16: **승인된 네 항목 수정과 Session류 책임 재검토 완료.** [검토·수정 결과](../../architecture/reviews/2026-09-16-ios-state-ownership.md), [iOS 구현 인계](ios-implementation-and-handoff.md)를 따른다. 이전 NoticeSession 제거는 PR29, 이번 후속은 PR30/31이다. PR27/28/29/30/31은 미병합이며 이번 요청에서 병합하지 않는다.

## 완료한 변경

- CalendarPreferences가 권한/앱 활성 수명을 조율하고 Feature adapter가 scene/EventKit 알림을 한 번 수신한다. background 알림·늦은 응답으로 조회가 재개되지 않으며 권한 창 inactive는 유지한다. BusyCalendarSession의 중복 consent/resume/lifecycle 경로를 제거했다.
- 저장된 조직 중심 FavoriteOrganizationListViewModel과 조직별 공고 인덱스를 구성한다. 저장/삭제 즉시 동기화, 신규 조회 실패 시 기존 카드 보존·재시도, snapshot 실패 시 기존 구독 유지·성공 시 교체를 구현했다.
- DiscoveryView는 saved를 읽지 않는 isDisplayable로 목록을 구성하고 count/paging/접근성에 재사용한다. 개별 카드/즐겨찾기/상세는 단일 즐겨찾기 상태를 즉시 반영한다.
- 유일한 BusyCalendarSession은 상세 조회 취소·선택·개인 결과 폐기 수명 때문에 유지한다. SettingsViewModel, FavoriteOrganizations/Store, 독립 repositories, SwiftDataSnapshotStore 등은 실제 상태·행동·저장 역할이 있어 유지한다. 범용 전달 Session/Store를 추가하지 않았다.

## 통합·PR

| 기능 | worker → root | PR |
| --- | --- | --- |
| 캘린더 수명 | 9e95709 → 361b668 | [30](https://github.com/fixabley/dearby/pull/30), base refactor/ios-notice-state |
| 즐겨찾기 목록 | 18699c6 → ec6129a | [31](https://github.com/fixabley/dearby/pull/31), base refactor/ios-calendar-lifecycle |
| 발견 카드 관찰 | b6c3d81 → 27783ab | PR31 내 별도 기능 커밋 |
| 소유권 주석·문서 | 75ca705 → 9b0b7af | PR31 |

기능별 코드·회귀·문서를 묶었다. 공통 정책·최종 인계는 root에서 별도 문서 커밋한다. Root refactor/ios-state-lifecycles, worker feat/ios-state-lifecycles. 사용자 Xcode27 project/scheme 변경은 시작 diff와 동일함을 확인하고 PR에서 제외했다. Android/API 변경 없음.

## 이번 검증

Worker에서 background 재개 기존 실패를 재현한 뒤 모든 회귀 통과. standalone, busy calendar, detail, architecture12/4 suites, production gate, strict SwiftLint160파일 위반0, Simulator build 성공. 저장/삭제·오류 격리·snapshot/구독 교체·부모 관찰0을 production 코드로 검사했다. root 통합 후 generic Simulator build도 성공(`/tmp/dearby-lifecycles-integration-build.log`). root 앱/검사 소스는 worker 최종과 동일하다. 실제 기기 UI·OS 권한 창 조작 및 렌더링 성능 실측은 하지 않았다.

PR30 CI35068007246은 architecture와 simulator build 모두 success. PR31 CI는 게시 후 실행 중이며 최종 상태를 GitHub에서 확인한다. 이전 PR29 검증과 이번 실행은 구분한다.

## 협업·다음 행동

task_6bf555ab4911 / ctx_2fb2d4ca0526 / term_e6d174b0-3d53-4fae-ac33-1f4b0d2ac42d succeeded·retained·delivery ack. runtime/checkout 정본은 [운영 문서](orca-sessions-and-worktrees.md). 사용자 리뷰 후 수정 제안을 반영한다. 이전 검토/승인 전 상태는 [인계 보관](archive/2026-09-16-fsd-rule-discussion/before-state-lifecycle-fixes.md)에 남겼다.
