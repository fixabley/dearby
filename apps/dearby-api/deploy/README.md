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

## Read-only Swagger reference

- `/docs/`: Swagger UI, local static assets, no external validator, no Try it out or authorization controls. Authorization is not persisted. Do not enter owner tokens or the server-only guest proxy key in a browser.
- `/docs/json`: OpenAPI 3.0.3 download; `/docs/yaml`: equivalent YAML. Generated from route metadata/shared Zod contracts at runtime, with no manually maintained spec copy.
- The reference lists the entire API. Public ingress currently exposes catalog, public card reads and trusted server guest paths only; owner/auth/private profile and card collection remain404 there. A permitted direct/local server connection is required for those operations. The docs do not grant new API access.

Download from an already running permitted API (no credentials needed):

```sh
curl --fail --silent --show-error http://127.0.0.1:58865/docs/json --output /tmp/dearby-openapi.json
```

Root may add [nginx-docs.location.conf](nginx-docs.location.conf) inside the existing HTTPS server after reviewing/building the new API image. It redirects exact `/docs` to `/docs/`, proxies only GET/HEAD under `/docs/`, suppresses docs logs and strips Authorization, Cookie and both X-Guest authentication headers. Keep [nginx-web-guest.location.conf](nginx-web-guest.location.conf)'s API restrictions. No shared nginx file is changed by this source PR. Verify `/docs/`, JSON/YAML and static assets after `nginx -t` and root-owned reload. Local TLS checks do not establish external connectivity (tracked separately in issue65).

Documentation schemas are supplied only to the Swagger transform through route config. They are never installed as Fastify validation or response serialization schemas: handlers keep their existing Zod422 validation, private-field projection, partial wallet import and guest token contracts. Zod refinements not expressible in JSON Schema are described in the reference. Existing production Prisma rollout, canonical external compose/env paths and SQLite preservation constraints are recorded in [the Prisma handoff](../../../docs/context/api-prisma-postgres.md).

## Dedicated API host api.dearby.wid.io.kr — prepared, not applied

2026-10-06 user decision. [nginx-api-domain.server.conf](nginx-api-domain.server.conf) is the review copy for root. It adds port 80 (acme-challenge passthrough, everything else 308 to https) and 443 for `api.dearby.wid.io.kr`. The 443 server `include`s the two existing snippets ([nginx-web-guest.location.conf](nginx-web-guest.location.conf), [nginx-docs.location.conf](nginx-docs.location.conf)), so the public surface stays exactly what wid.io.kr exposes today: catalog GET/HEAD, public card GET/HEAD, guest proxy paths and read-only `/docs/`. Everything else is 404, with the same upstream 58865 and header stripping. A future route added to a snippet (for example the share routes) reaches both hosts. The wid.io.kr locations stay in place during the transition; remove them only in a separate, later decision. No CORS change is needed: the web calls the API from its Next server, and apps are not browsers.

Root applies these steps in order. This PR does not change nginx, DNS or certificates.

1. **DNS:** add an `A` record `api.dearby.wid.io.kr` in Route 53 with the same public IP as `wid.io.kr`.
2. **Certificate SAN:** reissue the certificate with both `wid.io.kr` and `api.dearby.wid.io.kr`. nginx uses the http-level `/etc/nginx/certs/fullchain.pem`, so the server block needs no certificate lines once the SAN exists. Applying the server block before the SAN exists makes clients see a name-mismatch error.
3. **nginx:** copy the server file into `conf.d/`, and the two snippets into `conf.d/dearby-api/`. The snippets must not sit directly in `conf.d/`, because the `*.conf` glob would load them as standalone files. Then run `nginx -t` and reload.
4. **Consumers:** switch the web (Vercel) and app `DEARBY_API_ORIGIN` / API base setting to `https://api.dearby.wid.io.kr`, then verify `/v1/catalog` and a guest save through the web.

**Certificate risk (checked read-only 2026-10-06):** the current certificate has only `DNS:wid.io.kr` and expires **2026-12-28 10:55 UTC**. The renewal config is `authenticator = manual` with `pref_challs = dns-01`. The existing `certbot renew --non-interactive` loop therefore cannot answer the challenge, and the certificate will not renew by itself. Both hosts will fail TLS at expiry unless root renews it by hand or moves to an automatic method. For example, the Route 53 DNS plugin with a credential limited to this zone works without inbound reachability. HTTP-01 webroot would also need the external reachability below.

**External reachability:** until [#65](https://github.com/fixabley/dearby/issues/65) is resolved, this host is no more reachable from outside than wid.io.kr. The same public IP/router path applies, so DNS and server-block success only proves local TLS and routing.

Local check performed for this PR, temporary containers only: `nginx:stable-alpine` with a self-signed test certificate and a stub upstream aliased as `host.docker.internal` (no production API contact). `nginx -t` succeeded. Routing matched expectations:
- port 80: 308 to https, with the query string kept; acme file 200;
- `/v1/catalog` GET/HEAD 200, POST 405;
- public card GET 200, DELETE 405;
- `/v1/cards`, `/v1/profile`, `/v1/auth/*`, `/v1` and `/` all 404;
- guest GET/PUT/DELETE 200, POST 405;
- `/docs` 308, `/docs/json` 200, POST 405;
- the upstream received no Authorization or Cookie header, and `/docs/` received no `X-Guest-Proxy-Key`. The existing catalog location strips only Authorization and Cookie; the catalog route ignores guest headers.
