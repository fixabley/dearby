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
