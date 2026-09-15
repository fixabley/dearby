# iOS 구현 인계

## 현재 작업 — 승인 FSD 전체 이행 (2026-09-16 00:48 KST 확인)

checkout `dearby-ios-architecture-tests`, branch `fixabley/dearby-ios-architecture-tests`, 기반 main `f8f648c`. terminal `term_63e15d3b-5a6c-4075-aca7-500e2300490c`, dispatch `ctx_338f8e66f52b`. 재개 시 Orca/Git 상태를 재확인한다. 기존 flat Widget 규칙은 최신 승인 UI/Model 세그먼트 설계로 교체했다.

## 구현

기능별 로컬 commits: `cb42382` AST FSD/public API/cache facade 선행, `20ef057` NoticeCard, `d339ead` Favorite entity/connected card, `3803a2a` calendar/map 행동, `c703de3` App Entrypoint/Routes/Providers, `3129ac9` migration alias 제거/실제 위반 증거. Root는 앞 5개를 PR19–23으로 나눴으며 원격 PR/통합을 담당한다. worker는 merge/push하지 않는다.

현재 마지막 변경은 CalendarConnectionControl/State와 BusyTimeStatusView를 CheckCalendarOverlap UI/Model로 옮기고 Feature CalendarOverlapTimeline이 범용 EventDayTimeline의 status slot을 조립하도록 하는 것이다. 자기 Feature UI→State 정상, Shared UI→Feature State 위반 fixture와 busy script 경로도 함께 변경했다.

## 검증과 제한

Xcode26.6/Swift6.3.3에서 App 이동까지 architecture 9 Swift tests/기존 Python fixtures, standalone cache/favorite/snapshot, busy consent/cancel/privacy/overlap, detail 회귀 및 simulator build를 실제 통과했다. 이후 물리경로 최종 검사와 임시 production 상향/교차slice/순수UI effect/private API 위반 4개가 각각 runner exit1, 삭제 후 /tmp runner exit0 및 원본108파일 SHA256 일치를 확인했다.

전용 simulator `B04DEBB6-53B1-4CB1-858C-8C290846D4AB`에서 `--busy-calendar-fixture=empty`로 개인 calendar 접근 없이 feed/save/remove/favorites/detail, 실제 더블탭 저장, AX5 다음 카드 전환을 확인했다. 증거·기능별 결과 정본은 [FSD-MIGRATION](../../apps/ios/docs/FSD-MIGRATION.md). 앱은 c703de3 빌드이며 최종 Calendar UI 이동 전이다.

00:41 KST 외부 Xcode 업데이트로 27.0(27A266a) license 미동의 exit69가 발생했다. 라이선스를 대신 수락하지 않았고 root가 사용자에게 안내했다. 마지막 Calendar UI 변경은 Python109파일/diff만 통과했고 Swift/build 재검증은 대기; root가 Xcode16.4 hosted CI도 병행한다. 현재 전용 simulator font AX5는 license 해결 후 large로 복원할 것.

## 다음 행동

마지막 기능 commit을 root에 즉시 전달해 CI 실행, 라이선스 해결 후 architecture/standalone/busy/detail 및 simulator rebuild와 설정 mock/persistence UI 재검증, font 복원, 결과/한계 갱신 후 현재 dispatch worker_done을 정확히 한 번 보낸다. 개인 일정 읽기쓰기 금지, 새 DI/state/deeplink/API 없음. 앱 OS 실제권한·개인일정 검증은 기존 별도 이슈 범위다.
