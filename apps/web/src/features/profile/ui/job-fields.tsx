"use client";
import { useCallback } from "react";
import { Badge } from "@/shared/ui/badge";
import { Icon } from "@/shared/ui/icon";
import { searchInfo } from "../api/profile-client";
import { type JobForm, newId } from "../model/form";
import { SearchPicker } from "./search-picker";

/**
 * 직무 태그 (contract "프로필과 버전 이력" › 직무): up to ten, each picked from `GET /v1/jobs` or typed; a typed one waits for approval
 * (`pending`·`rejected` badge) and only approved ones reach the card's `job` line.
 */
export function JobFields({ jobs, onChange }: { jobs: JobForm[]; onChange: (jobs: JobForm[]) => void }) {
  const search = useCallback(
    (q: string) => searchInfo("jobs", q).then((page) => page.items.map((item) => ({ id: item.id, name: item.name, hint: item.parent?.name }))),
    [],
  );
  const update = (index: number, patch: Partial<JobForm>) => onChange(jobs.map((job, at) => (at === index ? { ...job, ...patch } : job)));
  return (
    <div className="flex flex-col gap-3">
      {jobs.map((job, index) => (
        <div key={job.id} className="flex items-end gap-2">
          <div className="flex-1">
            <SearchPicker
              label={`직무 ${index + 1}`}
              placeholder="예: 프론트엔드 개발"
              value={job.jobName}
              pickedId={job.jobId}
              search={search}
              onChange={({ id, name }) => update(index, { jobId: id, jobName: name, approval: id ? "approved" : job.approval })}
            />
          </div>
          {job.approval && job.approval !== "approved" && !job.jobId && (
            <Badge title={job.approvalReason ?? undefined} className="mb-2">
              {job.approval === "pending" ? "승인 대기" : "거절됨"}
            </Badge>
          )}
          <button type="button" aria-label={`직무 ${index + 1} 삭제`} className="inline-flex size-9 items-center justify-center text-danger" onClick={() => onChange(jobs.filter((_, at) => at !== index))}>
            <Icon name="trash" />
          </button>
        </div>
      ))}
      {jobs.length < 10 && (
        <button type="button" className="flex min-h-11 items-center gap-2 text-body text-teal" onClick={() => onChange([...jobs, { id: newId(), jobName: "" }])}>
          <Icon name="plus" size="inline" />
          직무 추가
        </button>
      )}
      <p className="text-footnote text-secondary-label">직접 입력한 직무는 관리자가 승인한 뒤 명함에 보여요.</p>
    </div>
  );
}
