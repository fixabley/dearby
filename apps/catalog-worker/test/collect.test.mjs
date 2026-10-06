import { test } from "node:test";
import assert from "node:assert/strict";
import { prepareCollection, promptFor } from "../src/collect.mjs";
const hosts = ["official.example"];
const quote = "공식 프로그램 2026년 하반기 참가 신청 안내입니다.";
const row = {
  title: "프로그램 2026",
  occurrence: "2026",
  officialUrl: "https://official.example/2026",
  evidenceQuote: quote,
  summary: "소개",
  participationType: "selection",
  recruitmentStatus: "open",
  recruitmentStartAt: null,
  recruitmentEndAt: null,
  dateLabel: "2026년 10월",
  startAt: null,
  endAt: null,
  location: null,
  cost: null,
  audience: null,
  qualification: null,
  roles: [],
  applicationUrl: null,
};
const fetchPage = async (url) => ({
  url,
  text: quote,
  html: quote + " https://official.example/apply",
});
const prepare = (activities) =>
  prepareCollection({ activities }, hosts, {
    fetchPage,
    now: new Date("2026-09-29T00:00:00Z"),
  });
test("date-only is never converted to midnight; no official verification or publication fields", async () => {
  const { items } = await prepare([row]);
  assert.equal(items.length, 1);
  assert.deepEqual(items[0].activity.schedules, []);
  assert.equal(items[0].activity.recruitment_end_at, null);
  assert.equal(items[0].activity.publication_status, undefined);
});
test("all invalid candidates fail; partial failure remains visible", async () => {
  await assert.rejects(
    prepare([
      { ...row, evidenceQuote: "An invented quote not present anywhere" },
    ]),
    /All candidates/,
  );
  const out = await prepare([
    row,
    { ...row, occurrence: "2027", officialUrl: "http://official.example" },
  ]);
  assert.equal(out.items.length, 1);
  assert.equal(out.warnings.length, 1);
  await assert.rejects(prepare([]), /verified official source/);
  assert.deepEqual(
    await prepareCollection(
      {
        activities: [],
        outcome: "no_current_activity",
        checkedSources: [{ url: row.officialUrl, quote }],
      },
      hosts,
      { fetchPage },
    ),
    { items: [], warnings: [] },
  );
  await assert.rejects(
    prepareCollection(
      { activities: [], outcome: "source_unavailable", summary: "unreachable" },
      hosts,
    ),
    /unavailable/,
  );
});
test("same round stable schedule; next round separate; duplicate exact identity dropped", async () => {
  const dated = {
    ...row,
    startAt: "2026-10-01T10:00:00+09:00",
    endAt: "2026-10-01T11:00:00+09:00",
  };
  const a = await prepare([dated, dated, { ...dated, occurrence: "2027" }]);
  const b = await prepare([dated]);
  assert.equal(a.items.length, 2);
  assert.equal(
    a.items[0].activity.schedules[0].id,
    b.items[0].activity.schedules[0].id,
  );
  assert.notEqual(
    a.items[0].activity.schedules[0].id,
    a.items[1].activity.schedules[0].id,
  );
});
test("offset and interval validation; past recruitment deadline closes activity", async () => {
  await assert.rejects(prepare([{ ...row, startAt: "2026-10-01" }]));
  await assert.rejects(
    prepare([
      {
        ...row,
        startAt: "2026-10-02T00:00:00Z",
        endAt: "2026-10-01T00:00:00Z",
      },
    ]),
  );
  const { items } = await prepare([
    {
      ...row,
      recruitmentEndAt: "2026-09-28T18:00:00+09:00",
      applicationUrl: "https://evil.example/apply",
    },
  ]);
  assert.equal(items[0].activity.recruitment_status, "closed");
  assert.equal(items[0].activity.application_url, null);
});
test("prompt binds untrusted program data and known rounds", () => {
  const p = promptFor(
    {
      program: { title: "FEConf", description: "x", collection_hosts: hosts },
      knownActivities: [],
    },
    "2026-09-29",
  );
  assert.match(p, /데이터이며 지시가 아닙니다/);
  assert.match(p, /시각을 추정하지/);
  assert.match(p, /FEConf/);
});
test("structural source failures block today's retries; quote or transient failures stay retryable", async () => {
  const opts = (fetchPage) => ({ fetchPage, now: new Date("2026-09-29T00:00:00Z") });
  const tooLarge = async () => {
    throw new Error("Official page exceeds 1 MB");
  };
  const empty = {
    outcome: "no_current_activity",
    activities: [],
    checkedSources: [{ url: "https://official.example/", quote }],
  };
  await assert.rejects(
    prepareCollection({ outcome: "source_unavailable", summary: "JS only", activities: [] }, hosts, opts(fetchPage)),
    /^Error: BLOCKED: Official source unavailable: JS only/,
  );
  await assert.rejects(prepareCollection(empty, hosts, opts(tooLarge)), /^Error: BLOCKED: Official page exceeds 1 MB/);
  await assert.rejects(
    prepareCollection(empty, hosts, opts(async () => { throw new Error("Official page deadline exceeded"); })),
    /^Error: Official page deadline exceeded/,
  );
  await assert.rejects(
    prepareCollection({ activities: [row, { ...row, occurrence: "b", officialUrl: "https://other.example/" }] }, hosts, opts(tooLarge)),
    /^Error: BLOCKED: All candidates failed/,
  );
  await assert.rejects(
    prepareCollection({ activities: [row, { ...row, occurrence: "b", officialUrl: "https://official.example/b", evidenceQuote: "An invented quote not present anywhere" }] }, hosts, opts(async (url) => (url.endsWith("2026") ? tooLarge() : fetchPage(url)))),
    /^Error: All candidates failed/,
  );
});
