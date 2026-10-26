// Signed-in account routes of the `/api/*` proxy (contract native-v1.md "웹 로그인"): a fixed allowlist, the access token from the
// session cookie as Bearer, one refresh on 401, and validated bodies both ways. Nothing here is reachable without the cookies.
import { z } from "zod";
import { TOKEN, readGuestCookie } from "./guest-upstream";
import { callAsAccount, clearedSessionCookies, withCookies } from "./session";
import { activityId, favoriteSessionListSchema, myActivityListSchema, myActivitySchema, profileConsentSchema, statusPickSchema } from "./models";

const headers = { "Cache-Control": "private, no-store, max-age=0", Vary: "Cookie, Origin" };
function failure(status: number, code: string, detail?: string) {
  return Response.json({ error: { code, ...(detail && { detail }) } }, { status, headers });
}

const challengeId = z.uuid();
const credential = z.looseObject({ id: z.string().min(1), rawId: z.string().min(1), type: z.literal("public-key"), response: z.looseObject({}) });
/** Request bodies per route; anything else is 422. */
const bodies = {
  consents: z.object({ privacyVersion: z.string().regex(/^\d{4}-\d{2}-\d{2}$/), termsVersion: z.string().regex(/^\d{4}-\d{2}-\d{2}$/) }).strict(),
  passkeyLabel: z.object({ label: z.string().trim().min(1).max(60) }).strict(),
  registrationOptions: z.object({ displayName: z.string().trim().min(1).max(60).optional() }).strict(),
  registration: z.object({ challengeId, credential }).strict(),
  linkToken: z.object({ linkToken: z.string().min(1).max(4096) }).strict(),
  empty: z.object({}).strict(),
  statusPick: statusPickSchema,
};
/** Response shapes that cross to the browser (tokens never do). */
const meSchema = z.object({
  id: z.uuid(),
  createdAt: z.string(),
  signInMethods: z.object({ passkeys: z.number().int(), apple: z.boolean(), google: z.boolean() }),
});
const passkeySchema = z.object({
  id: z.string(),
  label: z.string(),
  createdAt: z.string(),
  lastUsedAt: z.string().nullable(),
  backedUp: z.boolean(),
  transports: z.array(z.string()),
});
const passkeyListSchema = z.object({ items: z.array(passkeySchema) });
const optionsSchema = z.object({ challengeId, options: z.looseObject({}) });
/** `POST /v1/wallet/import-guest` (server PR, contract "웹 로그인"): how many saved cards moved into the account. */
const importSchema = z.object({ imported: z.number().int().nonnegative() });
/** `*`, or one or more (weak) entity tags. */
const IF_MATCH = /^(\*|(W\/)?"[^"]{1,128}"(, ?(W\/)?"[^"]{1,128}")*)$/;
const KNOWN_CODES = ["CONSENT_REQUIRED", "CONSENT_VERSION", "LAST_SIGN_IN_METHOD", "BLOCKED", "GUEST_SESSION_INVALID"];

type Route = { body?: keyof typeof bodies; parse: (json: unknown) => unknown; noContent?: boolean };
/** `METHOD path` → how the request and response are validated. `:id` is a passkey credential id, `:provider` apple|google. */
function route(method: string, segments: string[]): Route | undefined {
  const path = segments.join("/");
  if (method === "GET" && path === "me") return { parse: (json) => meSchema.parse(json) };
  if (method === "DELETE" && path === "me") return { parse: () => undefined, noContent: true };
  if (method === "GET" && path === "profile") return { parse: (json) => profileConsentSchema.parse(json) };
  if (method === "POST" && path === "me/consents") return { body: "consents", parse: () => undefined, noContent: true };
  if (method === "GET" && path === "me/passkeys") return { parse: (json) => passkeyListSchema.parse(json) };
  if (segments.length === 3 && segments[0] === "me" && segments[1] === "passkeys" && /^[A-Za-z0-9_-]{16,512}$/.test(segments[2])) {
    if (method === "PATCH") return { body: "passkeyLabel", parse: (json) => passkeySchema.parse(json) };
    if (method === "DELETE") return { parse: () => undefined, noContent: true };
  }
  if (method === "POST" && path === "auth/passkeys/registration/options")
    return { body: "registrationOptions", parse: (json) => optionsSchema.parse(json) };
  // The API answers a TokenPair for the same account; the browser only needs to know it worked.
  if (method === "POST" && path === "auth/passkeys/registration") return { body: "registration", parse: () => ({}) };
  if (method === "POST" && segments.length === 3 && segments[0] === "me" && segments[1] === "identities" && ["apple", "google"].includes(segments[2]))
    return { body: "linkToken", parse: () => undefined, noContent: true };
  if (method === "POST" && path === "wallet/import-guest") return { body: "empty", parse: (json) => importSchema.parse(json) };
  // 내 활동 (contract "내 활동 서버 저장·일정 상태", web 2단계 B): the list, add (idempotent PUT, empty body), remove, and a stage mark.
  if (method === "GET" && path === "me/activities") return { parse: (json) => myActivityListSchema.parse(json) };
  if (segments[0] === "me" && segments[1] === "activities" && activityId(segments[2] ?? "") !== undefined) {
    if (segments.length === 3 && method === "PUT") return { body: "empty", parse: (json) => myActivitySchema.parse(json) };
    if (segments.length === 3 && method === "DELETE") return { parse: () => undefined, noContent: true };
    if (segments.length === 5 && segments[3] === "schedules" && activityId(segments[4]) !== undefined && method === "PUT")
      return { body: "statusPick", parse: (json) => myActivitySchema.parse(json) };
  }
  // 찜한 세션, read only (the 오늘 내 행사 card shows the next favorited session; 찜 itself stays in the apps).
  if (method === "GET" && path === "me/session-favorites") return { parse: (json) => favoriteSessionListSchema.parse(json) };
}
export function isAccountRoute(method: string, segments: string[]) {
  return route(method, segments) !== undefined;
}

/**
 * Handles one allowlisted account route. Mutations need the same same-origin guard as guest mutations (exact Origin,
 * `X-Dearby-Request: 1`, JSON). Without an access cookie nothing goes upstream, and an upstream 401 passes through, both as 401
 * `SIGNED_OUT`: the browser refreshes once through `POST /api/auth/refresh` and retries (contract "refresh 회전"). Upstream
 * ProblemDetail crosses only as a known `code` plus its user-facing `detail`.
 */
export async function accountProxy(request: Request, segments: string[]): Promise<Response> {
  const method = request.method;
  const spec = route(method, segments);
  if (!spec) return failure(404, "NOT_FOUND");
  if (!process.env.DEARBY_API_ORIGIN) return failure(503, "SERVICE_UNAVAILABLE");
  const mutation = method !== "GET";
  if (
    mutation &&
    (request.headers.get("origin") !== new URL(request.url).origin ||
      request.headers.get("x-dearby-request") !== "1" ||
      request.headers.get("content-type") !== "application/json")
  )
    return failure(403, "FORBIDDEN");
  let body: unknown;
  if (spec.body) {
    try {
      const parsed = bodies[spec.body].safeParse(await request.json());
      if (!parsed.success) return failure(422, "INVALID_INPUT");
      body = parsed.data;
    } catch {
      return failure(422, "INVALID_INPUT");
    }
  } else if (mutation) body = {};
  const cookieHeader = request.headers.get("cookie");
  // 비로그인 저장 → 계정 합치기: the guest token comes from this server's cookie, never from the browser body. The guest session stays
  // (the API closes its saves); the browser clears it with the usual `DELETE /api/guest/session`.
  const importGuest = segments.join("/") === "wallet/import-guest";
  const guestToken = importGuest ? readGuestCookie(cookieHeader) : undefined;
  if (importGuest) {
    if (!guestToken || !TOKEN.test(guestToken)) return Response.json({ imported: 0 }, { headers });
    body = { guestToken };
  }
  const path = segments.join("/");
  // `If-Match` crosses as it is (contract "웹에서 쓰는 로그인 기능": the profile PUT's version check); a malformed value is dropped.
  const ifMatch = request.headers.get("if-match");
  try {
    const result = await callAsAccount(cookieHeader, {
      path,
      method,
      body: mutation ? body : undefined,
      ifMatch: ifMatch && IF_MATCH.test(ifMatch) ? ifMatch : undefined,
    });
    if (result.kind === "signedOut") return failure(401, "SIGNED_OUT");
    const { response } = result;
    // Account deletion ends the session here as well; the API has already dropped the refresh token.
    if (path === "me" && method === "DELETE" && response.ok)
      return withCookies(new Response(null, { status: 204, headers }), clearedSessionCookies());
    if (response.status === 401) return failure(401, "SIGNED_OUT");
    if (!response.ok) {
      const status = [400, 403, 404, 409, 422, 429, 503].includes(response.status) ? response.status : 502;
      const problem: unknown = await response.json().catch(() => undefined);
      const code = (problem as { code?: unknown } | undefined)?.code;
      const detail = (problem as { detail?: unknown } | undefined)?.detail;
      // A guest session the API no longer knows (400 `GUEST_SESSION_INVALID`, contract "비로그인 저장 → 계정 합치기") has nothing to move.
      if (importGuest && code === "GUEST_SESSION_INVALID") return Response.json({ imported: 0 }, { headers });
      return failure(
        status,
        typeof code === "string" && KNOWN_CODES.includes(code) ? code : "REQUEST_FAILED",
        typeof detail === "string" && status < 500 ? detail.slice(0, 200) : undefined,
      );
    }
    if (spec.noContent) return new Response(null, { status: 204, headers });
    const output = spec.parse(response.status === 204 ? undefined : await response.json());
    return Response.json(output ?? {}, { status: response.status === 201 ? 201 : 200, headers });
  } catch {
    return failure(502, "UPSTREAM_UNAVAILABLE");
  }
}
