import assert from "node:assert/strict";
import { afterEach, beforeEach, test } from "node:test";
import { proxy } from "../src/lib/proxy";
import { card, cardId, secondCardId } from "./fixtures";
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
  assert.deepEqual(await response.json(), { items: [], shares: [] });
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
  assert.deepEqual(await response.json(), { items: [card], shares: [] });
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
const shareId = "d0000000-0000-4000-8000-000000000001";
const share = {
  id: shareId,
  cardId,
  activities: [{ id: secondCardId, title: "커넥트 IT 컨퍼런스 (테스트)" }],
  createdAt: "2026-10-02T00:00:00.000Z",
};
test("share page is public, validated, and rejects a share pointing at another card", async () => {
  globalThis.fetch = async (url) => {
    assert.equal(String(url), `https://wid.io.kr/v1/shares/${shareId}`);
    return Response.json({ share: { ...share, ownerId: "private" }, card });
  };
  const response = await call(`shares/${shareId}`);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { share, card });
  globalThis.fetch = async () =>
    Response.json({ share: { ...share, cardId: secondCardId }, card });
  assert.equal((await call(`shares/${shareId}`)).status, 502);
  assert.equal((await call("shares/not-a-uuid")).status, 422);
  for (const path of [`cards/${cardId}/shares`, `guest/shares/${shareId}/x`])
    assert.equal((await call(path, "POST")).status, 404);
  assert.equal((await call(`shares/${shareId}`, "PUT")).status, 404);
});
test("saving a share keeps guest rules: first save sets the cookie and echoes the share", async () => {
  globalThis.fetch = async (url, init) => {
    assert.equal(String(url), `https://wid.io.kr/v1/guest/shares/${shareId}`);
    assert.equal(init?.method, "PUT");
    assert.equal(new Headers(init?.headers).get("X-Guest-Token"), null);
    return Response.json(
      { cardId, shareId, status: "saved", guestToken: token },
      { status: 201 },
    );
  };
  const response = await call(`guest/shares/${shareId}`, "PUT");
  assert.equal(response.status, 201);
  assert.deepEqual(await response.json(), {
    cardId,
    shareId,
    status: "saved",
  });
  assert.match(response.headers.get("set-cookie")!, new RegExp(token));
  globalThis.fetch = async () =>
    Response.json({ cardId, shareId: secondCardId, status: "saved" });
  assert.equal(
    (await call(`guest/shares/${shareId}`, "PUT", token)).status,
    502,
  );
  assert.equal(
    (
      await call(`guest/shares/${shareId}`, "PUT", undefined, {
        origin: "https://evil.test",
      })
    ).status,
    403,
  );
});
test("saved list carries share records and strips unknown fields", async () => {
  globalThis.fetch = async () =>
    Response.json({
      items: [card],
      shares: [
        {
          cardId,
          shareId,
          activities: share.activities,
          savedAt: "x",
          token: "secret",
        },
      ],
    });
  assert.deepEqual(await (await call("guest/cards", "GET", token)).json(), {
    items: [card],
    shares: [{ cardId, shareId, activities: share.activities, savedAt: "x" }],
  });
});
