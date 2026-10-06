# 관리자 웹 Vercel 배포 인계

검증일: 2026-09-29 KST. 담당 checkout: `admin-vercel`, 브랜치: `fixabley/admin-vercel`.
기준 main `f880d41`, 승인 원본 `8302f2b`의 tracked `apps/admin`만 복원했다. 원본 UI·npm lock·과거 증거 이미지를 보존했다. 과거 증거 이미지는 이번 실행 결과가 아니다.

## Root 배포 설정

- 별도 Vercel 프로젝트의 Root Directory: `apps/admin`, Framework: Vite, Node: **24.x** (`engines >=24 <25`, `.nvmrc` 24).
- Install: `npm ci`, Build: `npm run build`, Output: `dist`. 해당 디렉터리의 `vercel.json`이 설정과 SPA rewrite를 소유한다.
- root가 Production/필요한 Preview 범위에 `VITE_SUPABASE_URL`과 `VITE_SUPABASE_ANON_KEY`를 설정한다. 대상 cloud project ref는 `jsoclzeyybgdjxfuvaqs`. 키 값은 이 문서·로그·PR에 기록하지 않는다.
- anon/publishable 키는 본래 공개 키다. 브라우저에 포함되는 것이 정상이며 실제 보안 경계는 Auth + `is_catalog_admin` + DB RLS다. service_role, `sb_secret_*`, guest proxy 키를 프런트 환경에 설정하지 않는다.
- 빌드는 URL/키 누락, 원격 HTTP, anon/publishable 이외 키를 거부한다. Vite는 정확히 두 공개 환경값만 주입하므로 다른 `VITE_*`도 자동 공개되지 않는다. 키 종류 검사는 서명이나 프로젝트 소속 검증을 대신하지 않는다.
- 환경값은 빌드 시 고정되므로 변경 후 재배포가 필요하다. 이 작업은 `.env` 원본을 복사하거나 실제 키를 사용/출력하지 않았다.
- root가 `admin.dearby.wid.io.kr`을 프로젝트에 연결하고 Vercel이 제시하는 DNS 값을 적용한다. HTTPS 발급·최종 배포·프로덕션 검증은 root 소유다. 기존 공개 웹 프로젝트 설정을 덮어쓰지 않는다.
- `/collection`, `/activities/new`, `/audit` 직접 진입·새로고침 및 JS/CSS 응답을 실제 Vercel URL에서 확인한다. 로컬 preview 통과만으로 Vercel routing/DNS/TLS 통과를 주장하지 않는다.

공식 근거: [Vite SPA rewrites](https://vercel.com/docs/frameworks/frontend/vite#using-vite-to-make-spas), [Node 버전](https://vercel.com/docs/functions/runtimes/node-js/node-js-versions).

## 권한 검토와 검증 경계

`Authenticated`가 관리 Shell을 감싸며 로그인/기존 세션 모두 `getUser` 및 `is_catalog_admin`을 사용한다. 권한 조회 오류도 접근 거부한다. migration 6개를 읽어 관리 테이블/수집/감사 테이블의 RLS와 authenticated 관리자 제한, worker RPC의 service_role 제한을 확인했다. 권한은 변경 가능한 user_metadata 대신 `auth.users.raw_app_meta_data`를 조회한다. 공개 catalog RPC의 의도된 공개 데이터는 관리자 정보와 구분한다.

이번 브라우저 테스트는 가짜 세션과 가로챈 HTTP 응답을 사용한다. 비로그인·비관리자·권한 조회 오류에서 관리 메뉴와 데이터 요청이 없음을 검증하며 관리자 수집→활동→편집 진입을 검사한다. DB RLS 실제 실행 검증과 같지 않다. cloud 실사용 검증 완료는 원본 작업의 인계 사실이며 이번 재실행 결과가 아니다. root는 보호된 계정/키 경로로 실제 로그인·권한 및 RLS read-only 검증을 수행한다. 승인 없는 원격 쓰기 테스트는 실행하지 않는다.

기존 `admin.spec.ts`/`collection.spec.ts`는 로컬 DB에 쓰는 E2E다. cloud 대상으로 실행하지 않는다. 기존 collector checkout 및 launchd는 변경하지 않았다.

## 재현

Node 24에서 `apps/admin` 기준:

```sh
npm ci
npm run lint
npm run typecheck
VITE_SUPABASE_URL=http://localhost:54321 VITE_SUPABASE_ANON_KEY=sb_publishable_test npm run build
```

테스트용 공개키 문자열은 합성 fixture이며 운영 배포에 사용하지 않는다. 2026-10-06 사용자 요청으로 `npm test`·`test:e2e`(`deployment.spec.ts` 포함)를 지웠다.

## 상태

- Node 24.21.0, npm ci: 188 packages audit, 취약점 0; npm lock 원본 동일.
- lint, typecheck, 단위 테스트 7개, production build 통과.
- 번들 크기 경고: JS 약 1.57MB / gzip 485KB. 초기 다운로드 비용이 있으며 UI 보존 범위에서 코드 분할은 변경하지 않았다.
- production preview의 Chromium 브라우저 테스트 4개 통과: anonymous 6개 직접 경로, non-admin, 권한 RPC 오류, admin 수집 새로고침/활동 목록/편집 진입.
- 합성 secret/guest-proxy 키를 설정한 실제 Vite build 2개 거부 확인. 별도 `VITE_GUEST_PROXY_KEY` canary가 빌드 번들에 없음을 확인.
- 최초 preview 명령의 npm 인자 오류로 연결 실패했으나 명령 수정 후 전체 4개 재실행 통과. 실제 cloud/RLS 쓰기 E2E와 Vercel 원격 배포 검증은 실행하지 않음.
- ponytail-review: Lean already. Ship. 기존 승인 UI의 재설계나 추상화 추가 없음.
- root 인계 대상: `term_e1118002-4ecb-4409-a681-976050a7c86e`. 완료 후 세션/worktree retain.

## 최종 인계

[PR #68](https://github.com/fixabley/dearby/pull/68), UI 복원 `d96b6b8`, 배포 구현 `1a58df1`. 2026-09-29 root 전달 기준 `dearby-admin` 프로젝트/Node24/Vite/apps/admin/dist 및 Production 공개 환경변수 설정 완료, 도메인 연결과 Route53 CNAME 생성 제출 완료. 이는 root가 전달한 상태이며 이 checkout에서 원격 배포를 실행하거나 DNS/HTTPS 완료를 검증한 결과가 아니다. root가 PR의 최종 head를 검토·배포하고 공개 HTTPS/SPA 경로를 확인한다.

로컬 preview는 `http://127.0.0.1:5187`에서 합성 공개키로 실행 중이다. 실제 cloud 운영 미리보기가 아니다. 세션·worktree·preview를 유지한다.

## Root 최종 배포 검증 — 2026-09-29 전달

root 확인 결과이며 담당 checkout의 추가 실행 결과가 아니다.

- Production: https://admin.dearby.wid.io.kr READY, deployment `dpl_CoMSHCA4sxHHSM6XcXsAtwmRmqqT`, source `1a58df1` (`apps/admin`은 최종 PR head `99398a9`와 동일).
- Route53 `admin.dearby` CNAME `1588c031dca3967d.vercel-dns-017.com`, TTL 300. Vercel 인증서 90일·자동 갱신 활성.
- 정상 TLS curl: `/`, `/collection`, `/activities/new`, `/audit`, JS/CSS 성공. Chrome 실제 로그인 폼 표시 확인.
- 실제 JS 1,568,567 bytes / CSS 4,665 bytes. 번들에서 실제 service_role 및 guest-proxy 값 일치 0건, 대상 cloud URL 포함 확인.
- 기존 관리자 read-only 로그인 200, `is_catalog_admin=true`, catalog 조직 34 / 프로그램 36 / 활동 39건 모두 200. anon raw table 401. 테스트 세션 local logout 204. 데이터 쓰기 없음.
- 비공개 근거: `~/.dearby-deploy/admin-main/verification.json`, `~/.dearby-deploy/admin-main/auth-verification.json`. 이 checkout에서는 보호 파일을 열거나 복사하지 않았다.
- CI 진행 중. root가 PR 댓글·auto merge를 담당한다. 지시에 따라 이 갱신은 로컬에만 유지하며 추가 commit/push/재시작하지 않는다. 세션·worktree·preview retain.
- 기존 비관리자 차단 근거는 mock 브라우저 테스트다. 위 실제 배포 검증은 기존 관리자와 anon 대상이며 실제 비관리자 계정 테스트 완료를 뜻하지 않는다.
