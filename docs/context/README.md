# Dearby 문서 길잡이

현재 제품 범위는 [AGENTS.md](../../AGENTS.md) "현재 제품 범위", 진행 상태와 결정 로그는 [현재 작업과 결정](coordinator-current-task-and-decisions.md), 플랫폼 계약은 [native-v1.md](../../shared/contracts/native-v1.md)·[admin-v1.md](../../shared/contracts/admin-v1.md)가 기준입니다. 계약은 주제별로 현재 상태 한 절만 두고 대체된 내용은 각 절의 "변경 이력"(날짜 + Git 고정 링크)으로 남깁니다(2026-10-19 정리).

2026-10-06 사용자 요청으로 저장소의 자동 테스트(iOS·Android·웹·어드민·API·SQL)와 push 전 시뮬레이터 hook을 모두 지웠습니다. CI는 lint·타입 검사·빌드·API 테스트만 합니다. 새 테스트 파일은 사용자와 합의하며 하나씩 다시 만들고, 그전에는 기존 테스트 클래스를 확장합니다. 아래 문서의 테스트 통과 기록은 당시 기록입니다. [검사 운영](../architecture/architecture-tests.md)

2026-10-18 삭제(명함 공유·Supabase·TS 수집 워커)와 2026-10-19 재설계(명함 교환)는 [현재 작업과 결정](coordinator-current-task-and-decisions.md) "결정 로그"와 native-v1.md "명함 공유 — 변경 이력"에 있습니다. 삭제 전 상태는 [`6636e9a6`](https://github.com/fixabley/dearby/tree/6636e9a6)입니다.

## 모바일 개발·검증

| 먼저 읽을 문서 | 찾을 정보 |
| --- | --- |
| [모바일 프로토타입 범위](mobile-ui-prototype.md) | 다섯 탭, 예시 데이터, 실제 기능과의 경계(2026-10-03 기준; 실제 연결 범위는 native-v1.md "모바일 실제 연결 경계") |
| [시안·공통 컴포넌트 적용표](../design/mobile-prototype-reference-map.md) | 화면별 원본과 구현 위치 |
| [iOS 인계](ios-ui-prototype.md) · [Android 인계](android-ui-prototype.md) | 플랫폼 구조, 실행·검증 근거 |
| [검증·기기 안내](verification-and-local-devices.md) | 검사 재현 위치, 확인 시점과 한계 |
| [신청 활동·인라인 편집](feature-applied-activities-and-cards.md) | 2026-10-06 기능 요구와 플랫폼 공통 기준(명함 공유 부분은 2026-10-18 삭제, 2026-10-19 "명함 교환"으로 대체) |
| [에이전트 운영](agent-roster.md) | UI·유저플로우·어드민·API 담당 경로와 금지 작업 |
| [Git·워크트리 상태](orca-sessions-and-worktrees.md) | 통합·폴더 정리 기록 |
| [현재 작업과 결정](coordinator-current-task-and-decisions.md) | 현재 상태(운영 배포 V62~V64·Vercel 한도·기기·동의 버전, 2026-10-26 확인), 2026-10-26 결정 묶음(PR #376~#442: 활동 상태·필터 시트·시간표 모달·phase·오늘 내 행사 카드·세션 이동 알림·교환 세션 맥락·연락처 인증·웹 로그인·처리방침 10-28·광고 꺼짐), 남은 일·사용자 결정 대기, 2026-10-18·19 결정 로그 |
| [비공개 테스트 계획 2026-10](test-plan-2026-10.md) | 성공 지표 둘(활동 추가 → 참석, 명함 교환 3회)의 정의·GA4 계산식·목표값(추정)·기간(10/28 우아콘)·테스터 부탁·측정 전제; 이벤트는 [analytics-v1.md](../../shared/contracts/analytics-v1.md) "비공개 테스트 성공 지표" |

## 보존한 서비스와 운영

| 문서 | 찾을 정보 |
| --- | --- |
| [API Swagger](api-swagger.md) · [API 도메인](api-domain-main.md) | 삭제한 TS API 시절의 계약 확인·외부 연결 인계(당시 기록) |
| [API 구현 인계](api-implementation-and-handoff.md) | 구현 범위·한계·검증 기록 |
| [API 홈 화면 세션 잇기](api-guest-handoff.md) | 게스트 1회용 코드 발급·교환 |
| [플랫폼 계약 native-v1.md](../../shared/contracts/native-v1.md) | 발견 프로그램 묶음(열린 활동 판정·정렬 키·`프로그램 \| 이벤트`), 활동 상태·필터 시트·종류 아이콘 라벨·종류 필터, 모집 날짜 표기·시작 D-day·모집 일정 없는 활동은 마감, 태그, 대상자 조건, 명함·명함 표현·프로필 사진·연락처 아이콘·연락처 인증(GitHub·이메일), 명함 교환(QR·30일 회수·교환 세션 맥락), 받은 명함 색인 목록, 내 활동 진행 트랙·선발 과정·활동 일정 분리(phase)·진행 단계 화면·지난 선발 단계 통과 제안, 트랙·세션 시간표(모달)·세션 찜·찜한 세션 이동 알림(기기 로컬)·오늘 내 행사 카드, 패스키·Apple·Google 네이티브 로그인·웹 로그인, 동의 버전, 계정 삭제, 웹 비로그인 저장·홈 화면 세션 잇기, 맞춤 대기열 끝 광고(기본 꺼짐) |
| [관리자 계약 admin-v1.md](../../shared/contracts/admin-v1.md) | 조직·프로그램(수집 설정·주소 토큰·기본 종류·로고)·활동(추가·편집·묶음 보기), 일정 직접 지정·기간 확인·날짜 문구 피드백·조심 목록, 세부 일정 phase·`phaseSource`, 트랙·세션 시간표 편집, 운영 데이터 가져오기(세부 일정 블록·dry run), 운영자 지정 필드, 이전 기수 자동완성, 이력 검토·역할·직무 |
| [사용자 웹](web-implementation-and-handoff.md) | 공개 이력·게스트 명함 보관·웹 로그인 1단계(2026-10-26) |
| [관리자 웹](admin-web-deployment.md) | 탐색 관리·배포 인계 |
| [활동 수집 배치](activity-collection-batch.md) | Kotlin Spring Batch + Claude Haiku 수집 흐름, 추출·저장 규칙(기수 단위·두 기간·종류·운영자 피드백 반영), 실행·회수 배치, 아직 없는 것 |
| [도메인 도식도](database-and-domains.md) | 프로그램·활동·명함·계정 관계 |
| [공통 데이터](shared-data-and-source-decisions.md) | 출처·데이터 결정 |
| [앱 간 공유 환경값](env-shared-keys.md) | 앱별 env 위치, 여러 앱에서 같아야 하는 값, 로컬 포트 |

각 운영 문서의 확인 시점을 따릅니다. 보존된 배포 기록은 현재 서버가 정상 작동한다는 새 검증이 아닙니다.

## 제품 요구와 과거 근거

- [전체 서비스 제품 요구](../product/native-spec-2026-09.md), [제품 기획](product-planning-and-github-issues.md), [활동 계약](../../shared/contracts/catalog-v1.md), [사용자 가치·핵심 기술 정리](../product/value-and-core-tech-2026-10-19.md): 실제 서비스를 연결하거나 소개할 때 참고합니다.
- [기존 TestFlight 배포](testflight-main-readiness.md), [기기 캘린더 구현](issue-10-device-calendar.md), [네이티브 재착수](native-restart-2026-09-27.md), [이전 UI 작업](issue-02-native-ui.md): 당시 구현 기록입니다.
- [시각 구현 기준](../design/native-visual-contract.md), [구조 개편 설계](../architecture/fsd-domain-rules-draft.md): 날짜와 현재 코드의 적용 범위를 함께 확인합니다.
- [이전 워크트리 정리](worktree-cleanup-2026-10-03.md), [날짜별 문서 보관함](archive/2026-09-14-before-consolidation/README.md), [정리 전 목차](https://github.com/fixabley/dearby/blob/ec9dfec98c13661ab332c2567bfba1bb35f1329a/docs/context/README.md): 과거 작업을 추적할 때 읽습니다.
- 2026-10-18 삭제한 Supabase·명함 공유 문서([API Prisma 전환](https://github.com/fixabley/dearby/blob/6636e9a6/docs/context/api-prisma-postgres.md), [TS 수집 워커 인계](https://github.com/fixabley/dearby/blob/6636e9a6/docs/context/catalog-subscription-worker-handoff.md), [API 명함 공유](https://github.com/fixabley/dearby/blob/6636e9a6/docs/context/api-card-shares.md)): Git 고정 링크로만 남깁니다.
- [이번 문서 정리·스킬 적용 기록](documentation-maintenance.md): 삭제·통합 근거와 확인한 범위입니다.

## 갱신 규칙

현재 범위는 모바일 범위 문서, 실행 방법은 앱 README, 진행 상태는 조율 문서, 검증은 [작성 위치 안내](verification-and-local-devices.md#기록을-갱신할-때)에 따라 해당 실행 기록에 남깁니다. 같은 작업 로그를 여러 문서에 복제하지 않습니다. 상태가 바뀌면 기존 설명을 교체하고 이전 내용은 날짜가 있는 기록이나 Git 고정 링크로 보존합니다. 과거 테스트·기기·세션 상태를 현재 확인 결과로 표시하지 않습니다.
