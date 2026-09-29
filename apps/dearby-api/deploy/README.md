# Prisma PostgreSQL API deployment

Runtime now uses Prisma 7 / adapter-pg for **all API storage** in private `dearby_api`. Catalog reads call the existing `public.catalog_public_snapshot()` RPC through Prisma; there is no SQLite or REST fallback. Supabase managed Auth remains separate from the API's own profiles/challenges/sessions.

## Before rollout (root-owned, not executed by this source change)

1. Merge the reviewed source and wait for GitHub Supabase migration `20260929150000_api_prisma` to apply. Supabase migrations are the only DDL pipeline. Never run Prisma `db push`, `migrate deploy`, `migrate reset` or generated DDL against production.
2. Provision `dearby_api_runtime` LOGIN outside Git, with NOINHERIT/NOSUPERUSER/NOCREATEDB/NOCREATEROLE/NOREPLICATION/NOBYPASSRLS and no owned objects. The migration gives only private schema CRUD/sequence usage and public catalog RPC execution. The private schema must not be added to Supabase Data API exposed schemas. Runtime validates role flags on startup.
3. Freeze API writes (including guest proxy writes); take a final online SQLite backup and verify integrity. Keep the old SQLite volume, image, ignored env, OTP_SECRET and GUEST_PROXY_SECRET unchanged for rollback. Collector/admin need no changes.
4. Supply DATABASE_URL using the session pooler (5432) or direct PostgreSQL and DATABASE_CA_FILE using the official CA **outside the repository**. Connection query options are discarded; adapter-pg explicitly verifies TLS certificates/hostname for every non-loopback host. CA is never baked into an image. Env files are ignored0600, secret parent directories0700.
5. Run the importer dry-run, then approved apply, against the frozen SQLite backup. Keep its count/hash report outside Git0600. Every source table is included, including offline catalog/history; JSON text/IDs/digests/epoch-ms/text timestamps and original ordered row IDs are preserved. Conflicting nonempty target fails closed; an identical target is a no-op. All row writes and verification use one transaction.

From `apps/dearby-api` (Node24, protected env loaded externally):

```sh
npm ci
npm run generate
npm run import:sqlite -- --source /absolute/protected/frozen.sqlite
# Only after root's cutover approval:
npm run import:sqlite -- --source /absolute/protected/frozen.sqlite --apply
```

If source `cards`, `receipts` or `guest_cards` is nonempty, preserving explicit ordinals requires a **temporary UPDATE grant on only their respective sequences**, granted and revoked by the operator, including on import failure. The regular runtime lacks UPDATE on sequences. Empty ordered tables skip `setval` and need no temporary grants (the observed production backup has only rate_limits3/history3). Sequence changes themselves are not transactional in PostgreSQL: a failed import can leave harmless gaps; rows still roll back and a retry advances counters to the imported maximum. Never retain import-only grants or replace a live target with an older snapshot.

```sql
-- Root only, only for nonempty ordered source tables:
GRANT UPDATE ON SEQUENCE dearby_api.cards_ordinal_seq,
  dearby_api.receipts_ordinal_seq,dearby_api.guest_cards_ordinal_seq TO dearby_api_runtime;
-- Finally, whether import succeeds or fails:
REVOKE UPDATE ON SEQUENCE dearby_api.cards_ordinal_seq,
  dearby_api.receipts_ordinal_seq,dearby_api.guest_cards_ordinal_seq FROM dearby_api_runtime;
```

## Container

Use a new reviewed image without overwriting the retained rollback image. Build from repo root. CI runs the real PostgreSQL tests; the image build runs generation/typecheck/lint/build without any database credentials. The allowlisted Docker context excludes env, keys, CA and pre-generated client. Prisma CLI and optional development tooling are pruned from the final image.

The operator sets host `DEARBY_DATABASE_CA_FILE` to the protected external CA; the readonly bind appears at `/run/secrets/database-ca.crt`. The container DATABASE_CA_FILE must use that **container** path. UID1000 must be able to read the mounted public certificate (0644 file with parent0700 on host is acceptable). Copy no CA or env into source/image layers.

```sh
# Runtime env exists locally with mode0600; preserve OTP/GUEST values.
docker compose -f apps/dearby-api/deploy/compose.yaml build
# Root final approval, frozen writes, completed import and rollback ready first:
docker compose -f apps/dearby-api/deploy/compose.yaml up -d --no-deps --force-recreate api
```

The existing SQLite volume remains mounted readonly for preservation; the runtime never opens it. Never `down -v`. Only API58865 is recreated; nginx/Vercel/collector/Supabase are unchanged. Validate catalog200, missing public card404, guest no-key403, existing sessions/digests and private counts/hashes before unfreezing writes. Runtime errors are sanitized; do not print env, connection URIs, Prisma error objects or SQL data.

Rollback: freeze writes, retain the PostgreSQL state and reselect the previous image/env/compose with the preserved SQLite volume. **After new PostgreSQL writes, simply switching back to the old SQLite snapshot would lose those writes**; keep writes frozen and reconcile/export first. This change provides forward import, not an unreviewed reverse importer. Do not restore an old backup over a live DB.

## Boundaries

- Auth/card/wallet/guest HTTP contracts and HMAC/token digest algorithms are unchanged. Server guest sessions have no expiry; Next retains the same400-day cookie token. Public card data is only the immutable selected projection; private profiles are never returned publicly.
- All writes use one transaction-scoped advisory lock across API processes, preserving SQLite's prior serialized-write behavior and count-based quotas. SMTP/network calls do not hold it. High write volume will queue; review finer locks if measured contention grows. Guest abuse counters remain process-local; persistent session/card caps and OTP quotas are DB-atomic.
- The previous `database.ts`, catalog SQLite store/collector, `catalog-supabase.ts` and migrations are retained as offline history/recovery tools. `npm run catalog:offline` is explicitly offline; none is selected by `server.ts`. SMTP remains unconfigured unless root assigns it; failures remain503.
- Shared nginx route policy is unchanged. External ingress issue65 and real public data availability are independent of this storage migration.
