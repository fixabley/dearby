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
 * `page_location` without query or fragment (`?code=` card QR codes, tokens) and with the card id templated: a card URL
 * identifies one person, an activity URL is public.
 */
export function pageLocation(pathname: string): string {
  return window.location.origin + pathname.replace(/^\/cards\/[^/]+/, "/cards/[id]");
}
