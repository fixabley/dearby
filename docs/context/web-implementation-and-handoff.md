# Dearby 사용자 웹 구현 인계

## 현재 범위 — 2026-09-29

사용자 승인: `dearby.wid.io.kr` 공개 웹. `apps/web` 독립 Next 앱에서 탐색 → 상세 → 외부 신청, `/cards/:id` 공개 이력서(공개 Card 프로필·연락처·활동 이력), `/saved` 비로그인 쿠키 세션 명함 저장을 구현한다. PDF 여부는 root 확인 중이며 현재 PDF·작성·로그인·명함 발행은 구현하지 않는다. 기존 native/API/admin/worker/shared 계약은 수정하지 않는다.

이전 웹 구현 이력은 [보관본](archive/2026-09-29-before-public-web/web-implementation-and-handoff.md)이다. 이전 root Next 앱을 복구하지 않고 기존 의존성 버전/설정과 승인 native 스타일·원본 로고를 활용했다.

## 소유권과 상태

- checkout `/Users/jominjun/Documents/dearby/web-vercel`, branch `fixabley/web-vercel`.
- 확인한 웹 세션 handle `term_cfab069e-7400-430d-a19a-dffb56a54960`; API 협업 `term_ed77215a-daf0-49d7-92da-127ad6e91e3e`. 런타임 handle은 재시작 시 재확인한다.
- root가 Vercel 프로젝트·DNS·production env·main 통합·실제 배포를 담당한다. 이 세션은 배포/DNS/운영 데이터 변경을 실행하지 않았다. 완료 후 세션/worktree 유지 요청을 따른다.
- 웹 소스 `d03b4ca`까지 push·PR 인계 완료. 2026-09-29 root 전달: GitHub merge API로 API `70af9ac`을 PR64 브랜치에 통합하여 원격 head는 `097f955`. 이후 root 전달: PR64 모든 통합 CI 통과 후 main `a7b61b329be7b8305b81c5b4cec36a0ceba3a060`에 병합 완료. PR66 source도 포함되므로 별도 main 병합하지 않는다. API runtime 배정 source는 `70af9ac`을 유지한다.
- 현재 로컬 HEAD는 직접 확인한 `d03b4ca`이며 원격 통합본을 checkout/build했다고 주장하지 않는다. 최신 root 지시로 추가 코드/commit/push는 불필요하다. 이번 상태 갱신은 로컬 인계 문서와 카드에만 남기며 세션/worktree를 유지한다. 로컬 미커밋 인계를 보존하고 있으며 원격 통합 이력으로 fast-forward는 수행하지 않았다.

## 구현 계약

- API origin은 서버 env `DEARBY_API_ORIGIN`; guest secret은 `GUEST_PROXY_SECRET`. Supabase client/key 없음. 브라우저는 same-origin `/api`만 요청.
- 프록시는 공개 catalog·Card 단건·guest CRUD만 허용한다. UUID 검증 후 API에서 DB 존재·미철회 인가, 공개 DTO 필드만 반환. private profile과 타인 wallet 요청 경로 없음.
- 최초 PUT 저장+세션 생성은 API 원자 연산. token은 서버에서만 쿠키로 옮기고 JSON에서 제거. 기존 token은 갱신만 하며 불명/폐기 token 401을 새 세션으로 자동 교체하지 않는다.
- `__Host-dearby_guest` HttpOnly/Secure/SameSite=Lax/Path=/, Domain 없음, 400일. 성공한 guest 요청에 같은 token으로 갱신. 서버 세션 자동 만료 없음. 쿠키 유실·브라우저별 독립성 안내.
- 무쿠키 목록은 API 호출·세션 생성 없이 empty. Web Locks로 탭 간 mutation 직렬화. 저장 성공은 PUT 후 GET에서 카드 확인 뒤 표시. 명시 세션삭제·개별삭제는 서버 결과 뒤 UI 변경. 오류 시 기존 목록 보존.
- 철회/404·오류/재시도·빈 목록·401·429·저장한도 안내. 모의 모집/프로필 운영 fallback 없음. 브라우저 캘린더는 미지원 문구만 있고 기능처럼 보이는 버튼 없음.
- 구체적인 endpoint, Vercel 설정 및 root 운영 점검은 [웹 README](../../apps/web/README.md).

## 검증과 한계

2026-09-29 실제 실행: Node 24.21.0 lint/typecheck/unit+proxy 12개/build 통과. 초기 기본 shell Node26에서도 확인했지만 최종 배포 버전에 맞춰 Node24로 재검증했다.

- 로컬 Next+test API double Chromium desktop/mobile E2E 12개 통과. 탐색/상세/공식 CTA, 저장·재조회·리로드·삭제·세션초기화, 다른 브라우저 격리, 두 탭 첫 저장 합류, 철회404, 서버실패 vs empty, 불명token 미교체, 쿠키삭제, 320px/확대문자 흐름.
- axe 탐색/공개명함/저장 목록 위반 0. 실제 PNG를 열어 모바일 탐색/명함/빈목록 및 데스크톱 상세/저장 화면을 승인 시안과 비교했다. 크로미엄 모바일 에뮬레이션이며 물리기기/Safari 검증과 구분한다.
- 초기 E2E 8통과/4실패는 Next route announcer의 alert까지 잡은 테스트 선택자 충돌이었다. 본문 alert로 범위를 좁혀 12개 재통과했다.
- 로고 intrinsic 크기 불일치 경고를 확인하여 원본 2172×724 비율로 수정했다. 개발 indicator는 캡처에 섞이지 않도록 껐다. 최종 로고 sizes/eager 수정 후 Node24 lint/typecheck/build와 E2E12를 재실행해 모두 통과했고 로고 경고가 사라졌다.
- root 제공 실제 API 임시 DB(58867) + Next3211 + Chromium 통합도 통과했다: 원자적 두탭 저장, JSON 토큰 제거, 400일/동일토큰 갱신, 두 브라우저 격리, 삭제 격리, fixture0003 소유자 철회204→조회/저장404→목록 제외, 세션삭제 쿠키소거, pageerror0. 0002미철회/0003철회/0004미접근으로 root에 종료가능 통지. 재현 스크립트는 `apps/web/tests/real-api.integration.ts`. 로컬 HTTP 개발 쿠키 검증이므로 운영 Secure 쿠키는 단위검증과 root HTTPS 실검증을 구분한다.
- 운영 API/DB·실제 Vercel HTTPS 쿠키·외부 네트워크·모집 데이터·PDF는 위 테스트가 증명하지 않는다. DB published 0은 root 전달 사실이며 운영 mock 삽입 없음.

## Root 다음 작업

root 전달 배포 증거(이 세션의 재실행 결과가 아님): Vercel `d03b4ca` 배포 완료, 배포 source와 main의 `apps/web` 동일. `https://dearby.wid.io.kr` HTTPS 루트200·무쿠키 guest 목록200·WAF 실제429 및 정상 복귀 확인, static 12파일 약1MB에서 실키 노출0. 무쿠키 guest 목록은 upstream을 호출하지 않으므로 이200은 외부 API 연결 성공 증거가 아니다.

남은 차단: Vercel→외부 API 502, [#65](https://github.com/fixabley/dearby/issues/65) 미해결. root가 외부 연결 해소와 실제 운영 저장 검증을 계속 담당한다. 웹+API 통합 CI 및 PR64 main 병합은 완료되었다. 새 사용자 요청인 로컬 Supabase→cloud 전환은 API/수집 담당이 진행하며 웹 세션은 해당 코드·설정에 개입하지 않는다. 운영 공개명함이 승인되어 존재할 때 실제 저장/격리 확인이 필요하며, 운영 데이터나 mock을 임의 삽입하지 않는다. 브라우저 제한을 무기한 보존으로 표현하지 않는다. 웹 세션의 추가 코드/push는 요청되지 않았으며 로컬 인계·카드 갱신 후 retain한다.

## PR·리뷰

- PR [#64](https://github.com/fixabley/dearby/pull/64), checkpoint `39d26f6` public shell/catalog/proxy + `92c3396` public resume/guest UX. Git 기본 credential이 다른 계정이라 첫 push403, 명령 단위 gh 활성 fixabley helper로 성공. 전역 인증설정 미변경.
- Ponytail: 과거 설정에서 사용하지 않는 playwright.dev 경로·dearby-web ignore를 제거했다. 최종 변경은 실제 쓰는 화면/프록시/DTO/공통 UI만 두며 별도 상태관리·디자인 엔진·인메모리 rate-limit 저장소를 추가하지 않았다. 최종 `Lean already. Ship.` 정확성/보안/접근성 회귀는 위 별도 테스트로 검증했다.
- Next dev가 추가한 `apps/web/AGENTS.md`와 `CLAUDE.md`는 자동 재생성 안내 파일임을 확인하고 관련 설치본 Route Handler/Image 문서를 읽었다. 해당 파일은 후속 커밋에 보존했다.
