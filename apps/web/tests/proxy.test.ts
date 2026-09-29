import assert from "node:assert/strict";
import { afterEach, beforeEach, test } from "node:test";
import { proxy } from "../src/lib/proxy";
import { card, cardId } from "./fixtures";
const originalFetch = globalThis.fetch;
const originalEnv = { ...process.env };
const token = "a".repeat(43);
function req(
  path: string,
  method = "GET",
  cookie?: string,
  extra?: Record<string, string>,
) {
  return new Request(`https://dearby.wid.io.kr/api/${path}`, {
    method,
    headers: {
      ...(cookie ? { cookie: `__Host-dearby_guest=${cookie}` } : {}),
      ...(method !== "GET"
        ? {
            origin: "https://dearby.wid.io.kr",
            "content-type": "application/json",
            "x-dearby-request": "1",
          }
        : {}),
      ...extra,
    },
    ...(method !== "GET" ? { body: "{}" } : {}),
  });
}
async function call(
  path: string,
  method = "GET",
  cookie?: string,
  extra?: Record<string, string>,
) {
  return proxy(req(path, method, cookie, extra), path.split("/"));
}
beforeEach(() => {
  Object.assign(process.env, {
    NODE_ENV: "production",
    DEARBY_API_ORIGIN: "https://wid.io.kr",
    GUEST_PROXY_SECRET: "s".repeat(32),
  });
});
afterEach(() => {
  globalThis.fetch = originalFetch;
  process.env = { ...originalEnv };
});
test("public proxy allowlist cannot access private profile, wallet, list, auth, or arbitrary upstream", async () => {
  globalThis.fetch = async () => {
    throw new Error("should not call");
  };
  for (const path of [
    "profile",
    "wallet",
    "cards",
    "auth/sessions",
    "guest/session/new",
    "https:/evil.test",
    `cards/${cardId}/profile`,
  ])
    assert.equal((await call(path)).status, 404);
  assert.equal((await call("cards/not-a-uuid")).status, 422);
});
test("no-cookie saved list is empty with no upstream call/session creation", async () => {
  globalThis.fetch = async () => {
    throw new Error("should not call");
  };
  const response = await call("guest/cards");
  assert.deepEqual(await response.json(), { items: [] });
  assert.equal(response.headers.get("set-cookie"), null);
});
test("first save consumes token, strips secrets, and sets host-only secure cookie", async () => {
  globalThis.fetch = async (url, init) => {
    assert.equal(String(url), `https://wid.io.kr/v1/guest/cards/${cardId}`);
    const headers = new Headers(init?.headers);
    assert.equal(headers.get("X-Guest-Proxy-Key"), "s".repeat(32));
    assert.equal(headers.get("X-Guest-Token"), null);
    assert.equal(headers.get("Authorization"), null);
    assert.equal(headers.get("cookie"), null);
    assert.equal(init?.cache, "no-store");
    assert.equal(init?.redirect, "error");
    return Response.json(
      { cardId, status: "saved", guestToken: token, privateProfile: "private" },
      { status: 201 },
    );
  };
  const response = await call(`guest/cards/${cardId}`, "PUT", undefined, {
    "X-Guest-Proxy-Key": "attacker",
    "X-Guest-Token": "attacker",
    authorization: "Bearer attacker",
  });
  assert.equal(response.status, 201);
  assert.deepEqual(await response.json(), { cardId, status: "saved" });
  assert.equal(
    response.headers.get("set-cookie"),
    `__Host-dearby_guest=${token}; Path=/; HttpOnly; SameSite=Lax; Max-Age=34560000; Secure`,
  );
  assert.match(response.headers.get("cache-control")!, /no-store/);
});
test("existing token forwards only cookie token and renews the same session", async () => {
  globalThis.fetch = async (_, init) => {
    assert.equal(new Headers(init?.headers).get("x-guest-token"), token);
    return Response.json({
      items: [{ ...card, ownerId: "private-id", privateProfile: "secret" }],
    });
  };
  const response = await call("guest/cards", "GET", token);
  assert.match(response.headers.get("set-cookie")!, new RegExp(token));
  assert.deepEqual(await response.json(), { items: [card] });
});
test("cross-origin, missing custom header, non-JSON and invalid UUID requests never reach API", async () => {
  globalThis.fetch = async () => {
    throw new Error("should not call");
  };
  for (const extra of [
    { origin: "https://evil.test" },
    { "x-dearby-request": "" },
    { "content-type": "text/plain" },
  ] as Record<string, string>[])
    assert.equal(
      (await call(`guest/cards/${cardId}`, "PUT", token, extra)).status,
      403,
    );
  assert.equal((await call("guest/cards/not-a-uuid", "PUT")).status, 422);
});
test("missing secret fails closed; upstream HTTP is forbidden in production", async () => {
  globalThis.fetch = async () => {
    throw new Error("should not call");
  };
  delete process.env.GUEST_PROXY_SECRET;
  assert.equal((await call(`guest/cards/${cardId}`, "PUT")).status, 503);
  process.env.DEARBY_API_ORIGIN = "http://wid.io.kr";
  assert.equal((await call(`cards/${cardId}`)).status, 503);
});
test("revocation, capacity, invalid sessions and upstream failures retain error status and never fake empty success", async () => {
  for (const status of [401, 404, 409, 429, 503, 500]) {
    globalThis.fetch = async () =>
      Response.json(
        { guestToken: token, error: { message: "secret" } },
        { status },
      );
    const response = await call(`guest/cards/${cardId}`, "PUT", token);
    assert.equal(response.status, status === 500 ? 502 : status);
    assert.equal(response.headers.get("set-cookie"), null);
    assert.ok(!JSON.stringify(await response.json()).includes(token));
  }
});
test("missing first-save token and malformed public DTO fail closed", async () => {
  globalThis.fetch = async () =>
    Response.json({ cardId, status: "saved" }, { status: 201 });
  assert.equal((await call(`guest/cards/${cardId}`, "PUT")).status, 502);
  globalThis.fetch = async () => Response.json({ privateProfile: card });
  assert.equal((await call(`cards/${cardId}`)).status, 502);
});
test("session deletion clears cookie only after API success or confirmed invalid token", async () => {
  for (const status of [204, 401, 500]) {
    globalThis.fetch = async () => new Response(null, { status });
    const response = await call("guest/session", "DELETE", token);
    assert.equal(response.status, status === 500 ? 502 : 204);
    if (status === 500) assert.equal(response.headers.get("set-cookie"), null);
    else assert.match(response.headers.get("set-cookie")!, /Max-Age=0/);
  }
});

test("empty or malformed existing cookies cannot silently start a replacement session", async () => {
  globalThis.fetch = async () => {
    throw new Error("should not call");
  };
  for (const value of ["", "invalid", "a".repeat(44)]) {
    const request = req(`guest/cards/${cardId}`, "PUT", undefined, {
      cookie: `__Host-dearby_guest=${value}`,
    });
    assert.equal(
      (await proxy(request, ["guest", "cards", cardId])).status,
      401,
    );
  }
});
