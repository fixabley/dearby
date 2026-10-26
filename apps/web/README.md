# Dearby public web

`apps/web` is a standalone Next.js app for `dearby.wid.io.kr`. It reads published public data from the API, stores received public cards in a server guest wallet, and since 2026-10-26 signs people in with the same passkey·Apple·Google accounts as the apps (contract native-v1.md "웹 로그인"; see "Web sign-in" below). No card issuing, resume editing, PDF, calendar integration, or production seed data is included.

## Run and verify

Node 24, from `apps/web`:

```sh
npm ci
cp .env.example .env.local
# Fill server-only DEARBY_API_ORIGIN and GUEST_PROXY_SECRET outside Git.
npm run dev
npm run lint
npm run typecheck
npm run build
```

All automated tests were removed on 2026-10-06 at the user's request; they are being rebuilt one at a time with the user. API persistence, actual HTTPS cookies and cross-network connectivity require deployment verification.

## Routes and public boundary

- `/`: program accordion over `GET /v1/discovery/programs` (contract "발견 프로그램 묶음 — 2026-10-18"): only programs with open activities (expanding a program loads `GET /v1/activities?programId=&open=true` and shows every returned row; since 2026-10-19 the server alone decides what is open, and date-only recruitment windows get 모집 중/예정/마감 labels by Seoul day), header = organization · program name · `열린 활동 N개` · nearest `M월 d일 (요일) 마감`. The query lives in the URL (`/?q=`) and matches program/organization/activity names; the API pages by id with `nextCursor` and a "더 보기" button appends the next page. Expanding a header (a native `<button aria-expanded>`) loads `GET /v1/activities?programId=` and shows the open activities as the usual cards (status from the `application` period); programs with one open activity or an activity-name match start expanded. No participation filter on the web. The search is a stacked-label `type="search"` field with a magnifier and a clear button (Enter submits; clearing an applied query returns to `/`), and `마감 숨기기` is a label + switch row under it where the whole label toggles (contract "웹에도 같은 규칙", 2026-10-20). Empty (no open programs vs. no match for `q`) and failure states are distinct `CenteredMessage` screens, with retry.
- `/activities/:id`: numeric activity id, read from `GET /v1/activities/:id`; 404 shows not-found. A "대상" section renders the activity's `eligibility` (contract "대상자 조건 — 2026-10-18") as Korean chips (`나이 19~34세`, `재학상태 재학`, `any` groups joined with `또는`); omitted when the activity has none. Apply/official links open `officialUrl` (https only). Opening a link never claims application completion. Calendar comparison is explicitly unavailable in the browser.
- `/cards/:id`: public Card projection: name/job/introduction, selected contacts and activity history. Revoked/missing API 404 becomes an unavailable-card screen. Invalid IDs never reach upstream. No private profile fetch.
  - 2026-10-18 명함 표현(contract "명함 표현"): 한 장의 카드 면(흰 바탕·모서리 20·구분선 테두리·최대 480px 가운데)에 가운데 머리(이니셜 아바타 56·이름·`job`을 ` · `로 나눈 직무 칩·가장 최근 승인 이력의 조직), 소개 마크다운, 종류 아이콘이 있는 연락처 행(행 전체가 링크), 활동 이력 행(기간·조직 · 프로그램 · 활동·역할·인증 배지)을 담는다. 저장 버튼과 쿠키 안내는 카드 아래에 두고 `/saved`는 바꾸지 않는다.
  - 2026-10-19 명함 교환(contract "명함 교환"): `?activity=<n>`가 있으면 카드 위에 `<프로그램> <활동>에서 받은 명함`(공개 `GET /v1/activities/:id`로 이름을 찾고, 없으면 줄을 생략), 카드 아래 `Dearby 앱에 저장`(`dearby://cards/<id>?activity=<n>` 커스텀 스킴 링크, 앱이 없으면 아무 일도 없다는 안내)과 `브라우저에 저장`(비로그인 저장, `{activityId}`를 함께 보냄).
- `/saved`: this browser's public saved cards. No-cookie visits return an empty list without creating a database session. 2026-10-19부터 `GET /v1/guest/cards`는 GuestItem(`{card, context, savedAt, expiresAt}`)이며 행에 프로그램 칩과 `N일 뒤 사라져요`(당일 `오늘 사라져요`) 배지를 보인다. 웹 저장은 맞교환이 없어 저장한 날부터 30일 뒤 서버가 회수한다.
- `/settings`: 설정 (contract "설정", web rules in "웹 로그인 › 웹 화면"): signed out, a 로그인 CTA and 정보; signed in without consent, the 동의하기 notice; signed in, 계정 (가입일·로그인 수단·로그아웃·계정 삭제) and 패스키 (list, 삭제, 패스키 추가).
- `/api/auth/*`: sign-in starts and completions (`passkey/options`, `passkey`, `apple/nonce`, `apple`, `apple/register`, `google/nonce`, `google`, `google/register`), `refresh`, `logout`, and `GET session`. See "Web sign-in".
- `/api/[...path]`: fixed method/path allowlist for activity/program search (only validated `q`, `cursor`, `size`, `programId`, `open`, `openOnly`, `participationType` are forwarded; invalid values return 422; the web never sends `tagIds` — tags match through `q`, contract "태그 칩 줄 제거" 2026-10-19 밤), a single activity by numeric id, the public program logo image `programs/:id/logo?v=` (binary passthrough with the API's `Content-Type`, `Cache-Control`, `ETag`; only PNG/JPEG/WebP are served; the discovery card maps `ProgramSummary.logoUrl` to this path and falls back to the initial avatar on null or load error), single public card and guest wallet, plus the signed-in account routes listed under "Web sign-in" (`src/lib/account-proxy.ts`, Bearer from the session cookie). Everything else is inaccessible through this proxy.

Zod validates public DTOs and strips extra fields before returning JSON. React escapes all text. External links permit only http(s), no credentials; contact schemes are explicit. API responses use no-store, private, and Vary: Cookie/Origin, except `programs/:id/logo`, which carries the API's `public, max-age=31536000, immutable` (the `v` query busts it). Browser calls never include an upstream URL or server secret.

## Web sign-in (2026-10-26)

Contract native-v1.md "웹 로그인" (server PR #433) and its "웹 화면" subsection (this PR). Code: `src/lib/session.ts` (cookies, Bearer call, refresh), `src/lib/auth-routes.ts` (`/api/auth/*`), `src/lib/account-proxy.ts` (Bearer branch of `/api/*`), `src/features/auth` (provider, sheets, WebAuthn/Apple/Google browser glue), `src/components/settings.tsx`.

- Tokens: the API's TokenPair goes into `__Host-dearby_access` (Max-Age 60, the token's own lifetime) and `__Host-dearby_refresh` (90 days), HttpOnly·Secure·SameSite=Lax·Path=/; development uses `dearby_access_dev`/`dearby_refresh_dev` without Secure. No token reaches a response body or browser JS. Account routes read the access cookie as `Authorization: Bearer`.
- Refresh: the proxy never refreshes. Without an access cookie, or when the API says 401, it answers 401 `SIGNED_OUT`; the browser (`features/auth/api/auth-client.ts` `account()`) calls `POST /api/auth/refresh` once under the Web Lock `dearby-auth-refresh` and retries. The refresh route skips the rotation when the access cookie's `exp` still has more than 10 s (another tab just rotated), clears both cookies on a refused refresh, and keeps them on network/5xx (502).
- Mutations (all `POST/PATCH/DELETE` under `/api/auth/*` and the account routes) need the exact request `Origin`, `X-Dearby-Request: 1` and `Content-Type: application/json`, like guest mutations. Bodies are validated with zod per route; responses are reduced to the fields the pages use (`GET profile` returns only `version`, `consent`, `consentRequired`).
- Sign-in sheet: Apple JS popup (`NEXT_PUBLIC_APPLE_SERVICES_ID`; `NEXT_PUBLIC_APPLE_REDIRECT_URI` = the API's `APPLE_CALLBACK_URL` so `authorizationCode` converts, otherwise the page origin and no code), Google Identity Services `renderButton` (`NEXT_PUBLIC_GOOGLE_CLIENT_ID`, the same value as the API's `GOOGLE_WEB_CLIENT_ID`), passkeys through standard WebAuthn JSON (`parse*OptionsFromJSON`/`toJSON`, manual base64url for older browsers). Empty ids or a 503 from the nonce route hide that button. Nonces are fetched when the sheet opens and after every attempt so the popups start inside the click.
- Consent gate, guest import question, `/settings` and the `signed_in` GA user property follow the contract's "웹 화면" rules. The RP ID is `wid.io.kr`, so passkeys do not work on `localhost`; the API's `WEBAUTHN_ALLOWED_ORIGINS` must include the web origin.
- Checked locally on 2026-10-26 with `next dev` against a stub API (`node` script answering TokenPair/profile/me): passkey completion sets both cookies and returns only `{user}`; `session` reports `consentRequired`; `me` strips unknown fields; the proxy forwards Bearer and answers 401 `SIGNED_OUT` for an expired token without refreshing; `refresh` rotates only when the access cookie is expired, returns `{refreshed:false}` otherwise, clears cookies when refused; `logout`, `DELETE /api/me`, wrong `Origin` (403), bad bodies (422), a 503 nonce passthrough and import-guest with/without the guest cookie. Not run: real Apple/Google/passkey sign-ins (no Services ID, Google client or RP-matching origin locally).

## Home-screen web app

`src/app/manifest.webmanifest/route.ts` (`start_url` `/saved`, standalone, built per request; see below) and `src/app/apple-icon.png` make the site installable. Icons are resized copies of the iOS app icon. There is deliberately no service worker, so saved cards are never cached on the device. On 2026-10-06 Chromium 153 reported no installability errors without one (that test has since been removed); a real Android Chrome install prompt is not yet verified.

iOS home-screen cookie separation, checked 2026-10-06 on the iOS 26.5 simulator (iPhone Air, Xcode 27.0) with a throwaway local page using server-set `HttpOnly; SameSite=Lax` cookies like the guest cookie, Safari → Share → 홈 화면에 추가 with 웹 앱으로 열기 on:
- Not copied on add: Safari held `safari=1`, but the home-screen app's first request had no cookies.
- Separate afterwards: a cookie set for the home-screen app did not appear in Safari, which still sent only `safari=1`.
- So cards saved in Safari do not appear in the installed app, and vice versa; the iOS install guidance must say `홈 화면 앱은 Safari와 따로 저장돼요`. No session merging is built. Not checked: a real device, HTTPS with `__Host-` cookies, and whether the app's own cookie survives relaunch (the probe could not reload reliably inside the app). `src/lib/install.ts` detects known in-app browsers by user agent (best effort, never blocks saving) and exposes the install prompt/installed state; the screens that show them come after the web layer split.

## Home-screen session handoff

Contract "홈 화면 세션 잇기": the iOS home-screen app starts with an empty cookie jar (see above), so `/manifest.webmanifest` is built per request (`private, no-store`, `Vary: Cookie`). With a valid guest cookie and at least one saved card it asks the API (`POST /v1/guest/handoffs`) for a one-time code and sets `start_url` to `/saved?handoff=<code>`; otherwise `/saved`. `src/proxy.ts` handles `/saved?handoff=`: only without a guest cookie it redeems the code (`POST /v1/guest/handoffs/redeem`) and sets the same session cookie, and always 303-redirects to `/saved` without the query (`Referrer-Policy: no-referrer`, the code is never logged by this app). Both share `src/lib/guest-upstream.ts` with the API proxy. Checked 2026-10-06 against the test API double on the iOS 26.5 simulator: a card saved in Safari appeared on the first launch of the home-screen app. Deployment note: platform request logs may still record the query and need redaction there.

## Guest protocol (root/API agreed 2026-09-29)

The API verifies UUID, database existence and non-revocation. A public card ID only identifies a publicly readable card; it never authenticates a visitor's wallet.

| Web request | API request | Browser result |
| --- | --- | --- |
| GET `/api/guest/cards` | GET `/v1/guest/cards` with cookie token; no-cookie skips API | `{items: Card[]}` |
| PUT `/api/guest/cards/:id` | PUT `/v1/guest/cards/:id` | body `{}` or `{activityId}` (the only forwarded body); `{cardId,status}`; first-save `guestToken` becomes cookie only |
| DELETE `/api/guest/cards/:id` | DELETE `/v1/guest/cards/:id` | 204 after API success |
| DELETE `/api/guest/session` | DELETE `/v1/guest/session` | Clear cookie after 204 or already-invalid 401 |

Next reconstructs `X-Guest-Proxy-Key` from `GUEST_PROXY_SECRET` (minimum 32 characters) and `X-Guest-Token` from its HttpOnly cookie. Browser-supplied token/key/Authorization/forwarding headers and bodies are not forwarded. Mutations require exact request Origin, `X-Dearby-Request: 1`, `Content-Type: application/json`, and `{}`. Unknown or invalid sessions remain 401; they are never silently replaced.

Production cookie: `__Host-dearby_guest`, HttpOnly, Secure, SameSite=Lax, Path=/, no Domain, Max-Age=34560000 (400 days). Successful guest requests renew the same token, never rotate/create a replacement. The API's token digest session has no automatic expiry. Browser storage lifetime is not unlimited, and the UI explains cookie loss and browser/device isolation. Development alone uses `dearby_guest_dev` without Secure and allows a localhost HTTP upstream.

Web Locks serialize mutations across tabs in the same browser origin. Unsupported browsers get an explicit save error rather than concurrent first-session creation. Save success requires both the PUT response and a GET confirming the saved card, catching rejected cookies. Removal preserves the prior list on server failure. Session reset is explicit and confirmed.

## Vercel handoff (root owns deployment)

- 2026-10-14: the Vercel project `dearby-web` (Root Directory `apps/web`) is connected to this repository, so pushes deploy (main → production, other branches → preview). `vercel.json` `ignoreCommand` skips a build when nothing under `apps/web` changed since the last successful deployment of that branch (`VERCEL_GIT_PREVIOUS_SHA`, falling back to the previous commit). If that commit is missing from the shallow clone, `git diff` fails and the build runs, so a web change is never skipped.

- Root Directory: `apps/web`; Framework: Next.js; Node: 24.x; install `npm ci`; build `npm run build`; output default.
- Server-only production env: `DEARBY_API_ORIGIN` (API origin only, e.g. `https://api.example.test`), `GUEST_PROXY_SECRET` matching the API. Production values live only in the Vercel project environment (and GitHub Actions Variables/Secrets where needed), never in Git. No `NEXT_PUBLIC_*` secrets. Public sign-in ids (2026-10-26): `NEXT_PUBLIC_APPLE_SERVICES_ID`, `NEXT_PUBLIC_APPLE_REDIRECT_URI`, `NEXT_PUBLIC_GOOGLE_CLIENT_ID` — build-time values, so set them for Production (and Preview if wanted) and redeploy; empty hides the button. Optional public `NEXT_PUBLIC_GA_MEASUREMENT_ID` (GA4 web stream `G-…`, contract analytics-v1.md "웹"; read only when `VERCEL_ENV=production`, so set it for Production and redeploy). `.env*` and `.vercel` are gitignored; `.env.example` holds placeholders only.
- Production only accepts HTTPS API origins. Upstream timeout/redirect/schema/server failure returns an error, never an empty activity list. No caching of card/guest data. No secret values in application logs.
- [Vercel WAF rate limiting](https://vercel.com/docs/vercel-firewall/vercel-waf/rate-limiting) supports IP fixed-window rules on all plans; Hobby has one rate rule. Suggested deployment rule: `/api/guest/*`, IP, 60 requests per 60 seconds, 429. **Root reports the production rule has been published; this session did not configure it.** External 429 behavior still needs deployment verification. Counters are per-region, not a global persistent limit. API bounded rate/capacity controls are a separate layer.
- Root must verify external Vercel→API reachability, actual cookie flags, token/key absence from browser responses, separate browser lists and 404/401/failure UX. Published production data is currently reported as zero; never insert fixtures merely to make the site look populated. A real first-save smoke needs an authorized existing public card.
- Next server handling uses [Route Handlers](https://nextjs.org/docs/app/api-reference/file-conventions/route). No user data is embedded in the static page shell.

## Visual reference

Reuse `docs/design/native-visual-contract.md` and approved discovery/card/detail images: white/teal, original logo, mint cards, simple dividers, history timeline, 52px CTA. The brand mark is the inline `shared/ui/brand-symbol.tsx` (copy of `shared/assets/brand/symbol.svg`, stroke `currentColor` → `var(--teal)`, so it is the lighter teal in dark mode; 2026-10-19 밤 it replaced the `logo-teal.png` wordmark in the header and install prompt). The activity detail shows no header image (contract "활동 상세 이미지 섹션 제거", 2026-10-19 밤; `imageUrl` stays in the model, unused); no stock/mock production activities. Two web navigation destinations reflect this task's scope, not the native app's full tab contract.

2026-10-20 contract "웹에도 같은 규칙": every single-message screen (discovery empty/no results/error, saved empty/invalid session/error, missing card, activity or page not found, `error.tsx`) is `shared/ui/centered-message.tsx` — 64px `--teal-fill` circle with a 28px white glyph (`--danger` circle for errors), 22/700 title, 17px secondary message, full-width primary CTA and a text secondary action, centred without a card. Boxes stay only where they separate groups (program cards, activity cards, card faces, saved rows); the schedule rows, cookie note, install offer and error text sit on the background. `shared/ui/icon.tsx` is the one inline SVG set named after the mobile meaning table with three sizes (`inline` 1em, `row` 24, `state` 28) and two tones (secondary, teal). Tokens: `--teal-fill` #007F80 with `--on-teal` white in both schemes for filled surfaces (buttons, count badge, 모집 중 chip, switch); `--teal` (#3FB3B4 in dark) is the text/border accent only. Everything clickable is a `<button>`/`<a>` of at least 44px covering its padded row.

2026-10-22 contract "웹 리디자인 — iOS 앱과 같은 표현": Tailwind CSS v4 (`postcss.config.mjs`, tokens in `src/app/globals.css` `@theme`: iOS grouped background/surface/fill/separator/label colours with a `prefers-color-scheme` dark set, teal accent `--teal` and fill `--teal-fill`, iOS text styles `text-large-title`…`text-caption2`, radii `rounded-card` 20 / `rounded-control` 10) and shadcn/ui copies in `src/shared/ui` (`components.json`; Button, Input, Switch, Card, Badge, Separator, Skeleton, Sheet, Dialog restyled as iOS controls; `cn` in `src/shared/lib/utils.ts` knows the custom text/radius tokens). The shell is an iOS navigation bar (sticky, blurred, hairline after scrolling) whose centre shows the page's `LargeTitle` once it scrolls under the bar (`shared/ui/nav-title.tsx`, IntersectionObserver). Detail pages get a back link in the bar; the activity detail and a public card keep their CTAs in a sticky `BottomBar`. Sections are `InsetGroup`s; the install offer is a non-modal bottom sheet; the saved-session reset confirm is an iOS-style alert Dialog instead of `window.confirm`. CSS modules and screen classes are gone; only `.markdown` (card introduction) and `.legal` (privacy/terms) remain as content styles.

## 프로필·명함 v2 (2026-10-16)

- 명함 `introduction`은 서버가 템플릿을 채운 마크다운이다. `src/lib/markdown.tsx`가 계약의 부분집합(제목 #~###, 굵게, 기울임, 목록, 번호 목록, 링크 https·mailto·tel, 줄바꿈)만 React 요소로 그린다. 원시 HTML은 글자로 보이고 허용하지 않는 링크는 글자로 남는다. 외부 라이브러리는 쓰지 않는다.
- 이력은 조직 → 프로그램 → 활동 이름과 역할, 기간(`2026.03 – 2026.06` / `진행 중`)을 보이고, `verification`이 `verified`가 아니면 "미확인" 배지를 붙인다.
- 웹 비로그인 저장(`/v1/guest/*`)은 서버 4단계 전까지 실패하며 명함은 그대로 보인다.
