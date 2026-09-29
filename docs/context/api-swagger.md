# API Swagger 인계

2026-09-30 KST. 담당 checkout `api-domain-main`, branch `feat/api-swagger`, 기준 main `40b2d19`(Prisma PR69 병합). 기존 미커밋 Prisma 운영 인계는 `~/.dearby-deploy/api-swagger/20260930-010056/`(0700/파일0600) 및 stash에 보존하고 이번 문서에 함께 통합했다. 운영 DB·58865·nginx·worker·env·CA는 변경하지 않았다. 세션/worktree retain.

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
