import { organizations } from "./organizations";
export const roles = ["프론트엔드", "백엔드", "iOS", "Android", "AI", "데이터", "클라우드", "보안", "디자인", "기획", "Python", "Go", "Linux"] as const;
export type Activity = {
  action: string;
  target?: string;
  method?: string;
  evidence?: string;
};
export const experiences: Record<string, Activity & { label: string }> = {
  networking: { action: "네트워킹", label: "네트워킹 전체" },
  mentor: { action: "네트워킹", target: "현직자", label: "네트워킹 | 현직자" },
  peer: { action: "네트워킹", target: "동료", label: "네트워킹 | 동료" },
  making: { action: "제작", label: "제작 전체" },
  app: {
    action: "제작",
    target: "앱",
    method: "팀협업",
    label: "제작 | 앱 · 팀협업",
  },
  web: {
    action: "제작",
    target: "웹 서비스",
    method: "팀협업",
    label: "제작 | 웹 서비스 · 팀협업",
  },
  task: {
    action: "과제수행",
    target: "서비스 기획",
    label: "과제수행 | 서비스 기획",
  },
  lecture: { action: "청강", target: "기술 세션", label: "청강 | 기술 세션" },
  mentoring: { action: "멘토링", label: "멘토링" },
  practice: { action: "실습", label: "실습" },
  presentation: { action: "발표", label: "발표 전체" },
};
export type Source = { url: string; label: string; checkedAt: string; evidence: string };
export type Notice = {
  id: string;
  round: string;
  // Latest verified round, not a claim that the event is upcoming.
  current: boolean;
  participationType: "application" | "registration";
  status: "open" | "scheduled" | "closed" | "ended" | "unknown";
  roles: string[];
  activities: Activity[];
  start: string | null;
  deadline: string | null;
  eventDate: string | null;
  eventEndDate: string | null;
  location: string | null;
  cost: string | null;
  audience: string[] | null;
  qualification: string | null;
  selectionProcess?: string[];
  activitySchedule?: string | null;
  officialUrl: string;
  registrationUrl: string | null;
  sources: Source[];
};
export type Program = {
  id: string;
  orgId: string;
  title: string;
  subtitle: string;
  category: string;
  cover: string;
  coverSource: { url: string; pageUrl: string; kind: "og" | "capture" | "fallback"; checkedAt: string };
  notices: Notice[];
};
export function displayDate(value: string | null) {
  return value?.replaceAll("-", ".") ?? "미확인";
}
export function eventDates(n: Notice) {
  return displayDate(n.eventDate) + (n.eventEndDate && n.eventEndDate !== n.eventDate ? ` – ${displayDate(n.eventEndDate)}` : "");
}
export type Filters = {
  category: string;
  query: string;
  roles: string[];
  experiences: string[];
  allRoles: boolean;
  openOnly: boolean;
  priority: string;
};
export const emptyFilters: Filters = {
  category: "",
  query: "",
  roles: [],
  experiences: [],
  allRoles: false,
  openOnly: false,
  priority: "",
};
export function activityMatches(actual: Activity, wanted: Activity) {
  return (
    actual.action === wanted.action &&
    (!wanted.target || actual.target === wanted.target) &&
    (!wanted.method || actual.method === wanted.method)
  );
}
export function roleMatches(n: Notice, f: Filters) {
  return (
    !f.roles.length ||
    (f.allRoles
      ? f.roles.every((r) => n.roles.includes(r))
      : f.roles.some((r) => n.roles.includes(r)))
  );
}
export function experienceMatches(n: Notice, key: string) {
  return (
    !!experiences[key] &&
    n.activities.some((a) => activityMatches(a, experiences[key]))
  );
}
export function matchingNotices(p: Program, f: Filters) {
  return p.notices.filter(
    (n) =>
      n.current &&
      roleMatches(n, f) &&
      (!f.openOnly || n.status === "open") &&
      (!f.experiences.length ||
        f.experiences.some((e) => experienceMatches(n, e))),
  );
}
export function noticeStatus(n: Notice) {
  if (n.selectionProcess) return ({ open: "모집 중", scheduled: "모집 예정", closed: "모집 마감", ended: "활동 종료", unknown: "모집 미확인" })[n.status];
  const labels = { open: n.participationType === "application" ? "참가 신청 중" : "등록 중", scheduled: "등록 예정", closed: "등록 마감", ended: "행사 종료", unknown: "등록 미확인" };
  return labels[n.status];
}
export function representativeNotice(p: Program, f: Filters) {
  return matchingNotices(p, f).sort((a, b) => compare(score(a, f), score(b, f)) || a.id.localeCompare(b.id))[0];
}
function score(n: Notice, f: Filters) {
  const matched = f.experiences.filter((e) => experienceMatches(n, e));
  // A broad parent adds no extra match when a selected, matching child already covers it.
  const count = matched.filter(
    (key) =>
      !matched.some(
        (other) =>
          other !== key &&
          activityMatches(experiences[other], experiences[key]),
      ),
  ).length;
  return [
    ({ open: 4, scheduled: 3, unknown: 2, closed: 1, ended: 0 })[n.status],
    Number(!!f.priority && experienceMatches(n, f.priority)),
    count,
    n.status === "ended" ? Date.parse(n.eventDate ?? "1970-01-01") : -Date.parse(n.deadline ?? n.eventDate ?? "9999-12-31"),
    Date.parse(n.start ?? "1970-01-01"),
  ];
}
function compare(a: number[], b: number[]) {
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return b[i] - a[i];
  return 0;
}
export function searchPrograms(data: Program[], f: Filters) {
  const query = f.query.trim().toLocaleLowerCase();
  return data
    .filter(
      (p) =>
        `${p.title} ${p.subtitle} ${p.category} ${organizations.find(o => o.id === p.orgId)?.name ?? ""} ${p.notices.filter(n => n.current).map(n => n.round).join(" ")}`
          .toLocaleLowerCase()
          .includes(query) && (!f.category || p.category === f.category) && matchingNotices(p, f).length,
    )
    .sort((a, b) => {
      const best = (p: Program) =>
        matchingNotices(p, f)
          .map((n) => score(n, f))
          .sort(compare)[0];
      return compare(best(a), best(b)) || a.id.localeCompare(b.id);
    });
}
export function filterLabels(f: Filters) {
  return [
    ...(f.category ? [f.category] : []),
    ...(f.query ? [`검색: ${f.query}`] : []),
    ...f.roles,
    ...f.experiences.map((e) => experiences[e].label),
    ...(f.openOnly ? ["모집·등록 중"] : []),
  ];
}
export function suggestions(data: Program[], f: Filters) {
  // For AND, retain the largest selected subset supported by each real notice.
  // Smaller subsets lose more conditions without creating a new matching notice.
  const supported = data.flatMap(p => p.notices.filter(n => n.current).map(n => f.roles.filter(r => n.roles.includes(r))));
  const roleOptions = f.allRoles
    ? [...new Map([f.roles, ...supported, []].map(values => [values.join(","), values])).values()]
    : f.roles.length ? [f.roles, []] : [[]];
  const experienceOptions = f.experiences.length ? [f.experiences, []] : [[]];
  const queryOptions = f.query ? [f.query, ""] : [""];
  const categoryOptions = f.category ? [f.category, ""] : [""];
  const openOptions = f.openOnly ? [true, false] : [false];
  const originalLabels = filterLabels(f);
  const results: { filters: Filters; count: number; removed: string[] }[] = [];
  for (const roles of roleOptions)
    for (const experiences of experienceOptions)
      for (const query of queryOptions)
        for (const openOnly of openOptions)
          for (const category of categoryOptions) {
          const next = {
            ...f,
            category,
            roles,
            experiences,
            query,
            openOnly,
            priority: experiences.includes(f.priority) ? f.priority : "",
          };
          const labels = filterLabels(next);
          const removed = originalLabels.filter(
            (label) => !labels.includes(label),
          );
          if (!labels.length || !removed.length) continue;
          const count = searchPrograms(data, next).length;
          if (count) results.push({ filters: next, count, removed });
        }
  return results
    .sort((a, b) => a.removed.length - b.removed.length || b.count - a.count)
    .slice(0, 4);
}
