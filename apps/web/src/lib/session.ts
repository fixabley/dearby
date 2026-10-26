// Web sign-in session (contract native-v1.md "웹 로그인"): the API's TokenPair lives only in two HttpOnly cookies set by this
// server. Browser JavaScript never sees a token; it asks `/api/*` and this layer adds `Authorization: Bearer`.
import { z } from "zod";
import { upstreamFetch } from "./guest-upstream";

const production = () => process.env.NODE_ENV === "production";
/** `__Host-` names need Secure, so development over plain HTTP uses separate names (same rule as the guest cookie). */
const accessCookieName = () => (production() ? "__Host-dearby_access" : "dearby_access_dev");
const refreshCookieName = () => (production() ? "__Host-dearby_refresh" : "dearby_refresh_dev");
/** The API's access token lives 1 minute and the refresh token 90 days (`dearby.jwt.*-ttl`); an expired access cookie is refreshed on use. */
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
/** Whether the browser holds a session at all (either token cookie); no upstream call. */
export function hasSession(header: string | null): boolean {
  const tokens = readSession(header);
  return tokens.access !== undefined || tokens.refresh !== undefined;
}

export type AccountCall = {
  path: string;
  method?: string;
  body?: unknown;
  query?: URLSearchParams;
  acceptLanguage?: string;
};
export type AccountResult =
  /** No usable session: the cookies are missing, or the refresh was refused (`cookies` then clears them). */
  | { kind: "signedOut"; cookies: string[] }
  /** The upstream response for the call; `cookies` carries the rotated pair when a refresh happened. */
  | { kind: "response"; response: Response; cookies: string[] };

/**
 * One API call as the signed-in account (contract "웹 로그인" › 세션): Bearer from the access cookie; on 401 the refresh token is
 * rotated once (`POST /v1/auth/refresh`) and the call retried; a refused refresh signs the browser out. Network and 5xx failures
 * on the refresh itself throw and keep the cookies (an unstable connection must not log the person out). Concurrent requests that
 * both hit 401 each refresh; the second one loses because the token rotates, which a reload recovers from.
 */
export async function callAsAccount(cookieHeader: string | null, call: AccountCall): Promise<AccountResult> {
  const tokens = readSession(cookieHeader);
  if (!tokens.access && !tokens.refresh) return { kind: "signedOut", cookies: [] };
  if (tokens.access) {
    const response = await send(call, tokens.access);
    if (response.status !== 401) return { kind: "response", response, cookies: [] };
  }
  if (!tokens.refresh) return { kind: "signedOut", cookies: clearedSessionCookies() };
  const refreshed = await refresh(tokens.refresh);
  if (!refreshed) return { kind: "signedOut", cookies: clearedSessionCookies() };
  const response = await send(call, refreshed.accessToken);
  const cookies = sessionCookies(refreshed);
  if (response.status === 401) return { kind: "signedOut", cookies: clearedSessionCookies() };
  return { kind: "response", response, cookies };
}

function send(call: AccountCall, bearer: string) {
  return upstreamFetch(call.path, {
    method: call.method,
    body: call.body,
    query: call.query,
    bearer,
    acceptLanguage: call.acceptLanguage,
  });
}

/** Rotates the refresh token; `undefined` when the API refuses it (expired, revoked, replaced). Other failures throw. */
async function refresh(refreshToken: string): Promise<TokenPair | undefined> {
  const response = await upstreamFetch("auth/refresh", { method: "POST", body: { refreshToken } });
  if (response.status === 401 || response.status === 400) return;
  if (!response.ok) throw new Error("REFRESH_FAILED");
  return tokenPairSchema.parse(await response.json());
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
