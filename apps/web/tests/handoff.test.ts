import assert from "node:assert/strict";
import { afterEach, beforeEach, test } from "node:test";
import { issueHandoff, redeemHandoff } from "../src/lib/handoff";
const originalFetch = globalThis.fetch;
const originalEnv = { ...process.env };
const token = "a".repeat(43);
const code = "c".repeat(43);
let calls: string[] = [];
beforeEach(() => {
  calls = [];
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
function api(routes: Record<string, () => Response>) {
  globalThis.fetch = async (url, init) => {
    const key = `${init?.method ?? "GET"} ${new URL(String(url)).pathname}`;
    calls.push(key);
    const headers = new Headers(init?.headers);
    assert.equal(headers.get("X-Guest-Proxy-Key"), "s".repeat(32));
    return routes[key]?.() ?? new Response(null, { status: 404 });
  };
}
test("codes are issued only for a session with a saved card", async () => {
  api({
    "GET /v1/guest/cards": () => Response.json({ items: [] }),
    "POST /v1/guest/handoffs": () => Response.json({ code }, { status: 201 }),
  });
  assert.equal(await issueHandoff(token), undefined);
  assert.deepEqual(calls, ["GET /v1/guest/cards"]);
  api({
    "GET /v1/guest/cards": () => Response.json({ items: [{}] }),
    "POST /v1/guest/handoffs": () => Response.json({ code }, { status: 201 }),
  });
  assert.equal(await issueHandoff(token), code);
  assert.equal(await issueHandoff("not-a-token"), undefined);
});
test("redeem sends no token and accepts only a well-formed guest token back", async () => {
  globalThis.fetch = async (_, init) => {
    assert.equal(new Headers(init?.headers).get("X-Guest-Token"), null);
    assert.deepEqual(JSON.parse(String(init?.body)), { code });
    return Response.json({ guestToken: token });
  };
  assert.equal(await redeemHandoff(code), token);
  globalThis.fetch = async () => Response.json({ guestToken: "short" });
  assert.equal(await redeemHandoff(code), undefined);
  globalThis.fetch = async () => new Response(null, { status: 404 });
  assert.equal(await redeemHandoff(code), undefined);
  globalThis.fetch = async () => {
    throw new Error("should not call");
  };
  assert.equal(await redeemHandoff("bad code"), undefined);
});
