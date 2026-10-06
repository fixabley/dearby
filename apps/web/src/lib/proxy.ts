import { z } from "zod";
import {
  cardSchema,
  catalogSchema,
  guestShareSchema,
  sharePageSchema,
  uuid,
} from "./models";

const TOKEN = /^[A-Za-z0-9_-]{43}$/;
const MAX_AGE = 34_560_000;
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

export async function proxy(
  request: Request,
  segments: string[],
): Promise<Response> {
  const path = segments.join("/");
  const guest = segments[0] === "guest";
  // Card ID, or share ID for share routes.
  const id = segments[guest ? 2 : 1];
  const shareRoute = segments[guest ? 1 : 0] === "shares";
  const method = request.method;
  const allowed =
    (path === "catalog" && method === "GET") ||
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
  if (id && !uuid.safeParse(id).success) return failure(422, "INVALID_INPUT");
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
  const cookieName =
    process.env.NODE_ENV === "production"
      ? "__Host-dearby_guest"
      : "dearby_guest_dev";
  const cookie = request.headers
    .get("cookie")
    ?.split(";")
    .map((part) => part.trim())
    .find((part) => part.startsWith(`${cookieName}=`));
  const token = cookie?.slice(cookieName.length + 1);
  function setCookie(value: string, remove = false) {
    return `${cookieName}=${value}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${remove ? 0 : MAX_AGE}${process.env.NODE_ENV === "production" ? "; Secure" : ""}`;
  }
  if (guest && cookie !== undefined && !TOKEN.test(token ?? "")) {
    if (path === "guest/session" && method === "DELETE")
      return new Response(null, {
        status: 204,
        headers: { ...headers, "Set-Cookie": setCookie("", true) },
      });
    return failure(401, "GUEST_SESSION_INVALID");
  }
  if (path === "guest/cards" && !token)
    return Response.json({ items: [], shares: [] }, { headers });
  if (path === "guest/session" && !token)
    return new Response(null, { status: 204, headers });
  const origin = process.env.DEARBY_API_ORIGIN;
  const secret = process.env.GUEST_PROXY_SECRET;
  if (!origin || (guest && (!secret || secret.length < 32)))
    return failure(503, "GUEST_UNAVAILABLE");
  try {
    const upstream = new URL(origin);
    if (
      upstream.username ||
      upstream.password ||
      upstream.pathname !== "/" ||
      upstream.search ||
      upstream.hash ||
      (upstream.protocol !== "https:" &&
        !(
          process.env.NODE_ENV !== "production" &&
          upstream.protocol === "http:" &&
          ["localhost", "127.0.0.1"].includes(upstream.hostname)
        ))
    ) {
      return failure(503, "SERVICE_UNAVAILABLE");
    }
    const upstreamHeaders: Record<string, string> = {
      Accept: "application/json",
    };
    if (guest) {
      upstreamHeaders["X-Guest-Proxy-Key"] = secret!;
      if (token) upstreamHeaders["X-Guest-Token"] = token;
    }
    const result = await fetch(new URL(`/v1/${path}`, upstream), {
      method,
      headers: upstreamHeaders,
      cache: "no-store",
      redirect: "error",
      signal: AbortSignal.timeout(10_000),
    });
    if (path === "guest/session" && (result.ok || result.status === 401)) {
      return new Response(null, {
        status: 204,
        headers: { ...headers, "Set-Cookie": setCookie("", true) },
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
    if (guest && token) outputHeaders["Set-Cookie"] = setCookie(token);
    if (method === "DELETE")
      return new Response(null, { status: 204, headers: outputHeaders });
    const json: unknown = await result.json();
    if (guest && method === "PUT") {
      const saved = saveSchema.parse(json);
      const echoed = shareRoute ? saved.shareId : saved.cardId;
      if (echoed?.toLowerCase() !== id.toLowerCase())
        throw new Error("Mismatched save");
      if (!token && !saved.guestToken) throw new Error("Missing session");
      if (!token) outputHeaders["Set-Cookie"] = setCookie(saved.guestToken!);
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
      path === "catalog"
        ? catalogSchema.parse(json)
        : guest
          ? savedSchema.parse(json)
          : cardSchema.parse(json);
    return Response.json(output, { headers: outputHeaders });
  } catch {
    return failure(502, "UPSTREAM_UNAVAILABLE");
  }
}
