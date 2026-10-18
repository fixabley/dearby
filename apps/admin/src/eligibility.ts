/**
 * 대상자 조건(contract native-v1 "대상자 조건(eligibility)"): vocabulary, the editor's draft model and the JSON
 * conversions. JSON keys are camelCase; snake_case is DB-only. Korean is the operator-facing side, English twins are
 * filled from the vocabulary where it is fixed and typed by the operator where it is free.
 */
export type EligibilityCondition = {
  field: string;
  fieldEn: string;
  from?: number | null;
  to?: number | null;
  values?: string[] | null;
  valuesEn?: string[] | null;
  note?: string | null;
  noteEn?: string | null;
};
export type Eligibility = { op: "all" | "any"; items: (Eligibility | EligibilityCondition)[] };

export type FieldKind = "numeric" | "fixed" | "free" | "note";
export type FieldDef = { ko: string; en: string; kind: FieldKind; unit?: string; values?: { ko: string; en: string }[] };
/** Korean → English vocabulary from the contract. `fixed` fields carry their allowed values; `free` fields take typed values. */
export const FIELDS: FieldDef[] = [
  { ko: "나이", en: "age", kind: "numeric", unit: "세" },
  { ko: "출생연도", en: "birthYear", kind: "numeric", unit: "년" },
  { ko: "학년", en: "schoolYear", kind: "numeric", unit: "학년" },
  {
    ko: "학력",
    en: "educationLevel",
    kind: "fixed",
    values: [
      { ko: "고졸", en: "highSchool" },
      { ko: "대학", en: "university" },
      { ko: "대학원", en: "graduateSchool" },
    ],
  },
  {
    ko: "재학상태",
    en: "educationStatus",
    kind: "fixed",
    values: [
      { ko: "재학", en: "enrolled" },
      { ko: "졸업", en: "graduated" },
      { ko: "휴학", en: "onLeave" },
      { ko: "졸업예정", en: "graduating" },
    ],
  },
  { ko: "전공", en: "major", kind: "free" },
  { ko: "지역", en: "region", kind: "free" },
  {
    ko: "취업상태",
    en: "employment",
    kind: "fixed",
    values: [
      { ko: "학생", en: "student" },
      { ko: "미취업", en: "unemployed" },
      { ko: "재직", en: "employed" },
      { ko: "창업", en: "founder" },
    ],
  },
  { ko: "경력연수", en: "experienceYears", kind: "numeric", unit: "년" },
  { ko: "국적", en: "nationality", kind: "free" },
  { ko: "팀규모", en: "teamSize", kind: "numeric", unit: "명" },
  { ko: "기타", en: "other", kind: "note" },
];
export const OTHER = FIELDS[FIELDS.length - 1];
export const fieldByEn = (en: string): FieldDef | undefined => FIELDS.find((f) => f.en === en);
export const fieldByKo = (ko: string): FieldDef | undefined => FIELDS.find((f) => f.ko === ko);
/** English twins the editor fills in for commonly typed free values (국적 is ISO 3166-1 alpha-2 in the contract). */
const KNOWN_VALUES: Record<string, string> = { 대한민국: "KR", 한국: "KR", 미국: "US", 일본: "JP", 중국: "CN", 캐나다: "CA", 영국: "GB" };
export const suggestEn = (ko: string): string => KNOWN_VALUES[ko.trim()] ?? "";

/** Editor state: strings while typing, resolved to numbers/arrays only when serialising. */
export type DraftValue = { ko: string; en: string };
export type DraftCondition = {
  kind: "condition";
  key: string;
  fieldEn: string;
  from: string;
  to: string;
  values: DraftValue[];
  note: string;
  noteEn: string;
};
export type DraftGroup = { kind: "group"; key: string; op: "all" | "any"; items: DraftItem[] };
export type DraftItem = DraftGroup | DraftCondition;

let counter = 0;
const nextKey = () => `k${++counter}`;
export const newCondition = (fieldEn = "age"): DraftCondition => ({
  kind: "condition",
  key: nextKey(),
  fieldEn,
  from: "",
  to: "",
  values: [],
  note: "",
  noteEn: "",
});
export const newGroup = (op: "all" | "any" = "all"): DraftGroup => ({ kind: "group", key: nextKey(), op, items: [] });

const isGroup = (item: Eligibility | EligibilityCondition): item is Eligibility => "items" in item && Array.isArray(item.items);

/** Stored JSON → editor draft. Tolerates missing twins and unknown fields (they become 기타 with the original text as note). */
export function toDraft(eligibility: Eligibility | null | undefined): DraftGroup {
  const condition = (item: EligibilityCondition): DraftCondition => {
    const def = fieldByEn(item.fieldEn ?? "") ?? fieldByKo(item.field ?? "");
    const base = newCondition(def?.en ?? OTHER.en);
    if (!def) {
      base.note = [item.field || item.fieldEn, item.values?.join("/"), item.note].filter(Boolean).join(" ");
      base.noteEn = item.noteEn ?? "";
      return base;
    }
    base.from = item.from === null || item.from === undefined ? "" : String(item.from);
    base.to = item.to === null || item.to === undefined ? "" : String(item.to);
    base.values = (item.values ?? []).map((ko, index) => ({
      ko,
      en: item.valuesEn?.[index] ?? def.values?.find((v) => v.ko === ko)?.en ?? suggestEn(ko),
    }));
    base.note = item.note ?? "";
    base.noteEn = item.noteEn ?? "";
    return base;
  };
  const group = (node: Eligibility): DraftGroup => ({
    kind: "group",
    key: nextKey(),
    op: node.op === "any" ? "any" : "all",
    items: (Array.isArray(node.items) ? node.items : []).map((item) => (isGroup(item) ? group(item) : condition(item))),
  });
  return eligibility && typeof eligibility === "object" ? group(eligibility) : newGroup();
}

const number = (text: string): number | null => (text.trim() === "" ? null : Number(text));

/** Editor draft → stored JSON; an empty root (or root with only empty groups) means no conditions (null). */
export function fromDraft(root: DraftGroup): Eligibility | null {
  const condition = (item: DraftCondition): EligibilityCondition => {
    const def = fieldByEn(item.fieldEn) ?? OTHER;
    const result: EligibilityCondition = { field: def.ko, fieldEn: def.en };
    if (def.kind === "numeric") {
      const from = number(item.from),
        to = number(item.to);
      if (from !== null) result.from = from;
      if (to !== null) result.to = to;
    } else if (def.kind !== "note") {
      result.values = item.values.map((v) => v.ko.trim());
      result.valuesEn = item.values.map((v) => (def.values?.find((d) => d.ko === v.ko)?.en ?? v.en.trim()) || v.ko.trim());
    }
    if (item.note.trim()) result.note = item.note.trim();
    if (item.noteEn.trim()) result.noteEn = item.noteEn.trim();
    return result;
  };
  const group = (node: DraftGroup): Eligibility | null => {
    const items = node.items
      .map((item) => (item.kind === "group" ? group(item) : condition(item)))
      .filter((item): item is Eligibility | EligibilityCondition => item !== null);
    return items.length ? { op: node.op, items } : null;
  };
  return group(root);
}

/** Problems that would make the server reject the draft, described per row ("조건 2: …"). Empty when valid. */
export function validateDraft(root: DraftGroup): string[] {
  const problems: string[] = [];
  let index = 0;
  const visit = (node: DraftGroup) => {
    for (const item of node.items) {
      if (item.kind === "group") {
        visit(item);
        continue;
      }
      index += 1;
      const def = fieldByEn(item.fieldEn) ?? OTHER;
      const label = `조건 ${index}(${def.ko})`;
      if (def.kind === "numeric") {
        const from = number(item.from),
          to = number(item.to);
        if (from === null && to === null) problems.push(`${label}: 부터·까지 중 하나는 적어 주세요.`);
        if ((from !== null && !Number.isFinite(from)) || (to !== null && !Number.isFinite(to))) problems.push(`${label}: 숫자만 쓸 수 있어요.`);
        else if (from !== null && to !== null && from > to) problems.push(`${label}: 부터가 까지보다 클 수 없어요.`);
      } else if (def.kind === "note") {
        if (!item.note.trim()) problems.push(`${label}: 조건 내용을 적어 주세요.`);
      } else if (!item.values.length || item.values.some((v) => !v.ko.trim())) problems.push(`${label}: 값을 하나 이상 골라 주세요.`);
    }
  };
  visit(root);
  return problems;
}

/** One-line Korean reading for operators: `나이 19~34세 · 재학상태 재학 또는 취업상태 미취업`. */
export function eligibilitySummary(eligibility: Eligibility | null | undefined): string {
  if (!eligibility || typeof eligibility !== "object") return "";
  const condition = (item: EligibilityCondition): string => {
    const def = fieldByEn(item.fieldEn ?? "") ?? fieldByKo(item.field ?? "");
    const label = item.field || def?.ko || item.fieldEn || "조건";
    if (def?.kind === "note" || (!def && !item.values?.length && item.from == null && item.to == null)) return item.note || item.noteEn || label;
    const hasFrom = item.from !== undefined && item.from !== null,
      hasTo = item.to !== undefined && item.to !== null;
    if (hasFrom || hasTo) {
      const unit = def?.unit ?? "";
      if (hasFrom && hasTo) return `${label} ${item.from}~${item.to}${unit}`;
      if (hasFrom) return `${label} ${item.from}${unit} 이상`;
      return `${label} ${item.to}${unit} 이하`;
    }
    if (item.values?.length) return `${label} ${item.values.join("/")}`;
    return item.note || label;
  };
  const group = (node: Eligibility): string =>
    (Array.isArray(node.items) ? node.items : [])
      .map((item) => (isGroup(item) ? `(${group(item)})` : condition(item)))
      .filter(Boolean)
      .join(node.op === "any" ? " 또는 " : " · ");
  return group(eligibility);
}
