// Apple JS and Google Identity Services in the browser (contract "애플 로그인" › 웹, "구글 로그인"). Both scripts load once, on
// demand, when the sign-in sheet opens; nothing here touches Dearby tokens.

/** Public client ids baked in at build time; an empty value hides that button (same rule as the apps' build settings). */
export const APPLE_SERVICES_ID = process.env.NEXT_PUBLIC_APPLE_SERVICES_ID?.trim() || undefined;
export const GOOGLE_CLIENT_ID = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID?.trim() || undefined;
/**
 * Apple JS `redirectURI`. The API exchanges `authorizationCode` with `APPLE_CALLBACK_URL` as `redirect_uri`, so the code only
 * converts when Apple JS was given that same URL (`https://api…/v1/auth/apple/callback`, contract "웹 로그인" › 애플). Unset, the
 * page origin is used and no code is sent — sign-in still works, only Apple's refresh token is not stored.
 */
export const APPLE_REDIRECT_URI = process.env.NEXT_PUBLIC_APPLE_REDIRECT_URI?.trim() || undefined;

const loading = new Map<string, Promise<void>>();
function loadScript(src: string): Promise<void> {
  let pending = loading.get(src);
  if (!pending) {
    pending = new Promise((resolve, reject) => {
      const script = document.createElement("script");
      script.src = src;
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => {
        loading.delete(src);
        reject(new Error("SCRIPT_FAILED"));
      };
      document.head.append(script);
    });
    loading.set(src, pending);
  }
  return pending;
}

// Apple ---------------------------------------------------------------------------------------------------------------
export type AppleSignInResponse = {
  authorization: { code: string; id_token: string; state?: string };
  /** Only on the first authorisation for this Services ID. */
  user?: { email?: string; name?: { firstName?: string; lastName?: string } };
};
type AppleAuth = {
  init(config: { clientId: string; scope?: string; redirectURI: string; nonce?: string; usePopup?: boolean }): void;
  signIn(): Promise<AppleSignInResponse>;
};
declare global {
  interface Window {
    AppleID?: { auth: AppleAuth };
    google?: { accounts: { id: GoogleId } };
  }
}
const APPLE_JS = "https://appleid.cdn-apple.com/appleauth/static/jsapi/appleid/1/ko_KR/appleid.auth.js";

/**
 * Loads Apple JS and sets the nonce for the next `signIn`. The nonce goes in raw (the server compares the `nonce` claim with the
 * one it issued); the Services ID must list the `redirectURI` even for the popup.
 */
export async function prepareApple(clientId: string, nonce: string) {
  await loadScript(APPLE_JS);
  window.AppleID!.auth.init({ clientId, scope: "name email", redirectURI: APPLE_REDIRECT_URI ?? window.location.origin, nonce, usePopup: true });
}
/** Must be called inside the click handler so the popup is not blocked. */
export function appleSignIn(): Promise<AppleSignInResponse> {
  if (!window.AppleID) return Promise.reject(new Error("APPLE_NOT_READY"));
  return window.AppleID.auth.signIn();
}
/** The popup was closed or the request cancelled: no message. */
export function appleCancelled(error: unknown) {
  const code = (error as { error?: unknown } | undefined)?.error;
  return code === "popup_closed_by_user" || code === "user_cancelled_authorize";
}
/** Apple gives the name once; Korean names read family-first without a space, others given-first. */
export function appleDisplayName(user: AppleSignInResponse["user"]): string | undefined {
  const first = user?.name?.firstName?.trim() ?? "";
  const last = user?.name?.lastName?.trim() ?? "";
  if (!first && !last) return;
  const hangul = /^[가-힣]+$/;
  return hangul.test(first) && hangul.test(last) ? `${last}${first}` : [first, last].filter(Boolean).join(" ");
}

// Google --------------------------------------------------------------------------------------------------------------
type GoogleId = {
  initialize(config: { client_id: string; callback: (response: { credential: string }) => void; nonce?: string; ux_mode?: "popup"; itp_support?: boolean }): void;
  renderButton(parent: HTMLElement, options: Record<string, string | number>): void;
};
const GIS = "https://accounts.google.com/gsi/client";

/**
 * Renders Google's own `Google로 계속하기` button (the only button path that yields an ID token) with the nonce fixed at
 * initialisation. Re-rendered with a fresh nonce after an attempt, because the server accepts each nonce once. The button is an
 * iframe of a fixed pixel width (Google allows 200–400), so it takes the host's laid-out width; the host must be sized by its
 * container (`w-full`), or an empty host measures 0 and the button could only guess.
 */
export async function renderGoogleButton(parent: HTMLElement, clientId: string, nonce: string, onCredential: (idToken: string) => void) {
  await loadScript(GIS);
  const id = window.google!.accounts.id;
  id.initialize({ client_id: clientId, nonce, callback: (response) => onCredential(response.credential), ux_mode: "popup", itp_support: true });
  parent.replaceChildren();
  const dark = window.matchMedia("(prefers-color-scheme: dark)").matches;
  id.renderButton(parent, {
    type: "standard",
    theme: dark ? "filled_black" : "outline",
    size: "large",
    text: "continue_with",
    shape: "rectangular",
    logo_alignment: "center",
    width: Math.min(400, Math.max(200, parent.clientWidth)),
    locale: "ko",
  });
}
