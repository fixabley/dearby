# Dearby 문서 길잡이

현재 모바일은 오프라인 예시 화면입니다. 서버·사용자 웹·어드민·수집 워커는 별도로 보존합니다. 아래에서 작업 목적에 맞는 문서를 선택하세요.

2026-10-06 사용자 요청으로 저장소의 자동 테스트(iOS·Android·웹·어드민·API·수집 워커·SQL)와 push 전 시뮬레이터 hook을 모두 지웠습니다. CI는 lint·타입 검사·빌드·마이그레이션 재생만 합니다. 테스트는 사용자와 합의하며 하나씩 다시 만듭니다. 아래 문서의 테스트 통과 기록은 당시 기록입니다. [검사 운영](../architecture/architecture-tests.md)

## 모바일 개발·검증

| 먼저 읽을 문서 | 찾을 정보 |
| --- | --- |
| [모바일 프로토타입 범위](mobile-ui-prototype.md) | 다섯 탭, 예시 데이터, 실제 기능과의 경계 |
| [시안·공통 컴포넌트 적용표](../design/mobile-prototype-reference-map.md) | 화면별 원본과 구현 위치 |
| [iOS 인계](ios-ui-prototype.md) · [Android 인계](android-ui-prototype.md) | 플랫폼 구조, 실행·검증 근거 |
| [검증·기기 안내](verification-and-local-devices.md) | 검사 재현 위치, 확인 시점과 한계 |
| [신청 활동·명함 공유·인라인 편집](feature-applied-activities-and-cards.md) | 2026-10-06 기능 요구와 플랫폼 공통 기준 |
| [에이전트 운영](agent-roster.md) | UI·유저플로우·수집기·어드민 담당 경로와 금지 작업 |
| [Git·워크트리 상태](orca-sessions-and-worktrees.md) | 통합·폴더 정리 기록 |
| [현재 작업과 결정](coordinator-current-task-and-decisions.md) | 통합 상태, 남은 일, 주의할 로컬 변경 |

## 보존한 서비스와 운영

| 문서 | 찾을 정보 |
| --- | --- |
| [API Prisma 전환](api-prisma-postgres.md) | PostgreSQL 저장·운영 Compose |
| [API Swagger](api-swagger.md) · [API 도메인](api-domain-main.md) | 계약 확인·외부 연결 인계 |
| [API 구현 인계](api-implementation-and-handoff.md) | 구현 범위·한계·검증 기록 |
| [API 명함 공유](api-card-shares.md) | 공유 기록·게스트 공유 저장, 웹 테스트 데이터 |
| [API 홈 화면 세션 잇기](api-guest-handoff.md) | 게스트 1회용 코드 발급·교환 |
| [사용자 웹](web-implementation-and-handoff.md) | 공개 이력·게스트 명함 보관 |
| [관리자 웹](admin-web-deployment.md) | 탐색 관리·배포 인계 |
| [수집 워커](catalog-subscription-worker-handoff.md) | 주기 수집·실행 경로 |
| [도메인 도식도](database-and-domains.md) | 프로그램·활동·명함·계정 관계 |
| [공통 데이터](shared-data-and-source-decisions.md) | 출처·데이터 결정 |
| [앱 간 공유 환경값](env-shared-keys.md) | 앱별 env 위치, 여러 앱에서 같아야 하는 값, 로컬 포트 |

각 운영 문서의 확인 시점을 따릅니다. 보존된 배포 기록은 현재 서버가 정상 작동한다는 새 검증이 아닙니다.

## 제품 요구와 과거 근거

- [전체 서비스 제품 요구](../product/native-spec-2026-09.md), [제품 기획](product-planning-and-github-issues.md), [명함 계약](../../shared/contracts/native-v1.md), [활동 계약](../../shared/contracts/catalog-v1.md): 실제 서비스를 연결할 때 참고합니다.
- [기존 TestFlight 배포](testflight-main-readiness.md), [기기 캘린더 구현](issue-10-device-calendar.md), [네이티브 재착수](native-restart-2026-09-27.md), [이전 UI 작업](issue-02-native-ui.md): 당시 구현 기록입니다.
- [시각 구현 기준](../design/native-visual-contract.md), [구조 개편 설계](../architecture/fsd-domain-rules-draft.md): 날짜와 현재 코드의 적용 범위를 함께 확인합니다.
- [이전 워크트리 정리](worktree-cleanup-2026-10-03.md), [날짜별 문서 보관함](archive/2026-09-14-before-consolidation/README.md), [정리 전 목차](https://github.com/fixabley/dearby/blob/ec9dfec98c13661ab332c2567bfba1bb35f1329a/docs/context/README.md): 과거 작업을 추적할 때 읽습니다.
- [이번 문서 정리·스킬 적용 기록](documentation-maintenance.md): 삭제·통합 근거와 확인한 범위입니다.

## 갱신 규칙

현재 범위는 모바일 범위 문서, 실행 방법은 앱 README, 진행 상태는 조율 문서, 검증은 [작성 위치 안내](verification-and-local-devices.md#기록을-갱신할-때)에 따라 해당 실행 기록에 남깁니다. 같은 작업 로그를 여러 문서에 복제하지 않습니다. 상태가 바뀌면 기존 설명을 교체하고 이전 내용은 날짜가 있는 기록이나 Git 고정 링크로 보존합니다. 과거 테스트·기기·세션 상태를 현재 확인 결과로 표시하지 않습니다.
