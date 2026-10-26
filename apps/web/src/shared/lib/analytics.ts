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

/** `screen_view(screen_name)`: the web has no screens, so only the apps' shared names are sent (`card_qr`, analytics-v1.md). */
export function screenView(name: string) {
  window.gtag?.("event", "screen_view", { screen_name: name });
}

const EXCHANGED_KEY = "dearby.exchanged";
const MILESTONE_KEY = "dearby.exchange_milestone";
function readIds(): Set<string> {
  try {
    const raw = localStorage.getItem(EXCHANGED_KEY);
    return new Set(raw ? (JSON.parse(raw) as string[]) : []);
  } catch {
    return new Set();
  }
}
/**
 * 명함 교환 3회 지표 (analytics-v1.md "비공개 테스트 성공 지표" › 지표 2): compares the wallet with the receipts that were reciprocal
 * last time, sends `card_exchange_completed` once per newly reciprocal receipt, keeps the `card_exchanges` user property and sends
 * `card_exchange_milestone` the first time the count reaches 3. Ids stay in this browser only; `clearExchangeMemory` on sign-out.
 */
export function noteExchanges(receipts: { id: string; reciprocal: boolean; activityId: number | null }[], via: "reciprocate" | "wallet") {
  const before = readIds();
  const now = receipts.filter((receipt) => receipt.reciprocal);
  for (const receipt of now)
    if (!before.has(receipt.id)) track("card_exchange_completed", { via, ...(receipt.activityId !== null && { activity_id: receipt.activityId }) });
  window.gtag?.("set", "user_properties", { card_exchanges: String(now.length) });
  try {
    localStorage.setItem(EXCHANGED_KEY, JSON.stringify(now.map((receipt) => receipt.id)));
    if (now.length >= 3 && !localStorage.getItem(MILESTONE_KEY)) {
      track("card_exchange_milestone", { count: 3 });
      localStorage.setItem(MILESTONE_KEY, "1");
    }
  } catch {
    // Storage blocked: the events above were still sent; the next load may repeat `card_exchange_completed`.
  }
}
export function clearExchangeMemory() {
  try {
    localStorage.removeItem(EXCHANGED_KEY);
    localStorage.removeItem(MILESTONE_KEY);
  } catch {
    // nothing stored
  }
  window.gtag?.("set", "user_properties", { card_exchanges: null });
}
