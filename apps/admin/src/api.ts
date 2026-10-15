import { useRef, useSyncExternalStore } from "react";

/** Same-origin paths: Vercel rewrites and the Vite dev proxy forward them to the Kotlin API. */
/** Registration, authentication and refresh all return this; `user.roles` gates the UI (the API still enforces ADMIN). */
type Tokens = { accessToken: string; refreshToken: string; user: { id: string; roles: string[] } };
type Session = { tokens: Tokens | null; forbidden: boolean };
export type Page<T> = { items: T[]; nextCursor: number | string | null };

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

const storageKey = "dearby-admin-tokens";
let session: Session = sessionFor(readStoredTokens());
const listeners = new Set<() => void>();

function readStoredTokens(): Tokens | null {
  try {
    const value = JSON.parse(sessionStorage.getItem(storageKey) ?? "null");
    return value?.accessToken && value?.refreshToken && Array.isArray(value?.user?.roles) ? value : null;
  } catch {
    return null;
  }
}
function setSession(next: Session) {
  session = next;
  try {
    if (next.tokens) sessionStorage.setItem(storageKey, JSON.stringify(next.tokens));
    else sessionStorage.removeItem(storageKey);
  } catch {
    /* Memory still holds the session when storage is blocked. */
  }
  listeners.forEach((listener) => listener());
}
function sessionFor(tokens: Tokens | null): Session {
  return { tokens, forbidden: !!tokens && !tokens.user.roles.includes("ADMIN") };
}
function startSession(tokens: Tokens) {
  setSession(sessionFor(tokens));
}
export function useSession() {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => session,
  );
}

async function send(path: string, method: string, body: unknown, token?: string) {
  try {
    return await fetch(path, {
      method,
      headers: {
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, "서버에 연결하지 못했어요. 네트워크를 확인하고 다시 시도해 주세요.");
  }
}
async function failure(response: Response) {
  let detail = "";
  try {
    detail = (await response.json()).detail ?? "";
  } catch {
    /* Not a ProblemDetail body. */
  }
  return new ApiError(
    response.status,
    detail || (response.status >= 500 ? "서버 오류가 발생했어요. 잠시 뒤 다시 시도해 주세요." : `요청을 처리하지 못했어요 (${response.status}).`),
  );
}
async function authPost<T>(path: string, body?: unknown): Promise<T> {
  const response = await send(`/api/auth/${path}`, "POST", body);
  if (!response.ok) throw await failure(response);
  return response.status === 204 ? (undefined as T) : response.json();
}

let refreshing: Promise<Tokens> | null = null;
/** Refresh tokens rotate, so concurrent 401s share one refresh. Only a 401 from refresh ends the session. */
function refresh(used: Tokens): Promise<Tokens> {
  if (session.tokens && session.tokens.accessToken !== used.accessToken) return Promise.resolve(session.tokens);
  refreshing ??= (async () => {
    try {
      const response = await send("/api/auth/refresh", "POST", { refreshToken: used.refreshToken });
      if (response.status === 401) {
        setSession({ tokens: null, forbidden: false });
        throw new ApiError(401, "로그인이 만료됐어요. 다시 로그인해 주세요.");
      }
      if (!response.ok) throw await failure(response);
      const tokens: Tokens = await response.json();
      startSession(tokens);
      return tokens;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

export async function api<T>(path: string, method = "GET", body?: unknown): Promise<T> {
  const tokens = session.tokens;
  if (!tokens) throw new ApiError(401, "로그인이 필요해요.");
  let response = await send(`/api/admin/${path}`, method, body, tokens.accessToken);
  if (response.status === 401) {
    response = await send(`/api/admin/${path}`, method, body, (await refresh(tokens)).accessToken);
    if (response.status === 401) {
      setSession({ tokens: null, forbidden: false });
      throw new ApiError(401, "로그인이 만료됐어요. 다시 로그인해 주세요.");
    }
  }
  if (response.status === 403) {
    setSession({ ...session, forbidden: true });
    throw new ApiError(403, "관리자 권한이 없어요.");
  }
  if (!response.ok) throw await failure(response);
  return response.status === 204 ? (undefined as T) : response.json();
}

/**
 * Runs one request at a time. `isPending` reaches the UI only after a re-render, so a double click or a repeated Enter
 * inside that gap would send the same POST twice; this lock is set synchronously before the request starts.
 */
export function useSingleFlight() {
  const busy = useRef(false);
  return (run: () => Promise<unknown>) => {
    if (busy.current) return;
    busy.current = true;
    run()
      .catch(() => undefined) // The mutation keeps the error for the UI.
      .finally(() => {
        busy.current = false;
      });
  };
}

/** Re-reads roles after an operator grants ADMIN: refresh returns the current DB roles. */
export async function recheckRoles(): Promise<boolean> {
  if (session.tokens) await refresh(session.tokens);
  return !session.forbidden;
}

export async function passkey(kind: "registration" | "authentication") {
  if (typeof PublicKeyCredential === "undefined" || typeof PublicKeyCredential.parseCreationOptionsFromJSON !== "function")
    throw new Error("이 브라우저는 패스키를 지원하지 않아요. 최신 Chrome·Safari·Firefox를 사용해 주세요.");
  const { challengeId, options } = await authPost<{ challengeId: string; options: never }>(
    `passkeys/${kind}/options`,
    kind === "registration" ? {} : undefined,
  );
  let credential: Credential | null;
  try {
    credential =
      kind === "registration"
        ? await navigator.credentials.create({ publicKey: PublicKeyCredential.parseCreationOptionsFromJSON(options) })
        : await navigator.credentials.get({ publicKey: PublicKeyCredential.parseRequestOptionsFromJSON(options) });
  } catch (error) {
    const name = error instanceof DOMException ? error.name : "";
    if (name === "NotAllowedError") throw new Error("패스키 요청이 취소됐거나 시간이 지났어요. 다시 시도해 주세요.");
    if (name === "InvalidStateError") throw new Error("이 기기에 이미 등록된 패스키가 있어요. ‘패스키로 로그인’을 눌러 주세요.");
    throw new Error("패스키를 사용하지 못했어요. 이 주소에서 패스키를 쓸 수 있는지 확인해 주세요.");
  }
  if (!(credential instanceof PublicKeyCredential)) throw new Error("패스키를 받지 못했어요. 다시 시도해 주세요.");
  try {
    startSession(await authPost<Tokens>(`passkeys/${kind}`, { challengeId, credential: credential.toJSON() }));
  } catch (error) {
    if (error instanceof ApiError && error.status === 401)
      throw new Error("패스키를 확인하지 못했어요. 등록된 패스키인지 확인하거나 다시 시도해 주세요.");
    throw error;
  }
}

export async function logout() {
  const refreshToken = session.tokens?.refreshToken;
  setSession({ tokens: null, forbidden: false });
  if (refreshToken) await authPost("logout", { refreshToken }).catch(() => undefined);
}
