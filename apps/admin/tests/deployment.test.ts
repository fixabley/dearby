import { test } from "node:test";
import assert from "node:assert/strict";
import { validatePublicConfig } from "../vite.config";

const jwt = (role: string) => `header.${Buffer.from(JSON.stringify({ role })).toString("base64url")}.signature`;
test("build accepts public keys and rejects server/guest credentials", () => {
  for (const key of [jwt("anon"), "sb_publishable_test"]) {
    assert.doesNotThrow(() => validatePublicConfig("https://example.supabase.co", key));
  }
  for (const key of [jwt("service_role"), jwt("authenticated"), "sb_secret_test", "guest-proxy-secret", ""]) {
    assert.throws(() => validatePublicConfig("https://example.supabase.co", key));
  }
  assert.throws(() => validatePublicConfig("http://example.supabase.co", jwt("anon")));
  assert.doesNotThrow(() => validatePublicConfig("http://127.0.0.1:54321", jwt("anon")));
});
