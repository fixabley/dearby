// Guest cookie rules and the validated API upstream, shared by the API proxy and home-screen handoff.
export const TOKEN = /^[A-Za-z0-9_-]{43}$/;
const MAX_AGE = 34_560_000;
const production = () => process.env.NODE_ENV === "production";

export const guestCookieName = () =>
  production() ? "__Host-dearby_guest" : "dearby_guest_dev";

export function guestCookie(value: string, remove = false) {
  return `${guestCookieName()}=${value}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${remove ? 0 : MAX_AGE}${production() ? "; Secure" : ""}`;
}

/** The raw guest cookie value (possibly malformed), or undefined when absent. */
export function readGuestCookie(header: string | null) {
  const name = `${guestCookieName()}=`;
  return header
    ?.split(";")
    .map((part) => part.trim())
    .find((part) => part.startsWith(name))
    ?.slice(name.length);
}

export function guestProxyKey() {
  const secret = process.env.GUEST_PROXY_SECRET;
  return secret && secret.length >= 32 ? secret : undefined;
}

/** API origin without path or credentials; https only outside development localhost. */
export function apiOrigin() {
  try {
    const upstream = new URL(process.env.DEARBY_API_ORIGIN ?? "");
    const local =
      !production() &&
      upstream.protocol === "http:" &&
      ["localhost", "127.0.0.1"].includes(upstream.hostname);
    if (
      upstream.username ||
      upstream.password ||
      upstream.pathname !== "/" ||
      upstream.search ||
      upstream.hash ||
      (upstream.protocol !== "https:" && !local)
    )
      return;
    return upstream;
  } catch {
    return;
  }
}

/** One upstream call; guest calls carry the server-held proxy key and, if given, the cookie token. */
export function upstreamFetch(
  path: string,
  {
    method = "GET",
    guest = false,
    token,
    body,
  }: { method?: string; guest?: boolean; token?: string; body?: unknown },
) {
  const origin = apiOrigin();
  const key = guestProxyKey();
  if (!origin || (guest && !key)) throw new Error("UPSTREAM_UNCONFIGURED");
  const headers: Record<string, string> = { Accept: "application/json" };
  if (guest) headers["X-Guest-Proxy-Key"] = key!;
  if (guest && token) headers["X-Guest-Token"] = token;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  return fetch(new URL(`/v1/${path}`, origin), {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: "no-store",
    redirect: "error",
    signal: AbortSignal.timeout(10_000),
  });
}
