import { test } from "node:test";
import assert from "node:assert/strict";
import { reverifyPublished } from "../src/reverify.mjs";
import { pageText } from "../src/source.mjs";
const quote = "공식 프로그램 2026년 하반기 참가 신청 안내입니다.";
const due = (id) => ({ id, officialUrl: `https://official.example/${id}`, quote, hosts: ["official.example"] });
test("re-verification extends only pages that still contain the quote; failures are recorded, never thrown", async () => {
  const calls = [];
  const rpc = async (name, body) => {
    calls.push({ name, body });
    return name === "catalog_reverify_candidates"
      ? [due("same"), due("changed"), due("js"), due("large"), due("timeout")]
      : body.ok;
  };
  const pages = {
    same: `<h3>공식 프로그램</h3> 2026년 하반기 참가 신청 안내입니다.`,
    changed: "<p>2027년 상반기 모집이 곧 열립니다. 감사합니다.</p>",
    js: '<div id="root"></div><script>render()</script>',
  };
  const fetchPage = async (url) => {
    const id = url.split("/").pop();
    if (id === "large") throw new Error("Official page exceeds 3 MB");
    if (id === "timeout") throw new Error("Official page deadline exceeded");
    return { url, html: pages[id], text: pageText(pages[id]) };
  };
  const results = await reverifyPublished(rpc, { fetchPage });
  assert.deepEqual(
    results.map((r) => [r.id, r.ok, r.extended, r.reason]),
    [
      ["same", true, true, null],
      ["changed", false, false, "Official evidence quote could not be confirmed"],
      ["js", false, false, "BLOCKED: Official page has no readable text"],
      ["large", false, false, "BLOCKED: Official page exceeds 3 MB"],
      ["timeout", false, false, "Official page deadline exceeded"],
    ],
  );
  const same = calls.find((c) => c.body?.activity_id === "same").body;
  assert.match(same.body_sha256, /^[0-9a-f]{64}$/);
  assert.equal(calls.find((c) => c.body?.activity_id === "large").body.body_sha256, null);
  assert.equal(calls.filter((c) => c.name === "reverify_catalog_activity").length, 5);
});
test("nothing due means no page fetches", async () => {
  const results = await reverifyPublished(async () => null, {
    fetchPage: async () => assert.fail("fetched without a candidate"),
  });
  assert.deepEqual(results, []);
});
