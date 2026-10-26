// Web sign-in session (contract native-v1.md "웹 로그인" › 토큰 보관): the API's TokenPair lives only in two HttpOnly cookies set by
// this server. Browser JavaScript never sees a token; it asks `/api/*` and this layer adds `Authorization: Bearer`.
import { z } from "zod";
import { upstreamFetch } from "./guest-upstream";

const production = () => process.env.NODE_ENV === "production";
/** `__Host-` names need Secure, so development over plain HTTP uses separate names (same rule as the guest cookie). */
const accessCookieName = () => (production() ? "__Host-dearby_access" : "dearby_access_dev");
const refreshCookieName = () => (production() ? "__Host-dearby_refresh" : "dearby_refresh_dev");
/** The API's access token lives 1 minute and the refresh token 90 days (`dearby.jwt.*-ttl`). */
const ACCESS_MAX_AGE = 60;
const REFRESH_MAX_AGE = 90 * 86_400;

function cookie(name: string, value: string, maxAge: number) {
  return `${name}=${value}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${maxAge}${production() ? "; Secure" : ""}`;
}
/** JWT and refresh token characters only; anything else in the cookie is treated as absent. */
const TOKEN_TEXT = /^[A-Za-z0-9._~-]{16,4096}$/;

export const tokenPairSchema = z.object({
  accessToken: z.string().regex(TOKEN_TEXT),
  refreshToken: z.string().regex(TOKEN_TEXT),
  user: z.object({ id: z.uuid(), roles: z.array(z.string()) }),
});
export type TokenPair = z.infer<typeof tokenPairSchema>;

/** Set-Cookie values that store a new TokenPair (sign-in, refresh rotation). */
export function sessionCookies(pair: TokenPair): string[] {
  return [cookie(accessCookieName(), pair.accessToken, ACCESS_MAX_AGE), cookie(refreshCookieName(), pair.refreshToken, REFRESH_MAX_AGE)];
}
/** Set-Cookie values that remove both tokens (logout, account deletion, refused refresh). */
export function clearedSessionCookies(): string[] {
  return [cookie(accessCookieName(), "", 0), cookie(refreshCookieName(), "", 0)];
}

function readCookie(header: string | null, name: string): string | undefined {
  const prefix = `${name}=`;
  const value = header
    ?.split(";")
    .map((part) => part.trim())
    .find((part) => part.startsWith(prefix))
    ?.slice(prefix.length);
  return value && TOKEN_TEXT.test(value) ? value : undefined;
}
export type SessionTokens = { access?: string; refresh?: string };
export function readSession(header: string | null): SessionTokens {
  return { access: readCookie(header, accessCookieName()), refresh: readCookie(header, refreshCookieName()) };
}

/**
 * Seconds the access token has left by its `exp` claim, read without verifying (the API verifies; this only avoids a needless
 * refresh). Unreadable → 0.
 */
export function accessSecondsLeft(access: string | undefined): number {
  if (!access) return 0;
  try {
    const payload = access.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const { exp } = JSON.parse(Buffer.from(payload, "base64").toString()) as { exp?: number };
    return typeof exp === "number" ? exp - Math.floor(Date.now() / 1000) : 0;
  } catch {
    return 0;
  }
}

export type AccountCall = { path: string; method?: string; body?: unknown; ifMatch?: string };
export type AccountResult =
  /** No access token in the cookies: the browser refreshes (`POST /api/auth/refresh`) and retries, or is signed out. */
  | { kind: "signedOut" }
  /** The upstream response, including an upstream 401 for an expired token — the proxy never refreshes on its own. */
  | { kind: "response"; response: Response };

/**
 * One API call as the signed-in account with the access cookie as Bearer. The proxy does not refresh (contract "refresh 회전":
 * several Next instances racing on one rotating refresh token would sign the person out); a 401 goes back to the browser, which
 * refreshes once through the dedicated route and retries.
 */
export async function callAsAccount(cookieHeader: string | null, call: AccountCall): Promise<AccountResult> {
  const { access } = readSession(cookieHeader);
  if (!access) return { kind: "signedOut" };
  const response = await upstreamFetch(call.path, { method: call.method, body: call.body, bearer: access, ifMatch: call.ifMatch });
  return { kind: "response", response };
}

export type RefreshResult =
  /** The access cookie is still good (another tab or request refreshed moments ago): nothing to do. */
  | { kind: "current" }
  | { kind: "refreshed"; cookies: string[] }
  /** The API refused the refresh token (expired, revoked, already rotated): the browser is signed out. */
  | { kind: "refused"; cookies: string[] }
  | { kind: "none" };

/**
 * Rotates the refresh token (`POST /v1/auth/refresh`) and stores the new pair. Skipped when the access cookie still has time left,
 * so concurrent tabs that all saw a 401 do not burn the rotated token one after another. Network and 5xx failures throw and keep the
 * cookies (an unstable connection must not log the person out).
 */
export async function refreshSession(cookieHeader: string | null): Promise<RefreshResult> {
  const { access, refresh } = readSession(cookieHeader);
  if (accessSecondsLeft(access) > 10) return { kind: "current" };
  if (!refresh) return { kind: "none" };
  const response = await upstreamFetch("auth/refresh", { method: "POST", body: { refreshToken: refresh } });
  if (response.status === 401 || response.status === 400) return { kind: "refused", cookies: clearedSessionCookies() };
  if (!response.ok) throw new Error("REFRESH_FAILED");
  return { kind: "refreshed", cookies: sessionCookies(tokenPairSchema.parse(await response.json())) };
}

/** Appends Set-Cookie values to a response. */
export function withCookies(response: Response, cookies: string[]) {
  for (const value of cookies) response.headers.append("Set-Cookie", value);
  return response;
}

/** `POST /v1/auth/logout` with the cookie's refresh token; a failure is ignored because the cookies are cleared either way. */
export async function logoutUpstream(cookieHeader: string | null) {
  const { refresh } = readSession(cookieHeader);
  if (!refresh) return;
  try {
    await upstreamFetch("auth/logout", { method: "POST", body: { refreshToken: refresh } });
  } catch {
    return;
  }
}
