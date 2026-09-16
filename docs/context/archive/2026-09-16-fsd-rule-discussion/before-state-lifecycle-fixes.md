# 현재 작업과 결정

2026-09-16 후속: **다른 iOS 상태·조립 코드 소스 검토 완료. 구현 변경 없음.** [검토 결과](../architecture/reviews/2026-09-16-ios-state-ownership.md)에 캘린더 background 재개 경로, 즐겨찾기 선조립, 발견 페이지 관찰 범위, 권한·갱신 중복 및 유지할 객체를 기록했다. 새 테스트/빌드/실기기 재현은 하지 않았다. 권고는 미적용이며 다음 수정 요청 시 범위에 맞춰 담당에게 배정한다. iOS review task_84e3b13bba8d / ctx_f9dd89faa3bc은 succeeded·retained·ack 완료. Worker 자기 역할/워크스트림 두 문서 미커밋, root 검토/인계 문서도 미커밋으로 보존한다. 사용자 Xcode 설정 변경은 그대로다.

## 이전 구현 완료

2026-09-16: **NoticeSession 제거와 앱 상태·상세 수명 분리 구현·로컬 검증 완료. PR29 게시, 코드 CI 전체 통과.** PR27/28/29는 미병합이며 이번 요청에 병합은 포함하지 않았다. [이전 인계](archive/2026-09-16-fsd-rule-discussion/before-notice-session-removal.md)는 과거 상태다.

## 승인된 변경과 결과

NoticeSession 삭제, AppSession→AppState. 새 NoticeStore/NoticeFeedStore는 없다. AppState는 초기 로딩·실패·재시도 및 현재 카드 상태를 소유하고 app/providers의 AppSnapshotComposition이 의존성을 조립한다. SwiftDataSnapshotStore의 makeSession과 화면 VM·즐겨찾기 의존을 제거했다.

상세 ViewModel은 화면 진입 task에서 생성하며 화면 로컬 value NoticeDetailRouteState가 (공고 ID, snapshot generation)과 VM을 소유한다. body는 조회하지 않는다. 같은 키 재호출은 재생성하지 않고 ID·generation 변경 또는 새 화면 수명에서 다시 생성한다. 상세 전용 실패는 초기 피드에 영향을 주지 않는다.

동기 MainActor candidate transaction에서 새 데이터와 화면을 준비하고 최종 영속 save 이후에 공개 repository와 표시 모델을 교체한다. 후보 L1은 commit 전 외부에 노출하지 않는다. 조립/read/save 실패 시 기존 manifest/L2/L1/화면을 보존하며 pending 변경·재진입은 거부한다. 기존 즐겨찾기 단일 상태와 Widget ViewModel/State를 유지한다.

FSD 하위 두 레이어와 app/providers 조립 예외, lowerCamelCase, widgets/<slice>/ui|model, 공고·조직 독립, UI와 행동 유지. Android/API 이번 범위 밖이다.

## PR·검증

[PR29](https://github.com/fixabley/dearby/pull/29), head refactor/ios-notice-state, base PR28(test/ios-two-layer-boundaries). root 기능 커밋 582e0ba(스냅샷 트랜잭션), f1da9f8(AppState·상세 수명·공통 규칙). iOS 원본 커밋 4a9734c/b6cbfe8. 후속 문서만 정리했다. 코드 head f1da9f8의 [CI35064476227](https://github.com/fixabley/dearby/actions/runs/35064476227)은 iOS architecture·Simulator build 두 job 모두 SUCCESS 확인했다. 이후 변경은 인계 Markdown뿐이며 앱/검사 코드는 동일하다. 문서 후속 HEAD의 자동 CI와 이 코드 검증을 구분한다.

- iOS 담당: architecture12 tests/4 suites·36 layer pairs, 실제 production 위반 주입·복원, SwiftLint156파일/0위반, standalone/cache/calendar/map/startup/Observation/route/snapshot 및 busy/detail 회귀 모두 exit0.
- iOS Simulator build XcodeBuildMCP 성공. Root 통합 checkout에서도 generic Simulator Debug build exit0 및 BUILD SUCCEEDED 확인: /tmp/dearby-notice-state-integration-build.log.
- Root와 worker의 apps/ios 커밋 소스 트리 동일성 확인. root 기존 미커밋 Xcode27 project.pbxproj·Dearby.xcscheme은 시작 시 diff와 동일하며 보존·PR 제외. 사용자 변경을 정리하거나 커밋하지 않는다.
- 실제 UI 탭·스와이프/SwiftUI 렌더러 자동화는 미검증. 실행형 수명 테스트가 이를 대신했다고 주장하지 않는다. 자세한 결과는 iOS 역할 인계 및 apps/ios/docs/APP-STATE.md 참조.

## 소유권·다음 행동

Root는 refactor/ios-notice-state에서 공통 문서·통합 담당. iOS checkout dearby-ios-architecture-tests의 feat/ios-notice-state-composition은 구현 완료. Run run_58f7d03f4deb, 구현 task_8bb50e0f40a3 / ctx_812a0cdd5cbe는 succeeded·retained·ack 완료.

문서 진입점 후속 task_2118e21be810 완료: 기존 terminal 재사용 ctx_6c0b48cf9f13은 readiness timeout, 입력 미수락·잔여 리소스 없음. 동일 task를 새 terminal로 retry한 ctx_d22a8757002f / term_e6d174b0-3d53-4fae-ac33-1f4b0d2ac42d은 accepted/turn_started 확인. 문서 커밋7f3e3c3을 통합했고 succeeded·retained·ack 완료, reclaimable0 확인. 코드·검증은 재실행하지 않았다. 다음 구현은 새 Task/Dispatch로 배정하며 기존 완료 ID는 재사용하지 않는다.
