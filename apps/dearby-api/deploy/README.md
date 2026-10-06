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

Root may add the `/docs` locations (now in [nginx/dearby-api/api.locations.conf](nginx/dearby-api/api.locations.conf)) inside the existing HTTPS server after reviewing/building the new API image. It redirects exact `/docs` to `/docs/`, proxies only GET/HEAD under `/docs/`, suppresses docs logs and strips Authorization, Cookie and both X-Guest authentication headers. Keep the API restrictions in the same file. No shared nginx file is changed by this source PR. Verify `/docs/`, JSON/YAML and static assets after `nginx -t` and root-owned reload. Local TLS checks do not establish external connectivity (tracked separately in issue65).

Documentation schemas are supplied only to the Swagger transform through route config. They are never installed as Fastify validation or response serialization schemas: handlers keep their existing Zod422 validation, private-field projection, partial wallet import and guest token contracts. Zod refinements not expressible in JSON Schema are described in the reference. Existing production Prisma rollout, canonical external compose/env paths and SQLite preservation constraints are recorded in [the Prisma handoff](../../../docs/context/api-prisma-postgres.md).

## Production nginx: api.dearby.wid.io.kr and public API routes — prepared, not applied

2026-10-06 user decisions: add a dedicated API host and tidy the production nginx at the next API deployment. Main (root) applies it; this repository only holds the finished files. The production file was read, never changed.

| File | Production path | Role |
| --- | --- | --- |
| [nginx/default.conf](nginx/default.conf) | `conf.d/default.conf` (replace) | Complete file. Ports 80 and 443 for `wid.io.kr` and `api.dearby.wid.io.kr`. The unused `dearby.wid.io.kr`/`dev.dearby.wid.io.kr` blocks are removed: nothing listens on upstream 3000/3001, their DNS points to Vercel and the certificate does not list them. Their port-80 requests now fall to the `wid.io.kr` default server. |
| [nginx/dearby-api/api.locations.conf](nginx/dearby-api/api.locations.conf) | `conf.d/dearby-api/api.locations.conf` | Public API surface included by both HTTPS hosts. It has the read-only `/docs/`, catalog GET/HEAD, public card **and share** GET/HEAD, and guest cards/**shares**/session. Everything else under `/v1` is 404, including `/v1/auth/*`, `/v1/profile`, the card collection, share creation, wallet and exchanges (to be opened separately after #89). |
| [nginx/dearby-api/handoff.locations.conf](nginx/dearby-api/handoff.locations.conf) | `conf.d/dearby-api/handoff.locations.conf` | **Stage 2.** POST `/v1/guest/handoffs` and `/v1/guest/handoffs/redeem` with the same guest header handling. Its `include` line in `api.locations.conf` stays commented out until the PR #110 API image is deployed. |
| [nginx/default.conf.diff](nginx/default.conf.diff) | — | Review diff: production file to stage 1, with the include expanded. |

The snippets live in `conf.d/dearby-api/`, so the `conf.d/*.conf` glob never loads them as standalone files; the existing `conf.d` mount already covers that directory. Compared with production, the only change to an existing route is that catalog now also strips the `X-Guest-*` headers; the API ignores them there. The previous single-purpose snippets (`nginx-catalog`, `nginx-web-guest`, `nginx-docs`, `nginx-api-domain`) are replaced by these files.

Apply in order:

1. **DNS:** add an `A` record `api.dearby.wid.io.kr` in Route 53 with the same public IP as `wid.io.kr`.
2. **Certificate SAN:** reissue `/etc/nginx/certs/fullchain.pem` with `wid.io.kr` and `api.dearby.wid.io.kr`. Until then only the new host fails TLS (name mismatch); `wid.io.kr` is unaffected.
3. **Stage 1:** deploy the API image containing the share routes (#84). Copy the files above with the handoff include still commented, then run `nginx -t` and reload. Without the matching API image, the new share paths only reach Fastify's 404.
4. **Stage 2:** after the #110 API image is deployed, uncomment the handoff `include` line, then run `nginx -t` and reload.
5. **Consumers:** set the web (Vercel) and app `DEARBY_API_ORIGIN` to `https://api.dearby.wid.io.kr` and verify catalog and guest save through the web. Keep `wid.io.kr` routes until a separate decision removes them.

**Certificate risk (checked read-only 2026-10-06):** the current certificate has only `DNS:wid.io.kr` and expires **2026-12-28 10:55 UTC**. The renewal config is `authenticator = manual` with `pref_challs = dns-01`, so the existing `certbot renew --non-interactive` loop cannot answer the challenge and will not renew it. Both hosts fail TLS at expiry unless root renews it by hand or moves to an automatic method, for example the Route 53 DNS plugin with a credential limited to this zone, which works without inbound reachability. HTTP-01 webroot would also need the external reachability below.

**External reachability:** until [#65](https://github.com/fixabley/dearby/issues/65) is resolved, the new host is no more reachable from outside than `wid.io.kr` (same public IP and router path). Local `nginx -t` and routing checks prove only the configuration.

### Local verification (temporary containers only)

`nginx:stable-alpine` loaded the files, with a self-signed certificate for both names and a stub upstream aliased as `host.docker.internal`; production and the API were never contacted. `nginx -t` succeeded for stage 1 and stage 2. Status codes are nginx routing results; `200` means the request reached the upstream stub, where the API still enforces methods, the proxy secret and tokens.

| Request | wid.io.kr stage 1 | api stage 1 | wid.io.kr stage 2 | api stage 2 |
| --- | --- | --- | --- | --- |
| `GET`/`HEAD /v1/catalog` | 200 | 200 | 200 | 200 |
| `POST /v1/catalog` | 405 | 405 | 405 | 405 |
| `GET /v1/cards/:id` | 200 | 200 | 200 | 200 |
| `DELETE /v1/cards/:id` | 405 | 405 | 405 | 405 |
| `GET`/`HEAD /v1/shares/:id` | 200 | 200 | 200 | 200 |
| `POST /v1/shares/:id` | 405 | 405 | 405 | 405 |
| `POST /v1/cards/:id/shares` | 404 | 404 | 404 | 404 |
| `GET /v1/guest/cards`, `PUT`/`DELETE /v1/guest/cards/:id`, `DELETE /v1/guest/session`, `PUT /v1/guest/shares/:id` | 200 | 200 | 200 | 200 |
| `POST /v1/guest/cards` | 405 | 405 | 405 | 405 |
| `POST /v1/guest/handoffs`, `POST /v1/guest/handoffs/redeem` | 404 | 404 | 200 | 200 |
| `GET /v1/guest/handoffs` | 404 | 404 | 405 | 405 |
| `POST /v1/auth/challenges`, `POST /v1/auth/sessions`, `GET /v1/profile`, `GET`/`POST /v1/cards`, `GET /v1/wallet`, `POST /v1/exchanges`, `GET /v1` | 404 | 404 | 404 | 404 |
| `GET /docs` | 308 | 308 | 308 | 308 |
| `GET /docs/json` | 200 | 200 | 200 | 200 |
| `POST /docs/json` | 405 | 405 | 405 | 405 |
| `GET /` | 200 | 404 | 200 | 404 |

Port 80: `wid.io.kr` and `api.dearby.wid.io.kr` redirect 308 to their own https host, keeping the query string, and serve the acme file with 200. The removed `dearby`/`dev.dearby` names now get 308 to `https://wid.io.kr` on port 80 and the `wid.io.kr` default on 443.

Headers: the upstream never received Authorization or Cookie. Catalog, public share and `/docs/` received no `X-Guest-*`, while guest shares and handoff redeem received `X-Guest-Proxy-Key` and `X-Guest-Token`.
