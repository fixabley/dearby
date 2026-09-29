import { test } from "node:test";
import assert from "node:assert/strict";
import {
  criteriaRows,
  serializeCriteria,
  newNode,
  type Criteria,
} from "../src/criteria";
const data: Criteria = {
  audience: { 직무: ["개발자", "디자이너"], 재직: true },
  qualification: {
    연차: 3,
    경력: { 부터: 3, 까지: 4 },
    시작일: "2026-10-01",
    옵션: null,
    중첩: {
      배열: [3, "문자열", false, null, { 하위: { 키: "값" } }, [1, 2]],
      빈객체: {},
      빈배열: [],
    },
  },
  roles: { 역할: ["프론트엔드 개발자"] },
};
test("all JSON types, ISO date and recursively nested objects/arrays round-trip without coercion", () => {
  assert.deepEqual(serializeCriteria(criteriaRows(data)), data);
  assert.equal(
    typeof serializeCriteria(criteriaRows(data)).qualification.연차,
    "number",
  );
});
test("keys normalize whitespace/Unicode and reject duplicate keys; invalid numeric/date/range input is preserved until corrected", () => {
  const rows = criteriaRows(data);
  rows.audience.push({ key: " 직무 ", node: newNode("string") });
  assert.throws(() => serializeCriteria(rows), /중복/);
  for (const value of [
    "",
    "NaN",
    "Infinity",
    "9007199254740992",
    "3년",
    "0x10",
  ]) {
    const r = criteriaRows(data);
    r.qualification = [{ key: "연차", node: { kind: "number", value } }];
    assert.throws(() => serializeCriteria(r), /숫자/);
  }
  const r = criteriaRows(data);
  r.qualification = [
    { key: "날짜", node: { kind: "date", value: "2026-02-30" } },
  ];
  assert.throws(() => serializeCriteria(r), /날짜/);
  assert.throws(
    () =>
      serializeCriteria(
        criteriaRows({
          ...data,
          qualification: { 연차: { 부터: 4, 까지: 3 } },
        }),
      ),
    /부터/,
  );
  assert.deepEqual(
    serializeCriteria(
      criteriaRows({ ...data, qualification: { 연차: { 부터: 0 } } }),
    ).qualification,
    { 연차: { 부터: 0 } },
  );
});
test("nested duplicate keys and excessive depth are rejected, empty/null/false/zero values survive", () => {
  const r = criteriaRows(data);
  r.qualification = [
    {
      key: "중첩",
      node: {
        kind: "object",
        children: [
          { key: "key", node: newNode("null") },
          { key: " KEY ", node: newNode("null") },
        ],
      },
    },
  ];
  assert.throws(() => serializeCriteria(r), /중복/);
  let v: Criteria["qualification"] = { value: 0 };
  for (let i = 0; i < 9; i++) v = { nested: v };
  assert.throws(
    () => serializeCriteria(criteriaRows({ ...data, qualification: v })),
    /중첩/,
  );
  assert.deepEqual(
    serializeCriteria(
      criteriaRows({
        ...data,
        qualification: { empty: "", zero: 0, no: false, nil: null },
      }),
    ).qualification,
    { empty: "", zero: 0, no: false, nil: null },
  );
});
