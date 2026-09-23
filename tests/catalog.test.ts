import { test } from "node:test";
import assert from "node:assert/strict";
import { programs } from "../src/features/catalog/data";
import {
  emptyFilters as f,
  experiences,
  searchPrograms,
  suggestions,
  activityMatches,
  representativeNotice,
  recruitment,
  noticeStatus,
} from "../src/features/catalog/model";
test("unique programs, direction OR and AND", () => {
  assert.equal(searchPrograms(programs, f).length, 13);
  assert.equal(new Set(searchPrograms(programs, f).map(p => p.id)).size, 13);
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
  assert.equal(recruitment(p, { ...f, roles: ["iOS"] }), "선택 직무 종료 · 다른 직무 모집 중");
  assert.equal(
    representativeNotice(p, { ...f, roles: ["iOS"] })?.open,
    false,
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

test("OR subset suggestions keep complete OR groups and worst selection remains responsive", () => {
  const filters = {
    ...f,
    query: "존재하지않는검색",
    roles: ["프론트엔드", "백엔드", "디자인", "기획", "iOS"],
    experiences: Object.keys(experiences),
    allRoles: true,
    openOnly: true,
  };
  const start = performance.now();
  const options = suggestions(programs, filters);
  assert.ok(performance.now() - start < 1000);
  assert.ok(options.length);
  for (const option of options)
    assert.ok(
      option.filters.experiences.length === 0 ||
        option.filters.experiences.length === filters.experiences.length,
    );
  const orFilters = { ...filters, allRoles: false };
  for (const option of suggestions(programs, orFilters))
    assert.ok(
      option.filters.roles.length === 0 ||
        option.filters.roles.length === orFilters.roles.length,
    );
});

test("representative notice uses the same matching current notice for all card facts", () => {
  const p = programs[3];
  assert.equal(representativeNotice(p, f)?.id, "app-club-design");
  assert.equal(representativeNotice(p, { ...f, roles: ["iOS"] })?.id, "app-club-2");
  const restricted = { ...p, notices: p.notices.map(n => ({ ...n, activities: n.open ? [experiences.peer] : [experiences.app] })) };
  assert.equal(representativeNotice(restricted, { ...f, experiences: ["app"] })?.open, false);
  assert.ok(programs.some(p => p.notices[0].audience.includes("고등학생")));
  assert.ok(programs.some(p => !p.notices[0].audience.includes("대학생")));
});


test("conference remains searchable by name/category and uses current topic/experience filters", () => {
  const conference = programs.find(p => p.id === "next-step-conference")!;
  for (const query of ["넥스트 스텝", "컨퍼런스"])
    assert.deepEqual(searchPrograms(programs, { ...f, query }).map(p => p.id), [conference.id]);
  const filters = { ...f, query: "컨퍼런스", roles: ["디자인"], experiences: ["lecture"], openOnly: true };
  assert.deepEqual(searchPrograms(programs, filters).map(p => p.id), [conference.id]);
  assert.equal(searchPrograms(programs, { ...filters, roles: ["iOS"] }).length, 0);
  assert.equal(searchPrograms(programs, { ...filters, experiences: ["app"] }).length, 0);
  const n = representativeNotice(conference, filters)!;
  assert.equal(n.participationType, "registration");
  assert.deepEqual(n.audience, ["고등학생", "대학생", "취준생"]);
  assert.equal(n.deadline, "2026-10-20");
  assert.ok(n.participationType === "registration" && n.eventDate === "2026-10-24");
  assert.equal(noticeStatus(n), "등록 중");
  const closed = { ...conference, notices: [{ ...n, open: false }] };
  assert.equal(noticeStatus(closed.notices[0]), "등록 마감");
  assert.equal(searchPrograms([closed], filters).length, 0);
  assert.equal(searchPrograms([closed], { ...filters, openOnly: false }).length, 1);
});
