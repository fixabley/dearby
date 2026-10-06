# Dearby public web

`apps/web` is a standalone Next.js app for `dearby.wid.io.kr`. It reads published public data from the API and stores shared cards in a server guest wallet. No authentication, card issuing, resume editing, PDF, Supabase client, calendar integration, or production seed data is included.

## Run and verify

Node 24, from `apps/web`:

```sh
npm ci
cp .env.example .env.local
# Fill server-only DEARBY_API_ORIGIN and GUEST_PROXY_SECRET outside Git.
npm run dev
npm run lint
npm run typecheck
npm test
npm run build
npm run test:e2e
```

`test:e2e` launches Next dev on localhost:3210 and a **test-only API double** on 127.0.0.1:4319. Its records and in-memory sessions are fixtures, not production persistence evidence. Screenshots are written under ignored `test-results/`. Production code has no fixture imports or fallback data. Run build separately; local E2E uses the development cookie. API persistence, actual HTTPS cookies and cross-network connectivity require deployment verification.

The opt-in `tests/real-api.integration.ts` additionally tests a real isolated local API. It requires explicit `WEB_TEST_ORIGIN`, `API_TEST_ORIGIN` (both localhost HTTP), and `API_TEST_OWNER_TOKEN`; card UUIDs ending 0002/0003 must be disposable test fixtures. It revokes card 0003 and must never target production. On 2026-09-29 this passed against root’s temporary DB API on port 58867 through Next on 3211.

## Routes and public boundary

- `/`: currently recruiting, verified, unexpired activities only; empty and failure states are distinct, with retry.
- `/activities/:id`: public catalog detail and safe official/application links. Opening a link never claims application completion. Calendar comparison is explicitly unavailable in the browser.
- `/cards/:id`: public Card projection: name/job/introduction, selected contacts and activity history. Revoked/missing API 404 becomes an unavailable-card screen. Invalid IDs never reach upstream. No private profile fetch.
- `/s/:shareId`: a shared card with the activities its sender chose (not proof of participation). One button saves via PUT `/api/guest/shares/:id`, then the list is re-read to confirm the cookie, the "이 브라우저에만 저장됐어요" notice replaces the button and focus moves to "저장한 명함 보기". Revoked/missing shares and malformed IDs show the unavailable-card screen. Known in-app browsers (`src/lib/in-app-browser.ts`) get an external-browser notice; saving is never blocked. The optional home-screen prompt after saving is added with the install PR (#87).
- `/saved`: this browser's public saved cards. No-cookie visits return an empty list without creating a database session.
- `/api/[...path]`: fixed method/path allowlist for catalog, single public card and guest wallet only. Private profiles, owner card lists, wallet/auth/publishing endpoints are inaccessible through this proxy Card shares (contract "명함 공유 기록과 게스트 공유 정보 저장"): GET `/api/shares/:id` (share must point at the returned card) and PUT `/api/guest/shares/:id` (same Origin/header/body/cookie rules as card saves; the response echoes the share ID). Creating shares (`POST /v1/cards/:id/shares`) is not proxied. The guest list adds `shares`, defaulting to empty against an API without share records. `src/lib/saved-groups.ts` groups saved cards by shared activity for `/saved`, in the order activities were first saved, with `활동 없음` last. The `/s/:shareId` and `/saved` screens come after the web layer split. Test share data in `tests/card-shares.json` is copied from API PR #84.

Zod validates public DTOs and strips extra fields before returning JSON. React escapes all text. External links permit only http(s), no credentials; contact schemes are explicit. API responses use no-store, private, and Vary: Cookie/Origin. Browser calls never include a Supabase key or upstream URL.

## App link association

`/.well-known/apple-app-site-association` and `/.well-known/assetlinks.json` declare shared-card links (`/s/*`) for the iOS and Android apps. Values come only from server env (`DEARBY_APPLE_TEAM_ID`, `DEARBY_IOS_BUNDLE_IDS`, `DEARBY_ANDROID_PACKAGE`, `DEARBY_ANDROID_CERT_SHA256`; see `.env.example`). Each file returns 404 until its values are set and valid, and is served as `application/json` without redirects, read per request. Android path scoping lives in the app's intent filter because Digital Asset Links has no paths.

## Guest protocol (root/API agreed 2026-09-29)

The API verifies UUID, database existence and non-revocation. A public card ID only identifies a publicly readable card; it never authenticates a visitor's wallet.

| Web request | API request | Browser result |
| --- | --- | --- |
| GET `/api/guest/cards` | GET `/v1/guest/cards` with cookie token; no-cookie skips API | `{items: Card[]}` |
| PUT `/api/guest/cards/:id` | PUT `/v1/guest/cards/:id` | `{cardId,status}`; first-save `guestToken` becomes cookie only |
| DELETE `/api/guest/cards/:id` | DELETE `/v1/guest/cards/:id` | 204 after API success |
| DELETE `/api/guest/session` | DELETE `/v1/guest/session` | Clear cookie after 204 or already-invalid 401 |

Next reconstructs `X-Guest-Proxy-Key` from `GUEST_PROXY_SECRET` (minimum 32 characters) and `X-Guest-Token` from its HttpOnly cookie. Browser-supplied token/key/Authorization/forwarding headers and bodies are not forwarded. Mutations require exact request Origin, `X-Dearby-Request: 1`, `Content-Type: application/json`, and `{}`. Unknown or invalid sessions remain 401; they are never silently replaced.

Production cookie: `__Host-dearby_guest`, HttpOnly, Secure, SameSite=Lax, Path=/, no Domain, Max-Age=34560000 (400 days). Successful guest requests renew the same token, never rotate/create a replacement. The API's token digest session has no automatic expiry. Browser storage lifetime is not unlimited, and the UI explains cookie loss and browser/device isolation. Development alone uses `dearby_guest_dev` without Secure and allows a localhost HTTP upstream.

Web Locks serialize mutations across tabs in the same browser origin. Unsupported browsers get an explicit save error rather than concurrent first-session creation. Save success requires both the PUT response and a GET confirming the saved card, catching rejected cookies. Removal preserves the prior list on server failure. Session reset is explicit and confirmed.

## Vercel handoff (root owns deployment)

- Root Directory: `apps/web`; Framework: Next.js; Node: 24.x; install `npm ci`; build `npm run build`; output default.
- Server-only production env: `DEARBY_API_ORIGIN=https://wid.io.kr` (origin only), `GUEST_PROXY_SECRET` matching the API. No `NEXT_PUBLIC_*` secrets. `.env*` and `.vercel` are gitignored; `.env.example` holds placeholders only.
- Production only accepts HTTPS API origins. Upstream timeout/redirect/schema/server failure returns an error, never an empty catalog. No caching of card/guest data. No secret values in application logs.
- [Vercel WAF rate limiting](https://vercel.com/docs/vercel-firewall/vercel-waf/rate-limiting) supports IP fixed-window rules on all plans; Hobby has one rate rule. Suggested deployment rule: `/api/guest/*`, IP, 60 requests per 60 seconds, 429. **Root reports the production rule has been published; this session did not configure it.** External 429 behavior still needs deployment verification. Counters are per-region, not a global persistent limit. API bounded rate/capacity controls are a separate layer.
- Root must verify external Vercel→API reachability, actual cookie flags, token/key absence from browser responses, separate browser lists and 404/401/failure UX. Published production data is currently reported as zero; never insert fixtures merely to make the site look populated. A real first-save smoke needs an authorized existing public card.
- Next server handling uses [Route Handlers](https://nextjs.org/docs/app/api-reference/file-conventions/route). No user data is embedded in the static page shell.

## Visual reference

Reuse `docs/design/native-visual-contract.md` and approved discovery/shared-card/detail images: white/teal, original logo, mint cards, simple dividers, history timeline, 52px CTA. `public/logo-teal.png` is an unchanged copy of the approved logo. Missing catalog imagery is an honest calendar placeholder; no stock/mock production activities. Two web navigation destinations reflect this task's scope, not the native app's full tab contract.
