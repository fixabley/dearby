"use client";
import { useSaved } from "./provider";
import type { Saved } from "./storage";
export function SaveButton({
  kind,
  id,
  compact = false,
}: {
  kind: keyof Saved;
  id: string;
  compact?: boolean;
}) {
  const { saved, ready, issue, toggle } = useSaved();
  const on = saved[kind].includes(id);
  const label = kind === "programs" ? "프로그램" : "조직";
  return (
    <button
      className={`save-button ${on ? "is-saved" : ""} ${compact ? "compact" : ""}`}
      disabled={!ready || issue === "read" || issue === "corrupt"}
      aria-pressed={on}
      aria-label={`${label} 스크랩${on ? " 해제" : ""}`}
      onClick={() => toggle(kind, id)}
    >
      <span aria-hidden="true">{on ? "♥" : "♡"}</span>
      {!compact && `${label} ${on ? "저장됨" : "스크랩"}`}
    </button>
  );
}
