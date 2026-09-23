export const roles = ["프론트엔드", "백엔드", "디자인", "기획", "iOS"] as const;
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
  lecture: { action: "청강", target: "업무 소개", label: "청강 | 업무 소개" },
  presentation: { action: "발표", label: "발표 전체" },
};
export type Notice = {
  id: string;
  round: string;
  current: boolean;
  open: boolean;
  roles: string[];
  activities: Activity[];
  start: string;
  deadline: string;
  qualification: string;
};
export type Program = {
  id: string;
  orgId: string;
  title: string;
  subtitle: string;
  category: string;
  location: string;
  cover: string;
  notices: Notice[];
};
export type Filters = {
  query: string;
  roles: string[];
  experiences: string[];
  allRoles: boolean;
  openOnly: boolean;
  priority: string;
};
export const emptyFilters: Filters = {
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
      (!f.openOnly || n.open) &&
      (!f.experiences.length ||
        f.experiences.some((e) => experienceMatches(n, e))),
  );
}
export function recruitment(p: Program, f: Filters) {
  if (p.notices.some((n) => n.current && n.open && roleMatches(n, f)))
    return f.roles.length ? "선택 직무 모집 중" : "모집 중";
  return p.notices.some((n) => n.current && n.open)
    ? "선택 직무 종료 · 다른 직무 모집 중"
    : "모집 종료";
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
    Number(n.open),
    Number(!!f.priority && experienceMatches(n, f.priority)),
    count,
    -Date.parse(n.deadline),
    Date.parse(n.start),
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
        `${p.title} ${p.subtitle} ${p.category}`
          .toLocaleLowerCase()
          .includes(query) && matchingNotices(p, f).length,
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
    ...(f.query ? [`검색: ${f.query}`] : []),
    ...f.roles,
    ...f.experiences.map((e) => experiences[e].label),
    ...(f.openOnly ? ["모집 중"] : []),
  ];
}
export function suggestions(data: Program[], f: Filters) {
  // Removing individual OR terms only narrows results. Keep or drop each OR group;
  // only the AND direction group needs subsets (at most 2^5 in this prototype).
  const roleOptions = f.allRoles
    ? Array.from({ length: 2 ** f.roles.length }, (_, mask) =>
        f.roles.filter((_, i) => Math.floor(mask / 2 ** i) % 2),
      )
    : f.roles.length
      ? [f.roles, []]
      : [[]];
  const experienceOptions = f.experiences.length ? [f.experiences, []] : [[]];
  const queryOptions = f.query ? [f.query, ""] : [""];
  const openOptions = f.openOnly ? [true, false] : [false];
  const originalLabels = filterLabels(f);
  const results: { filters: Filters; count: number; removed: string[] }[] = [];
  for (const roles of roleOptions)
    for (const experiences of experienceOptions)
      for (const query of queryOptions)
        for (const openOnly of openOptions) {
          const next = {
            ...f,
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
