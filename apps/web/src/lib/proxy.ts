import { z } from "zod";
import {
  activityId,
  activitySchema,
  cardSchema,
  guestListSchema,
  activityPageSchema,
  programPageSchema,
  uuid,
} from "./models";
import {
  apiOrigin,
  guestCookie,
  guestProxyKey,
  readGuestCookie,
  TOKEN,
  upstreamFetch,
} from "./guest-upstream";

// PUT /guest/cards/:id may carry the activity the card was exchanged at (contract "명함 교환").
const guestSaveBody = z.object({ activityId: z.number().int().positive().optional() }).strict();
const saveSchema = z.object({
  cardId: uuid,
  status: z.enum(["saved", "alreadySaved"]),
  guestToken: z.string().regex(TOKEN).optional(),
});
const headers = {
  "Cache-Control": "private, no-store, max-age=0",
  Vary: "Cookie, Origin",
};
function failure(status: number, code: string) {
  return Response.json({ error: { code } }, { status, headers });
}
/** The browser's language preference picks ko or en activity text upstream. Anything that is not a plain language list is dropped. */
function acceptLanguage(value: string | null): string | undefined {
  return value && /^[A-Za-z0-9*,;=. -]{1,200}$/.test(value) ? value : undefined;
}
/**
 * Only `q`, `cursor`, `size`, `programId`, `open` (activities), `participationType` and `openOnly` (programs) are forwarded
 * to the searches, rebuilt from validated values. Tags are searched through `q` only; the web never sends `tagIds`
 * (contract "태그 칩 줄 제거", 2026-10-19 밤).
 */
function searchQuery(url: URL, programs: boolean): URLSearchParams | undefined {
  const query = new URLSearchParams();
  const q = url.searchParams.get("q")?.trim();
  const cursor = url.searchParams.get("cursor");
  const size = url.searchParams.get("size");
  const programId = url.searchParams.get("programId");
  const participationType = url.searchParams.get("participationType");
  // `open=true` asks the server for its open-activity rule (contract "열린 활동 판정 일원화"); no other value is accepted.
  const openOnly = url.searchParams.get("open");
  if (openOnly !== null && (programs || openOnly !== "true")) return;
  // `openOnly=false` lists programs with activity in the last two years as well (contract "모집 중인 프로그램만 보기").
  const programsOpenOnly = url.searchParams.get("openOnly");
  if (programsOpenOnly !== null && (!programs || !["true", "false"].includes(programsOpenOnly))) return;
  if (q && q.length > 100) return;
  if (cursor !== null && !/^\d{1,18}$/.test(cursor)) return;
  if (size !== null && !(/^\d{1,3}$/.test(size) && +size >= 1 && +size <= 100))
    return;
  if (programId !== null && (programs || !/^[1-9]\d{0,17}$/.test(programId)))
    return;
  if (
    participationType !== null &&
    (!programs || !["REGISTRATION", "SELECTION"].includes(participationType))
  )
    return;
  if (q) query.set("q", q);
  if (cursor !== null) query.set("cursor", cursor);
  if (size !== null) query.set("size", size);
  if (programId !== null) query.set("programId", programId);
  if (openOnly !== null) query.set("open", openOnly);
  if (programsOpenOnly !== null) query.set("openOnly", programsOpenOnly);
  if (participationType !== null)
    query.set("participationType", participationType);
  return query;
}
/** `GET programs/:id/logo?v=<epoch seconds>` (contract "프로그램 로고"): only `v` is forwarded; anything else is rejected. */
function logoQuery(url: URL): URLSearchParams | undefined {
  const keys = [...url.searchParams.keys()];
  const v = url.searchParams.get("v");
  if (keys.some((key) => key !== "v") || (v !== null && !/^\d{1,18}$/.test(v))) return;
  const query = new URLSearchParams();
  if (v !== null) query.set("v", v);
  return query;
}
// Stored logo kinds (admin-v1.md "프로그램 로고"); any other upstream type is not served under this origin.
const LOGO_TYPE = /^image\/(png|jpeg|webp)$/;

export async function proxy(
  request: Request,
  segments: string[],
): Promise<Response> {
  const path = segments.join("/");
  const guest = segments[0] === "guest";
  const id = segments[guest ? 2 : 1];
  const activityRoute = segments.length === 2 && segments[0] === "activities";
  // Public program logo image (contract "프로그램 로고"): binary passthrough, cached like the API says.
  const logoRoute = segments.length === 3 && segments[0] === "programs" && segments[2] === "logo";
  const programs = path === "discovery/programs";
  const search = path === "activities" || programs;
  const method = request.method;
  const allowed =
    (search && method === "GET") ||
    (activityRoute && method === "GET") ||
    (logoRoute && method === "GET") ||
    (segments.length === 2 && segments[0] === "cards" && method === "GET") ||
    (path === "guest/cards" && method === "GET") ||
    (segments.length === 3 &&
      segments[0] === "guest" &&
      segments[1] === "cards" &&
      ["PUT", "DELETE"].includes(method)) ||
    (path === "guest/session" && method === "DELETE");
  if (!allowed) return failure(404, "NOT_FOUND");
  if (id && !(activityRoute || logoRoute ? activityId(id) : uuid.safeParse(id).success))
    return failure(422, "INVALID_INPUT");
  const query = search
    ? searchQuery(new URL(request.url), programs)
    : logoRoute
      ? logoQuery(new URL(request.url))
      : undefined;
  if ((search || logoRoute) && !query) return failure(422, "INVALID_INPUT");
  const mutation = method !== "GET";
  if (
    mutation &&
    (request.headers.get("origin") !== new URL(request.url).origin ||
      request.headers.get("x-dearby-request") !== "1" ||
      request.headers.get("content-type") !== "application/json")
  )
    return failure(403, "FORBIDDEN");
  // Only a guest save forwards a body, and only `{activityId}`; no bearer token, forwarding header, or proxy key goes upstream.
  let body: { activityId?: number } | undefined;
  if (mutation) {
    try {
      const parsed: unknown = await request.json();
      const guestSave = guest && segments[1] === "cards" && method === "PUT";
      const accepted = guestSave
        ? guestSaveBody.safeParse(parsed)
        : z.object({}).strict().safeParse(parsed);
      if (!accepted.success) return failure(422, "INVALID_INPUT");
      if (guestSave && "activityId" in accepted.data && accepted.data.activityId !== undefined)
        body = { activityId: accepted.data.activityId };
    } catch {
      return failure(422, "INVALID_INPUT");
    }
  }
  const token = readGuestCookie(request.headers.get("cookie"));
  if (guest && token !== undefined && !TOKEN.test(token)) {
    if (path === "guest/session" && method === "DELETE")
      return new Response(null, {
        status: 204,
        headers: { ...headers, "Set-Cookie": guestCookie("", true) },
      });
    return failure(401, "GUEST_SESSION_INVALID");
  }
  if (path === "guest/cards" && !token)
    return Response.json({ items: [] }, { headers });
  if (path === "guest/session" && !token)
    return new Response(null, { status: 204, headers });
  if (!process.env.DEARBY_API_ORIGIN || (guest && !guestProxyKey()))
    return failure(503, "GUEST_UNAVAILABLE");
  if (!apiOrigin()) return failure(503, "SERVICE_UNAVAILABLE");
  try {
    const result = await upstreamFetch(path, {
      method,
      guest,
      token,
      body,
      query,
      accept: logoRoute ? "image/*" : undefined,
      acceptLanguage:
        search || activityRoute
          ? acceptLanguage(request.headers.get("accept-language"))
          : undefined,
    });
    if (path === "guest/session" && (result.ok || result.status === 401)) {
      return new Response(null, {
        status: 204,
        headers: { ...headers, "Set-Cookie": guestCookie("", true) },
      });
    }
    if (!result.ok) {
      const status = [401, 403, 404, 409, 422, 429, 503].includes(result.status)
        ? result.status
        : 502;
      // Never forward arbitrary error bodies, tokens, or upstream headers.
      return failure(
        status,
        status === 401 ? "GUEST_SESSION_INVALID" : "REQUEST_FAILED",
      );
    }
    if (logoRoute) {
      // Pass the image and the API's cache headers through (`public, max-age=31536000, immutable`; `v` busts it),
      // not this proxy's `private, no-store` set. The body is at most 1MB (admin upload cap).
      const contentType = result.headers.get("content-type") ?? "";
      if (!LOGO_TYPE.test(contentType)) throw new Error("Unexpected logo type");
      const logoHeaders: Record<string, string> = {
        "Content-Type": contentType,
        "X-Content-Type-Options": "nosniff",
      };
      for (const name of ["Cache-Control", "ETag"]) {
        const value = result.headers.get(name);
        if (value) logoHeaders[name] = value;
      }
      return new Response(await result.arrayBuffer(), { headers: logoHeaders });
    }
    const outputHeaders: Record<string, string> = { ...headers };
    if (search || activityRoute)
      outputHeaders.Vary = `${headers.Vary}, Accept-Language`;
    if (guest && token) outputHeaders["Set-Cookie"] = guestCookie(token);
    if (method === "DELETE")
      return new Response(null, { status: 204, headers: outputHeaders });
    const json: unknown = await result.json();
    if (guest && method === "PUT") {
      const saved = saveSchema.parse(json);
      if (saved.cardId.toLowerCase() !== id.toLowerCase())
        throw new Error("Mismatched save");
      if (!token && !saved.guestToken) throw new Error("Missing session");
      if (!token) outputHeaders["Set-Cookie"] = guestCookie(saved.guestToken!);
      return Response.json(
        { cardId: saved.cardId, status: saved.status },
        { status: result.status, headers: outputHeaders },
      );
    }
    const output =
      path === "activities"
        ? activityPageSchema.parse(json)
        : programs
          ? programPageSchema.parse(json)
          : activityRoute
          ? activitySchema.parse(json)
          : guest
          ? guestListSchema.parse(json)
          : cardSchema.parse(json);
    return Response.json(output, { headers: outputHeaders });
  } catch {
    return failure(502, "UPSTREAM_UNAVAILABLE");
  }
}
