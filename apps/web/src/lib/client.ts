export class RequestError extends Error {
  constructor(public status: number) {
    super("Request failed");
  }
}
export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, {
    ...init,
    credentials: "same-origin",
    cache: "no-store",
  });
  if (!response.ok) throw new RequestError(response.status);
  return response.status === 204 ? (undefined as T) : response.json();
}
export function errorMessage(error: unknown): string {
  if (error instanceof RequestError && error.status === 429)
    return "요청이 많아 잠시 쉬고 있어요. 잠시 후 다시 시도해 주세요.";
  return "서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.";
}
// Same origin Web Locks serialize first saves across tabs before a cookie exists.
export async function mutateGuest<T>(
  path: string,
  method: "PUT" | "DELETE",
): Promise<T> {
  if (!navigator.locks) throw new Error("WEB_LOCKS_UNAVAILABLE");
  return navigator.locks.request("dearby-guest-wallet", () =>
    request<T>(path, {
      method,
      headers: { "Content-Type": "application/json", "X-Dearby-Request": "1" },
      body: "{}",
    }),
  );
}

/** Shared failure handling for card and share saves; `missing` means the card or share is gone. */
export function saveFailure(error: unknown): "missing" | string {
  if (error instanceof RequestError && error.status === 404) return "missing";
  if (error instanceof RequestError && error.status === 401)
    return "저장 세션을 사용할 수 없어요. 저장한 명함 화면에서 세션을 초기화해 주세요.";
  if (error instanceof RequestError && error.status === 409)
    return "저장 가능한 한도에 도달했어요. 저장한 명함을 정리하거나 잠시 후 다시 시도해 주세요.";
  if (error instanceof Error && error.message === "COOKIE_UNAVAILABLE")
    return "저장을 확인하지 못했어요. 브라우저에서 쿠키를 허용한 뒤 다시 시도해 주세요.";
  if (error instanceof Error && error.message === "WEB_LOCKS_UNAVAILABLE")
    return "이 브라우저에서는 안전한 저장을 지원하지 않아요. 최신 브라우저에서 열어 주세요.";
  return errorMessage(error);
}
