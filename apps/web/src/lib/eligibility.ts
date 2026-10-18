import type { Eligibility, EligibilityCondition } from "./models";

/**
 * Korean chip texts for an activity's eligibility (contract "대상자 조건 — 2026-10-18").
 * Each top-level item becomes one line; an `any` group joins its parts with `또는`.
 */
export function eligibilityLines(eligibility: Eligibility | null | undefined): string[] {
  if (!eligibility) return [];
  return eligibility.items.map(line).filter((text): text is string => !!text);
}

function line(item: Eligibility["items"][number]): string | undefined {
  if ("items" in item) {
    const parts = item.items.map(line).filter((text): text is string => !!text);
    if (!parts.length) return;
    return parts.join(item.op === "any" ? " 또는 " : ", ");
  }
  return condition(item);
}

const UNIT: Record<string, [string, string]> = {
  // fieldEn → [label, unit]
  age: ["나이", "세"],
  experience_years: ["경력", "년"],
  school_year: ["", "학년"],
  team_size: ["팀", "명"],
};

function condition(item: EligibilityCondition): string | undefined {
  const hasRange = item.from !== undefined || item.to !== undefined;
  if (item.fieldEn === "other" || (!hasRange && !item.values?.length)) {
    return item.note?.trim() || undefined;
  }
  if (item.fieldEn === "birth_year" && hasRange) {
    if (item.from !== undefined && item.to !== undefined)
      return `${item.from}~${item.to}년 출생`;
    return item.from !== undefined
      ? `${item.from}년 이후 출생`
      : `${item.to}년 이전 출생`;
  }
  if (hasRange) {
    const [label, unit] = UNIT[item.fieldEn] ?? [item.field, ""];
    const prefix = label ? `${label} ` : "";
    if (item.from !== undefined && item.to !== undefined)
      return `${prefix}${item.from}~${item.to}${unit}`;
    return item.from !== undefined
      ? `${prefix}${item.from}${unit} 이상`
      : `${prefix}${item.to}${unit} 이하`;
  }
  return `${item.field} ${item.values!.join("·")}`;
}
