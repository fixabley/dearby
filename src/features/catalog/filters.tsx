"use client";
import { useRef } from "react";
import { roles, experiences, type Filters } from "./model";
export function FilterControls({
  filters: f,
  onChange,
}: {
  filters: Filters;
  onChange: (f: Filters) => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const toggle = (field: "roles" | "experiences", value: string) => {
    const values = f[field].includes(value)
      ? f[field].filter((x) => x !== value)
      : [...f[field], value];
    onChange({
      ...f,
      [field]: values,
      priority:
        field === "experiences" && !values.includes(f.priority)
          ? ""
          : f.priority,
    });
  };
  return (
    <>
      <div className="category-tabs" role="group" aria-label="프로그램 유형">
        {["", "컨퍼런스", "연합동아리"].map(category => <button key={category} aria-pressed={f.category === category} className={f.category === category ? "selected" : ""} onClick={() => onChange({ ...f, category })}>{category || "전체 경험"}</button>)}
      </div>
      <div className="chip-row" aria-label="분야 빠른 필터">
        <button
          className={!f.roles.length ? "chip selected" : "chip"}
          onClick={() => onChange({ ...f, roles: [] })}
        >
          전체
        </button>
        {roles.map((r) => (
          <button
            className={f.roles.includes(r) ? "chip selected" : "chip"}
            aria-pressed={f.roles.includes(r)}
            key={r}
            onClick={() => toggle("roles", r)}
          >
            {r}
          </button>
        ))}
        <span className="chip-divider" />
        <button
          className="chip filter-open"
          onClick={() => dialog.current?.showModal()}
        >
          ☷ 경험·상세 필터
          {f.experiences.length ? ` (${f.experiences.length})` : ""}
        </button>
      </div>
      <dialog
        ref={dialog}
        className="filter-dialog"
        aria-labelledby="filter-title"
      >
        <div className="dialog-heading">
          <h2 id="filter-title">나에게 맞는 경험 찾기</h2>
          <button
            className="icon-button"
            aria-label="필터 닫기"
            onClick={() => dialog.current?.close()}
          >
            ✕
          </button>
        </div>
        <p className="muted">분야와 경험을 함께 충족하는 프로그램을 찾아요.</p>
        <fieldset>
          <legend>관심 분야</legend>
          <div className="filter-options">
            {roles.map((r) => (
              <label key={r}>
                <input
                  type="checkbox"
                  checked={f.roles.includes(r)}
                  onChange={() => toggle("roles", r)}
                />
                {r}
              </label>
            ))}
          </div>
          <label className="check-line">
            <input
              type="checkbox"
              checked={f.allRoles}
              onChange={(e) => onChange({ ...f, allRoles: e.target.checked })}
            />
            선택한 분야 모두 포함 <small>같은 공고 기준</small>
          </label>
        </fieldset>
        <fieldset>
          <legend>
            지금 원하는 경험 <small>하나라도 일치</small>
          </legend>
          <div className="filter-options experience-options">
            {Object.entries(experiences).map(([id, e]) => (
              <label key={id}>
                <input
                  type="checkbox"
                  checked={f.experiences.includes(id)}
                  onChange={() => toggle("experiences", id)}
                />
                {e.label}
                {!e.target &&
                  f.experiences.some(
                    (key) =>
                      experiences[key].action === e.action &&
                      !!experiences[key].target,
                  ) && (
                    <small className="child-selected">세부 항목 선택됨</small>
                  )}
              </label>
            ))}
          </div>
          <p className="filter-hint">
            세부 항목은 표시된 행위에 속합니다. 세부 선택이 전체 조건을 추가하지
            않아요.
          </p>
        </fieldset>
        <label className="priority-label">
          가장 원하는 경험
          <select
            value={f.priority}
            onChange={(e) => onChange({ ...f, priority: e.target.value })}
          >
            <option value="">지정하지 않음</option>
            {f.experiences.map((id) => (
              <option key={id} value={id}>
                {experiences[id].label}
              </option>
            ))}
          </select>
        </label>
        <button
          className="primary full"
          onClick={() => dialog.current?.close()}
        >
          결과 보기
        </button>
      </dialog>
    </>
  );
}
