# Dearby 컨텍스트 재개 목차

> **최신 상태 — 2026-09-14:** 구조 개선 PR #3·#4·#5·#6은 main에 병합했고 #1을 닫았다. 하위 worktree/담당 터미널은 정리 완료했다. #2 디자인 구현은 아직 시작하지 않았다. 이전 세션 ID·draft/retained 기록은 이력이다. [통합·정리 및 다음 작업 인계](coordinator-architecture-merged-and-worktrees-cleaned.md)를 먼저 읽는다.

2026-09-14 (Asia/Seoul). 사용자가 컨텍스트 압축 전에 역할별 정보를 최대한 Markdown으로 보존하도록 요청하여 작성했다. 이 폴더는 작성 시점의 인계 스냅샷이다. 실제 코드·Git·GitHub·Orca 상태를 다시 확인하고 사용자의 최신 지시를 우선한다.

## 가장 먼저 읽기

1. [메인 조율·현재 작업·다음 행동](coordinator-current-task-and-decisions.md)
2. [기획·Symposium·이슈](product-planning-and-github-issues.md)
3. 작업 역할에 따라 [iOS](ios-implementation-and-handoff.md), [Android](android-implementation-and-handoff.md), [API](api-implementation-and-handoff.md)
4. 병렬 작업 배정 전 [Orca 세션·worktree 운영](orca-sessions-and-worktrees.md)
5. 공통 변경 전 [데이터 규격·원문·판단 규칙](shared-data-and-source-decisions.md)
6. 검증 전 [빌드·테스트·기기 주의사항](verification-and-local-devices.md)

[Git 상태 스냅샷](git-state-at-capture.md), [Socrates 전체 인터뷰 스냅샷](symposium-interview-snapshot.md), [이슈 #1 본문](issue-01-native-app-architecture.md), [이슈 #2 본문](issue-02-native-ui.md)도 보존했다.

이 문서 저장은 제품 코드 구현·커밋·푸시를 뜻하지 않는다. 플랫폼 역할 문서는 기존 초기 인계 원문을 보존한 뒤 최신 조율 상황을 앞에 덧붙였다. 원문 중 '이번 인계' 제한은 과거 문서 작성 작업의 범위이며 향후 작업 전체를 금지하는 규칙이 아니다.

## 지속 요청

사용자는 앞으로 컨텍스트 압축 전마다 이 기록을 갱신하도록 요청했다. 자동 압축 시점 제어는 보장하지 않으며 주요 결정·작업 완료 때도 갱신한다. 세부 운영 규칙은 루트 AGENTS.md의 컨텍스트 보존 항목을 따른다.

## 최신 Seed

#1의 Seed v2를 GitHub에 반영했다. [Seed 발전 이력](symposium-seed-evolution.md)에 원본과 A~F 전체 채택 및 변경 슬롯을 보존했다. 기존 선택 대기 기록보다 이 상태가 우선한다.

진행 중인 #1의 [PR 분리·검증 기록](pr-split-and-verification.md)을 재개 시 확인한다.

## 현재 완료 지점

#1 구현 PR #3(설계), #4(Android), #5(iOS)를 생성·검토했다. 모두 draft/미병합. 두 Orca 작업은 succeeded 및 retained. iOS 실제 터치 스와이프 미검증 한계가 남는다. 자세한 결과는 [PR 검증 기록](pr-split-and-verification.md)을 따른다.

최신: FSD 추가 요청 구현 진행 중. 이전 완료 기록 이후 새 Run run_3a0308534b10이 활성화되었으므로 coordinator 문서 마지막 부분을 먼저 확인한다.

최신 완료: FSD 구현과 기능별 후속 커밋을 기존 PR #3/#4/#5에 반영했다. Run run_3a0308534b10 양쪽 succeeded/retained. [최종 검토](pr-split-and-verification.md) 마지막 항목을 먼저 읽는다.

최신 체크포인트: iOS summary·Repository 후속 작업 완료, PR #5 head a6d7b98. coordinator/ios/pr-split/orca 역할 문서의 2026-09-14 완료 항목 참조.

최신: 내부 UI 파일 분리 완료 (iOS PR#5 head54b6699, Android 추가추출없음). 역할별 마지막 완료 항목 참조.

최신: 모델 단순화·지도 연동 완료. 공통PR6 a157e42, iOSPR5 9103609, AndroidPR4 b90adac. Run5feb189ab803종료/세션유지. 각역할최종완료참조.

최신 완료: 신청·활동 캘린더 연결. 공통PR6 79f5a82 / iOSPR5 9a8cd02 / AndroidPR4 6e5ad55, 모두 draft 미병합. run_ee6bffef760e 양쪽 succeeded/retained 및 완료 ack. coordinator/pr-split 역할 문서 마지막 항목의 검증·한계 참조.

최신 진행: ActivityDetail 및 조직 말단 ID cache-aside 작업 run_b6299acf926a. 이전 캘린더 완료 이후 새 작업이므로 coordinator 마지막 진행 항목 참조.

최신 완료: ActivityDetail·조직 ID cache-aside. 공통PR6 c71cac3 / iOSPR5 2a2b05f / AndroidPR4 b8838e2, 모두 draft 미병합. run_b6299acf926a 최종 후속까지 succeeded/retained·delivery ack·reclaimable0. coordinator/pr-split 마지막 완료 항목 참조.

최신 진행: iOS ActivityDetail 순수 생성자 분리 run_6cee981daf56. coordinator 마지막 진행 항목 참조.

최신 완료: iOS 순수 ActivityDetail 생성자 분리 PR5 94d5ab4, 상세/캐시/캘린더 검사·빌드 통과. run_6cee981daf56 succeeded/retained·ack 완료. coordinator/ios 최신 항목 참조.

최신 완료: Notice 명칭 통일. iOSPR5 eb6b25f/AndroidPR4 bfee6dc/설계PR3 d493fd1/공통PR6 c07b019. 코드경로 Entities/NoticeCatalog·widgets.noticecard 등으로 변경. run_94fec7f4f195 모두 succeeded/retained·ack완료. 역할문서 마지막항목 참조.

최신 진행: 단일 NoticeModel·독립 OrganizationModel + 컴포넌트 ViewModel/State 규칙 적용 run_5cd9e61b181e. 이전 Notice 명칭통일 완료 이후 새 작업이며 coordinator 마지막 항목 확인.

- [SwiftUI·Ice Cubes 아키텍처 비교](ios-architecture-comparison-swiftui-ice-cubes.md): 2026-09-14 추가 질문의 공식 근거, 현재 구조와 장단점, SDK 적용 한계. 기존 양 플랫폼 리팩터링은 계속 진행 중.

- 최신 완료: 2026-09-14 공고·조직 독립 Model/Repository 및 카드·상세·즐겨찾기 ViewModel→State 적용. PR4 f44e712 / PR5 65de84f, 두 세션 유지·활성 작업 없음. coordinator 문서의 최종 완료 절을 먼저 읽는다.

- 진행 중: iOS SwiftData를 인메모리 캐시 다음 계층에 적용. run_0a9cd0651126 / ctx_255797f75b5d. coordinator 최신 절 참조. 이전 완료 이후 새 사용자 승인 작업이다.

- 최신 완료: iOS 메모리→SwiftData→외부 mock 3계층. PR5 7c4b857 / 공통PR6 049e9bb, 검증·원격푸시·Orca retain/ack 완료. 활성 작업 없음. coordinator 최신 완료 절 참조.

최신: 캘린더 메모 원본 URL 변경 PR4/5/6 반영. coordinator 마지막 기록 참조.

최신 완료: iOS 저장 버튼 컴포넌트 분리 PR5 d2a0210. 공고 제목 유지. coordinator 마지막 기록 참조.

최신: Shared 디자인버튼/NoticeCard 저장조합 PR5 57026bc 완료, 도메인별widget그룹 질문은 설명만.

최신: iOS Widget 도메인별·UI/Model 폴더제거 PR5 e888014 완료. coordinator 최신항목 참조.

최신 완료: Android Shared버튼·Widget도메인/flat구조·Room3계층 PR4 8572ecd, 공유계약PR6 d97543b. JVM39/계측35/FSD60 통과. 담당세션retained.
