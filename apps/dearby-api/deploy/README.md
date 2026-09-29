# Main API deployment

Base: `b3094f9`. Only the Supabase catalog reader from `8984fbf` is selected; no admin, collector, presentation or mobile source is included. Auth/cards/wallet keep their SQLite contract. This deployment reads the existing Supabase public RPC with the anon key; it never runs migrations, seeds, resets or collection against Supabase.

## Runtime (from repository root)

Create `apps/dearby-api/deploy/.env.runtime` privately with mode 0600, using `.env.example` for variable names. Generate a new OTP_SECRET; set CATALOG_BACKEND to `supabase`, SUPABASE_URL to `http://host.docker.internal:54321`, and use only the operator-approved SUPABASE_ANON_KEY. Never use the service-role key. Do not print `docker compose config`, inspect container environment, or commit the actual env. SMTP is unassigned; challenge requests deliberately return 503.

```sh
chmod 600 apps/dearby-api/deploy/.env.runtime
docker compose -f apps/dearby-api/deploy/compose.yaml build
docker compose -f apps/dearby-api/deploy/compose.yaml up -d
docker compose -f apps/dearby-api/deploy/compose.yaml ps
curl --fail --silent http://127.0.0.1:58865/v1/catalog
docker exec nginx-nginx-1 sh -c 'wget -q -O /dev/null http://host.docker.internal:58865/v1/catalog && echo OK'
```

The build executes lint, typecheck, HTTP/storage/authorization tests and TypeScript compilation. The image has a pinned Node 24 base digest, no build tools in its final stage, and an allowlist-only build context excluding env/private keys. The runtime runs as UID 1000, uses a read-only root filesystem, drops capabilities, and persists only its own `dearby-api-main_data` volume. It binds `127.0.0.1:58865`, restarts unless stopped, and requires Docker/host availability. Health means the public catalog RPC responds; Docker does not restart an unhealthy but running process automatically. No uptime monitor, backup schedule, sleep prevention or Supabase restart policy is installed here.

The named volume includes private auth/profile state. Preserve it and the independent OTP_SECRET on upgrades; never run `down -v`. Recreate only this service after rebuilding. A rollback needs a retained prior image and a compatible SQLite schema; this initial deployment has no prior deployed API image. Operational backup/restore remains required before enabling private write routes.

## Root-only nginx integration

Public origin is **https://wid.io.kr**, path **/v1/catalog** (no `/api` prefix). After root confirms a valid apex certificate, insert `nginx-catalog.location.conf` **inside the existing wid.io.kr HTTPS server** in `/Users/jominjun/nginx/nginx/conf.d/default.conf`. It is a location snippet, not a standalone `conf.d` server. Preserve all existing server blocks and the apex `location /`.

Root should back up that file, apply only the three new locations, then run:

```sh
docker exec nginx-nginx-1 nginx -t
# Only after successful validation; reload existing nginx, do not restart other services.
docker exec nginx-nginx-1 nginx -s reload
curl --fail --silent https://wid.io.kr/v1/catalog
curl --head https://wid.io.kr/v1/catalog
curl --silent --output /dev/null --write-out '%{http_code}\n' -X POST https://wid.io.kr/v1/catalog
curl --silent --output /dev/null --write-out '%{http_code}\n' https://wid.io.kr/v1/profile
```

Expect 200, 200, 405, 404. Roll back by restoring root's backup, `nginx -t`, then reload. DNS, certificate issuance, shared file edits and shared nginx reload are root-owned and are not executed by this checkout.

Only GET/HEAD catalog reads are publicly proxied. Authorization/cookies are stripped and forwarding headers overwritten, while Fastify still distrusts all proxy headers. Auth quotas therefore aggregate by proxy IP if those routes are exposed later: SMTP assignment and a separately reviewed trusted ingress configuration must precede that exposure. Access logs are disabled for `/v1` and location error logs discarded to prevent URL/query credentials from entering nginx logs; this trades per-request diagnostics for privacy. Use health/status monitoring instead. Never put credentials in URLs.

## Data and failure semantics

The RPC publishes only `publication_status=published`; draft/hidden items never leave it. API schema validation rejects unexpected fields, recalculates freshness and deadlines, times out after 8 seconds, refuses redirects and returns sanitized 503 on upstream failure. It does not fall back to SQLite or label a failure as an empty successful catalog. Only the known Docker host and loopback may use HTTP; remote Supabase requires HTTPS.

The bundled official snapshot is historical and available only for an explicit isolated SQLite import. It is not imported into existing Supabase or used to conceal an empty public catalog. No mail delivery, live collection, publication or full-service readiness is implied by successful deployment.

## Web guest deployment (root integration required)

The web guest extension adds migration `003_guest_cards.sql`; it is additive and does not alter existing owner sessions/cards. Guest sessions have no automatic expiry. See [the web guest contract](../../../../shared/contracts/native-v1.md#웹-비로그인-명함-저장--2026-09-29-승인) for payloads, isolation, cookie renewal and capacity rules. Next owns `__Host-dearby_guest`: Secure/HttpOnly/SameSite=Lax/Path=/, no Domain, 400-day Max-Age renewed with the **same** token. Browser storage limits and cookie deletion can still lose access; the API cannot recover a lost guest token from a card ID.

Root must generate and privately set a new random `GUEST_PROXY_SECRET` (at least 32 characters) in the API's ignored `.env.runtime` and the Vercel **server-only** environment. The same environment variable name is used in both services; it is sent only as `X-Guest-Proxy-Key` from the trusted Next server. The API fails closed with 503 while it is unconfigured. No production key is generated by this implementation. Changing the proxy secret does not change guest token digests, but both services must be coordinated during rotation. Do not use a NEXT_PUBLIC-prefixed variable or bake the secret into image builds.

After PR integration, root should preserve the existing SQLite volume and OTP_SECRET, take a consistent SQLite backup, build/deploy only the API service and apply `nginx-web-guest.location.conf` **in place of** the old catalog-only snippet. Retaining the old `location ^~ /v1/` would prevent the new regex routes from matching. The proposed snippet permits public card reads and narrow guest paths, strips owner Authorization/Cookie, and leaves profile/auth/member-wallet/card-creation unavailable. The API separately enforces exact guest methods and the proxy key; nginx does not grant access by header presence alone. Run `nginx -t` before reload. This checkout does not edit or reload the shared nginx.

Do not deploy the review image or fixture DB. The separate `dearby-api-guest:review` image is only for isolated tests; production is rebuilt from integrated main using the normal Compose procedure. The old application can ignore the additive tables on rollback, but never delete the guest tables/volume to roll back: that would discard guest saves. A consistent pre-migration backup and restore procedure remain operator responsibilities.

API limits are single-process, fixed one-minute memory counters: 1,200 authorized-proxy guest requests, 120 requests per valid guest token, and 60 successful new sessions. They reset on restart; they are independent of the persistent 100-card/session and 10,000-session DB limits. With unlimited session lifetime, reaching 10,000 sessions blocks new guest saves until an operator resolves capacity or users explicitly delete sessions. Existing session reads/saves/deletes keep working. Never silently evict old sessions. Next must separately enforce exact Origin checks, a JSON/custom-header CSRF condition, no shared caching, first-save serialization and abuse limits based on its trusted client context. Fastify `trustProxy` stays false.

Real production public card data can be absent. A passing test using virtual public cards does not publish a real profile or prove production content availability. PDF support is not implemented by this change.
