import { test } from "node:test";
import assert from "node:assert/strict";
import {
  collectionBlock,
  organizationPath,
  parentCandidates,
  discoveryStatus,
  evidenceQuoteError,
  inferredRecruitment,
  recheckState,
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
  assert.equal(discoveryStatus(item, now).label, "발견 노출 중");
  assert.equal(
    discoveryStatus(item, now + 86400000).label,
    "미노출 · 공식 확인 만료",
  );
  assert.equal(
    discoveryStatus({ ...item, publication_status: "draft" }, now).label,
    "미노출 · 초안",
  );
  assert.equal(
    discoveryStatus(
      { ...item, recruitment_end_at: new Date(now).toISOString() },
      now,
    ).label,
    "미노출 · 마감",
  );
  assert.equal(
    discoveryStatus(
      { ...item, recruitment_start_at: new Date(now + 1).toISOString() },
      now,
    ).label,
    "미노출 · 모집 예정",
  );
  assert.equal(
    discoveryStatus(
      { ...item, valid_until: new Date(now + 86400001).toISOString() },
      now,
    ).label,
    "미노출 · 공식 확인 만료",
  );
});
test("closed recruitment is not relabeled scheduled by a future opening", () => {
  assert.equal(
    discoveryStatus(
      {
        ...item,
        recruitment_status: "closed",
        recruitment_start_at: new Date(now + 3600000).toISOString(),
      },
      now,
    ).label,
    "미노출 · 마감",
  );
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
  for (const reason of [
    "Official source unavailable: JS only",
    "Official page exceeds 3 MB",
    "Official host did not resolve to a public address",
    "All candidates failed source verification",
  ])
    assert.equal(
      collectionBlock(`BLOCKED: ${reason}`).label,
      "원문 차단 · 이 프로그램만",
    );
  assert.equal(
    collectionBlock("BLOCKED: Source must use a configured official HTTPS host")
      .label,
    "설정 필요 · 이 프로그램만",
  );
  assert.deepEqual(collectionBlock(null), {
    kind: "other",
    label: "조치 필요",
    hint: "",
  });
});

test("evidence quotes follow the database rule and reject Markdown", () => {
  assert.equal(evidenceQuoteError("   "), null);
  assert.equal(
    evidenceQuoteError(
      "  2026년 하반기 모집은 10월 24일 오후 6시에 마감합니다.  ",
    ),
    null,
  );
  assert.match(evidenceQuoteError("짧은 문장")!, /20~200자/);
  assert.match(evidenceQuoteError("가".repeat(201))!, /20~200자/);
  assert.match(
    evidenceQuoteError("모집은 10월 24일까지입니다.\n자세한 내용은 공지 참고")!,
    /줄바꿈/,
  );
  assert.match(
    evidenceQuoteError("모집은 10월 24일까지 진행하며 자세한 내용은…")!,
    /생략부호/,
  );
  assert.match(
    evidenceQuoteError("모집은 10월 24일까지 진행하며 자세한 내용은...")!,
    /생략부호/,
  );
  assert.match(
    evidenceQuoteError("**모집 마감** 2026년 10월 24일 오후 6시까지")!,
    /마크다운/,
  );
});

test("automatic re-check status flags activities that need manual verification", () => {
  const until = now + 3 * 3600000 + 5 * 60000;
  const published = { ...item, valid_until: new Date(until).toISOString() };
  const evidence = {
    activity_id: "a",
    quote: "2026년 하반기 모집은 10월 24일 오후 6시에 마감합니다.",
    verified_at: new Date(now).toISOString(),
    last_check_at: null,
    last_check_ok: null,
    last_error: null,
  };
  assert.equal(
    recheckState({ ...published, publication_status: "draft" }, evidence, now),
    null,
  );
  assert.deepEqual(recheckState(published, evidence, now), {
    label: "자동 재확인 대기",
    color: "default",
    detail: "만료까지 3시간 5분",
  });
  assert.equal(
    recheckState(
      published,
      {
        ...evidence,
        last_check_ok: true,
        last_check_at: new Date(now).toISOString(),
      },
      now,
    )!.label,
    "자동 재확인 성공",
  );
  const missing = recheckState(published, undefined, now)!;
  assert.equal(missing.label, "수동 확인 필요");
  assert.match(missing.detail, /구절이 없어.*만료까지 3시간 5분/);
  const blocked = recheckState(
    published,
    {
      ...evidence,
      last_check_ok: false,
      last_error: "BLOCKED: Official page exceeds 3 MB",
    },
    now,
  )!;
  assert.equal(blocked.label, "수동 확인 필요");
  assert.match(blocked.detail, /^재확인 불가\(원문 차단\)/);
  const transient = recheckState(
    published,
    {
      ...evidence,
      last_check_ok: false,
      last_error: "Evidence quote not found",
    },
    now,
  )!;
  assert.match(
    transient.detail,
    /^자동 재확인 실패 · 1시간 뒤 다시 시도: Evidence quote not found/,
  );
  assert.match(
    recheckState(
      { ...published, valid_until: new Date(now - 1).toISOString() },
      evidence,
      now,
    )!.detail,
    /만료/,
  );
  assert.equal(
    recheckState({ ...published, freshness: "stale" }, evidence, now)!.label,
    "수동 확인 필요",
  );
});

test("recruitment status follows the period unless an administrator closed it", () => {
  const at = (offset: number) => new Date(now + offset).toISOString();
  const period = (
    status: Activity["recruitment_status"],
    start: string | null,
    end: string | null,
  ) =>
    inferredRecruitment(
      {
        recruitment_status: status,
        recruitment_start_at: start,
        recruitment_end_at: end,
      },
      now,
    );
  assert.deepEqual(period("open", at(-1), at(1)), {
    status: "open",
    byPeriod: true,
  });
  assert.deepEqual(period("unknown", at(-1), at(1)), {
    status: "open",
    byPeriod: true,
  });
  assert.deepEqual(period("open", at(1), at(2)), {
    status: "scheduled",
    byPeriod: true,
  });
  assert.deepEqual(period("open", at(-2), at(0)), {
    status: "closed",
    byPeriod: true,
  });
  assert.deepEqual(period("unknown", null, at(1)), {
    status: "open",
    byPeriod: true,
  });
  assert.deepEqual(period("closed", at(-1), at(1)), {
    status: "closed",
    byPeriod: false,
  });
  assert.deepEqual(period("scheduled", null, null), {
    status: "scheduled",
    byPeriod: false,
  });
  // Published and verified, but no confirmation recorded: the Let'Swift case.
  assert.equal(
    discoveryStatus(
      {
        ...item,
        freshness: "stale",
        source_checked_at: null,
        valid_until: null,
      },
      now,
    ).label,
    "미노출 · 공식 확인 없음",
  );
  assert.match(
    recheckState(
      {
        ...item,
        freshness: "stale",
        source_checked_at: null,
        valid_until: null,
      },
      undefined,
      now,
    )!.detail,
    /확인 기록이 없어요/,
  );
  assert.equal(
    discoveryStatus(
      {
        ...item,
        recruitment_status: "unknown",
        recruitment_start_at: at(-1),
        recruitment_end_at: at(1),
      },
      now,
    ).label,
    "발견 노출 중",
  );
});

test("organization parents exclude self, descendants and moves deeper than four levels", () => {
  const org = (id: string, parent_id: string | null) => ({
    id,
    name: id,
    description: "",
    parent_id,
  });
  const all = [
    org("A", null),
    org("B", "A"),
    org("C", "B"),
    org("D", "C"),
    org("X", null),
    org("Y", "X"),
  ];
  assert.deepEqual(
    organizationPath(all, "D").map((o) => o.id),
    ["A", "B", "C", "D"],
  );
  // B carries C and D, so only level-1 parents keep it within four levels.
  assert.deepEqual(
    parentCandidates(all, "B").map((o) => o.id),
    ["A", "X"],
  );
  // X has two levels, so it fits only under a level-1 or level-2 parent.
  assert.deepEqual(
    parentCandidates(all, "X").map((o) => o.id),
    ["A", "B"],
  );
  assert.deepEqual(
    parentCandidates(all).map((o) => o.id),
    ["A", "B", "C", "X", "Y"],
  );
});
