import { test } from "node:test";
import assert from "node:assert/strict";
import { programs } from "../src/features/catalog/data";
import {
  emptyFilters as f,
  experiences,
  searchPrograms,
  suggestions,
  activityMatches,
  recruitment,
} from "../src/features/catalog/model";
test("unique programs, direction OR and AND", () => {
  assert.equal(searchPrograms(programs, f).length, 12);
  assert.ok(
    searchPrograms(programs, { ...f, roles: ["iOS", "백엔드"] }).length >
      searchPrograms(programs, {
        ...f,
        roles: ["iOS", "백엔드"],
        allRoles: true,
      }).length,
  );
});
test("same notice prevents invented cross-role matches and past evidence", () => {
  const p = programs[3];
  assert.equal(
    searchPrograms([p], { ...f, roles: ["iOS"], openOnly: true }).length,
    0,
  );
  assert.equal(
    recruitment(p, { ...f, roles: ["iOS"] }),
    "선택 직무 종료 · 다른 직무 모집 중",
  );
  assert.equal(
    searchPrograms([programs[5]], { ...f, experiences: ["mentor"] }).length,
    0,
  );
});
test("activity attribute conjunction and parent inclusion", () => {
  assert.ok(
    activityMatches(
      { action: "제작", target: "앱", method: "팀협업" },
      { action: "제작" },
    ),
  );
  assert.ok(
    !activityMatches(
      { action: "제작", target: "앱", method: "개인" },
      { action: "제작", target: "앱", method: "팀협업" },
    ),
  );
  assert.ok(
    searchPrograms(programs, { ...f, experiences: ["networking"] }).length >=
      searchPrograms(programs, { ...f, experiences: ["mentor"] }).length,
  );
});
test("zero results offer only nonempty proper subsets under original logic", () => {
  const filters = { ...f, roles: ["백엔드"], experiences: ["app"] };
  assert.equal(searchPrograms(programs, filters).length, 0);
  const options = suggestions(programs, filters);
  assert.ok(options.length);
  for (const s of options) {
    assert.ok(s.count > 0);
    assert.equal(s.count, searchPrograms(programs, s.filters).length);
    assert.ok(s.removed.length);
  }
});
test("matching open roles outrank other-role recruitment", () => {
  const result = searchPrograms(programs, { ...f, roles: ["iOS"] });
  assert.notEqual(result[0].id, "app-club");
});

test("priority experience precedes match count, then deadline and latest start", () => {
  const base = programs[0];
  const make = (
    id: string,
    keys: string[],
    deadline = "2026-10-10",
    start = "2026-09-01",
  ) => ({
    ...base,
    id,
    notices: [
      {
        ...base.notices[0],
        deadline,
        start,
        activities: keys.map((k) => experiences[k]),
      },
    ],
  });
  const f1 = {
    ...f,
    experiences: ["mentor", "web", "presentation"],
    priority: "mentor",
  };
  const a = make("priority", ["mentor"]);
  const b = make("many", ["web", "presentation"]);
  assert.equal(searchPrograms([b, a], f1)[0].id, "priority");
  assert.equal(
    searchPrograms([a, make("both", ["mentor", "web"])], f1)[0].id,
    "both",
  );
  const early = make("early", ["mentor"], "2026-10-01");
  assert.equal(searchPrograms([a, early], f1)[0].id, "early");
  const recent = make("recent", ["mentor"], "2026-10-10", "2026-09-20");
  assert.equal(searchPrograms([a, recent], f1)[0].id, "recent");
});
test("parent does not inflate count, distinct networking experiences still count", () => {
  const base = programs[0];
  const make = (id: string, keys: string[]) => ({
    ...base,
    id,
    notices: [
      { ...base.notices[0], activities: keys.map((k) => experiences[k]) },
    ],
  });
  const results = searchPrograms(
    [make("a", ["mentor"]), make("b", ["mentor", "peer"])],
    { ...f, experiences: ["networking", "mentor", "peer"] },
  );
  assert.equal(results[0].id, "b");
});
test("attributes from separate activities or notices cannot combine", () => {
  const base = programs[0];
  const p = {
    ...base,
    notices: [
      {
        ...base.notices[0],
        roles: ["iOS"],
        activities: [
          { action: "제작", target: "앱", method: "개인" },
          { action: "제작", target: "웹 서비스", method: "팀협업" },
        ],
      },
      { ...base.notices[0], roles: ["백엔드"], activities: [experiences.app] },
    ],
  };
  assert.equal(
    searchPrograms([p], { ...f, roles: ["iOS"], experiences: ["app"] }).length,
    0,
  );
});
