# Dearby 컨텍스트 목차

## 현재 시뮬레이터 — 컨퍼런스 목 데이터

PR55는 main b3094f9로 병합됐고 모든 원격 CI가 통과했다. 이후 사용자 요청으로 공식 출처를 조사한 컨퍼런스5개를 개발 전용 목 데이터로 추가했다. [데이터·출처·실행 안내](../../tests/fixtures/conferences/README.md). 현재 iPhone17은 localhost58764 목 서버를 바라보며 앱/서버를 켜 두었다. 후속 요청으로 가상 캘린더 체험1건을 추가(총6건), 9월30일14–16시 활동과 겹치는 로컬 데모 일정2건을 별도 Dearby 데모 캘린더에 생성했다. 실제 API57937 및 운영 데이터는 별개다. 작업 브랜치는 feat/conference-mock-data이며 플랫폼 production 코드는 수정하지 않았다.

## 최신 재개 지점 — 2026-09-29

PR40 main 병합 완료(849b1fe). 사용자가 기존 파일과 데이터를 보존하며 기본 앱을 탐색/상세/신청/실제 기기 캘린더 겹침 확인으로 좁히도록 승인했다. [제품 명세의 최신 승인](../product/native-spec-2026-09.md)이 기존 다섯 탭 노출 요구보다 우선한다. #54 로컬 구현·검증 완료. 하위4개 워크트리는 백업 후 승인 제거했고 root만 유지한다. 구현·검증과 실행 세션은 [조율 문서](coordinator-current-task-and-decisions.md)를 따른다. 아래 기록은 이전 구현 시점이다.

갱신: 2026-09-27 KST. 재개할 때 **조율 문서 → 담당 역할 문서 → 필요한 검증 기록** 순서로 읽는다.

## 최신 재개 지점 — 2026-09-27

[네이티브 재착수](native-restart-2026-09-27.md)와 [확정 제품 명세](../product/native-spec-2026-09.md)가 최신 정본이다. Cycle 4 Seed 확정, iOS·Android 개발·웹 제거 승인. 차단 사항은 이슈로 남기고 독립 작업을 계속한다. 첫 명함 구현은 통합·원격 CI 통과했고 활동 카탈로그와 발견·저장·상세·신청 기록까지 통합·로컬 검증했다. 최신 원격 CI 상태는 조율 문서와 PR40에서 확인한다. 실행 세션과 다음 행동은 [조율 문서](coordinator-current-task-and-decisions.md)를 따른다. 아래 9월 24일 웹 상태는 이전 이력이다.

## 이전 재개 지점

현재 완료: 공식 컨퍼런스·선발형 연합동아리 28프로그램·발견 홈·활성 공고 노출 통합·메인 검증. [조사 정본](../research/korea-it-conferences-2026-09.md), 유지 중인 Orca 웹 세션·미리보기는 조율 문서를 확인한다.

2026-09-24: 사용자 요청으로 네이티브·API 실행 코드를 보존 후 제거하고 최근 재기획 기반 웹을 재구현한다. 인터페이스는 YouTube 클론, 범위는 프로그램 탐색·필터·상세·조직/프로그램 스크랩이다. [조율 문서](coordinator-current-task-and-decisions.md)가 최신 작업 정본이다. 아래 플랫폼 문서는 과거 구현 이력이며 신규 웹 규칙으로 해석하지 않는다.

| 문서 | 담당 정보 |
| --- | --- |
| [조율·현재 작업](coordinator-current-task-and-decisions.md) | 완료 상태, 협업 원칙, 다음 행동 |
| [웹 구현 인계](web-implementation-and-handoff.md) | 새 Next.js 웹의 구현·검증·프로토타입 한계 |
| [iOS 인계](ios-implementation-and-handoff.md) | SwiftUI 상태·컴포넌트·SwiftData 경계 |
| [Android 인계](android-implementation-and-handoff.md) | Compose 상태·컴포넌트·Room 경계 |
| [API 인계](api-implementation-and-handoff.md) | 서버 현황, 미구현 범위, 실행 위치 |
| [제품·기획](product-planning-and-github-issues.md) | 제품 목적, 구현 범위, Symposium 정본 |
| [공통 데이터](shared-data-and-source-decisions.md) | 조직·공고 관계, 승인 사례, 데이터 정본 |
| [검증·기기](verification-and-local-devices.md) | 실행한 검증, 한계, 사용자 기기 보존 |
| [Git·Orca 운영](orca-sessions-and-worktrees.md) | 현재 checkout, 역할 배정, 세션 재구성 |
| [#2 네이티브 UI](issue-02-native-ui.md) | 구현 범위·디자인 참고·완료 기준 |
| [백업·통합 기록](coordinator-architecture-merged-and-worktrees-cleaned.md) | 병합 기준, 전체 worktree 백업, 복원 방법 |

## 정본과 이력

현재 제품 정본은 [네이티브 명세](../product/native-spec-2026-09.md), 데이터 계약은 [명함](../../shared/contracts/native-v1.md)·[활동 카탈로그](../../shared/contracts/catalog-v1.md)다. [9월 24일 웹 재기획](../product/replanning-2026-09.md)과 당시 웹 구현 인계는 역사 자료다. 플랫폼별 현재 역할 문서와 실제 검사 증거를 우선한다.

정리 전 문서 17개와 workstreams 원문은 [날짜별 보관함](archive/2026-09-14-before-consolidation/README.md)에 그대로 보존했다. 과거 인터뷰·Seed 변화·중간 커밋·세션 ID가 필요할 때만 읽는다.

## 갱신 규칙

- 진행 상태는 조율 문서, 플랫폼 구현은 역할 문서, 검증 결과는 검증 문서에 기록한다. 같은 작업 로그를 모든 파일에 복제하지 않는다.
- 완료·진행·미착수를 명시하고 기존 상태를 교체한다. 새로운 “최신” 단락을 끝없이 덧붙이지 않는다.
- 상세 이력은 날짜별 archive에 보존하고 현재 문서에는 결정·이유·다음 행동을 남긴다.
- 컨텍스트 압축 전과 주요 결정·작업 완료 때 갱신한다. 자동 압축 시점의 사전 감지는 보장할 수 없다.
- 이전 테스트 결과를 이번 실행으로 표시하지 않는다. Git·GitHub·Orca·기기는 작업을 재개할 때 다시 확인한다.

- [기기 캘린더: 동의·내 일정 ON/OFF·바쁜 시간 겹침](issue-10-device-calendar.md) — #10 완료 규격과 후속 이슈.

- [FSD 구조 개편 설계안](../architecture/fsd-domain-rules-draft.md) — 2026-09-16 iOS Harmonize 리팩터링의 목표 규칙. 구현·검증 완료 여부는 조율/플랫폼 문서에서 확인한다.

- [컨펌 화면별 시각 구현 기준](../design/native-visual-contract.md): 수정 후 원본 시안, 최신 문구/레이아웃 우선순위, 실제 캡처 비교 조건.
