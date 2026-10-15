import { z } from "zod";
import {
  activityId,
  activitySchema,
  cardSchema,
  activityPageSchema,
  guestShareSchema,
  sharePageSchema,
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

// `shares` defaults to empty so the list still works against an API without share records.
const savedSchema = z.object({
  items: z.array(cardSchema),
  shares: z.array(guestShareSchema).default([]),
});
const saveSchema = z.object({
  cardId: uuid,
  shareId: uuid.optional(),
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
/** Only `q`, `cursor` and `size` are forwarded to the activity search, rebuilt from validated values. */
function activityQuery(url: URL): URLSearchParams | undefined {
  const query = new URLSearchParams();
  const q = url.searchParams.get("q")?.trim();
  const cursor = url.searchParams.get("cursor");
  const size = url.searchParams.get("size");
  if (q && q.length > 100) return;
  if (cursor !== null && !/^\d{1,18}$/.test(cursor)) return;
  if (size !== null && !(/^\d{1,3}$/.test(size) && +size >= 1 && +size <= 100))
    return;
  if (q) query.set("q", q);
  if (cursor !== null) query.set("cursor", cursor);
  if (size !== null) query.set("size", size);
  return query;
}

export async function proxy(
  request: Request,
  segments: string[],
): Promise<Response> {
  const path = segments.join("/");
  const guest = segments[0] === "guest";
  // Card ID, or share ID for share routes.
  const id = segments[guest ? 2 : 1];
  const shareRoute = segments[guest ? 1 : 0] === "shares";
  const activityRoute = segments.length === 2 && segments[0] === "activities";
  const method = request.method;
  const allowed =
    (path === "activities" && method === "GET") ||
    (activityRoute && method === "GET") ||
    (segments.length === 2 &&
      ["cards", "shares"].includes(segments[0]) &&
      method === "GET") ||
    (path === "guest/cards" && method === "GET") ||
    (segments.length === 3 &&
      segments[0] === "guest" &&
      segments[1] === "cards" &&
      ["PUT", "DELETE"].includes(method)) ||
    (segments.length === 3 &&
      segments[0] === "guest" &&
      segments[1] === "shares" &&
      method === "PUT") ||
    (path === "guest/session" && method === "DELETE");
  if (!allowed) return failure(404, "NOT_FOUND");
  if (id && !(activityRoute ? activityId(id) : uuid.safeParse(id).success))
    return failure(422, "INVALID_INPUT");
  const query =
    path === "activities" ? activityQuery(new URL(request.url)) : undefined;
  if (path === "activities" && !query) return failure(422, "INVALID_INPUT");
  const mutation = method !== "GET";
  if (
    mutation &&
    (request.headers.get("origin") !== new URL(request.url).origin ||
      request.headers.get("x-dearby-request") !== "1" ||
      request.headers.get("content-type") !== "application/json")
  )
    return failure(403, "FORBIDDEN");
  // No client body, bearer token, forwarding header, or proxy key is sent upstream.
  if (mutation) {
    try {
      const body = await request.json();
      if (
        typeof body !== "object" ||
        !body ||
        Array.isArray(body) ||
        Object.keys(body).length
      )
        return failure(422, "INVALID_INPUT");
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
    return Response.json({ items: [], shares: [] }, { headers });
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
      query,
      acceptLanguage:
        path === "activities" || activityRoute
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
    const outputHeaders: Record<string, string> = { ...headers };
    if (path === "activities" || activityRoute)
      outputHeaders.Vary = `${headers.Vary}, Accept-Language`;
    if (guest && token) outputHeaders["Set-Cookie"] = guestCookie(token);
    if (method === "DELETE")
      return new Response(null, { status: 204, headers: outputHeaders });
    const json: unknown = await result.json();
    if (guest && method === "PUT") {
      const saved = saveSchema.parse(json);
      const echoed = shareRoute ? saved.shareId : saved.cardId;
      if (echoed?.toLowerCase() !== id.toLowerCase())
        throw new Error("Mismatched save");
      if (!token && !saved.guestToken) throw new Error("Missing session");
      if (!token) outputHeaders["Set-Cookie"] = guestCookie(saved.guestToken!);
      return Response.json(
        shareRoute
          ? { cardId: saved.cardId, shareId: id, status: saved.status }
          : { cardId: saved.cardId, status: saved.status },
        { status: result.status, headers: outputHeaders },
      );
    }
    if (shareRoute) {
      const page = sharePageSchema.parse(json);
      if (
        page.share.id.toLowerCase() !== id.toLowerCase() ||
        page.card.id.toLowerCase() !== page.share.cardId.toLowerCase()
      )
        throw new Error("Mismatched share");
      return Response.json(page, { headers: outputHeaders });
    }
    const output =
      path === "activities"
        ? activityPageSchema.parse(json)
        : activityRoute
          ? activitySchema.parse(json)
          : guest
          ? savedSchema.parse(json)
          : cardSchema.parse(json);
    return Response.json(output, { headers: outputHeaders });
  } catch {
    return failure(502, "UPSTREAM_UNAVAILABLE");
  }
}
