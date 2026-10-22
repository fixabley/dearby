"use client";
import { CenteredMessage } from "./centered-message";

/** iOS `ProgressView` with a label: a spinning teal ring and the loading text, announced politely. */
export function Loading({ label = "불러오는 중이에요." }: { label?: string }) {
  return (
    <div role="status" className="flex items-center justify-center gap-2 py-10 text-subheadline text-secondary-label">
      <span aria-hidden="true" className="size-5 animate-spin rounded-full border-2 border-fill border-t-teal" />
      {label}
    </div>
  );
}

/** Request failure preset of CenteredMessage: danger circle, fixed title, the error text and a retry CTA. */
export function Failure({ message, retry, compact }: { message: string; retry: () => void; compact?: boolean }) {
  return (
    <CenteredMessage
      icon="warning"
      tone="danger"
      title="다시 확인해 주세요"
      message={message}
      primary={{ label: "다시 시도", onClick: retry }}
      role="alert"
      compact={compact}
    />
  );
}
