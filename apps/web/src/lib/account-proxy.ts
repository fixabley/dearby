// Signed-in account routes of the `/api/*` proxy (contract native-v1.md "웹 로그인"): a fixed allowlist, the access token from the
// session cookie as Bearer, one refresh on 401, and validated bodies both ways. Nothing here is reachable without the cookies.
import { z } from "zod";
import { TOKEN, acceptLanguage as acceptLanguageHeader, readGuestCookie } from "./guest-upstream";
import { callAsAccount, clearedSessionCookies, withCookies } from "./session";
import {
  activityInfoPageSchema,
  blockBodySchema,
  blockSchema,
  cardPublishSchema,
  eligibilityProfileSchema,
  emailChallengeBodySchema,
  emailChallengeSchema,
  emailVerifyBodySchema,
  evidenceSchema,
  githubCompleteBodySchema,
  githubStartBodySchema,
  githubStartSchema,
  myCardListSchema,
  myCardSchema,
  notificationPageSchema,
  photoSchema,
  profileRequestSchema,
  profileSchema,
  qrCodeSchema,
  receiptSchema,
  reciprocateBodySchema,
  reciprocateSchema,
  recommendationPreferencesSchema,
  unreadCountSchema,
  walletSaveBodySchema,
  walletSchema,
} from "./account-models";
import { activityId, favoriteSessionListSchema, favoritesSchema, myActivityListSchema, myActivitySchema, statusPickSchema } from "./models";

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
  // 2단계 C (contract "웹 화면 — 2단계 C"): 프로필·내 명함·받은 명함·연락처 인증.
  profile: profileRequestSchema,
  cardPublish: cardPublishSchema,
  walletSave: walletSaveBodySchema,
  reciprocate: reciprocateBodySchema,
  block: blockBodySchema,
  githubStart: githubStartBodySchema,
  githubComplete: githubCompleteBodySchema,
  emailChallenge: emailChallengeBodySchema,
  emailVerify: emailVerifyBodySchema,
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
const KNOWN_CODES = [
  "CONSENT_REQUIRED",
  "CONSENT_VERSION",
  "LAST_SIGN_IN_METHOD",
  "BLOCKED",
  "GUEST_SESSION_INVALID",
  // 2단계 C: 받은 명함·연락처 인증 (contract "명함 교환", "연락처 인증").
  "OWN_CARD",
  "PROFILE_REQUIRED",
  "GITHUB_LINK_EXPIRED",
  "CODE_MISMATCH",
  "CHALLENGE_EXPIRED",
];

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const DIGITS = /^\d{1,18}$/;
/** Photo uploads (contract "프로필 사진") and 인증자료 (contract "인증자료"): kinds the API stores, 5MB each. */
const PHOTO_TYPES = /^image\/(jpeg|png)$/;
const EVIDENCE_TYPES = /^(image\/(jpeg|png|webp)|application\/pdf)$/;
const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
/** Binary answers are at most this (the API re-encodes photos to 512px JPEG; evidence is capped at 5MB on upload). */
const MAX_BINARY_BYTES = 6 * 1024 * 1024;

/**
 * Only the listed query keys, each matching its pattern, are forwarded; anything else makes the request 422 rather than being
 * silently dropped (same rule as the public searches).
 */
function pickQuery(url: URL, allowed: Record<string, RegExp>): URLSearchParams | null {
  const query = new URLSearchParams();
  for (const [key, value] of url.searchParams) {
    const rule = allowed[key];
    if (!rule || !rule.test(value)) return null;
    query.set(key, value);
  }
  return query;
}
const CURSOR_SIZE = { cursor: DIGITS, size: /^(100|[1-9]\d?)$/ };
const SEARCH = { ...CURSOR_SIZE, q: /^[^\u0000-\u001f]{1,100}$/, parentId: DIGITS, rootsOnly: /^(true|false)$/, organizationId: DIGITS };
const VERSION = { v: DIGITS };

type Route = {
  body?: keyof typeof bodies;
  parse: (json: unknown) => unknown;
  noContent?: boolean;
  /** Validated query to forward; `null` means the request's query was not acceptable (422). */
  query?: URLSearchParams | null;
  /** Multipart upload of one `file` of these kinds. */
  multipart?: RegExp;
  /** Binary passthrough (photo, 인증자료 file) of these Content-Types with the API's cache headers. */
  binary?: RegExp;
  /** Forward the browser's `If-Match` (profile version). */
  ifMatch?: boolean;
  /** Forward the browser's `Accept-Language` (알림의 활동·프로그램 이름). */
  acceptLanguage?: boolean;
};
const NUMERIC_ID = /^[1-9]\d{0,17}$/;
const noContent: Route = { parse: () => undefined, noContent: true };
/** `METHOD path` → how the request and response are validated. `:id` is a passkey credential id, `:provider` apple|google. */
function route(method: string, segments: string[], url?: URL): Route | undefined {
  const path = segments.join("/");
  if (method === "GET" && path === "me") return { parse: (json) => meSchema.parse(json) };
  // 즐겨찾기·세션 찜 (contract "즐겨찾기(하트)", "트랙·세션 시간표", web "웹 화면 — 2단계 A"): PUT without a body keeps the API's default
  // notify level; DELETE is idempotent. Both answer 204.
  if (method === "GET" && path === "me/favorites") return { parse: (json) => favoritesSchema.parse(json), acceptLanguage: true };
  if (segments.length === 4 && segments[0] === "me" && segments[1] === "favorites" && ["programs", "activities"].includes(segments[2]) && NUMERIC_ID.test(segments[3])) {
    if (method === "PUT") return { ...noContent, body: "empty" };
    if (method === "DELETE") return noContent;
  }
  // Opening a favorited program turns its `새 모집` off.
  if (method === "POST" && segments.length === 5 && path.startsWith("me/favorites/programs/") && segments[4] === "seen" && NUMERIC_ID.test(segments[3]))
    return { ...noContent, body: "empty" };
  if (segments.length === 3 && segments[0] === "me" && segments[1] === "session-favorites" && NUMERIC_ID.test(segments[2])) {
    if (method === "PUT") return { ...noContent, body: "empty" };
    if (method === "DELETE") return noContent;
  }
  if (method === "DELETE" && path === "me") return { parse: () => undefined, noContent: true };
  // 2단계 C: the whole profile (1단계 returned only the consent fields; `/api/auth/session` keeps its own reduced read).
  if (method === "GET" && path === "profile") return { parse: (json) => profileSchema.parse(json) };
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
  // 내 시간표 목록 (contract "트랙·세션 시간표"; 2단계 A hearts, 2단계 B 오늘 내 행사 card).
  if (method === "GET" && path === "me/session-favorites") return { parse: (json) => favoriteSessionListSchema.parse(json) };

  // ── 2단계 C (contract "웹 화면 — 2단계 C"): 프로필·내 명함·받은 명함·알림·연락처 인증 ─────────────────────────────────────
  const query = (allowed: Record<string, RegExp>) => (url ? pickQuery(url, allowed) : new URLSearchParams());
  // 프로필 (contract "프로필과 버전 이력", "프로필 사진", "인증자료")
  if (method === "PUT" && path === "profile") return { body: "profile", parse: (json) => profileSchema.parse(json), ifMatch: true };
  if (path === "profile/photo") {
    if (method === "PUT") return { multipart: PHOTO_TYPES, parse: (json) => photoSchema.parse(json) };
    if (method === "GET") return { parse: () => undefined, binary: /^image\/jpeg$/, query: query(VERSION) };
    if (method === "DELETE") return { parse: () => undefined, noContent: true };
  }
  if (segments[0] === "profile" && segments[1] === "histories" && UUID.test(segments[2] ?? "") && segments[3] === "evidence") {
    if (segments.length === 4 && method === "POST") return { multipart: EVIDENCE_TYPES, parse: (json) => evidenceSchema.parse(json) };
    if (segments.length === 5 && UUID.test(segments[4])) {
      if (method === "GET") return { parse: () => undefined, binary: EVIDENCE_TYPES, query: query({}) };
      if (method === "DELETE") return { parse: () => undefined, noContent: true };
    }
  }
  // 이력·직무 입력의 선택 창 검색 (contract "이력"·"직무"); activities use the public search.
  if (method === "GET" && ["organizations", "programs", "roles", "jobs"].includes(path))
    return { parse: (json) => activityInfoPageSchema.parse(json), query: query(SEARCH) };
  // 내 명함 (contract "명함·지갑", "명함 고치기", "명함 QR 코드"); the public `GET cards/:id` stays on the public branch.
  if (method === "GET" && path === "cards") return { parse: (json) => myCardListSchema.parse(json) };
  if (method === "POST" && path === "cards") return { body: "cardPublish", parse: (json) => myCardSchema.parse(json) };
  if (segments[0] === "cards" && UUID.test(segments[1] ?? "")) {
    if (segments.length === 2 && method === "PUT") return { body: "cardPublish", parse: (json) => myCardSchema.parse(json) };
    if (segments.length === 2 && method === "DELETE") return { parse: () => undefined, noContent: true };
    if (segments.length === 3 && segments[2] === "photo" && method === "GET")
      return { parse: () => undefined, binary: /^image\/jpeg$/, query: query(VERSION) };
    if (segments.length === 3 && segments[2] === "qr-codes" && method === "POST") return { parse: (json) => qrCodeSchema.parse(json) };
    if (segments.length === 4 && segments[2] === "qr-codes" && UUID.test(segments[3]) && method === "DELETE")
      return { parse: () => undefined, noContent: true };
  }
  // 받은 명함 (contract "명함 교환", "명함 신고·차단")
  if (method === "GET" && path === "wallet") return { parse: (json) => walletSchema.parse(json) };
  if (segments[0] === "wallet" && segments[1] === "cards" && UUID.test(segments[2] ?? "")) {
    if (segments.length === 3 && method === "PUT") return { body: "walletSave", parse: (json) => receiptSchema.parse(json) };
    if (segments.length === 4 && segments[3] === "reciprocate" && method === "POST")
      return { body: "reciprocate", parse: (json) => reciprocateSchema.parse(json) };
  }
  if (method === "POST" && path === "wallet/blocks") return { body: "block", parse: (json) => blockSchema.parse(json) };
  // 알림 (contract "알림 목록(발견 우상단 종)")
  if (method === "GET" && path === "me/notifications")
    return { parse: (json) => notificationPageSchema.parse(json), query: query({ cursor: DIGITS }), acceptLanguage: true };
  if (method === "GET" && path === "me/notifications/unread-count") return { parse: (json) => unreadCountSchema.parse(json) };
  if (method === "POST" && path === "me/notifications/read-all") return { parse: () => undefined, noContent: true };
  if (method === "POST" && segments.length === 4 && segments[0] === "me" && segments[1] === "notifications" && DIGITS.test(segments[2]) && segments[3] === "read")
    return { parse: () => undefined, noContent: true };
  // 연락처 인증 (contract "연락처 인증")
  if (method === "POST" && path === "me/contacts/github/start") return { body: "githubStart", parse: (json) => githubStartSchema.parse(json) };
  if (method === "POST" && path === "me/contacts/github/complete") return { body: "githubComplete", parse: (json) => profileSchema.parse(json) };
  if (method === "POST" && path === "me/contacts/email/challenges")
    return { body: "emailChallenge", parse: (json) => emailChallengeSchema.parse(json) };
  if (method === "POST" && path === "me/contacts/email/verify") return { body: "emailVerify", parse: (json) => profileSchema.parse(json) };
  // 추천 설정·지원 자격 (contract "추천 설정 퍼널·지원 자격 정보·추천"): shown to the signed-in person, edited in the apps.
  if (method === "GET" && path === "me/recommendation-preferences") return { parse: (json) => recommendationPreferencesSchema.parse(json) };
  if (method === "GET" && path === "me/eligibility-profile") return { parse: (json) => eligibilityProfileSchema.parse(json) };
}
export function isAccountRoute(method: string, segments: string[]) {
  return route(method, segments) !== undefined;
}

/**
 * Handles one allowlisted account route. Mutations need the same same-origin guard as guest mutations (exact Origin,
 * `X-Dearby-Request: 1`, JSON — or multipart for the two uploads). Without an access cookie nothing goes upstream, and an upstream
 * 401 passes through, both as 401 `SIGNED_OUT`: the browser refreshes once through `POST /api/auth/refresh` and retries (contract
 * "refresh 회전"). Upstream ProblemDetail crosses only as a known `code` plus its user-facing `detail`.
 */
export async function accountProxy(request: Request, segments: string[]): Promise<Response> {
  const method = request.method;
  const url = new URL(request.url);
  const spec = route(method, segments, url);
  if (!spec) return failure(404, "NOT_FOUND");
  if (spec.query === null) return failure(422, "INVALID_INPUT");
  if (!process.env.DEARBY_API_ORIGIN) return failure(503, "SERVICE_UNAVAILABLE");
  const mutation = method !== "GET";
  const contentType = request.headers.get("content-type") ?? "";
  if (
    mutation &&
    (request.headers.get("origin") !== url.origin ||
      request.headers.get("x-dearby-request") !== "1" ||
      (spec.multipart ? !contentType.startsWith("multipart/form-data") : contentType !== "application/json"))
  )
    return failure(403, "FORBIDDEN");
  let body: unknown;
  let formData: FormData | undefined;
  if (spec.multipart) {
    // One `file` part of an accepted kind and size; nothing else from the browser's form crosses.
    const form = await request.formData().catch(() => undefined);
    const file = form?.get("file");
    if (!(file instanceof File) || file.size === 0 || file.size > MAX_UPLOAD_BYTES || !spec.multipart.test(file.type)) return failure(422, "INVALID_INPUT");
    formData = new FormData();
    formData.append("file", file, file.name.replace(/[\r\n"]/g, "").slice(0, 200) || "file");
  } else if (spec.body) {
    try {
      const parsed = bodies[spec.body].safeParse(await request.json());
      if (!parsed.success) return failure(422, "INVALID_INPUT");
      body = parsed.data;
    } catch {
      return failure(422, "INVALID_INPUT");
    }
  } else if (mutation) body = {};
  // `If-Match` (contract "프로필과 버전 이력"): a version number only; anything else is left out and the API saves unconditionally.
  const ifMatch = spec.ifMatch ? (request.headers.get("if-match")?.match(/^"?(\d{1,9})"?$/)?.[1] ?? undefined) : undefined;
  const acceptLanguage = spec.acceptLanguage ? acceptLanguageHeader(request.headers.get("accept-language")) : undefined;
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
  try {
    const result = await callAsAccount(cookieHeader, {
      path,
      method,
      body: mutation && !formData ? body : undefined,
      formData,
      ifMatch,
      acceptLanguage,
      query: spec.query ?? undefined,
      accept: spec.binary ? "*/*" : undefined,
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
    if (spec.binary) {
      // A photo or an 인증자료 file as the API sent it (its private, immutable cache for `?v=` photos), never any other kind.
      const type = response.headers.get("content-type") ?? "";
      if (!spec.binary.test(type)) throw new Error("Unexpected binary type");
      const bytes = await response.arrayBuffer();
      if (bytes.byteLength > MAX_BINARY_BYTES) throw new Error("Binary too large");
      const binaryHeaders: Record<string, string> = {
        "Content-Type": type,
        "X-Content-Type-Options": "nosniff",
        "Cache-Control": response.headers.get("cache-control") ?? headers["Cache-Control"],
        Vary: "Cookie",
      };
      const disposition = response.headers.get("content-disposition");
      if (disposition) binaryHeaders["Content-Disposition"] = disposition;
      return new Response(bytes, { headers: binaryHeaders });
    }
    if (spec.noContent) return new Response(null, { status: 204, headers });
    const output = spec.parse(response.status === 204 ? undefined : await response.json());
    const outputHeaders = acceptLanguage ? { ...headers, Vary: `${headers.Vary}, Accept-Language` } : headers;
    return Response.json(output ?? {}, { status: response.status === 201 ? 201 : 200, headers: outputHeaders });
  } catch {
    return failure(502, "UPSTREAM_UNAVAILABLE");
  }
}
