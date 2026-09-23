# 현재 작업과 결정

2026-09-24 KST. 사용자가 기존 실행 코드를 제거하고 웹으로 재구현하도록 요청했다. 첫 범위는 최근 재기획 중심 프로그램 탐색·필터·상세·조직/프로그램 스크랩이며 인터페이스는 YouTube 같은 가독성 있는 탐색 구조다.

## 최신 사용자 의도

- 고등학생·대학생·취준생 등 대외활동 경험을 찾는 사람들을 대상으로 한다. 대학생으로 한정하지 않는다.
- 영상 서비스를 재현하기보다 썸네일→제목→주최 조직→참여 판단에 필요한 정보 순서를 활용한다. 가짜 재생·조회수·로그인은 만들지 않는다.
- 현재 영문 장식 포스터 비중을 줄이고 활동 내용이 드러나는 한국어 포스터와 모집 직무·지원 대상·마감·장소를 카드에 보완한다. 공고별 자격을 표시하며 자격 미충족으로 자동 제외하지 않는다.
- 프로그램 카드와 상세의 회차/직무별 공고, 조직/프로그램 스크랩 분리는 유지한다. 실제 데이터 수집·API·인증·추천 학습·프로필/자격 자동 판정은 이번 프로토타입 구현 범위 밖이다.

## 완료한 초기 웹 구현·통합

- root feat/web-rebuild, 코드 통합 HEAD f7826df. 기존 실행코드 제거 be10837, root 조율 문서 366e91d/e0e0669, 웹 담당 기능 커밋 8개를 88f7ee0..f7826df로 cherry-pick했다. 원격 push/PR/merge/배포는 수행하지 않았다.
- root Next.js 16.3.6·TypeScript, 가상 조직 4개/프로그램 12개/공고 25개, 명시적인 2026-09-24 기준 샘플과 브라우저 저장이다.
- 검색·같은 현재 공고 단위 방향/경험 필터·결과 없음 부분 조합/되돌리기·URL 상태 복원·상세·독립 스크랩·저장 오류 복구를 구현했다. 상세 근거/제약은 web-implementation-and-handoff.md.
- 메인 checkout에서 npm ci, lint, typecheck, 단위 테스트 11개, production build, Playwright 12개(1440/390 및 axe)가 통과했다. root test-results/에 실제 캡처가 있다. 최초 담당 검증과 별개로 실행했다.
- npm ci: 취약점 0. ESLint 9 지원 종료 경고가 있다. npm registry의 최신 eslint-plugin-react peer 범위가 아직 9까지여서 ESLint 10 강제 설치를 하지 않았다. npm 12의 install-script 차단 경고도 있으나 실제 lint/test/build는 통과했다.
- 초기 Task task_c5abe8d83ebe / Dispatch ctx_a529f998bad6는 succeeded 보고를 검토하고 release/ack했다. 해당 worker terminal은 종료·출력 보관, checkout/branch는 유지했다.

## 현재 후속 작업 — 아직 미완료

동일 dearby-web checkout에서 새 Task task_2c46a954b462 / Dispatch ctx_a30c192f9930 / terminal term_f94fe84b-50b8-486d-90aa-0595ce675676에 대상 확대·카드 가독성 보완을 배정했다. 자기 src/public/tests/README/web 역할 문서만 수정하고 제품 정본/AGENTS/메인 문서는 메인이 소유한다. 새로운 worker_start는 ready·input accepted·turn started가 확인됐다. 이전 핸들을 재사용하지 않는다.

메인 Orca 브라우저로 개발서버를 확인하던 중 hydration mismatch(서버 SaveButton disabled, 클라이언트 false)와 버튼 DOM 비활성 고착을 추가 발견했다. production Chromium 12개 통과가 이 개발모드/WebKit 문제를 검증한 것은 아니다. 후속 worker에 원인 검증과 근본 수정, dev 새 진입/저장 복원 console 오류 회귀를 함께 배정했다. suppressHydrationWarning으로 숨기지 않는다.

다음: 후속 worker 보고·코드·스크린샷 검토, 기능 커밋 root 통합, 변경 관련 검증과 Orca 개발서버 실제 조작, 최종 실행 링크 제공. worker mailbox는 처리한 Delivery를 ack해야 다음 후속이 전달된다.

## 로컬 실행과 보존

- root 개발서버: http://127.0.0.1:3000, Orca terminal term_60a40728-7163-45a2-a690-7721f7d41230. 2026-09-24 00:56 KST ready/HTTP200 확인. 후속 작업 중 유지하며 현재 초기 웹 코드가 표시된다.
- root Orca page b99a9e23-82b0-4349-a854-b6bfa204ef82가 위 URL을 표시한다. 처음 CUA IAB는 미연결 실패했으나 Orca 내장 브라우저로 우회했다. 실제 YouTube 비로그인 헤더·접힌 메뉴만 확인했고 추천 그리드는 없어서 전체 대조하지 못했다.
- 기존 apps/scripts/shared/root Node 설정·의존성과 잔여 dearby-ios는 /Users/jominjun/Documents/dearby-backups/2026-09-24-before-web-rebuild/에 이동 보존했다. repository.bundle 검증, 사용자 Xcode 변경 binary diff·원본, 700개 소스/로컬 파일 해시 대조를 보존했다. main f1d9a63에도 추적 코드가 남아 있다.
- 기획·발표 자료·Git 이력은 유지한다. dearby-ir은 별도 저장소이며 변경하지 않았다. 과거 네이티브 규칙은 archive로 보존하고 새 웹에 적용하지 않는다.
- GitHub main에는 기존 iOS architecture 필수 체크가 남아 있다. 향후 PR 병합 시 실제 web CI에 맞는 보호규칙 변경을 별도 조율하며 가짜 iOS 체크로 우회하지 않는다.
