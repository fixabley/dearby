export class RequestError extends Error {
  /**
   * `code`: the proxy's `{error: {code}}`, e.g. `BLOCKED` (contract "명함 신고·차단"); `detail`: the API's user-facing sentence,
   * forwarded only for account and sign-in routes (contract "웹 로그인").
   */
  constructor(
    public status: number,
    public code?: string,
    public detail?: string,
  ) {
    super("Request failed");
  }
}
export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, {
    ...init,
    credentials: "same-origin",
    cache: "no-store",
  });
  if (!response.ok) {
    const body: unknown = await response.json().catch(() => undefined);
    const error = (body as { error?: { code?: unknown; detail?: unknown } } | undefined)?.error;
    throw new RequestError(
      response.status,
      typeof error?.code === "string" ? error.code : undefined,
      typeof error?.detail === "string" ? error.detail : undefined,
    );
  }
  return response.status === 204 ? (undefined as T) : response.json();
}
export function errorMessage(error: unknown): string {
  if (error instanceof RequestError && error.status === 429)
    return "요청이 많아 잠시 쉬고 있어요. 잠시 후 다시 시도해 주세요.";
  return "서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.";
}
/** Headers every same-origin mutation carries (the proxy's CSRF guard). */
export const JSON_HEADERS = { "Content-Type": "application/json", "X-Dearby-Request": "1" };

/**
 * Refreshes the session once (contract "웹 로그인" › refresh 회전): `POST /api/auth/refresh` under a Web Lock so tabs take turns, and the
 * route itself skips the rotation when another caller just did it. `false` means the refresh token was refused — signed out.
 */
async function refreshOnce(): Promise<boolean> {
  const work = async () => {
    try {
      await request<unknown>("/auth/refresh", { method: "POST", headers: JSON_HEADERS, body: "{}" });
      return true;
    } catch (error) {
      if (error instanceof RequestError && error.status === 401) return false;
      throw error;
    }
  };
  return navigator.locks ? navigator.locks.request("dearby-auth-refresh", work) : work();
}
/** An account request: on 401 (expired access token) refresh once and retry; a second 401 means the session is over. */
export async function accountRequest<T>(path: string, init?: RequestInit): Promise<T> {
  try {
    return await request<T>(path, init);
  } catch (error) {
    if (!(error instanceof RequestError && error.status === 401)) throw error;
    if (!(await refreshOnce())) throw error;
    return request<T>(path, init);
  }
}
export function accountSend<T>(path: string, method: "POST" | "PUT" | "PATCH" | "DELETE", body: unknown = {}): Promise<T> {
  return accountRequest<T>(path, { method, headers: JSON_HEADERS, body: JSON.stringify(body) });
}

// Same origin Web Locks serialize first saves across tabs before a cookie exists.
export async function mutateGuest<T>(
  path: string,
  method: "PUT" | "POST" | "DELETE",
  body: Record<string, unknown> = {},
): Promise<T> {
  if (!navigator.locks) throw new Error("WEB_LOCKS_UNAVAILABLE");
  return navigator.locks.request("dearby-guest-wallet", () =>
    request<T>(path, {
      method,
      headers: { "Content-Type": "application/json", "X-Dearby-Request": "1" },
      body: JSON.stringify(body),
    }),
  );
}

/** Failure handling for card saves; `missing` means the card is gone. */
export function saveFailure(error: unknown): "missing" | string {
  if (error instanceof RequestError && error.status === 404) return "missing";
  if (error instanceof RequestError && error.status === 401)
    return "저장 세션을 사용할 수 없어요. 저장한 명함 화면에서 세션을 초기화해 주세요.";
  if (error instanceof RequestError && error.status === 409 && error.code === "BLOCKED")
    return "차단한 사람의 명함이에요";
  if (error instanceof RequestError && error.status === 409)
    return "저장 가능한 한도에 도달했어요. 저장한 명함을 정리하거나 잠시 후 다시 시도해 주세요.";
  if (error instanceof Error && error.message === "COOKIE_UNAVAILABLE")
    return "저장을 확인하지 못했어요. 브라우저에서 쿠키를 허용한 뒤 다시 시도해 주세요.";
  if (error instanceof Error && error.message === "WEB_LOCKS_UNAVAILABLE")
    return "이 브라우저에서는 안전한 저장을 지원하지 않아요. 최신 브라우저에서 열어 주세요.";
  return errorMessage(error);
}
