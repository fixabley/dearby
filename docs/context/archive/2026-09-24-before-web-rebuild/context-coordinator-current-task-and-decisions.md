# 현재 작업과 결정

갱신: 2026-09-23 KST.

## 현재 요청과 범위

사용자가 작업 중인 Dearby 세션·worktree의 변경을 커밋·push·병합하고 정리하도록 승인했다. iOS·Android 구현은 PR27~34로 이미 병합되어 있다. 이번 추가 반영은 기획 문서·발표 산출물과 인계 문서이며 신규 앱 구현은 승인되지 않았다.

- iOS와 Android worktree는 미커밋 변경이 없고 각 플랫폼 전체 경로의 Git tree가 origin/main과 동일함을 이번에 확인했다. 원본 작업 브랜치도 원격에 push했다.
- 제품 재기획 담당은 주제별 4커밋과 서식 보정 1커밋을 push하고 PR35를 작성했다. 메인이 공통 인계·정리 결과를 같은 PR의 별도 커밋으로 통합한다. 승인 내용과 제안·미정 항목은 구분해 유지한다.
- 발표 후속 작업은 별도 저장소 dearby-ir에 인계되어 있으며 이번 Dearby worktree 정리 대상이 아니다.
- root의 사용자 Xcode project/scheme 및 ArchitectureTests/.swiftpm 변경은 보존하고 커밋에서 제외한다.

담당 세션은 모두 종료했고 플랫폼 2개·기획 1개 worktree를 제거했다. 원격 원본 브랜치와 저장소 밖 로컬 백업을 보존했다. [PR35](https://github.com/fixabley/dearby/pull/35)의 최종 CI·병합 상태는 GitHub를 확인한다.

## 제품 기획 정본

기획 정본은 [제품 역할 문서](product-planning-and-github-issues.md), docs/product/replanning-2026-09.md 및 .symposium/scratch/socrates.md다. 기획의 상세 승인·미정 목록은 제품 역할 문서에서 관리한다. 기존 공고 카드에서 프로그램 카드 중심 탐색으로 바꾸는 기획이 앱에 반영되었다는 뜻은 아니다.

후속 구현 지시가 오면 프로그램·회차/직군 공고 관계, 선택 직무별 모집 판정·정렬, 조직/프로그램 스크랩과 기존 저장값, 필터 조합·개수, 자격 정보 처리에 필요한 공통 계약을 먼저 조율한다. 이후 API·iOS·Android 담당을 각각 메인 하위 Orca 세션·worktree로 구성한다. 최종 기술 스택 등 미정 항목을 정리 작업을 이유로 확정하지 않는다.

## 검증과 보존

이번에는 원격 fetch, PR 병합 상태, worktree별 변경 및 플랫폼 소스 일치 여부를 확인했다. 과거 앱 검증은 [iOS 인계](ios-implementation-and-handoff.md), [Android 인계](android-implementation-and-handoff.md)를 따른다. 이번 문서 정리에서 앱의 로컬 빌드·기기 회귀를 새로 실행한 것으로 기록하지 않는다.

정리 전 세션 연결·세부 이력은 [보관본](archive/2026-09-23-before-worktree-cleanup/coordinator-current-task-and-decisions.md)에 보존한다. 백업·실시간 정리 결과는 [Git·Orca 운영](orca-sessions-and-worktrees.md)에서 관리한다. 보호 규칙 우회·강제 push는 하지 않는다.
