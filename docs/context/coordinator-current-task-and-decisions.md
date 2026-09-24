# 현재 작업과 결정

2026-09-24 01:47 KST. **컨퍼런스 우선 웹 구현·조사·메인 검증 완료**, 사용자 검토 대기.

## 완료: 기업 로고 아바타 — 2026-09-24 10:26 KST

사용자가 기업 주최기관의 Avatar를 기업 로고로 바꾸도록 요청했다. 기업8개 공식 로고를 카드·상세·스크랩 조직 목록에 공통 적용하고 커뮤니티는 initial 유지, 이미지 실패시 fallback과 비율보존을 적용한다. 기존 웹 세션 재사용: Task `task_9caee518b4c1` / Dispatch `ctx_beeec3876c97`, child 기준 `4a6df2d`. 담당 `2373bc5`를 root `1f9cbdc`에 통합했다. 공식 SVG6/PNG2 출처는 public/organizations/SOURCES.md. 담당 lint/typecheck/unit12/build/prod36/dev18 통과, 메인 lint/typecheck/build 및 아바타 브라우저4개 재통과. 데스크톱/모바일 실제 캡처 확인, Orca 새로고침 후 화면 안 로고 로딩 확인. 긴 워드마크는 작게 보이지만 비율과 조직명 텍스트를 유지한다. Ponytail 추가 삭제 후보 없음. succeeded·retain·ack 완료, 원격 push 없음.

## 사용자 목적과 승인

기존 네이티브/API 실행 코드를 보존 후 제거하고 최근 재기획의 프로그램 탐색·필터·상세·조직/프로그램 스크랩을 웹으로 구현한다. 고등학생·대학생·취준생을 포함하며 YouTube처럼 읽기 쉬운 썸네일 탐색 구조를 원한다. 첫 콘텐츠는 컨퍼런스, 교육 과정은 후속이다. 잠든 동안 범위 내 미정 사항은 자율 판단하도록 승인했다.

최신 제품 정본: [컨퍼런스 우선 웹](../product/conference-first-web-2026-09.md). 조사: [국내 IT 컨퍼런스](../research/korea-it-conferences-2026-09.md). 기업·커뮤니티·학회 등 공식 자료 60행과 검증 대기 후보를 분리했다. 계보/지역 회차를 포함하여 고유 행사 60개나 전체 누락 없음의 뜻은 아니다.

## 구현과 통합

- Root `/Users/jominjun/Documents/dearby`, `feat/web-rebuild`. 실제 데이터 통합 커밋 `e829b74`, 인계 `b4ecc3a`, Toss/DEVIEW 근거 보완 `703c136`. child 원본은 `f0489a5`, `d9a8627`, `4a6df2d`; 기준 `1dae183` 이후만 cherry-pick했다.
- Next.js 16.3.6·React 19.2.8·TypeScript. 공식 컨퍼런스 18개, 회차 20개, 조직 16개. 공식 OG14·실제 랜딩 캡처4, 출처·확인일 기록. shadcn 허용이나 필수 아님; 현재 HTML 기본 컨트롤과 CSS.
- 검색(조직 포함), 주제/경험 필터, 결과 없음의 유효 부분 조건과 개수·되돌리기, URL 복원, 회차별 참가 정보, 독립 조직/프로그램 스크랩. 같은 공고 안에서 조건을 결합한다.
- 등록 중/예정/미확인/마감/행사 종료를 구분한다. 자격 미충족으로 자동 제외하지 않는다. 무료·무제한 참가·발표 경험을 임의 추정하지 않는다. 카카오26·우아콘26·드로이드26은 확인일 기준 접수 중. FEConf는 접수 예정, 삼성 AI 포럼은 등록 미확인.
- 실제 API·인증·신청/결제·자동 크롤러·일일 개인화 추천·기기간 동기화·프로필 자동 자격 판정은 미구현. 정적 2026-09-24 스냅샷이고 취소/매진은 자동 반영되지 않는다.
- 원격 push/PR/merge/배포는 이번에 수행하지 않았다. GitHub main에는 과거 iOS architecture 필수 체크가 남아 있어 향후 web CI 보호규칙 조율이 필요하며 가짜 체크로 우회하지 않는다.

## 이번 메인 검증

703c136 통합 후 lint, typecheck, 단위12, production build, production E2E32, dev Chromium9+WebKit9가 모두 통과했다. 1440×1000/390×844 캡처를 실제 확인했고 axe AA·overflow·console/hydration·저장 실패/복구/탭 동기화 회귀를 유지했다. 원격 CI·실기기·수동 스크린리더는 미실행이다.

Orca 실제 브라우저에서 전체 목록→우아콘 상세→스크랩→새로고침 복원을 확인하고 테스트 저장만 해제한 뒤 전체 목록으로 복귀했다. 현재 페이지 console error0. 최종 캡처 `/tmp/dearby-final-orca-screen.png`를 확인했다. Ponytail 최종 변경 검토: 추가 삭제 후보 없음. 공식 아트워크의 작은 글씨는 카드 텍스트로 보완하며 모바일 상세의 참가 정보는 긴 설명보다 앞에 둔다.

## 세션과 미리보기

- http://127.0.0.1:3000 실행 중. 서버 terminal `term_f9504257-412a-4374-ac61-85add5f30816`, root browser page `d26f381c-df01-4020-9115-53457b1007ba`.
- Orca `run_469a74207c03`, Task `task_11416b48d24f`, Dispatch `ctx_259771a9ca90`: succeeded 보고 수신·검토·retain·delivery ack 완료. 하위 웹 세션 `term_db69f423-a4bc-45ae-910d-ede154eab9d9` 유지. 회수 대기 worker 없음.
- 하위 checkout `dearby-web`, `fixabley/dearby-web`, 메인 부모 연결. 문서/커밋은 자동 동기화되지 않는다. child의 Next 자동 AGENTS diff는 기존대로 보존하며 root 통합 대상에서 제외했다.

## 보존과 다음 행동

이전 실행코드는 `/Users/jominjun/Documents/dearby-backups/2026-09-24-before-web-rebuild/`에 bundle·사용자 Xcode diff·원본·700파일 해시 확인과 함께 보존했다. main f1d9a63에도 추적 이력이 남는다. dearby-ir은 별도 저장소로 변경하지 않았다.

사용자 화면 검토 이후 우선순위를 정한다. 후보는 조사 목록의 추가 검증/수록, 운영 갱신 정책, 개인정보/이미지별 재사용 조건 확인, 서버 저장·개인화다. 과거 초기 샘플·통합 이력은 [보관본](archive/2026-09-24-web-prototype/coordinator-current-task-and-decisions.md)에 있다. 재개 시 runtime·Git 상태를 다시 확인한다.
