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
