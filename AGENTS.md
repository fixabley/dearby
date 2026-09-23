# Dearby 웹 협업 기준

2026-09-24 사용자가 기존 네이티브·API 코드를 제거하고 최근 재기획 중심 웹으로 재구현하도록 승인했다. 이 파일은 이전 네이티브 FSD·SwiftLint·Harmonize·3계층 캐시 규칙을 대체한다. 이전 규칙은 docs/context/archive/2026-09-24-before-web-rebuild/AGENTS.md에 역사 자료로 보존한다.

## 현재 제품과 구현

- 범위: 프로그램 탐색·검색/필터·프로그램 상세의 회차/직군별 공고·조직/프로그램 스크랩.
- 첫 콘텐츠는 IT 컨퍼런스에 집중한다. 선발형 활동과 참가등록형 행사를 구분해 참가 대상·등록 마감·개최일·장소를 표시하고, 결제나 신청 실행을 구현한 것으로 표현하지 않는다.
- 최신 대상(2026-09-24): 대학생에 한정하지 않고 고등학생·대학생·취준생 등 대외활동 경험을 찾는 사람들이다. 공고별 지원 대상·조건을 구분하며 모두가 모든 활동에 지원할 수 있다고 표현하지 않는다. YouTube 참조의 목적은 활동을 빠르게 판단할 수 있는 가독성 있는 정보 구조다.
- 인터페이스: 사용자가 YouTube 클론을 요청했다. 상단 검색창, 좌측 메뉴, 가로 칩, 썸네일 카드 그리드와 상세 레이아웃을 따르되 Dearby 브랜드와 실제 프로그램 행동을 사용한다. 가짜 영상 재생·조회수·구독 기능을 만들지 않는다.
- Next.js App Router + TypeScript, 한국어 반응형 웹. 첫 버전은 공식 출처를 수동 확인한 컨퍼런스 스냅샷과 브라우저 로컬 저장을 사용한다. 확인일·출처·미확인 필드를 표시한다. API·인증·크롤링·추천 학습의 완성을 주장하지 않는다.
- 사용자는 shadcn/ui 사용도 허용했다. 필수 전환 요청은 아니며 필요한 공통 컨트롤에 선택적으로 검토한다. 현재 구현은 HTML 기본 컨트롤·CSS이며 shadcn/ui 설치 완료로 표현하지 않는다.
- 기획 정본: docs/product/replanning-2026-09.md 및 연결 문서. 최신 컨퍼런스 우선 범위는 docs/product/conference-first-web-2026-09.md를 함께 따른다. 구현에 필요한 미정 세부는 가역적인 프로토타입 선택으로 명시하며 승인된 기획으로 둔갑시키지 않는다.
- 프로그램/모집 공고/조직을 분리한다. 공고 간 조건을 합쳐 거짓 일치를 만들지 않고 프로그램 수는 중복 제거한다. 스크랩과 추천 신호는 별개다.
- Next 라우팅은 src/app, 제품별 데이터·로직·UI는 기능별 디렉터리로 분리한다. 큰 단일 화면 파일, 불필요한 Repository/DI/계층 거리 규칙을 만들지 않는다. 실제 재사용 UI와 저장 상태 소유권은 분리한다.

## 협업·검증

- 구현은 메인 하위 Orca worktree·담당 세션에 배정하고 메인은 공통 결정·통합을 맡는다. 생성 전 기존 worktree·세션을 확인한다. 내장 서브에이전트로 Orca 감독을 중복 실행하지 않는다.
- 사용자는 하위 세션에서도 현재 작업을 볼 수 있도록 완료 후에도 세션 유지를 요청했다. 작업·검증·남은 일을 해당 세션 응답과 카드·역할 문서에 남기고, supervised worker 완료 시 사용자 요청에 따른 retain을 사용한다. 임의 종료하지 않는다.
- 담당자는 자기 checkout만 수정·빌드한다. root 설정 변경은 배정 시 범위를 조율한다. 다른 checkout 변경·강제 push·기존 변경 삭제는 임의로 하지 않는다.
- 기능·컴포넌트 단위로 작은 커밋을 만들고 해당 테스트·문서를 함께 묶는다. 게시한 이력은 재작성하지 않는다.
- 변경 후 ponytail-review로 불필요한 복잡성을 검토하되 정확성·접근성·상태/저장 회귀는 별도로 확인한다. 실제 실행한 검사만 통과로 기록한다.
- 의미 있는 제품 규칙과 저장 동작을 테스트한다. lint·typecheck·production build 및 데스크톱/모바일 브라우저 흐름을 검증한다. 실행하지 못한 항목은 명시한다.
- docs/context/README.md를 목차로 현재 역할 문서를 갱신한다. 주요 결정·완료·압축 전 인계를 남기고 과거 기록은 archive에 보존한다. 세션 ID와 검증 시점을 구분한다.
- 전체 저장소 검색에서 하위 worktree·node_modules·.next·역사 archive를 불필요하게 중복 탐색하지 않는다.

<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->
