// `/api/auth/*` (contract native-v1.md "웹 로그인"): sign-in starts and completions whose TokenPair becomes the HttpOnly session
// cookies, logout, and the session summary the shell reads. Tokens never appear in a response body.
import { z } from "zod";
import { accountProxy } from "./account-proxy";
import { upstreamFetch } from "./guest-upstream";
import { profileConsentSchema, type ProfileConsent } from "./models";
import { callAsAccount, clearedSessionCookies, logoutUpstream, readSession, refreshSession, sessionCookies, tokenPairSchema } from "./session";

const headers = { "Cache-Control": "private, no-store, max-age=0", Vary: "Cookie, Origin" };
function failure(status: number, code: string, detail?: string) {
  return Response.json({ error: { code, ...(detail && { detail }) } }, { status, headers });
}
function withCookies(response: Response, cookies: string[]) {
  for (const value of cookies) response.headers.append("Set-Cookie", value);
  return response;
}

const challengeId = z.uuid();
const text = (max: number) => z.string().min(1).max(max);
const credential = z.looseObject({ id: text(2048), rawId: text(2048), type: z.literal("public-key"), response: z.looseObject({}) });
const bodies = {
  empty: z.object({}).strict(),
  passkey: z.object({ challengeId, credential }).strict(),
  apple: z
    .object({ challengeId, identityToken: text(8192), displayName: z.string().trim().min(1).max(100).optional(), authorizationCode: text(2048).optional() })
    .strict(),
  google: z.object({ idToken: text(8192), nonce: text(512) }).strict(),
  linkToken: z.object({ linkToken: text(4096) }).strict(),
};
const optionsSchema = z.object({ challengeId, options: z.looseObject({}) });
const appleNonceSchema = z.object({ challengeId, nonce: text(512) });
const googleNonceSchema = z.object({ nonce: text(512) });
/** 202: a first-time Apple/Google account; the browser then starts a new account or links a passkey account (`linkToken`, 10 minutes, once). */
const needsAccountSchema = z.object({ linkToken: text(4096), email: z.string().nullable().optional(), displayName: z.string().nullable().optional() });

type Start = { upstream: string; body: keyof typeof bodies; parse: (json: unknown) => unknown };
type Finish = { upstream: string; body: keyof typeof bodies; needsAccount?: boolean };
/** Requests that only start a sign-in (challenge or nonce); their answers cross as they are. */
const starts: Record<string, Start> = {
  "passkey/options": { upstream: "auth/passkeys/authentication/options", body: "empty", parse: (json) => optionsSchema.parse(json) },
  "apple/nonce": { upstream: "auth/apple/nonce", body: "empty", parse: (json) => appleNonceSchema.parse(json) },
  "google/nonce": { upstream: "auth/google/nonce", body: "empty", parse: (json) => googleNonceSchema.parse(json) },
};
/** Requests whose success is a TokenPair, stored as cookies. */
const finishes: Record<string, Finish> = {
  passkey: { upstream: "auth/passkeys/authentication", body: "passkey" },
  apple: { upstream: "auth/apple", body: "apple", needsAccount: true },
  "apple/register": { upstream: "auth/apple/register", body: "linkToken" },
  google: { upstream: "auth/google", body: "google", needsAccount: true },
  "google/register": { upstream: "auth/google/register", body: "linkToken" },
};

export async function authRoute(request: Request, segments: string[]): Promise<Response> {
  const path = segments.join("/");
  const method = request.method;
  // Adding a passkey to the signed-in account (contract "설정" › 패스키): the Bearer branch of the proxy.
  if (segments[0] === "passkeys" && segments[1] === "registration") return accountProxy(request, ["auth", ...segments]);
  if (method === "GET") return path === "session" ? session(request) : failure(404, "NOT_FOUND");
  if (method !== "POST" || !(path === "logout" || path === "refresh" || path in starts || path in finishes)) return failure(404, "NOT_FOUND");
  if (
    request.headers.get("origin") !== new URL(request.url).origin ||
    request.headers.get("x-dearby-request") !== "1" ||
    request.headers.get("content-type") !== "application/json"
  )
    return failure(403, "FORBIDDEN");
  if (path === "logout") {
    await logoutUpstream(request.headers.get("cookie"));
    return withCookies(new Response(null, { status: 204, headers }), clearedSessionCookies());
  }
  if (!process.env.DEARBY_API_ORIGIN) return failure(503, "SERVICE_UNAVAILABLE");
  if (path === "refresh") return refresh(request);
  const start: Start | undefined = starts[path];
  const spec: Start | Finish = start ?? finishes[path];
  let body: unknown;
  try {
    const parsed = bodies[spec.body].safeParse(await request.json());
    if (!parsed.success) return failure(422, "INVALID_INPUT");
    body = parsed.data;
  } catch {
    return failure(422, "INVALID_INPUT");
  }
  try {
    const response = await upstreamFetch(spec.upstream, { method: "POST", body: spec.body === "empty" ? undefined : body });
    if (!response.ok) return problem(response);
    if (start) return Response.json(start.parse(await response.json()), { headers });
    const json: unknown = await response.json();
    if (response.status === 202 && (spec as Finish).needsAccount) return Response.json(needsAccountSchema.parse(json), { status: 202, headers });
    const pair = tokenPairSchema.parse(json);
    return withCookies(Response.json({ user: pair.user }, { headers }), sessionCookies(pair));
  } catch {
    return failure(502, "UPSTREAM_UNAVAILABLE");
  }
}

/** Upstream ProblemDetail → status, a coarse code and the user-facing `detail` (contract: `detail` may be shown as the notice). */
async function problem(response: Response) {
  const status = [400, 401, 403, 404, 409, 422, 429, 503].includes(response.status) ? response.status : 502;
  const body: unknown = await response.json().catch(() => undefined);
  const detail = (body as { detail?: unknown } | undefined)?.detail;
  const code = status === 401 ? "SIGN_IN_FAILED" : status === 503 ? "UNAVAILABLE" : status === 429 ? "RATE_LIMITED" : "REQUEST_FAILED";
  return failure(status, code, typeof detail === "string" && status < 500 ? detail.slice(0, 200) : undefined);
}

/**
 * `POST /api/auth/refresh` (contract "refresh 회전"): the one place the refresh token is used. The browser calls it once under a Web
 * Lock after a 401 and retries; a still-valid access cookie means another tab already did, so nothing is rotated.
 */
async function refresh(request: Request) {
  try {
    const result = await refreshSession(request.headers.get("cookie"));
    if (result.kind === "current") return Response.json({ refreshed: false }, { headers });
    if (result.kind === "refreshed") return withCookies(Response.json({ refreshed: true }, { headers }), result.cookies);
    return withCookies(failure(401, "SIGNED_OUT"), result.kind === "refused" ? result.cookies : []);
  } catch {
    return failure(502, "UPSTREAM_UNAVAILABLE");
  }
}

export type Session = { signedIn: boolean; consent?: ProfileConsent | null };
/**
 * `GET /api/auth/session`: whether the browser holds a session, and for a signed-in one the consent state from `GET /v1/profile`
 * (contract "개인정보 동의": asked at every start while required). An expired access token is 401 `SIGNED_OUT` like any account
 * route, so the browser refreshes and asks again. `consent` is null when the API could not be reached, which the shell treats as
 * unknown — no gate, no account sync.
 */
async function session(request: Request) {
  const cookieHeader = request.headers.get("cookie");
  const tokens = readSession(cookieHeader);
  if ((!tokens.access && !tokens.refresh) || !process.env.DEARBY_API_ORIGIN) return Response.json({ signedIn: false } satisfies Session, { headers });
  try {
    const result = await callAsAccount(cookieHeader, { path: "profile" });
    if (result.kind === "signedOut" || result.response.status === 401) return failure(401, "SIGNED_OUT");
    const consent = result.response.ok ? profileConsentSchema.parse(await result.response.json()) : null;
    return Response.json({ signedIn: true, consent } satisfies Session, { headers });
  } catch {
    return Response.json({ signedIn: true, consent: null } satisfies Session, { headers });
  }
}
