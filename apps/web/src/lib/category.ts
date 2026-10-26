/** 활동 종류 (contract "활동 종류(category) — 2026-10-19"): API codes in contract order, Korean labels, null = 미분류. */
export const categoryLabels: Record<string, string> = {
  BOOTCAMP: "부트캠프",
  CONFERENCE: "컨퍼런스",
  WORKSHOP: "워크샵",
  STUDY: "스터디",
  CLUB: "동아리",
  LECTURE: "강연",
  HACKATHON: "해커톤",
  CONTEST: "공모전",
  MEETUP: "밋업",
  OTHER: "기타",
};
const order = Object.keys(categoryLabels);
/** 이벤트 종류 (the server's `kind=event` set); every other code is 프로그램. */
const EVENT_CATEGORIES = ["CONFERENCE", "WORKSHOP", "LECTURE", "MEETUP"];

/**
 * 종류 필터의 항목 (contract "종류 필터" 2026-10-26): the 프로그램 side's codes in contract order. The 이벤트 side offers none (user 2026-10-26:
 * every 운영 이벤트 is a 컨퍼런스; the contract brings the list back once a second 이벤트 종류 arrives).
 */
export function kindCategories(kind: "program" | "event"): string[] {
  return kind === "program" ? order.filter((code) => !EVENT_CATEGORIES.includes(code)) : [];
}
/** `?category=BOOTCAMP,CLUB` → the side's known codes in contract order; anything else is dropped (none = 전체, always on the 이벤트 side). */
export function parseCategories(value: string | undefined, kind: "program" | "event"): string[] {
  const codes = new Set(value?.split(","));
  return kindCategories(kind).filter((code) => codes.has(code));
}

export function categoryLabel(code: string | null | undefined): string {
  return (code && categoryLabels[code]) || "미분류";
}

/** Items bucketed by category in contract order, unknown codes and null last as 미분류; empty buckets dropped. */
export function groupByCategory<T>(items: T[], category: (item: T) => string | null | undefined): { label: string; items: T[] }[] {
  const buckets = new Map<string, T[]>();
  for (const item of items) {
    const code = category(item);
    const key = code && categoryLabels[code] ? code : "";
    buckets.set(key, [...(buckets.get(key) ?? []), item]);
  }
  return [...order, ""]
    .filter((key) => buckets.has(key))
    .map((key) => ({ label: key ? categoryLabels[key] : "미분류", items: buckets.get(key)! }));
}
