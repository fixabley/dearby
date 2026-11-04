"use client";
import { useCallback, useRef, useState } from "react";
import { errorMessage, RequestError } from "@/lib/client";
import type { ProfileHistory } from "@/lib/account-models";
import { ROLE_OWNER_KINDS, type RoleOwnerKind } from "@/lib/models";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { Icon } from "@/shared/ui/icon";
import { deleteEvidence, searchRoles, searchUnits, uploadEvidence } from "../api/profile-client";
import { type HistoryForm, newId } from "../model/form";
import { SearchPicker } from "./search-picker";

const EVIDENCE_TYPES = ["image/jpeg", "image/png", "image/webp", "application/pdf"];
const MAX_EVIDENCE_BYTES = 5 * 1024 * 1024;
const UNIT_LABEL: Record<RoleOwnerKind, string> = { organization: "조직", program: "프로그램", activity: "활동" };
const unitLabel = (kind: RoleOwnerKind) => UNIT_LABEL[kind];

/**
 * 활동 이력 입력 (contract "이력 (History) — 역할 매칭", 2026-11-03 퍼널 하나): 어디에서(조직·프로그램·활동 통합 검색, 없으면 종류를 고르고
 * 이름 직접 입력) → 역할(그 단위의 역할 목록, 없으면 직접 입력) → 기간 → 설명. Saved histories show approval·verification badges and
 * their 인증자료 (contract "인증자료": up to three files, 5MB, JPEG·PNG·WebP·PDF; uploading makes the history `pending`).
 */
export function HistoryFields({
  histories,
  saved,
  onChange,
  onEvidenceChanged,
}: {
  histories: HistoryForm[];
  /** Histories the server already has, by id: only these can carry 인증자료. */
  saved: Record<string, ProfileHistory>;
  onChange: (histories: HistoryForm[]) => void;
  /** 인증자료 uploaded or removed: the owner reloads the profile to show the server's list. */
  onEvidenceChanged: () => void;
}) {
  return (
    <div className="flex flex-col gap-4">
      {histories.map((history, index) => (
        <HistoryRow
          key={history.id}
          index={index}
          history={history}
          server={saved[history.id]}
          onChange={(patch) => onChange(histories.map((item, at) => (at === index ? { ...item, ...patch } : item)))}
          onRemove={() => onChange(histories.filter((_, at) => at !== index))}
          onEvidenceChanged={onEvidenceChanged}
        />
      ))}
      <button
        type="button"
        className="flex min-h-11 items-center gap-2 text-body text-teal"
        onClick={() => onChange([...histories, { id: newId(), ownerKind: "organization", ownerName: "", roleName: "", startDate: "", endDate: "", description: "" }])}
      >
        <Icon name="plus" size="inline" />
        이력 추가
      </button>
      <p className="text-footnote text-secondary-label">직접 입력한 조직·프로그램·활동·역할은 관리자가 승인한 뒤 명함에 넣을 수 있어요. 인증자료는 저장한 이력에 올릴 수 있어요.</p>
    </div>
  );
}

/** One history: 어디에서 → 역할 → 기간 → 설명. Changing the unit drops the role, because a role belongs to its unit. */
function HistoryRow({
  index,
  history,
  server,
  onChange,
  onRemove,
  onEvidenceChanged,
}: {
  index: number;
  history: HistoryForm;
  server?: ProfileHistory;
  onChange: (patch: Partial<HistoryForm>) => void;
  onRemove: () => void;
  onEvidenceChanged: () => void;
}) {
  const { ownerKind, ownerId } = history;
  const roles = useCallback(
    (q: string) => (ownerId === undefined ? Promise.resolve([]) : searchRoles({ kind: ownerKind, id: ownerId }, q).then((page) => page.items.map((role) => ({ id: role.id, name: role.name })))),
    [ownerKind, ownerId],
  );
  return (
    <div className="flex flex-col gap-3 rounded-control bg-fill/50 p-3">
      <div className="flex items-center justify-between gap-2">
        <span className="text-subheadline font-semibold">이력 {index + 1}</span>
        <span className="flex items-center gap-1.5">
          {server && server.approval !== "approved" && <Badge title={server.approvalReason ?? undefined}>{server.approval === "pending" ? "승인 대기" : "거절됨"}</Badge>}
          {server && (server.verification === "verified" ? <Badge variant="tinted">✓ 확인됨</Badge> : <Badge>미확인</Badge>)}
          <button type="button" aria-label={`이력 ${index + 1} 삭제`} className="inline-flex size-9 items-center justify-center text-danger" onClick={onRemove}>
            <Icon name="trash" />
          </button>
        </span>
      </div>
      <SearchPicker<RoleOwnerKind>
        label="어디에서"
        required
        placeholder="조직·프로그램·활동 이름 검색"
        value={history.ownerName}
        pickedId={ownerId}
        search={searchUnits}
        kindLabel={unitLabel}
        onChange={({ id, name, kind }) =>
          onChange({ ownerId: id, ownerName: name, ownerKind: kind ?? ownerKind, ...(id !== ownerId && { roleId: undefined, roleName: "" }) })
        }
      />
      {ownerId === undefined && (
        <fieldset className="flex flex-wrap gap-3">
          <legend className="mb-1 text-footnote text-secondary-label">직접 입력한 곳의 종류</legend>
          {ROLE_OWNER_KINDS.map((kind) => (
            <label key={kind} className="flex min-h-9 items-center gap-1.5 text-subheadline">
              <input type="radio" name={`history-unit-kind-${history.id}`} className="size-4 accent-teal" checked={ownerKind === kind} onChange={() => onChange({ ownerKind: kind })} />
              {UNIT_LABEL[kind]}
            </label>
          ))}
        </fieldset>
      )}
      <SearchPicker
        label="역할"
        required
        placeholder={ownerId === undefined ? "예: 참가자, 운영진" : "역할을 고르거나 입력"}
        value={history.roleName}
        pickedId={history.roleId}
        search={roles}
        listOnFocus={ownerId !== undefined}
        onChange={({ id, name }) => onChange({ roleId: id, roleName: name })}
      />
      <div className="grid grid-cols-2 gap-2">
        <label className="flex flex-col gap-1">
          <span className="text-footnote text-secondary-label">
            시작일<span className="text-danger"> *</span>
          </span>
          <input type="date" value={history.startDate} className={dateClass} onChange={(event) => onChange({ startDate: event.target.value })} />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-footnote text-secondary-label">종료일 (비우면 진행 중)</span>
          <input type="date" value={history.endDate} min={history.startDate || undefined} className={dateClass} onChange={(event) => onChange({ endDate: event.target.value })} />
        </label>
      </div>
      <label className="flex flex-col gap-1">
        <span className="text-footnote text-secondary-label">설명 (선택)</span>
        <textarea
          value={history.description}
          maxLength={2000}
          rows={2}
          className="w-full resize-none rounded-control bg-fill px-2 py-2 text-body text-label outline-none focus-visible:outline-2 focus-visible:outline-teal"
          onChange={(event) => onChange({ description: event.target.value })}
        />
      </label>
      {server && <EvidenceList history={server} onChanged={onEvidenceChanged} />}
    </div>
  );
}
const dateClass = "h-9 rounded-control bg-fill px-2 text-body text-label outline-none focus-visible:outline-2 focus-visible:outline-teal";

/** 인증자료 of one saved history: the server's list with 삭제, and 파일 올리기 while under three. */
function EvidenceList({ history, onChanged }: { history: ProfileHistory; onChanged: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const input = useRef<HTMLInputElement>(null);
  const evidence = history.evidence ?? [];
  async function upload(file: File | undefined) {
    if (!file) return;
    if (!EVIDENCE_TYPES.includes(file.type)) return setError("JPEG·PNG·WebP·PDF 파일만 올릴 수 있어요.");
    if (file.size > MAX_EVIDENCE_BYTES) return setError("파일은 5MB까지 올릴 수 있어요.");
    setBusy(true);
    setError("");
    try {
      await uploadEvidence(history.id, file);
      onChanged();
    } catch (caught) {
      setError(caught instanceof RequestError && caught.detail ? caught.detail : errorMessage(caught));
    } finally {
      setBusy(false);
      if (input.current) input.current.value = "";
    }
  }
  async function remove(id: string) {
    setBusy(true);
    setError("");
    try {
      await deleteEvidence(history.id, id);
      onChanged();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="flex flex-col gap-1.5 border-t border-separator pt-2">
      <span className="text-footnote font-semibold text-secondary-label">인증자료 {evidence.length}/3</span>
      {evidence.map((file) => (
        <div key={file.id} className="flex items-center gap-2 text-subheadline">
          <a href={`/api/profile/histories/${history.id}/evidence/${file.id}`} target="_blank" rel="noopener noreferrer" className="flex-1 truncate text-teal underline">
            {file.fileName || "파일"}
          </a>
          <span className="text-footnote text-secondary-label">{Math.max(1, Math.round(file.size / 1024))}KB</span>
          <button type="button" aria-label={`${file.fileName || "파일"} 삭제`} className="inline-flex size-9 items-center justify-center text-danger" disabled={busy} onClick={() => remove(file.id)}>
            <Icon name="trash" />
          </button>
        </div>
      ))}
      {evidence.length < 3 && (
        <>
          <input ref={input} type="file" accept={EVIDENCE_TYPES.join(",")} className="sr-only" onChange={(event) => upload(event.target.files?.[0])} />
          <Button variant="plain" size="sm" className="self-start px-0" disabled={busy} onClick={() => input.current?.click()}>
            <Icon name="plus" size="inline" />
            {busy ? "올리는 중…" : "파일 올리기"}
          </Button>
        </>
      )}
      {error && (
        <p role="alert" className="text-footnote text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
