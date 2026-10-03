# API Swagger 인계

2026-09-30 KST. 담당 checkout `api-domain-main`, branch `feat/api-swagger`, 기준 main `40b2d19`(Prisma PR69 병합). 기존 미커밋 Prisma 운영 인계는 `~/.dearby-deploy/api-swagger/20260930-010056/`(0700/파일0600) 및 stash에 보존하고 이번 문서에 함께 통합했다. 운영 DB·58865·nginx·worker·env·CA는 변경하지 않았다. 세션/worktree retain.

## 현재 운영 상태 — 2026-09-30 root 최종 보고

아래 배포·운영 검증은 **root가 실행하고 전달한 결과**이며 본 API 담당이 재실행한 검사가 아니다. 이전 절의 CI 진행/운영 미변경 문구는 구현 당시 기록이다.

- PR70 병합 main `f07210f8a4bb16736a4553085eb787025ff52581`. API/Android/iOS PR CI 모두 success. root가 source `2cc7f3c`와 main의 API tree 동일성 확인.
- live `dearby-api-main:swagger-2cc7f3c`, image `sha256:df603ef986b68a8e39d13d5e587bdeb29f9f51382f5ba9434c80c869eb217f4d`, API58865 healthy.
- root가 nginx docs snippet만 추가하고 config test/reload 완료. 신뢰된 로컬 TLS에서 `/docs`308, `/docs/`·JSON·YAML·8개 자산200, POST `/docs/json`405. 기존 profile404/catalog200 유지.
- 실제 `http://127.0.0.1:58865/docs/` UI 렌더·읽기 전용·console[] 검증. 명세23 operations/13 paths. root 소유 임시 docs 검증서버50023 종료, 작업 세션은 retain.
- 최종 이미지 `/app`4482파일/132878035bytes에서 실제 비밀/env 노출0; 응답/로그 비밀 노출0 보고. 운영DB/env/CA/OTP/guest 변경 없음. 공유기/DNS 변경 없음. 외부 연결 #65는 여전히 미해결이며 로컬 TLS/UI 성공과 구분한다.

실제 Compose 정본은 `~/.dearby-deploy/api-swagger/source-2cc7f3c/apps/dearby-api/deploy/compose.yaml`과 `~/.dearby-deploy/api-swagger/runtime-compose.override.yaml`이다. env0600 유지, CA는 기존 외부 `~/.dearby-deploy/api-prisma/connection/prod-ca-2021.crt`를 사용한다. 이전 Prisma source staging은 보존 이력이며 신규 재시작은 이 Swagger 정본과 root 조율을 따른다. 기존 SQLite 원본·백업 보존 및 신규 PostgreSQL 쓰기 이후 단순 SQLite 롤백 금지 조건도 유지한다.

### staging 파일 권한 사고 및 복구 — root 보고

root staging tar가 umask077로 소스를0600 추출하여 첫 컨테이너의 node user가 package.json 읽기를 거부당했다. root가 기존 Prisma 이미지를 즉시 복구한 뒤 staging 소스0644/디렉터리0755로 정리(env0600 유지)하고 재build했다. 별도의 실제 node user canary에서 docs/catalog200 확인 후 재배포를 완료했다. 코드 결함이나 DB 변경으로 기록하지 않는다. staging 권한과 실행 UID의 파일 접근성을 재배포 전 확인해야 한다.

운영 증거는 repo 밖 `~/.dearby-deploy/api-swagger/`의 `deployment-verification.json`, `deployed-browser.json`, `image-inspection.json`, `packaged-runtime-verification.json`. root가 PR 운영 댓글을 작성할 예정이다. 본 담당은 이 보고를 로컬 인계/카드에만 반영하며 추가 commit/push, 운영 재검사·서비스 변경을 하지 않는다. 세션/worktree retain.

## 구현

- Fastify 5.12.5와 Prisma 7 유지. 공식 `@fastify/swagger`9.9.0, `@fastify/swagger-ui`6.1.1 고정. UI README 호환표가 뒤처져 있지만 [6.1.1 공식 plugin metadata](https://raw.githubusercontent.com/fastify/fastify-swagger-ui/v6.1.1/index.js)는 `fastify: '5.x'`를 명시한다. UI5의 static 의존 취약점을 피하고 patched6.1.1로 검증했다.
- `/docs/` UI, `/docs/json` OpenAPI3.0.3, `/docs/yaml`. 로컬 정적 자산, CSP, `supportedSubmitMethods: []`, `persistAuthorization: false`, `tryItOutEnabled: false`, `validatorUrl: null`; auth controls도 CSS로 숨겼다. guest proxy key는 브라우저 입력 대상이 아닌 서버 전용으로 명시한다. UI 읽기 전용 설정은 API 권한제어를 대신하지 않는다.
- 총17개 명시적 API 메서드 +6개 자동 HEAD를 문서화. 자동 HEAD는 고유 operationId와 빈 response content로 표현한다. body/params/response schema는 기존 Zod에서 변환하고, refinement의 추가 제약은 설명한다. wallet import의 unknown items/부분 실패 계약은 유지하며 가짜 UUID·context·savedAt 예시를 제공한다.
- `documented()`는 **route.config.swaggerTransform만** 반환한다. live Fastify body/params/response schema를 설치하지 않아 추가 validator/serializer나 응답 필드 제거가 없다. 기존 handler의 Zod422, error status, 공개명함 projection, 무기한 guest digest·폐기·원자성 모두 유지한다.
- 문서는 전체 API를 설명하지만 현재 공개 nginx는 catalog/public card/guest만 허용한다. owner/auth/profile/card collection은 공개 proxy404이며 허용된 직접/로컬 연결용임을 명시한다. 내부 RPC 이름·이전 전환 이력·이슈번호는 공개 명세에 넣지 않았다.
- 별도 export CLI/중복 정적 spec은 만들지 않았다. [deploy README](../../apps/dearby-api/deploy/README.md)의 curl로 생성 명세를 내려받는다.

## 검증

Node24, 자기 API cwd에서 설치/실행. 운영에 연결하지 않는 임시 PostgreSQL17 컨테이너·DB/테스트 role 사용. 새 테스트는 기존 CI `npm test`에 자동 포함된다.

- lint/typecheck/build 통과. 전체37 tests 통과(fail0/skip0): 기존35개와 OpenAPI 검증2개.
- live onRoute와 spec의23개 method/path가 정확히 일치하고 operationId 중복0, live validator/serializer schema 미부착, public/owner/guest 보안 조건 확인.
- 실제 PostgreSQL 응답을 Ajv+formats로 문서 schema에 대조: profile/card/wallet/import/exchange/guest/auth/catalog(비어 있지 않은 fixture), HEAD/204,401/403/404/409/422/429/500/503. 모든 명시적 메서드와 HEAD 응답을 실행하며 no-store/부분 import/철회/원본 비공개 기존검사 유지.
- docs HTML/JSON/YAML과 참조된 CSS/JS/image 자산200, 외부 자산 없음, CSP 확인. JS 전부 구문 검사하고 initializer를 VM에서 실행해 readonly/auth persistence/validator 설정을 확인했다. 실제 브라우저 시각 검사는 수행하지 않았으며 root 배포 후 UI 확인과 구분한다.
- 명세/UI 응답에 fixture OTP/proxy secret 포함0. 실제 운영 secret을 읽거나 출력하지 않는다. `npm audit --omit=dev --omit=optional`: 취약점0. 기존 Prisma CLI 개발 의존 경고는 운영 의존과 분리한다.
- ponytail-review: Lean already. Ship. 문서용으로 validator pipeline/export abstraction/새 repository를 추가하지 않았다. 보안/정확성 검토는 위 테스트와 별도다.

## root 배포 인계

[nginx-docs.location.conf](../../apps/dearby-api/deploy/nginx-docs.location.conf)는 HTTPS server 안에 추가할 최소 제안이다. exact `/docs`308→`/docs/`, `^~ /docs/` GET/HEAD proxy58865, Authorization/Cookie/X-Guest-Token/X-Guest-Proxy-Key 제거, access/error 로그 비활성. 기존 `/v1` allowlist를 바꾸지 않는다. 실제 shared nginx 파일/인증서/DB/운영 컨테이너는 root만 변경한다.

root가 source/image 검토·CI 확인·merge·런타임 배포 후 `/docs/`, JSON/YAML, 정적 자산과 nginx 제한을 검증한다. 외부 Vercel→API 연결 #65는 문서 구현이나 로컬 TLS 성공으로 해결된 것으로 표현하지 않는다. 앞선 운영 전환 완료와 외부 Compose/env 정본, SQLite 원본 보존·신규 PG 쓰기 후 단순 SQLite rollback 금지는 [Prisma 인계](api-prisma-postgres.md)를 따른다.

공식 참고: [Fastify Swagger](https://github.com/fastify/fastify-swagger), [Swagger UI](https://github.com/fastify/fastify-swagger-ui), [Zod JSON Schema](https://zod.dev/json-schema). 원격 PR/CI 결과는 게시 후 로컬 인계/PR 댓글로 추가해 문서만으로 CI를 재시작하지 않는다.

## 게시 후 로컬 인계 — 추가 push 없음

PR70: https://github.com/fixabley/dearby/pull/70 . head `2cc7f3c188b972bf5651288289f8312a1002782a`; 기능/검사 `da5d77d`, 문서/스니펫 `2cc7f3c`. API/Android/iOS 원격 CI 시작을 확인했다. Supabase Preview skip은 DDL 변경 없음과 구분한다.

게시 직후 수신한 root 독립검증 보고: DB미연결 임시 loopback docs 서버에서 Orca embedded UI 렌더·catalog 펼침·Parameters/Responses 표시·Try it out/Authorize 부재·console[] 확인. 외부 임시 `@apidevtools/swagger-cli`4.0.4 validate valid(폐기예정 CLI이므로 OAS3 교차검사 증거로만 사용; repo dependency 추가 없음). nginx snippet 검토 OK. 실제 live 변경 없음. 증거는 `~/.dearby-deploy/api-swagger/browser-review.json`, `browser-review-snapshot.txt`, `review-docs.png`, `review-openapi.json`. 본 세션에서 실행한 브라우저 검증으로 표현하지 않는다. root 임시서버 종료는 root 소유. 이 수신결과는 PR 본문/로컬 인계만 갱신하고 CI 재시작 push하지 않았다.

원격 확인: PR70 run36596494083 API job success(1m9s). Android/iOS는 진행 중으로 아직 통과로 기록하지 않는다. committed diff secret pattern 검사0, 변경범위는 API와 docs/context16파일뿐이다.
