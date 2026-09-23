import { test } from "node:test";
import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import { programs, organizations, snapshotDate } from "../src/features/catalog/data";
import { emptyFilters as f, roles, experiences, searchPrograms, matchingNotices, suggestions, activityMatches, representativeNotice, noticeStatus, eventDates, type Notice, type Program } from "../src/features/catalog/model";
const base = programs.find(p => p.id === "woowacon")!;
const notice = (patch: Partial<Notice> = {}): Notice => ({ ...base.notices[0], ...patch });
const program = (id: string, notices: Notice[]): Program => ({ ...base, id, notices });

test("official snapshot has unique programs/rounds, valid source provenance and local artwork", () => {
  assert.equal(programs.length, 18);
  assert.equal(new Set(programs.map(p => p.id)).size, 18);
  assert.equal(searchPrograms(programs, f).length, 18);
  const ids = programs.flatMap(p => p.notices.map(n => n.id));
  assert.equal(new Set(ids).size, ids.length);
  for (const p of programs) {
    assert.ok(organizations.some(o => o.id === p.orgId));
    assert.equal(p.notices.filter(n => n.current).length, 1);
    assert.ok(existsSync(`public${p.cover}`));
    assert.ok(p.coverSource.url.startsWith("https://"));
    assert.equal(p.coverSource.checkedAt, snapshotDate);
    for (const n of p.notices) {
      assert.ok(n.sources.length > 0);
      for (const source of n.sources) { assert.equal(source.checkedAt, snapshotDate); assert.ok(new URL(source.url).protocol === "https:"); assert.ok(source.evidence.length > 15); }
      for (const field of [n.start, n.deadline, n.eventDate, n.eventEndDate]) if (field) assert.ok(/^\d{4}-\d{2}-\d{2}$/.test(field) && Number.isFinite(Date.parse(field)));
      assert.ok(n.roles.every(r => roles.some(known => known === r)));
      assert.ok(n.activities.every(a => a.evidence && a.evidence.length > 10));
      assert.ok(!n.activities.some(a => a.action === "발표")); // Being in the audience is not giving a talk.
      if (n.eventDate && (n.eventEndDate ?? n.eventDate) < snapshotDate) assert.equal(n.status, "ended");
      if (n.status === "open") { assert.ok(n.eventDate && n.eventDate >= snapshotDate); assert.ok(n.registrationUrl); assert.ok(!n.deadline || n.deadline >= snapshotDate); }
    }
  }
});
test("verified latest rounds, costs and restrictions are not invented from stale buttons", () => {
  const get = (id: string) => programs.find(p => p.id === id)!.notices[0];
  assert.equal(get("kakao").round, "2026");
  assert.equal(get("kakao").deadline, "2026-09-28");
  assert.match(get("kakao").qualification!, /낮 12시/);
  assert.deepEqual(get("kakao").audience, ["만 18세 이상"]);
  assert.equal(get("woowacon").cost, "무료");
  assert.match(get("woowacon").qualification!, /추첨/);
  assert.equal(get("droid").cost, "일반 69,000원 · 개인후원 150,000원");
  assert.equal(get("droid").deadline, "2026-11-01");
  assert.equal(get("feconf").status, "scheduled");
  assert.equal(get("feconf").cost, null);
  assert.equal(get("saif").status, "unknown");
  assert.equal(get("saif").audience, null);
  assert.equal(get("aws").status, "ended");
  assert.equal(get("dan").round, "2025");
  assert.equal(get("toss").eventDate, null);
  assert.equal(eventDates(get("toss")), "미확인");
  assert.ok(searchPrograms(programs, f).some(p => p.id === "kakao")); // No profile/eligibility exclusion.
});
test("all five registration states are distinct and open filter excludes unknown/ended/scheduled", () => {
  assert.deepEqual(searchPrograms(programs, { ...f, openOnly: true }).map(p => p.id), ["kakao", "woowacon", "droid"]);
  const labels = ["open", "scheduled", "closed", "ended", "unknown"].map(status => noticeStatus(notice({ status: status as Notice["status"] })));
  assert.equal(new Set(labels).size, 5);
  const closed = program("closed", [notice({ status: "closed" })]);
  assert.equal(searchPrograms([closed], f).length, 1);
  assert.equal(searchPrograms([closed], { ...f, openOnly: true }).length, 0);
});
test("search supports organization, title and confirmed round", () => {
  assert.deepEqual(searchPrograms(programs, { ...f, query: "우아한형제들" }).map(p => p.id), ["woowacon"]);
  assert.deepEqual(new Set(searchPrograms(programs, { ...f, query: "네이버" }).map(p => p.id)), new Set(["dan", "deview"]));
  assert.deepEqual(searchPrograms(programs, { ...f, query: "FEConf" }).map(p => p.id), ["feconf"]);
  assert.ok(!searchPrograms(programs, { ...f, query: "2026" }).some(p => p.id === "dan"));
});
test("same notice and activity conjunction prevent invented cross-role and historical matches", () => {
  const p = program("mixed", [
    notice({ id: "a", roles: ["Android"], status: "closed", activities: [{ action: "제작", target: "앱", method: "개인" }, experiences.web] }),
    notice({ id: "b", roles: ["백엔드"], activities: [experiences.app] }),
    notice({ id: "old", current: false, roles: ["Android"], activities: [experiences.app] }),
  ]);
  assert.equal(searchPrograms([p], { ...f, roles: ["Android"], experiences: ["app"] }).length, 0);
  assert.equal(searchPrograms([p], { ...f, roles: ["Android"], openOnly: true }).length, 0);
  assert.equal(searchPrograms([p], { ...f, roles: ["Android", "백엔드"], allRoles: true }).length, 0);
  assert.equal(searchPrograms([p], { ...f, roles: ["Android", "백엔드"] }).length, 1);
  assert.equal(representativeNotice(p, { ...f, roles: ["Android"] })?.id, "a");
});
test("representative facts come from one matching notice and historical round does not leak", () => {
  const p = programs.find(p => p.id === "springcamp")!;
  assert.equal(matchingNotices(p, f).length, 1);
  assert.equal(representativeNotice(p, f)?.round, "2026");
  assert.equal(p.notices[1].round, "2025");
  assert.deepEqual(p.notices[1].activities, []);
  const restricted = program("restricted", [notice({ id: "open", activities: [experiences.peer] }), notice({ id: "closed", status: "closed", activities: [experiences.app] })]);
  assert.equal(representativeNotice(restricted, { ...f, experiences: ["app"] })?.id, "closed");
});
test("activity attributes and parent selection maintain intended semantics", () => {
  assert.ok(activityMatches({ action: "제작", target: "앱", method: "팀협업" }, { action: "제작" }));
  assert.ok(!activityMatches({ action: "제작", target: "앱", method: "개인" }, experiences.app));
  const p = program("parent", [notice({ activities: [experiences.peer] })]);
  assert.equal(searchPrograms([p], { ...f, experiences: ["networking"] }).length, 1);
  assert.equal(searchPrograms([p], { ...f, experiences: ["mentor"] }).length, 0);
});
test("priority, nonduplicated parent counts, deadline and latest start ranking", () => {
  const make = (id: string, keys: string[], patch: Partial<Notice> = {}) => program(id, [notice({ activities: keys.map(k => experiences[k]), ...patch })]);
  const filters = { ...f, experiences: ["networking", "mentor", "peer", "web"], priority: "mentor" };
  assert.equal(searchPrograms([make("many", ["peer", "web"]), make("priority", ["mentor"])], filters)[0].id, "priority");
  assert.equal(searchPrograms([make("a", ["mentor"]), make("b", ["mentor", "peer"])], filters)[0].id, "b");
  assert.equal(searchPrograms([make("a", ["mentor"]), make("early", ["mentor"], { deadline: "2026-10-01" })], filters)[0].id, "early");
  assert.equal(searchPrograms([make("a", ["mentor"], { start: "2026-09-01" }), make("recent", ["mentor"], { start: "2026-09-20" })], filters)[0].id, "recent");
  assert.deepEqual(searchPrograms([make("missing", [], { eventDate: null, deadline: null }), make("known", [], { deadline: "2026-10-01" })], f).map(p => p.id), ["known", "missing"]);
});
test("zero-results alternatives are valid, preserve whole OR groups and never silently relax", () => {
  const filters = { ...f, roles: ["Android"], experiences: ["app"] };
  assert.equal(searchPrograms(programs, filters).length, 0);
  const options = suggestions(programs, filters);
  assert.ok(options.length > 0 && options.length <= 4);
  for (const option of options) { assert.ok(option.removed.length); assert.equal(option.count, searchPrograms(programs, option.filters).length); assert.ok(option.count > 0); }
  assert.deepEqual(filters.roles, ["Android"]);
  const worst = { ...f, query: "없는 행사", roles: [...roles], experiences: Object.keys(experiences), allRoles: true, openOnly: true };
  const start = performance.now();
  const alternatives = suggestions(programs, worst);
  assert.ok(performance.now() - start < 1000);
  assert.ok(alternatives.length > 0);
  for (const option of alternatives) assert.ok(option.filters.experiences.length === 0 || option.filters.experiences.length === worst.experiences.length);
  for (const option of suggestions(programs, { ...worst, allRoles: false })) assert.ok(option.filters.roles.length === 0 || option.filters.roles.length === roles.length);
});
test("AND alternatives retain the most supported fields rather than joining separate notices", () => {
  const data = [program("a", [notice({ roles: ["AI", "데이터"] })]), program("b", [notice({ roles: ["Android"] }), notice({ roles: ["클라우드"] })])];
  const filters = { ...f, roles: ["AI", "데이터", "Android", "클라우드"], allRoles: true };
  const options = suggestions(data, filters);
  assert.deepEqual(options[0].filters.roles, ["AI", "데이터"]);
  assert.ok(!options.some(o => o.filters.roles.includes("Android") && o.filters.roles.includes("클라우드")));
});
