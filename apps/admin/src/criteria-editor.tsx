import { useEffect, useState } from "react";
import { AutoComplete, Button, Input, Select, Switch } from "antd";
import { PlusOutlined } from "@ant-design/icons";
import { supabase } from "./supabase";
import {
  cleanKey,
  criteriaCategories,
  newNode,
  type ConditionRow,
  type CriteriaCategory,
  type JsonNode,
  type NodeKind,
} from "./criteria";
const kinds = {
  string: "문자열",
  number: "숫자",
  date: "날짜",
  boolean: "참/거짓",
  null: "null",
  object: "객체",
  array: "배열",
  tags: "문자열 배열",
};
type Suggestion = { key: string; kind: string; values: string[] };
function FieldInput({
  category,
  parentPath,
  row,
  index,
  isArray,
  onChange,
  onRemove,
}: {
  category: CriteriaCategory;
  parentPath: string[];
  row: ConditionRow;
  index: number;
  isArray: boolean;
  onChange: (row: ConditionRow) => void;
  onRemove: () => void;
}) {
  const [suggestions, setSuggestions] = useState<Suggestion[]>([]);
  const [failed, setFailed] = useState(false);
  const pathString = JSON.stringify(parentPath);
  useEffect(() => {
    if (isArray) return;
    let current = true;
    setSuggestions([]);
    const timer = setTimeout(async () => {
      try {
        const { data, error } = await supabase!.rpc(
          "catalog_criteria_suggestions",
          {
            category,
            query_text: row.key.slice(0, 80),
            parent_path: JSON.parse(pathString),
          },
        );
        if (!current) return;
        setFailed(!!error);
        setSuggestions(error ? [] : data);
      } catch {
        if (current) setFailed(true);
      }
    }, 200);
    return () => {
      current = false;
      clearTimeout(timer);
    };
  }, [category, pathString, row.key, isArray]);
  const label = `${criteriaCategories[category]} ${[...parentPath, String(index + 1)].join(" ")}`;
  const changeNode = (node: JsonNode) => onChange({ ...row, node });
  const values = [
    ...new Set(
      suggestions
        .filter(
          (s) =>
            cleanKey(s.key).toLowerCase() === cleanKey(row.key).toLowerCase(),
        )
        .flatMap((s) => s.values),
    ),
  ];
  function changeKind(kind: NodeKind | "range") {
    if (kind === row.node.kind) return;
    const populated =
      (row.node.children?.length ?? 0) > 0 ||
      (Array.isArray(row.node.value)
        ? row.node.value.length > 0
        : !!row.node.value);
    if (
      populated &&
      !window.confirm(
        "형식을 바꾸면 이 항목의 기존 값이 초기화됩니다. 바꾸시겠어요?",
      )
    )
      return;
    changeNode(
      kind === "range"
        ? {
            kind: "object",
            children: [
              { key: "부터", node: newNode("number") },
              { key: "까지", node: newNode("number") },
            ],
          }
        : newNode(kind),
    );
  }
  return (
    <div className="condition-row">
      <div className="condition-heading">
        {isArray ? (
          <strong>항목 {index + 1}</strong>
        ) : (
          <AutoComplete
            virtual={false}
            aria-label={`${label} 항목 이름`}
            value={row.key}
            maxLength={80}
            options={suggestions.map((s) => ({
              value: s.key,
              label: `${s.key} · ${kinds[s.kind as NodeKind] ?? "여러 형식"}`,
            }))}
            placeholder="항목 이름 검색 또는 직접 입력"
            onChange={(key) => onChange({ ...row, key })}
          />
        )}
        <Select
          aria-label={`${label} 값 형식`}
          virtual={false}
          value={row.node.kind}
          options={[
            ...Object.entries(kinds).map(([value, label]) => ({
              value,
              label,
            })),
            { value: "range", label: "범위 객체 만들기" },
          ]}
          onChange={changeKind}
        />
        <Button aria-label={`${label} 제거`} onClick={onRemove}>
          제거
        </Button>
      </div>
      {row.node.kind === "object" || row.node.kind === "array" ? (
        <Fields
          category={category}
          path={[...parentPath, isArray ? "*" : row.key]}
          isArray={row.node.kind === "array"}
          rows={row.node.children ?? []}
          onChange={(children) => changeNode({ ...row.node, children })}
        />
      ) : row.node.kind === "tags" ? (
        <Select
          aria-label={`${label} 값`}
          virtual={false}
          mode="tags"
          style={{ width: "100%" }}
          value={Array.isArray(row.node.value) ? row.node.value : []}
          options={values.map((value) => ({ value, label: value }))}
          onChange={(value) => changeNode({ ...row.node, value })}
          placeholder="기존 값 검색 또는 직접 입력 후 Enter"
        />
      ) : row.node.kind === "boolean" ? (
        <Switch
          aria-label={`${label} 값`}
          checked={row.node.value === true}
          checkedChildren="true"
          unCheckedChildren="false"
          onChange={(value) => changeNode({ ...row.node, value })}
        />
      ) : row.node.kind === "null" ? (
        <code>null</code>
      ) : row.node.kind === "string" ? (
        <AutoComplete
          virtual={false}
          aria-label={`${label} 값`}
          style={{ width: "100%" }}
          value={String(row.node.value ?? "")}
          options={values.map((value) => ({ value }))}
          filterOption={(input, option) =>
            String(option?.value).toLowerCase().includes(input.toLowerCase())
          }
          onChange={(value) => changeNode({ ...row.node, value })}
          placeholder="문자열 입력"
        />
      ) : (
        <Input
          aria-label={`${label} 값`}
          type={row.node.kind === "date" ? "date" : "text"}
          inputMode={row.node.kind === "number" ? "decimal" : undefined}
          value={String(row.node.value ?? "")}
          placeholder={row.node.kind === "number" ? "예: 3, 3.5" : undefined}
          onChange={(e) => changeNode({ ...row.node, value: e.target.value })}
        />
      )}
      {row.node.kind === "date" && (
        <p className="field-note">YYYY-MM-DD 문자열로 저장합니다.</p>
      )}
      {failed && (
        <p role="status" className="field-note">
          추천을 불러오지 못했어요. 직접 입력하거나 항목 이름을 다시 검색해
          주세요.
        </p>
      )}
    </div>
  );
}
function Fields({
  category,
  path,
  rows,
  onChange,
  isArray = false,
}: {
  category: CriteriaCategory;
  path: string[];
  rows: ConditionRow[];
  onChange: (rows: ConditionRow[]) => void;
  isArray?: boolean;
}) {
  return (
    <div>
      {rows.map((row, index) => (
        <FieldInput
          key={index}
          category={category}
          parentPath={path}
          row={row}
          index={index}
          isArray={isArray}
          onChange={(row) =>
            onChange(rows.map((v, i) => (i === index ? row : v)))
          }
          onRemove={() => onChange(rows.filter((_, i) => i !== index))}
        />
      ))}
      <Button
        icon={<PlusOutlined aria-hidden="true" />}
        disabled={rows.length >= (isArray ? 100 : 30) || path.length >= 8}
        onClick={() =>
          onChange([...rows, { key: "", node: newNode("string") }])
        }
      >
        {path.length
          ? `${path[path.length - 1] || "하위"} 항목 추가`
          : `${criteriaCategories[category]} 항목 추가`}
      </Button>
    </div>
  );
}
export function CriteriaEditor({
  category,
  value = [],
  onChange,
}: {
  category: CriteriaCategory;
  value?: ConditionRow[];
  onChange?: (value: ConditionRow[]) => void;
}) {
  return (
    <Fields
      category={category}
      path={[]}
      rows={value}
      onChange={(rows) => onChange?.(rows)}
    />
  );
}
