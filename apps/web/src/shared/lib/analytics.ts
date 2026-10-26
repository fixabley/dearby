// 웹 사용 통계 (contract analytics-v1.md "웹"): GA4 gtag, loaded only in production with a measurement id (`GoogleAnalytics`).
// Without it `window.gtag` is missing and every call here is a no-op.
type Params = Record<string, string | number>;
declare global {
  interface Window {
    dataLayer?: unknown[];
    gtag?: (...args: unknown[]) => void;
  }
}

/** Sends one event; ids and codes only, never card contents, names or contact details. */
export function track(name: string, params?: Params) {
  window.gtag?.("event", name, params);
}

/**
 * The URL both GA (`page_location`) and Vercel Web Analytics receive: no query or fragment (`?code=` card QR codes, tokens,
 * the search `q`) and per-item paths templated (`/cards/[id]`, `/activities/[id]`); a card URL identifies one person.
 */
export function analyticsUrl(url: string): string {
  const { origin, pathname } = new URL(url);
  return origin + pathname.replace(/^\/(cards|activities)\/[^/]+/, "/$1/[id]");
}

/**
 * The one user property the web sends (contract analytics-v1.md "웹", 2026-10-26): `signed_in` is `yes` only for a signed-in
 * account that has given the current consent, `no` otherwise; set before the next event or page view.
 */
export function setSignedIn(signedIn: boolean) {
  window.gtag?.("set", "user_properties", { signed_in: signedIn ? "yes" : "no" });
}
