// Browser side of the web sign-in (contract native-v1.md "웹 로그인"): same-origin `/api/auth/*` and the account routes of `/api/*`.
// Tokens never arrive here; the server keeps them in HttpOnly cookies.
import { mutateGuest, request, RequestError } from "@/lib/client";
import type { ProfileConsent } from "@/lib/models";

const json = { "Content-Type": "application/json", "X-Dearby-Request": "1" };
function send<T>(path: string, method: "POST" | "PATCH" | "DELETE", body: unknown = {}) {
  return request<T>(path, { method, headers: json, body: JSON.stringify(body) });
}

/**
 * Refreshes the session once (contract "refresh 회전"): `POST /api/auth/refresh` under a Web Lock so tabs take turns, and the route
 * itself skips the rotation when another caller just did it. `false` means the refresh token was refused — signed out.
 */
async function refreshOnce(): Promise<boolean> {
  const work = async () => {
    try {
      await send<unknown>("/auth/refresh", "POST");
      return true;
    } catch (error) {
      if (error instanceof RequestError && error.status === 401) return false;
      throw error;
    }
  };
  return navigator.locks ? navigator.locks.request("dearby-auth-refresh", work) : work();
}
/** An account request: on 401 (expired access token) refresh once and retry; a second 401 means the session is over. */
async function account<T>(path: string, init?: RequestInit): Promise<T> {
  try {
    return await request<T>(path, init);
  } catch (error) {
    if (!(error instanceof RequestError && error.status === 401)) throw error;
    if (!(await refreshOnce())) throw error;
    return request<T>(path, init);
  }
}
function accountSend<T>(path: string, method: "POST" | "PATCH" | "DELETE", body: unknown = {}) {
  return account<T>(path, { method, headers: json, body: JSON.stringify(body) });
}

export type Session = { signedIn: boolean; consent?: ProfileConsent | null };
/** Signed out when the session route says so, or when its 401 survives a refresh. */
export const session = () =>
  account<Session>("/auth/session").catch((error) => {
    if (error instanceof RequestError && error.status === 401) return { signedIn: false } satisfies Session;
    throw error;
  });
export const logout = () => send<void>("/auth/logout", "POST");

/** Standard WebAuthn JSON (`PublicKeyCredential*OptionsJSON`), passed through from the API. */
export type Options = { challengeId: string; options: Record<string, unknown> };
export const passkeyOptions = () => send<Options>("/auth/passkey/options", "POST");
export const passkeySignIn = (challengeId: string, credential: unknown) => send<{ user: { id: string } }>("/auth/passkey", "POST", { challengeId, credential });

export type Provider = "apple" | "google";
/** 200 → signed in; 202 → a first-time account that still has to be created or linked (`linkToken`, 10 minutes, once). */
export type SocialResult = { kind: "signedIn" } | { kind: "needsAccount"; linkToken: string; email: string | null };
async function social(path: string, body: unknown): Promise<SocialResult> {
  const response = await fetch(`/api${path}`, { method: "POST", headers: json, body: JSON.stringify(body), credentials: "same-origin", cache: "no-store" });
  const payload: unknown = await response.json().catch(() => undefined);
  if (!response.ok) {
    const error = (payload as { error?: { code?: unknown; detail?: unknown } } | undefined)?.error;
    throw new RequestError(response.status, typeof error?.code === "string" ? error.code : undefined, typeof error?.detail === "string" ? error.detail : undefined);
  }
  if (response.status === 202) {
    const { linkToken, email } = payload as { linkToken: string; email?: string | null };
    return { kind: "needsAccount", linkToken, email: email ?? null };
  }
  return { kind: "signedIn" };
}
export const appleNonce = () => send<{ challengeId: string; nonce: string }>("/auth/apple/nonce", "POST");
export const appleSignIn = (body: { challengeId: string; identityToken: string; authorizationCode?: string; displayName?: string }) =>
  social("/auth/apple", body);
export const googleNonce = () => send<{ nonce: string }>("/auth/google/nonce", "POST");
export const googleSignIn = (idToken: string, nonce: string) => social("/auth/google", { idToken, nonce });
export const registerAccount = (provider: Provider, linkToken: string) => send<{ user: { id: string } }>(`/auth/${provider}/register`, "POST", { linkToken });
/** Links a first-time Apple/Google account to the passkey account that is signed in now. */
export const linkIdentity = (provider: Provider, linkToken: string) => accountSend<void>(`/me/identities/${provider}`, "POST", { linkToken });

export const giveConsent = (privacyVersion: string, termsVersion: string) => accountSend<void>("/me/consents", "POST", { privacyVersion, termsVersion });
export const deleteAccount = () => accountSend<void>("/me", "DELETE");

// 비로그인 저장 → 계정 합치기 (contract "웹 로그인"): count first, ask, then move; the guest token travels server-side only.
export const guestSavedCount = () => request<{ items: unknown[] }>("/guest/cards").then((list) => list.items.length);
export const importGuest = () => accountSend<{ imported: number }>("/wallet/import-guest", "POST");
/** After a move the API has closed the session's saves; dropping the cookie ends the empty guest list here too. */
export const clearGuestSession = () => mutateGuest<void>("/guest/session", "DELETE");

export type Me = { id: string; createdAt: string; signInMethods: { passkeys: number; apple: boolean; google: boolean } };
export const me = () => account<Me>("/me");
export type Passkey = { id: string; label: string; createdAt: string; lastUsedAt: string | null; backedUp: boolean; transports: string[] };
export const passkeys = () => account<{ items: Passkey[] }>("/me/passkeys");
export const deletePasskey = (id: string) => accountSend<void>(`/me/passkeys/${encodeURIComponent(id)}`, "DELETE");
export const registrationOptions = () => accountSend<Options>("/auth/passkeys/registration/options", "POST");
export const registerPasskey = (challengeId: string, credential: unknown) => accountSend<unknown>("/auth/passkeys/registration", "POST", { challengeId, credential });
