/** 활동 종류 (contract native-v1.md "활동 종류(category) — 2026-10-19"): code → label, in display order; null is 미분류. */
export const categoryLabels = {
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
} as const;
export type Category = keyof typeof categoryLabels;
export const categoryOrder = Object.keys(categoryLabels) as Category[];
export const UNCATEGORIZED = "미분류";
/** Label for a stored value; unknown or missing codes read as 미분류. */
export const categoryLabel = (category: string | null | undefined): string =>
  category && category in categoryLabels ? categoryLabels[category as Category] : UNCATEGORIZED;
/** Select options: every code plus 미분류 (value null). */
export const categoryOptions: { value: Category | null; label: string }[] = [
  ...categoryOrder.map((value) => ({ value, label: categoryLabels[value] })),
  { value: null, label: UNCATEGORIZED },
];
