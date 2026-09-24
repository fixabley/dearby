"use client";
import { OrganizationAvatar } from "./organization-avatar";
import { useSearchParams } from "next/navigation";
import { readFilters, filtersUrl } from "./search-state";
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
import { useSaved } from "../saved/provider";
import { SaveButton } from "../saved/save-button";
export function Explore() {
  const params = useSearchParams();
  const view = params.get("view") || "all";
  const savedTab = params.get("tab") || "programs";
  const f = readFilters(params);
  const currentUrl = "/" + (params.size ? "?" + params.toString() : "");
  const [undo, setUndo] = useState<{
    previous: string;
    applied: string;
    removed: string[];
  } | null>(null);
  const { saved, ready } = useSaved();
  const showOrganizations = view === "saved" && savedTab === "organizations";
  const setSavedTab = (tab: string) => {
    const next = new URLSearchParams(params.toString());
    next.set("tab", tab);
    window.history.replaceState(null, "", "/?" + next.toString());
    setUndo(null);
  };
  const data =
    view === "saved"
      ? programs.filter((p) => saved.programs.includes(p.id))
      : programs;
  const results = searchPrograms(data, f);
  const options = !results.length ? suggestions(data, f) : [];
  const change = (next: Filters) => {
    window.history.replaceState(null, "", filtersUrl(next, params.toString()));
    setUndo(null);
  };
  return (
    <>
      {!showOrganizations && <FilterControls filters={f} onChange={change} />}
      <div className="listing-heading">
        <div>
          <h1>
            {view === "saved"
              ? "나의 스크랩"
              : f.openOnly
                ? "지금, 함께할 수 있는 경험"
                : f.query
                  ? `“${f.query}” 검색 결과`
                  : "다음 경험을 만나보세요"}
          </h1>
          <p>
            {view === "saved"
              ? "이 브라우저에 저장한 프로그램과 조직"
              : "기술을 듣고, 동료와 만들고 · 컨퍼런스부터 연합동아리까지"}
          </p>
        </div>
        <span className="result-count" aria-live="polite">
          {showOrganizations
            ? `조직 ${saved.organizations.length}개`
            : `프로그램 ${results.length}개`}
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
      {!showOrganizations && filterLabels(f).length > 0 && (
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
      {undo && undo.applied === currentUrl && (
        <div className="undo-notice" role="status">
          해제된 조건: {undo.removed.join(", ")}
          <button
            onClick={() => {
              window.history.replaceState(null, "", undo.previous);
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
                <OrganizationAvatar organization={o} large />
                <div>
                  <h2>{o.name}</h2>
                  <p>{o.description}</p>
                  <p>
                    프로그램{" "}
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
          {results.map((p, index) => (
            <ProgramCard key={p.id} program={p} filters={f} eager={index < 4} />
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
                  const applied = filtersUrl(s.filters, params.toString());
                  setUndo({
                    previous: currentUrl,
                    applied,
                    removed: s.removed,
                  });
                  window.history.replaceState(null, "", applied);
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
