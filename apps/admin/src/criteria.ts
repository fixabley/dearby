export const criteriaCategories = {
  audience: "참가 대상",
  qualification: "지원 자격",
  roles: "모집 역할",
} as const;
export type CriteriaCategory = keyof typeof criteriaCategories;
export type JsonValue =
  string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue };
export type Criteria = Record<CriteriaCategory, Record<string, JsonValue>>;
export type NodeKind =
  | "string"
  | "number"
  | "boolean"
  | "null"
  | "date"
  | "object"
  | "array"
  | "tags";
export type JsonNode = {
  kind: NodeKind;
  value?: string | boolean | string[];
  children?: ConditionRow[];
};
export type ConditionRow = { key: string; node: JsonNode };
export type CriteriaRows = Record<CriteriaCategory, ConditionRow[]>;
export const cleanKey = (value: string) =>
  value.normalize("NFC").trim().replace(/\s+/g, " ");
export function newNode(kind: NodeKind): JsonNode {
  return kind === "object" || kind === "array"
    ? { kind, children: [] }
    : { kind, value: kind === "boolean" ? false : kind === "tags" ? [] : "" };
}
export function nodeFromValue(value: JsonValue): JsonNode {
  if (value === null) return newNode("null");
  if (Array.isArray(value))
    return value.every((v) => typeof v === "string")
      ? { kind: "tags", value: value as string[] }
      : {
          kind: "array",
          children: value.map((v, i) => ({
            key: String(i),
            node: nodeFromValue(v),
          })),
        };
  if (typeof value === "object")
    return {
      kind: "object",
      children: Object.entries(value).map(([key, v]) => ({
        key,
        node: nodeFromValue(v),
      })),
    };
  if (typeof value === "number")
    return { kind: "number", value: String(value) };
  if (typeof value === "boolean") return { kind: "boolean", value };
  return {
    kind:
      /^\d{4}-\d{2}-\d{2}$/.test(value) && validDate(value) ? "date" : "string",
    value,
  };
}
function validDate(value: string) {
  const date = new Date(value);
  return (
    Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value
  );
}
export function criteriaRows(criteria: Criteria): CriteriaRows {
  return Object.fromEntries(
    Object.keys(criteriaCategories).map((category) => [
      category,
      Object.entries(criteria[category as CriteriaCategory]).map(
        ([key, value]) => ({ key, node: nodeFromValue(value) }),
      ),
    ]),
  ) as CriteriaRows;
}
export function serializeNode(
  node: JsonNode,
  path: string,
  depth = 0,
): JsonValue {
  if (depth > 8) throw new Error(`${path}: 중첩은 최대 8단계까지 지원해요.`);
  if (node.kind === "null") return null;
  if (node.kind === "boolean") return node.value === true;
  if (node.kind === "number") {
    const text = String(node.value ?? "").trim();
    const value = Number(text);
    if (
      !text ||
      !/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(text) ||
      !Number.isFinite(value) ||
      Math.abs(value) > Number.MAX_SAFE_INTEGER
    )
      throw new Error(
        `${path}: 유효한 숫자를 입력해 주세요 (절댓값 최대 ${Number.MAX_SAFE_INTEGER}).`,
      );
    return value;
  }
  if (node.kind === "date" || node.kind === "string") {
    const value = String(node.value ?? "");
    if (
      node.kind === "date" &&
      (!/^\d{4}-\d{2}-\d{2}$/.test(value) || !validDate(value))
    )
      throw new Error(`${path}: 올바른 날짜를 입력해 주세요.`);
    if (value.length > 2000)
      throw new Error(`${path}: 문자열은 최대 2,000자예요.`);
    return value;
  }
  if (node.kind === "tags") {
    const values = Array.isArray(node.value) ? node.value : [];
    if (values.length && depth >= 8)
      throw new Error(`${path}: 중첩은 최대 8단계까지 지원해요.`);
    if (values.length > 100 || values.some((v) => v.length > 2000))
      throw new Error(`${path}: 배열은 최대 100개, 문자열은 최대 2,000자예요.`);
    return values;
  }
  const children = node.children ?? [];
  if (node.kind === "array") {
    if (children.length > 100)
      throw new Error(`${path}: 배열은 최대 100개예요.`);
    return children.map((row, i) =>
      serializeNode(row.node, `${path}[${i}]`, depth + 1),
    );
  }
  if (children.length > 30)
    throw new Error(`${path}: 객체에는 최대 30개 항목을 넣을 수 있어요.`);
  const seen = new Set<string>();
  const value = Object.fromEntries(
    children.map((row) => {
      const key = cleanKey(row.key);
      if (!key || key.length > 80)
        throw new Error(`${path}: 항목 이름은 1~80자로 입력해 주세요.`);
      if (seen.has(key.toLowerCase()))
        throw new Error(`${path}: ‘${key}’ 항목이 중복됐어요.`);
      seen.add(key.toLowerCase());
      return [key, serializeNode(row.node, `${path}.${key}`, depth + 1)];
    }),
  );
  if (
    Object.keys(value).every((k) => ["부터", "까지"].includes(k)) &&
    typeof value.부터 === "number" &&
    typeof value.까지 === "number" &&
    value.부터 > value.까지
  )
    throw new Error(`${path}: ‘부터’는 ‘까지’보다 클 수 없어요.`);
  return value;
}
export function serializeCriteria(rows: CriteriaRows): Criteria {
  const value = Object.fromEntries(
    Object.entries(criteriaCategories).map(([category, label]) => [
      category,
      serializeNode(
        { kind: "object", children: rows[category as CriteriaCategory] ?? [] },
        label,
      ),
    ]),
  ) as Criteria;
  if (new TextEncoder().encode(JSON.stringify(value)).length > 65536)
    throw new Error("조건 JSON은 최대 64KB까지 저장할 수 있어요.");
  return value;
}
