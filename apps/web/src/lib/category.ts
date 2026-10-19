/** 활동 종류 (contract "활동 종류(category) — 2026-10-19"): API codes in contract order, Korean labels, null = 미분류. */
export const categoryLabels: Record<string, string> = {
  BOOTCAMP: "부트캠프",
  CONFERENCE: "컨퍼런스",
  WORKSHOP: "워크샵",
  STUDY: "스터디",
  LECTURE: "강연",
  HACKATHON: "해커톤",
  CONTEST: "공모전",
  MEETUP: "밋업",
  OTHER: "기타",
};
const order = Object.keys(categoryLabels);

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
