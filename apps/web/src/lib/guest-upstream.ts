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

/**
 * One upstream call with an already validated query; guest calls carry the server-held proxy key and, if given, the cookie token;
 * account calls carry the access token from the session cookie as `Authorization: Bearer` (contract "웹 로그인").
 */
export function upstreamFetch(
  path: string,
  {
    method = "GET",
    guest = false,
    token,
    bearer,
    body,
    query,
    accept = "application/json",
    acceptLanguage,
    ifMatch,
    revalidate,
    formData,
  }: {
    method?: string;
    guest?: boolean;
    token?: string;
    /** Access token of the signed-in account; never a browser-supplied header. */
    bearer?: string;
    body?: unknown;
    query?: URLSearchParams;
    /** Response kind asked of the API; JSON everywhere except the program logo image. */
    accept?: string;
    /** Already validated; the API answers activity text in ko or en from it. */
    acceptLanguage?: string;
    /** Already validated `If-Match` of an account mutation (the profile version check). */
    ifMatch?: string;
    /** Seconds to keep the response in the data cache (public, cookie-free reads such as Open Graph); otherwise never cached. */
    revalidate?: number;
    /** Multipart upload (photo, 인증자료): sent as is so the runtime sets the boundary; `body` is ignored then. */
    formData?: FormData;
  },
) {
  const origin = apiOrigin();
  const key = guestProxyKey();
  if (!origin || (guest && !key)) throw new Error("UPSTREAM_UNCONFIGURED");
  const headers: Record<string, string> = { Accept: accept };
  if (guest) headers["X-Guest-Proxy-Key"] = key!;
  if (guest && token) headers["X-Guest-Token"] = token;
  if (bearer) headers.Authorization = `Bearer ${bearer}`;
  if (body !== undefined && !formData) headers["Content-Type"] = "application/json";
  if (acceptLanguage) headers["Accept-Language"] = acceptLanguage;
  if (ifMatch) headers["If-Match"] = ifMatch;
  const url = new URL(`/v1/${path}`, origin);
  if (query) url.search = query.toString();
  return fetch(url, {
    method,
    headers,
    body: formData ?? (body === undefined ? undefined : JSON.stringify(body)),
    ...(revalidate === undefined ? { cache: "no-store" as const } : { next: { revalidate } }),
    redirect: "error",
    signal: AbortSignal.timeout(10_000),
  });
}
