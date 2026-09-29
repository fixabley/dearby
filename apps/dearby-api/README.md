# Dearby native API — local vertical slice

Implements `shared/contracts/native-v1.md`. This is a local SQLite HTTP runtime, not production delivery or overall product completion.

## Run

Use Node **24.21.0 LTS** (`nvm use` with this directory's `.nvmrc`). From `apps/dearby-api`:

```sh
npm ci
cp .env.example .env
# Set OTP_SECRET to independently generated random secret and configure SMTP.
# openssl rand -hex 32 (store result privately, never commit it)
npm run dev
npm run typecheck
npm run lint
npm test
npm run build
npm start
```

SQLite migrations in `migrations/*.sql` run atomically once on startup; preserve that directory beside `dist`. Runtime binds loopback by default. No SMTP settings means challenge requests return 503. No development HTTP endpoint reveals OTPs; only the test harness (`NODE_ENV=test`) injects an in-memory mail sink, never server startup. SMTP requires TLS and certificate validation. Authentication success necessarily returns `sessionToken` as specified; no other response or logger exposes tokens/codes. Access logging is disabled; never add raw HTTP bodies/Authorization to infrastructure logs.

OTP: 6 random decimal digits, HMAC-SHA256 with server secret and challenge ID, 5-minute expiry, max 5 guesses, single use, new successful send invalidates prior challenge. Persistent quotas: one send/email/minute, five/email/hour, twenty/IP/hour, sixty verifications/IP/minute. Failed deliveries consume quotas to limit provider abuse. Unknown/expired/used/locked challenges use the same 401 response, and new/existing emails follow the same challenge response. Session tokens are 256-bit random, SHA256 persisted, expire after 30 days, and revoke individually.

SQLite uses foreign keys, WAL, busy timeout, synchronous short transactions and numbered migrations. Single host/local disk only: shared-network filesystems, multi-host scaling, production backup/restore, TLS ingress, secret management, actual email delivery and operational limits remain unvalidated. File permissions are restricted by startup umask; database contains private profiles and requires protected storage/backups. Reverse-proxy trust is disabled; configure/test a trusted ingress before deployment so IP quotas reflect clients.

Dependencies were checked against official npm registry on 2026-09-27, pinned with package-lock: Fastify 5.12.5, better-sqlite3 13.0.3, Zod 4.6.5, Nodemailer 10.0.10, TypeScript 7.0.2, tsx 4.23.15. Node 24 is LTS; local host Node 26 is Current, so checks use Node 24 explicitly. Sources: [Node release policy](https://nodejs.org/en/about/previous-releases), [Fastify docs](https://fastify.dev/docs/latest/), [SQLite driver](https://github.com/WiseLibs/better-sqlite3), [SMTP transport](https://nodemailer.com/smtp).

Owner profile and card routes enforce Bearer authentication. Public `GET /v1/cards/:id` exposes only the selected snapshot; owner lists exclude withdrawn cards. Publishing an ID outside the owner's profile fails with 422, cross-owner revoke with 403, withdrawn public reads with 404. Profile edits never widen previous publications. HTTPS is the only URL scheme accepted in contact values; phone/email/handle text remains supported.

Wallet import accepts at most 100 items and returns one outcome per input in order. Each valid item is committed independently, failures remain `failed` with null receipt ID; the app must remove only imported/alreadySaved selected items from local storage. Same recipient/card across import or delivery reports `alreadySaved`. Imported `receivedAt` preserves the validated guest `savedAt`; direct delivery uses server time. Self-delivery returns 422. A new exchange request ID keeps another historical receipt; retries with the same sender/request ID return the original result, changed validated body returns 409. SQL receipt and idempotency writes commit together. Mutual receipt history determines reciprocity, never a client click.

Withdrawn cards are 404 publicly and omitted from fresh wallet responses; receipts remain in SQL for history. A prior successful exchange retry returns only its existing receipt ID/time even after withdrawal; new delivery/import is blocked. The current contract has no unavailable-card tombstone; client UX follow-up is #41. Activity references require UUID, and do not certify participation; catalog UUIDs use the stable mapping below. Exchange context validation remains UUID-format-only and does not certify activity existence or attendance.

## Public catalog and bounded official collection (#45)

`GET /v1/catalog` follows `shared/contracts/catalog-v1.md`, requires no authentication and includes closed/unknown details. Empty DB returns empty arrays; database failures return 500. Reads recalculate exclusive deadlines and freshness; consumers must also expire cached discovery at `validUntil`. Only semantically verified explicit opening can be recruiting, for at most 24 hours. Failed collection preserves the prior content/check time/hash but marks it unavailable and hides it from discovery until a successful verification.

```sh
# Explicit isolated SQLite path is required; commands do not read DATABASE_PATH.
npm run catalog -- refresh --db /tmp/dearby-catalog.sqlite
npm run catalog -- refresh --db /tmp/dearby-catalog.sqlite --source kakao-2026
# Optional historical import. Never verifies or overwrites existing source records.
npm run catalog -- import-legacy --db /tmp/dearby-catalog.sqlite
# Serve this DB with the normal server, an independently generated OTP_SECRET,
# DATABASE_PATH=/tmp/dearby-catalog.sqlite and an available loopback PORT.
```

Only `https://if.kakao.com/2026` and `https://2026.feconf.kr/` are fetched, sequentially, with 15-second timeout, 1 MiB decoded-body limit and no redirects, linked-page crawling, JavaScript execution, retries, login or submission. Changed/uncertain source structure fails closed with nonzero CLI exit. `catalog_refreshes` stores the most recent attempt; `catalog_activities.good_body_sha256` retains the last good HTML SHA256 across failed attempts. Structured source notes capture interpretation; raw HTML is not a permanent archive. Run refresh explicitly or from an operator-owned scheduler; no scheduler/monitoring deployment is included.

Reviewed adapters are intentionally round-specific: if(kakao) checks the application section and FAQ, and interprets September 28 noon as Korean local time (03:00Z). Its date-only event keeps null start/end. FEConf's event opening at 10:00 is separate from ticket opening; positive ticket countdown means scheduled, never a computed ticket date or open registration. Known explicit deadline closes if(kakao) even while its HTML still says OPEN. Parser changes require reviewed fixtures plus a separate real fetch; offline fixture tests never establish live verification.

IDs use UUIDv5 with DNS namespace `6ba7b810-9dad-11d1-80b4-00c04fd430c8` and UTF-8 name `dearby/catalog/{organization|program|activity|schedule}/{source key}`. Existing snapshot slugs are identities: `org-kakao`, `kakao`, `kakao-2026`; `org-fedg`, `feconf`, `feconf-2026`; schedule keys append `/main` to activity keys. Refresh/import order cannot duplicate those rounds. History import normalizes audience arrays, rejects duplicate identities/missing references, and stays stale with null semantic checked/expiry times. It never trusts the old `current`/`open` flags. Exchange context DTO remains unchanged; this slice does not certify activity attendance or add receipt title fields.

## Supabase 탐색 어드민

`CATALOG_BACKEND=supabase`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`를 지정하면 `/v1/catalog`만 Supabase의 게시된 활동 스냅샷을 조회한다. 계정·명함 저장은 SQLite를 유지한다. 설정을 생략하면 기존 SQLite 카탈로그를 사용한다. Supabase 실패는503이며 자동으로 다른 데이터로 대체하지 않는다. `imageUrl`은 선택적 표시 필드다.

[Refine 어드민 및 로컬 설치](../admin/README.md)를 따른다. `node --env-file=.env.admin-local --import tsx src/server.ts`로 기존 서버와 별도의 localhost58765 인스턴스를 실행한다. `.env.admin-local`은 로컬 bootstrap이 생성하며 Git에 넣지 않는다. Supabase 모드에서 기존 SQLite 수집 CLI는 실행을 거부한다.
