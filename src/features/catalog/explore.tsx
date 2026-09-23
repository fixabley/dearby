"use client";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { organizations, programs } from "./data";
import {
  emptyFilters,
  filterLabels,
  searchPrograms,
  suggestions,
  type Filters,
} from "./model";
import { FilterControls } from "./filters";
import { ProgramCard } from "./card";
import { SaveButton, useSaved } from "../saved/provider";
export function Explore() {
  const params = useSearchParams();
  return (
    <ExploreView
      key={`${params.get("view")}:${params.get("q")}`}
      view={params.get("view") || "all"}
      query={params.get("q") || ""}
    />
  );
}
function ExploreView({ view, query }: { view: string; query: string }) {
  const [f, setF] = useState<Filters>({
      ...emptyFilters,
      query,
      openOnly: view === "open",
    }),
    [undo, setUndo] = useState<Filters | null>(null),
    [removed, setRemoved] = useState<string[]>([]),
    [savedTab, setSavedTab] = useState("programs");
  const { saved, ready } = useSaved();
  const data =
    view === "saved"
      ? programs.filter((p) => saved.programs.includes(p.id))
      : programs;
  const results = searchPrograms(data, f);
  const options = !results.length ? suggestions(data, f) : [];
  const change = (next: Filters) => {
    setF(next);
    setUndo(null);
  };
  return (
    <>
      <FilterControls filters={f} onChange={change} />
      <div className="listing-heading">
        <div>
          <h1>
            {view === "saved"
              ? "나의 스크랩"
              : view === "open"
                ? "지금, 함께할 수 있는 경험"
                : f.query
                  ? `“${f.query}” 검색 결과`
                  : "당신의 다음 경험"}
          </h1>
          <p>
            {view === "saved"
              ? "이 브라우저에 저장한 프로그램과 조직"
              : "작은 관심이 새로운 가능성이 되는 곳"}
          </p>
        </div>
        <span className="result-count" aria-live="polite">
          프로그램 {results.length}개
        </span>
      </div>
      {view === "saved" && (
        <div className="saved-tabs">
          <button
            className={savedTab === "programs" ? "selected" : ""}
            onClick={() => setSavedTab("programs")}
          >
            프로그램 {saved.programs.length}
          </button>
          <button
            className={savedTab === "organizations" ? "selected" : ""}
            onClick={() => setSavedTab("organizations")}
          >
            조직 {saved.organizations.length}
          </button>
          <span>스크랩은 추천 학습에 사용되지 않아요.</span>
        </div>
      )}
      {filterLabels(f).length > 0 && (
        <div className="applied-filters">
          <span>적용 중</span>
          {filterLabels(f).map((x) => (
            <span className="applied-tag" key={x}>
              {x}
            </span>
          ))}
          <button onClick={() => change({ ...emptyFilters })}>
            필터 초기화
          </button>
        </div>
      )}
      {undo && (
        <div className="undo-notice" role="status">
          해제된 조건: {removed.join(", ")}
          <button
            onClick={() => {
              setF(undo);
              setUndo(null);
            }}
          >
            되돌리기
          </button>
        </div>
      )}
      {view === "saved" && !ready ? (
        <p role="status">스크랩을 불러오는 중입니다.</p>
      ) : view === "saved" && savedTab === "organizations" ? (
        <div className="organization-list">
          {organizations
            .filter((o) => saved.organizations.includes(o.id))
            .map((o) => (
              <article key={o.id}>
                <span className="avatar large" style={{ background: o.color }}>
                  {o.initial}
                </span>
                <div>
                  <h2>{o.name}</h2>
                  <p>{o.description}</p>
                  <p>
                    샘플 조직 · 프로그램{" "}
                    {programs.filter((p) => p.orgId === o.id).length}개
                  </p>
                  <div className="org-programs">
                    {programs
                      .filter((p) => p.orgId === o.id)
                      .map((p) => (
                        <a key={p.id} href={`/programs/${p.id}`}>
                          {p.title} ↗
                        </a>
                      ))}
                  </div>
                </div>
                <SaveButton kind="organizations" id={o.id} />
              </article>
            ))}
          {!saved.organizations.length && (
            <div className="empty-state">
              <h2>아직 스크랩한 조직이 없어요</h2>
              <p>프로그램 상세에서 마음에 드는 조직을 저장해 보세요.</p>
            </div>
          )}
        </div>
      ) : results.length ? (
        <div className="program-grid">
          {results.map((p) => (
            <ProgramCard key={p.id} program={p} filters={f} />
          ))}
        </div>
      ) : (
        <div className="empty-state">
          <span className="empty-icon">⌕</span>
          <h2>
            {view === "saved" && !saved.programs.length
              ? "다시 만나고 싶은 경험을 모아보세요"
              : "모든 조건에 맞는 프로그램이 없어요"}
          </h2>
          <p>
            {view === "saved" && !saved.programs.length
              ? "프로그램의 하트를 누르면 여기에 저장됩니다."
              : "선택한 조건 중 일부를 유지하면 이런 경험을 찾을 수 있어요."}
          </p>
          <div className="suggestions">
            {options.map((s, i) => (
              <button
                key={i}
                onClick={() => {
                  setUndo(f);
                  setF(s.filters);
                  setRemoved(s.removed);
                }}
              >
                <b>{filterLabels(s.filters).join(" + ")}</b>
                <span>{s.count}개 프로그램 →</span>
                <small>해제: {s.removed.join(", ")}</small>
              </button>
            ))}
          </div>
          <button
            className="primary"
            onClick={() => change({ ...emptyFilters })}
          >
            필터 초기화
          </button>
        </div>
      )}
    </>
  );
}
