import { test } from "node:test";
import assert from "node:assert/strict";
import {
  collectionBlock,
  discoveryStatus,
  normalizeSchedules,
  type Activity,
} from "../src/catalog";
const now = Date.parse("2026-09-29T05:00:00Z");
const item = {
  publication_status: "published",
  freshness: "verified",
  recruitment_status: "open",
  source_checked_at: new Date(now).toISOString(),
  valid_until: new Date(now + 86400000).toISOString(),
  recruitment_start_at: null,
  recruitment_end_at: null,
} as Activity;
test("admin eligibility follows native API clock boundaries and publication", () => {
  assert.equal(discoveryStatus(item, now).label, "탐색 노출");
  assert.equal(discoveryStatus(item, now + 86400000).label, "재확인 필요");
  assert.equal(
    discoveryStatus({ ...item, publication_status: "draft" }, now).label,
    "초안",
  );
  assert.equal(
    discoveryStatus(
      { ...item, recruitment_end_at: new Date(now).toISOString() },
      now,
    ).label,
    "마감 · 탐색 제외",
  );
  assert.equal(
    discoveryStatus(
      { ...item, recruitment_start_at: new Date(now + 1).toISOString() },
      now,
    ).label,
    "모집 예정",
  );
  assert.equal(
    discoveryStatus(
      { ...item, valid_until: new Date(now + 86400001).toISOString() },
      now,
    ).label,
    "재확인 필요",
  );
});
test("closed recruitment is not relabeled scheduled by a future opening", () => {
  assert.equal(discoveryStatus({...item,recruitment_status:"closed",recruitment_start_at:new Date(now+3600000).toISOString()},now).label,"모집 마감");
});

test("unknown schedule times stay null and invalid/reversed intervals fail", () => {
  const s = {
    id: "test",
    title: "일정",
    dateLabel: "10월 24일",
    timeZone: "Asia/Seoul",
    startAt: null,
    endAt: null,
  };
  assert.equal(normalizeSchedules([s])[0].startAt, null);
  assert.throws(() =>
    normalizeSchedules([{ ...s, startAt: "2026-10-24T13:00", endAt: null }]),
  );
  assert.throws(() =>
    normalizeSchedules([
      {
        ...s,
        startAt: "2026-10-24T16:00:00+09:00",
        endAt: "2026-10-24T14:00:00+09:00",
      },
    ]),
  );
  assert.throws(() => normalizeSchedules([{ ...s, timeZone: "invalid" }]));
});

test("blocked collection jobs distinguish subscription, source and host setup", () => {
  assert.equal(
    collectionBlock(
      "BLOCKED: Codex subscription quota or login requires attention",
    ).label,
    "구독 차단 · 전체 일시정지",
  );
  assert.equal(
    collectionBlock("BLOCKED: Official source unavailable: JS only").label,
    "원문 차단 · 이 프로그램만",
  );
  assert.equal(
    collectionBlock("BLOCKED: Official page exceeds 3 MB").label,
    "원문 차단 · 이 프로그램만",
  );
  assert.equal(
    collectionBlock("BLOCKED: All candidates failed").label,
    "원문 차단 · 이 프로그램만",
  );
  assert.equal(
    collectionBlock("BLOCKED: Configure official hosts for this program").label,
    "설정 필요 · 이 프로그램만",
  );
  assert.deepEqual(collectionBlock(null), { label: "조치 필요", hint: "" });
});
