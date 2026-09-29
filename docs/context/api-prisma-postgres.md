# API Prisma/PostgreSQL 전환 인계

갱신: 2026-09-30 KST. 담당 checkout `api-domain-main`, branch `feat/api-prisma-postgres`, 최신 기준 main `3d3922c`(PR68 포함). 기존 API/Supabase 인계 미커밋은 외부 `~/.dearby-deploy/api-prisma/20260929-235249/`와 git stash에 보존했다. 세션 retain.

## 구현

사용자 확정에 따라 API 계정·OTP·세션·프로필·명함·전달·게스트를 전부 Prisma7.10.0/adapter-pg로 전환했다. Node24에서 검증했다. Prisma 모델은 private `dearby_api`에14테이블을 매핑한다. public catalog는 기존 `catalog_public_snapshot()`을 tagged `$queryRaw`로 호출하고 DTO 검증/시각 갱신/실패503을 유지한다. raw query 결과의 TS generic은 런타임 검증이 아니므로 기존 Zod 검증도 유지한다. public 원본테이블, Supabase managed Auth를 API 모델에 합치지 않는다.

`server.ts`에는 SQLite/REST fallback이 없다. SQLite database/catalog 도구 및 옛 REST reader 파일은 기존 파일 보존 요구에 따라 오프라인 이력/복구용으로 유지한다. `catalog:offline`로 명시하며 API 시작 시 실행하지 않는다. 런타임 SQLite volume은 readonly로 보존한다.

- API 테이블은 TEXT ID/JSON 원문/기존 ISO문자열, epoch ms BIGINT, digest를 보존한다. Card/Receipt/GuestCard ordinal은 SQLite rowid 순서를 보존한다. 기존 HTTP 계약·원본비공개·명함철회·guest무기한·동일token 갱신은 유지한다.
- 쓰기는 Prisma interactive transaction 안에서 공통 transaction advisory lock을 얻는다. 여러 API 인스턴스 사이에서도 SQLite의 직렬 쓰기를 유지해 OTP·100card/10000session 한도와 중복저장/교환 idempotency를 보장한다. SMTP 호출은 잠금 밖이다. 높은 쓰기량에서는 병목 가능; 측정 전 키별 잠금/재시도 계층은 도입하지 않았다. guest abuse 메모리 카운터의 프로세스별 한계는 기존과 같다.
- DDL 정본은 `supabase/migrations/20260929150000_api_prisma.sql`뿐이다. Prisma generate는 DB 접속이 없고, db push/migrate reset/deploy는 운영에서 사용하지 않는다.
- `dearby_api_runtime`은 객체를 소유하지 않으며 NOINHERIT/NOSUPERUSER/NOCREATEDB/NOCREATEROLE/NOREPLICATION/NOBYPASSRLS다. private schema CRUD+sequence USAGE/SELECT와 public catalog RPC EXECUTE만 부여한다. RLS14테이블 활성화, browser/service_role에는 schema/table권한 없음. Data API exposed schema에 추가하지 않는다. root가 LOGIN/password/CONNECT를 별도 준비했고 migration은 기존 안전속성을 검증한다.
- `DATABASE_URL`과 `DATABASE_CA_FILE`은 ignored0600 환경으로 주입한다. non-loopback TLS는 rejectUnauthorized=true, URL query 옵션 제거로 pg sslmode의 SSL object 덮어쓰기를 방지한다. 공식 CA는 외부 readonly bind이며 소스/이미지에 포함하지 않는다. OTP_SECRET/GUEST_PROXY_SECRET을 변경하지 않는다.

## 이관과 검증

`npm run import:sqlite -- --source <frozen-backup>`은 기본 dry-run이다. readonly SQLite integrity/FK와14테이블 경계를 검사하고 count+SHA256 canonical row 비교만 반환한다. 0/1 외 SQLite boolean, 안전범위 밖 정수는 추정변환하지 않고 거부한다. `--apply`는 빈 target에만 전체 트랜잭션으로 삽입/검증; 전체가 같으면 unchanged, 비어있지 않고 다르면 merge/overwrite 없이 거부한다. raw profile/email/token은 출력하지 않는다. JSON 원문/ID/digest/시간/참조관계를 보존한다.

원본 cards/receipts/guest_cards가 비어있지 않을 때만 해당 sequence의 UPDATE를 root가 임시 grant하고 성공/실패 모두 revoke해야 한다. 영구 runtime권한에는 없다. 빈 ordered table의 setval은 건너뛴다. PG sequence변경은 rollback되지 않아 실패시 gap은 남을 수 있지만 데이터는 rollback된다. root의 현재 source보고는 rate_limits3/history3, 그외0이므로 해당 임시권한이 필요없다. 비어있지 않은 fixture에서도 실제runtime role로 권한부족 rollback→임시grant→정확이관→revoke를 검증했다.

실행 검증(운영DB import 없음):

- Node24 `npm run typecheck`, `npm run lint`, `npm test`, `npm run build` 통과. **34 tests, fail0, skip0**. PostgreSQL17 격리 임시 컨테이너/DB와 실제 runtime role 사용. 전체 Supabase migration은 fresh cluster에서 적용하고 각 API fixture는 독립 DB에 catalog2 migrations/API migration을 적용한다. fixture Auth는 catalog 정책 의존용 최소 테이블/uid함수이며 실제 Supabase Auth 서비스가 아니다.
- 기존 OTP/HTTP/profile/wallet/exchange/guest 검증을 Prisma 모델에 이식. 두 API client의 동시 마지막slot/10000session, OTP 동시 send/verify/오입력 최대5회, 중복저장, 만료/철회/DB재연결 검증.
-14테이블 비어있지 않은 SQLite fixture의 count/hash 일치, digest로 기존인증/guest/OTP 재사용, 원문JSON과row순서/새sequence 증가, rollback/재실행/충돌거부 검증.
- runtime DDL/TRUNCATE/공개원본/Auth/role승격/sequence reset 거부,14RLS/browser역할private접근거부, runtime객체소유0. public RPC published만 반환, 권한박탈시503·fallback없음.
- `.github/workflows/native.yml` API job에 격리PG service를 추가했다. npm test가 실제PG 초기화/테스트를 반드시 수행하며 DB없으면 fail한다. Android/iOS job은 변경하지 않았다.
- Docker review build 통과. build에는 DB secret이 필요없고 CI에서 실DB테스트를 수행한다. final image는 `npm prune --omit=dev --omit=optional`으로 Prisma CLI/optional dev tooling 제거, 해당 production dependency audit0. 개발 CLI transitive deepmerge-ts/mysql2에 npm high4 경고는 남아 있으므로 build tooling 이슈와 런타임을 구분한다; 임의 major downgrade/force fix 하지 않았다.
- root 보고: 별도보호env+공식CA로 본 Prisma7 openPostgres/verifyRuntimeRole 실제cloud 연결 성공. root는 review 컨테이너 readonly/256m/capdrop/nodeUID+외부CA readonly bind에서도 dist/postgres.js verifyRuntimeRole 성공을 보고했다(port바인딩/데이터쓰기0). 최종source commit이미지는 root가 별도build한다. 본 담당은 실제 키/env/CA를 읽거나 운영권한/DB를 바꾸지 않았다.

## 검토 및 운영 경계

ponytail-review 결과: Lean already. Ship. 일반 CRUD를 다시 감싸는 repository계층, 중복DDL pipeline, 두 런타임 backend, speculative retries를 추가하지 않았다. 명시적인 Prisma 모델과 단일write transaction helper만 사용한다. 정확성/보안은 위 별도 테스트 근거다.

root 운영 runbook은 `~/.dearby-deploy/api-prisma/PREPARED-NOT-CUTOVER.md`; 상세 승인전제/복구 순서는 [deploy README](../../apps/dearby-api/deploy/README.md). schema 자동배포→API쓰기freeze→최종SQLite onlinebackup→dry-run/apply/hash→같은58865 새이미지/CA주입→검증→unfreeze 순서다. 기존 image `c14e009`, volume, env, OTP/GUEST secret 보존. PostgreSQL 신규쓰기 후 단순SQLite rollback은 신규데이터를 잃으므로 재freeze·정합성복구가 필요하고 자동 reverse importer는 없다. collector/admin/root서비스 영향 없이 source PR만 준비했다.

설치 경로 사고도 보존: 첫 npm설치를 checkout루트에서 실행해 npm이 상위root를 선택했고 node_modules 일부가 변경됐다. 생성된 root package.json/lock 두 파일은 위 외부backup에 저장 후 제거했고 기존 node_modules는 더 건드리지 않았다. root에 즉시 알렸으며 root는 운영 영향이 확인되지 않았다고 보고했다. 이후 API cwd를 명시했다. 이 사건을 정상 검증으로 숨기지 않는다.

운영import/컨테이너전환/실제권한변경은 root 최종조율 전 실행하지 않는다. 현재 운영58865는 기존SQLite 이미지이며 이번 PR완료를 운영전환 완료로 표현하지 않는다.

## 공식 참조

- [Prisma7 upgrade: Node/ESM/adapter/config/TLS](https://docs.prisma.io/docs/guides/upgrade-prisma-orm/v7)
- [Prisma tagged raw queries](https://www.prisma.io/docs/orm/v7/prisma-client/using-raw-sql/raw-queries)
- [Supabase Prisma connection guide](https://supabase.com/docs/guides/database/prisma)
