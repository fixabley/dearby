import { Button, Input, Select, Space } from "antd";
import { DeleteOutlined, PlusOutlined } from "@ant-design/icons";
import {
  type DraftCondition,
  type DraftGroup,
  type DraftItem,
  FIELDS,
  fieldByEn,
  newCondition,
  newGroup,
  OTHER,
  suggestEn,
} from "./eligibility";

/** Groups may nest three deep (contract); the root is depth 1. */
export const MAX_DEPTH = 3;
const OPS = [
  { value: "all", label: "모두 충족" },
  { value: "any", label: "하나 이상" },
];
const FIELD_OPTIONS = FIELDS.map((f) => ({ value: f.en, label: `${f.ko} (${f.en})` }));

/**
 * Form editor for 대상자 조건: a tree of groups (모두 충족/하나 이상) and conditions whose inputs follow the field's kind
 * (숫자 범위 부터·까지, 정해진 값 다중 선택, 자유 값 + 영문 쌍, 기타 메모). Values stay strings while editing; `fromDraft`
 * resolves them when saving. Every control carries an accessibility label like the old criteria editor did.
 */
export function EligibilityEditor({ value, onChange }: { value: DraftGroup; onChange: (next: DraftGroup) => void }) {
  return <GroupRow group={value} depth={1} label="대상자 조건" onChange={onChange} />;
}

function GroupRow({
  group,
  depth,
  label,
  onChange,
  onRemove,
}: {
  group: DraftGroup;
  depth: number;
  label: string;
  onChange: (next: DraftGroup) => void;
  onRemove?: () => void;
}) {
  const replace = (index: number, item: DraftItem) => onChange({ ...group, items: group.items.map((it, i) => (i === index ? item : it)) });
  const remove = (index: number) => onChange({ ...group, items: group.items.filter((_, i) => i !== index) });
  const append = (item: DraftItem) => onChange({ ...group, items: [...group.items, item] });
  let conditionNumber = 0;
  return (
    <div className={`eligibility-group${depth === 1 ? " root" : ""}`}>
      <div className="eligibility-group-heading">
        <Select
          aria-label={`${label} 결합 방식`}
          value={group.op}
          options={OPS}
          onChange={(op) => onChange({ ...group, op })}
          style={{ width: 130 }}
          popupMatchSelectWidth={false}
        />
        <span className="field-note">{group.op === "any" ? "아래 중 하나만 맞으면 돼요." : "아래를 모두 충족해야 해요."}</span>
        {onRemove && (
          <Button aria-label={`${label} 제거`} icon={<DeleteOutlined />} onClick={onRemove}>
            제거
          </Button>
        )}
      </div>
      {group.items.length === 0 && <p className="field-note">조건이 없어요. 아래에서 조건이나 그룹을 추가해 주세요.</p>}
      {group.items.map((item, index) => {
        if (item.kind === "group")
          return (
            <GroupRow
              key={item.key}
              group={item}
              depth={depth + 1}
              label={`${label} > 그룹 ${index + 1}`}
              onChange={(next) => replace(index, next)}
              onRemove={() => remove(index)}
            />
          );
        conditionNumber += 1;
        return (
          <ConditionRow
            key={item.key}
            condition={item}
            label={`${label} > 조건 ${conditionNumber}`}
            onChange={(next) => replace(index, next)}
            onRemove={() => remove(index)}
          />
        );
      })}
      <Space wrap>
        <Button aria-label={`${label}에 조건 추가`} icon={<PlusOutlined />} onClick={() => append(newCondition())}>
          조건 추가
        </Button>
        <Button
          aria-label={`${label}에 그룹 추가`}
          icon={<PlusOutlined />}
          disabled={depth >= MAX_DEPTH}
          title={depth >= MAX_DEPTH ? "그룹은 3단계까지만 겹칠 수 있어요." : undefined}
          onClick={() => append(newGroup(group.op === "all" ? "any" : "all"))}
        >
          그룹 추가
        </Button>
      </Space>
    </div>
  );
}

function ConditionRow({
  condition,
  label,
  onChange,
  onRemove,
}: {
  condition: DraftCondition;
  label: string;
  onChange: (next: DraftCondition) => void;
  onRemove: () => void;
}) {
  const def = fieldByEn(condition.fieldEn) ?? OTHER;
  const populated = condition.from.trim() !== "" || condition.to.trim() !== "" || condition.values.length > 0;
  function changeField(fieldEn: string) {
    if (fieldEn === condition.fieldEn) return;
    if (populated && !window.confirm("항목을 바꾸면 이 조건의 값이 초기화됩니다. 바꾸시겠어요?")) return;
    onChange({ ...newCondition(fieldEn), key: condition.key, note: condition.note, noteEn: condition.noteEn });
  }
  const setValues = (kos: string[]) =>
    onChange({
      ...condition,
      values: kos.map((ko) => condition.values.find((v) => v.ko === ko) ?? { ko, en: def.values?.find((d) => d.ko === ko)?.en ?? suggestEn(ko) }),
    });
  return (
    <div className="eligibility-condition">
      <div className="eligibility-condition-heading">
        <Select
          aria-label={`${label} 항목`}
          value={condition.fieldEn}
          options={FIELD_OPTIONS}
          onChange={changeField}
          style={{ minWidth: 220 }}
          popupMatchSelectWidth={false}
          showSearch
          optionFilterProp="label"
        />
        <Button aria-label={`${label} 제거`} icon={<DeleteOutlined />} onClick={onRemove}>
          제거
        </Button>
      </div>
      {def.kind === "numeric" && (
        <Space wrap>
          <Input
            aria-label={`${label} 부터`}
            inputMode="decimal"
            placeholder="부터"
            addonAfter={def.unit}
            value={condition.from}
            onChange={(e) => onChange({ ...condition, from: e.target.value })}
            style={{ width: 150 }}
          />
          <span>~</span>
          <Input
            aria-label={`${label} 까지`}
            inputMode="decimal"
            placeholder="까지"
            addonAfter={def.unit}
            value={condition.to}
            onChange={(e) => onChange({ ...condition, to: e.target.value })}
            style={{ width: 150 }}
          />
          <span className="field-note">한쪽만 적으면 그쪽만 제한해요(포함 범위).</span>
        </Space>
      )}
      {def.kind === "fixed" && (
        <Select
          aria-label={`${label} 값`}
          mode="multiple"
          style={{ width: "100%" }}
          placeholder="해당하는 값을 고르세요"
          value={condition.values.map((v) => v.ko)}
          options={(def.values ?? []).map((v) => ({ value: v.ko, label: `${v.ko} (${v.en})` }))}
          onChange={setValues}
        />
      )}
      {def.kind === "free" && (
        <>
          <Select
            aria-label={`${label} 값`}
            mode="tags"
            style={{ width: "100%" }}
            placeholder="값을 입력하고 Enter"
            value={condition.values.map((v) => v.ko)}
            onChange={setValues}
            tokenSeparators={[","]}
            open={false}
          />
          {condition.values.map((v, i) => (
            <div key={v.ko} className="eligibility-twin">
              <span>{v.ko}</span>
              <Input
                aria-label={`${label} ${v.ko} 영문`}
                placeholder={def.en === "nationality" ? "국가 코드 (예: KR)" : "영문 표기"}
                value={v.en}
                onChange={(e) =>
                  onChange({ ...condition, values: condition.values.map((item, j) => (j === i ? { ...item, en: e.target.value } : item)) })
                }
                style={{ width: 220 }}
              />
            </div>
          ))}
          <p className="field-note">
            {def.en === "nationality" ? "영문은 ISO 3166-1 alpha-2 국가 코드로 적어요(대한민국 → KR)." : "영문 표기를 비우면 한글 값을 그대로 저장해요."}
          </p>
        </>
      )}
      {def.kind === "note" ? (
        <Space direction="vertical" style={{ width: "100%" }} size={6}>
          <Input aria-label={`${label} 내용`} placeholder="조건 내용(한글)" value={condition.note} onChange={(e) => onChange({ ...condition, note: e.target.value })} />
          <Input aria-label={`${label} 영문 내용`} placeholder="영문 내용(선택)" value={condition.noteEn} onChange={(e) => onChange({ ...condition, noteEn: e.target.value })} />
        </Space>
      ) : (
        <Input
          aria-label={`${label} 원문 메모`}
          placeholder="원문 메모(선택) — 공지의 해당 구절"
          value={condition.note}
          onChange={(e) => onChange({ ...condition, note: e.target.value })}
        />
      )}
    </div>
  );
}
