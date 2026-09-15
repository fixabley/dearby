# Dearby 컨텍스트 목차

갱신: 2026-09-15 KST. 재개할 때 **조율 문서 → 담당 역할 문서 → 필요한 검증 기록** 순서로 읽는다.

## 재개 지점

#1·#2·#10 구현 PR은 main에 병합 완료했다. 완료된 플랫폼 워크트리·세션을 정리했고 후속 #13·#14·#15는 미착수다. 현재는 Harmonize 아키텍처 테스트를 iOS 담당 세션에서 구현 중이며 root가 CI를 연결한다. 조율 문서의 활성 작업을 먼저 확인한다.

#1·#2·#10 구현 PR은 main에 병합 완료했다. 완료된 플랫폼 워크트리·세션을 정리했고 후속 #13·#14·#15는 미착수다. 이후 main 빌드·실행 요청을 완료했고 새 실행용 checkout과 기기를 보존했다. 카드 일정 2열·지도 버튼·URL 도메인·네이티브 아이콘 및 시작/종료 별도 컴포넌트·장소 필드/단어 줄배치 개선까지 로컬 main 통합 완료했다. 현재 연결·증거는 Git·Orca 운영 및 검증 문서를 따른다.

| 문서 | 담당 정보 |
| --- | --- |
| [조율·현재 작업](coordinator-current-task-and-decisions.md) | 완료 상태, 협업 원칙, 다음 행동 |
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

설계의 상세 정본은 [공통 아키텍처](../architecture/native-apps.md), 각 앱 ARCHITECTURE.md, [제품 규격](../product/activity-data-v1.md)이다. 이 폴더에는 재개에 필요한 결정과 파일 위치를 요약한다.

정리 전 문서 17개와 workstreams 원문은 [날짜별 보관함](archive/2026-09-14-before-consolidation/README.md)에 그대로 보존했다. 과거 인터뷰·Seed 변화·중간 커밋·세션 ID가 필요할 때만 읽는다.

## 갱신 규칙

- 진행 상태는 조율 문서, 플랫폼 구현은 역할 문서, 검증 결과는 검증 문서에 기록한다. 같은 작업 로그를 모든 파일에 복제하지 않는다.
- 완료·진행·미착수를 명시하고 기존 상태를 교체한다. 새로운 “최신” 단락을 끝없이 덧붙이지 않는다.
- 상세 이력은 날짜별 archive에 보존하고 현재 문서에는 결정·이유·다음 행동을 남긴다.
- 컨텍스트 압축 전과 주요 결정·작업 완료 때 갱신한다. 자동 압축 시점의 사전 감지는 보장할 수 없다.
- 이전 테스트 결과를 이번 실행으로 표시하지 않는다. Git·GitHub·Orca·기기는 작업을 재개할 때 다시 확인한다.

- [기기 캘린더: 동의·내 일정 ON/OFF·바쁜 시간 겹침](issue-10-device-calendar.md) — #10 완료 규격과 후속 이슈.
